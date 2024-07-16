package com.tencent.supersonic.headless.server.facade.service.impl;

import com.google.common.collect.Lists;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.common.pojo.enums.QueryType;
import com.tencent.supersonic.common.pojo.enums.TimeDimensionEnum;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SchemaElementMatch;
import com.tencent.supersonic.headless.api.pojo.SchemaElementType;
import com.tencent.supersonic.headless.api.pojo.SchemaItem;
import com.tencent.supersonic.headless.api.pojo.SchemaMapInfo;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.SemanticSchema;
import com.tencent.supersonic.headless.api.pojo.SqlEvaluation;
import com.tencent.supersonic.headless.api.pojo.SqlInfo;
import com.tencent.supersonic.headless.api.pojo.request.QueryMapReq;
import com.tencent.supersonic.headless.api.pojo.request.QueryNLReq;
import com.tencent.supersonic.headless.api.pojo.request.QuerySqlReq;
import com.tencent.supersonic.headless.api.pojo.response.DataSetMapInfo;
import com.tencent.supersonic.headless.api.pojo.response.DataSetResp;
import com.tencent.supersonic.headless.api.pojo.response.MapInfoResp;
import com.tencent.supersonic.headless.api.pojo.response.MapResp;
import com.tencent.supersonic.headless.api.pojo.response.ParseResp;
import com.tencent.supersonic.headless.api.pojo.response.SearchResult;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import com.tencent.supersonic.headless.chat.corrector.GrammarCorrector;
import com.tencent.supersonic.headless.chat.corrector.SchemaCorrector;
import com.tencent.supersonic.headless.chat.knowledge.builder.BaseWordBuilder;
import com.tencent.supersonic.headless.chat.query.SemanticQuery;
import com.tencent.supersonic.headless.server.facade.service.ChatLayerService;
import com.tencent.supersonic.headless.server.service.RetrieveService;
import com.tencent.supersonic.headless.api.pojo.MetaFilter;
import com.tencent.supersonic.headless.server.utils.ChatWorkflowEngine;
import com.tencent.supersonic.headless.server.utils.ComponentFactory;
import com.tencent.supersonic.headless.server.service.DataSetService;
import com.tencent.supersonic.headless.server.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class S2ChatLayerService implements ChatLayerService {
    @Autowired
    private SchemaService schemaService;
    @Autowired
    private DataSetService dataSetService;
    @Autowired
    private RetrieveService retrieveService;
    @Autowired
    private ChatWorkflowEngine chatWorkflowEngine;

    @Override
    public MapResp performMapping(QueryNLReq queryNLReq) {
        MapResp mapResp = new MapResp();
        ChatQueryContext queryCtx = buildChatQueryContext(queryNLReq);
        ComponentFactory.getSchemaMappers().forEach(mapper -> {
            mapper.map(queryCtx);
        });
        SchemaMapInfo mapInfo = queryCtx.getMapInfo();
        mapResp.setMapInfo(mapInfo);
        mapResp.setQueryText(queryNLReq.getQueryText());
        return mapResp;
    }

    @Override
    public MapInfoResp map(QueryMapReq queryMapReq) {

        QueryNLReq queryNLReq = new QueryNLReq();
        BeanUtils.copyProperties(queryMapReq, queryNLReq);
        List<DataSetResp> dataSets = dataSetService.getDataSets(queryMapReq.getDataSetNames(), queryMapReq.getUser());

        Set<Long> dataSetIds = dataSets.stream().map(SchemaItem::getId).collect(Collectors.toSet());
        queryNLReq.setDataSetIds(dataSetIds);
        MapResp mapResp = performMapping(queryNLReq);
        dataSetIds.retainAll(mapResp.getMapInfo().getDataSetElementMatches().keySet());
        return convert(mapResp, queryMapReq.getTopN(), dataSetIds);
    }

    @Override
    public ParseResp performParsing(QueryNLReq queryNLReq) {
        ParseResp parseResult = new ParseResp(queryNLReq.getQueryText());
        // build queryContext
        ChatQueryContext queryCtx = buildChatQueryContext(queryNLReq);

        chatWorkflowEngine.execute(queryCtx, parseResult);

        List<SemanticParseInfo> parseInfos = queryCtx.getCandidateQueries().stream()
                .map(SemanticQuery::getParseInfo).collect(Collectors.toList());
        parseResult.setSelectedParses(parseInfos);
        return parseResult;
    }

    public ChatQueryContext buildChatQueryContext(QueryNLReq queryNLReq) {
        SemanticSchema semanticSchema = schemaService.getSemanticSchema();
        Map<Long, List<Long>> modelIdToDataSetIds = dataSetService.getModelIdToDataSetIds();
        ChatQueryContext queryCtx = ChatQueryContext.builder()
                .queryFilters(queryNLReq.getQueryFilters())
                .semanticSchema(semanticSchema)
                .candidateQueries(new ArrayList<>())
                .mapInfo(new SchemaMapInfo())
                .modelIdToDataSetIds(modelIdToDataSetIds)
                .text2SQLType(queryNLReq.getText2SQLType())
                .mapModeEnum(queryNLReq.getMapModeEnum())
                .dataSetIds(queryNLReq.getDataSetIds())
                .build();
        BeanUtils.copyProperties(queryNLReq, queryCtx);
        return queryCtx;
    }

    public void correct(QuerySqlReq querySqlReq, User user) {
        SemanticParseInfo semanticParseInfo = correctSqlReq(querySqlReq, user);
        querySqlReq.setSql(semanticParseInfo.getSqlInfo().getCorrectedS2SQL());
    }

    @Override
    public SqlEvaluation validate(QuerySqlReq querySqlReq, User user) {
        SemanticParseInfo semanticParseInfo = correctSqlReq(querySqlReq, user);
        return semanticParseInfo.getSqlEvaluation();
    }

    @Override
    public List<SearchResult> retrieve(QueryNLReq queryNLReq) {
        return retrieveService.retrieve(queryNLReq);
    }

    private SemanticParseInfo correctSqlReq(QuerySqlReq querySqlReq, User user) {
        ChatQueryContext queryCtx = new ChatQueryContext();
        SemanticSchema semanticSchema = schemaService.getSemanticSchema();
        queryCtx.setSemanticSchema(semanticSchema);
        SemanticParseInfo semanticParseInfo = new SemanticParseInfo();
        SqlInfo sqlInfo = new SqlInfo();
        sqlInfo.setCorrectedS2SQL(querySqlReq.getSql());
        sqlInfo.setParsedS2SQL(querySqlReq.getSql());
        semanticParseInfo.setSqlInfo(sqlInfo);
        semanticParseInfo.setQueryType(QueryType.DETAIL);

        Long dataSetId = querySqlReq.getDataSetId();
        if (Objects.isNull(dataSetId)) {
            dataSetId = dataSetService.getDataSetIdFromSql(querySqlReq.getSql(), user);
        }
        SchemaElement dataSet = semanticSchema.getDataSet(dataSetId);
        semanticParseInfo.setDataSet(dataSet);

        ComponentFactory.getSemanticCorrectors().forEach(corrector -> {
            if (!(corrector instanceof GrammarCorrector || (corrector instanceof SchemaCorrector))) {
                corrector.correct(queryCtx, semanticParseInfo);
            }
        });
        log.info("chatQueryServiceImpl correct:{}", sqlInfo.getCorrectedS2SQL());
        return semanticParseInfo;
    }

    private MapInfoResp convert(MapResp mapResp, Integer topN, Set<Long> dataSetIds) {
        MapInfoResp mapInfoResp = new MapInfoResp();
        if (Objects.isNull(mapResp)) {
            return mapInfoResp;
        }
        BeanUtils.copyProperties(mapResp, mapInfoResp);
        MetaFilter metaFilter = new MetaFilter();
        metaFilter.setIds(new ArrayList<>(dataSetIds));
        List<DataSetResp> dataSetList = dataSetService.getDataSetList(metaFilter);
        Map<Long, DataSetResp> dataSetMap = dataSetList.stream()
                .collect(Collectors.toMap(DataSetResp::getId, d -> d));
        mapInfoResp.setDataSetMapInfo(getDataSetInfo(mapResp.getMapInfo(), dataSetMap, topN));
        mapInfoResp.setTerms(getTerms(mapResp.getMapInfo(), dataSetMap));
        return mapInfoResp;
    }

    private Map<String, DataSetMapInfo> getDataSetInfo(SchemaMapInfo mapInfo,
                                                       Map<Long, DataSetResp> dataSetMap,
                                                       Integer topN) {
        Map<String, DataSetMapInfo> map = new HashMap<>();
        Map<Long, List<SchemaElementMatch>> mapFields = getMapFields(mapInfo, dataSetMap);
        Map<Long, List<SchemaElementMatch>> topFields = getTopFields(topN, mapInfo, dataSetMap);
        for (Long dataSetId : mapInfo.getDataSetElementMatches().keySet()) {
            DataSetResp dataSetResp = dataSetMap.get(dataSetId);
            if (dataSetResp == null) {
                continue;
            }
            if (CollectionUtils.isEmpty(mapFields.get(dataSetId))) {
                continue;
            }
            DataSetMapInfo dataSetMapInfo = new DataSetMapInfo();
            dataSetMapInfo.setMapFields(mapFields.getOrDefault(dataSetId, Lists.newArrayList()));
            dataSetMapInfo.setTopFields(topFields.getOrDefault(dataSetId, Lists.newArrayList()));
            dataSetMapInfo.setName(dataSetResp.getName());
            dataSetMapInfo.setDescription(dataSetResp.getDescription());
            map.put(dataSetMapInfo.getName(), dataSetMapInfo);
        }
        return map;
    }

    private Map<Long, List<SchemaElementMatch>> getMapFields(SchemaMapInfo mapInfo,
                                                             Map<Long, DataSetResp> dataSetMap) {
        Map<Long, List<SchemaElementMatch>> result = new HashMap<>();
        for (Map.Entry<Long, List<SchemaElementMatch>> entry : mapInfo.getDataSetElementMatches().entrySet()) {
            List<SchemaElementMatch> values = entry.getValue().stream()
                    .filter(schemaElementMatch ->
                            !SchemaElementType.TERM.equals(schemaElementMatch.getElement().getType()))
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(values) && dataSetMap.containsKey(entry.getKey())) {
                result.put(entry.getKey(), values);
            }
        }
        return result;
    }

    private Map<Long, List<SchemaElementMatch>> getTopFields(Integer topN,
                                                             SchemaMapInfo mapInfo,
                                                             Map<Long, DataSetResp> dataSetMap) {
        Map<Long, List<SchemaElementMatch>> result = new HashMap<>();
        if (0 == topN) {
            return result;
        }
        SemanticSchema semanticSchema = schemaService.getSemanticSchema();
        for (Map.Entry<Long, List<SchemaElementMatch>> entry : mapInfo.getDataSetElementMatches().entrySet()) {
            Long dataSetId = entry.getKey();
            List<SchemaElementMatch> values = entry.getValue();
            DataSetResp dataSetResp = dataSetMap.get(dataSetId);
            if (dataSetResp == null || CollectionUtils.isEmpty(values)) {
                continue;
            }
            String dataSetName = dataSetResp.getName();
            //topN dimensions
            Set<SchemaElementMatch> dimensions = semanticSchema.getDimensions(dataSetId)
                    .stream().sorted(Comparator.comparing(SchemaElement::getUseCnt).reversed())
                    .limit(topN - 1).map(mergeFunction()).collect(Collectors.toSet());

            SchemaElementMatch timeDimensionMatch = getTimeDimension(dataSetId, dataSetName);
            dimensions.add(timeDimensionMatch);

            //topN metrics
            Set<SchemaElementMatch> metrics = semanticSchema.getMetrics(dataSetId)
                    .stream().sorted(Comparator.comparing(SchemaElement::getUseCnt).reversed())
                    .limit(topN).map(mergeFunction()).collect(Collectors.toSet());

            dimensions.addAll(metrics);
            result.put(dataSetId, new ArrayList<>(dimensions));
        }
        return result;
    }

    private Map<String, List<SchemaElementMatch>> getTerms(SchemaMapInfo mapInfo,
                                                           Map<Long, DataSetResp> dataSetNameMap) {
        Map<String, List<SchemaElementMatch>> termMap = new HashMap<>();
        Map<Long, List<SchemaElementMatch>> dataSetElementMatches = mapInfo.getDataSetElementMatches();
        for (Map.Entry<Long, List<SchemaElementMatch>> entry : dataSetElementMatches.entrySet()) {
            DataSetResp dataSetResp = dataSetNameMap.get(entry.getKey());
            if (dataSetResp == null) {
                continue;
            }
            List<SchemaElementMatch> terms = entry.getValue().stream().filter(schemaElementMatch
                            -> SchemaElementType.TERM.equals(schemaElementMatch.getElement().getType()))
                    .collect(Collectors.toList());
            termMap.put(dataSetResp.getName(), terms);
        }
        return termMap;
    }

    /***
     * get time dimension SchemaElementMatch
     * @param dataSetId
     * @param dataSetName
     * @return
     */
    private SchemaElementMatch getTimeDimension(Long dataSetId, String dataSetName) {
        SchemaElement element = SchemaElement.builder().dataSet(dataSetId).dataSetName(dataSetName)
                .type(SchemaElementType.DIMENSION).bizName(TimeDimensionEnum.DAY.getName()).build();

        SchemaElementMatch timeDimensionMatch = SchemaElementMatch.builder().element(element)
                .detectWord(TimeDimensionEnum.DAY.getChName()).word(TimeDimensionEnum.DAY.getChName())
                .similarity(1L).frequency(BaseWordBuilder.DEFAULT_FREQUENCY).build();

        return timeDimensionMatch;
    }

    private Function<SchemaElement, SchemaElementMatch> mergeFunction() {
        return schemaElement -> SchemaElementMatch.builder().element(schemaElement)
                .frequency(BaseWordBuilder.DEFAULT_FREQUENCY).word(schemaElement.getName()).similarity(1)
                .detectWord(schemaElement.getName()).build();
    }

}
