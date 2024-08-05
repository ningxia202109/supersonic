package com.tencent.supersonic.headless.chat.utils;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.Aggregator;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.DateConf;
import com.tencent.supersonic.common.pojo.Filter;
import com.tencent.supersonic.common.pojo.Order;
import com.tencent.supersonic.common.pojo.enums.AggOperatorEnum;
import com.tencent.supersonic.common.pojo.enums.AggregateTypeEnum;
import com.tencent.supersonic.common.pojo.enums.QueryType;
import com.tencent.supersonic.common.pojo.enums.TimeDimensionEnum;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.SqlInfo;
import com.tencent.supersonic.headless.api.pojo.request.QueryFilter;
import com.tencent.supersonic.headless.api.pojo.request.QueryMultiStructReq;
import com.tencent.supersonic.headless.api.pojo.request.QuerySqlReq;
import com.tencent.supersonic.headless.api.pojo.request.QueryStructReq;
import com.tencent.supersonic.headless.chat.query.QueryManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
public class QueryReqBuilder {

    public static QueryStructReq buildStructReq(SemanticParseInfo parseInfo) {
        QueryStructReq queryStructReq = new QueryStructReq();
        queryStructReq.setDataSetId(parseInfo.getDataSetId());
        queryStructReq.setDataSetName(parseInfo.getDataSet().getName());
        queryStructReq.setQueryType(parseInfo.getQueryType());
        queryStructReq.setDateInfo(rewrite2Between(parseInfo.getDateInfo()));

        List<Filter> dimensionFilters = getFilters(parseInfo.getDimensionFilters());
        queryStructReq.setDimensionFilters(dimensionFilters);

        List<Filter> metricFilters = parseInfo.getMetricFilters().stream()
                .map(chatFilter -> new Filter(chatFilter.getBizName(), chatFilter.getOperator(), chatFilter.getValue()))
                .collect(Collectors.toList());
        queryStructReq.setMetricFilters(metricFilters);

        addDateDimension(parseInfo);
        List<String> dimensions = parseInfo.getDimensions().stream().map(SchemaElement::getBizName)
                .collect(Collectors.toList());
        queryStructReq.setGroups(dimensions);
        queryStructReq.setLimit(parseInfo.getLimit());
        // only one metric is queried at once
        Set<SchemaElement> metrics = parseInfo.getMetrics();
        if (!CollectionUtils.isEmpty(metrics)) {
            SchemaElement metricElement = parseInfo.getMetrics().iterator().next();
            Set<Order> order = getOrder(parseInfo.getOrders(), parseInfo.getAggType(), metricElement);
            queryStructReq.setAggregators(getAggregatorByMetric(parseInfo.getAggType(), metricElement));
            queryStructReq.setOrders(new ArrayList<>(order));
        }

        deletionDuplicated(queryStructReq);

        return queryStructReq;
    }

    private static List<Filter> getFilters(Set<QueryFilter> queryFilters) {
        List<Filter> dimensionFilters = queryFilters.stream()
                .filter(chatFilter -> StringUtils.isNotEmpty(chatFilter.getBizName()))
                .map(chatFilter -> new Filter(chatFilter.getBizName(), chatFilter.getOperator(), chatFilter.getValue()))
                .collect(Collectors.toList());
        return dimensionFilters;
    }

    private static void deletionDuplicated(QueryStructReq queryStructReq) {
        if (!CollectionUtils.isEmpty(queryStructReq.getGroups()) && queryStructReq.getGroups().size() > 1) {
            Set<String> groups = new HashSet<>();
            groups.addAll(queryStructReq.getGroups());
            queryStructReq.getGroups().clear();
            queryStructReq.getGroups().addAll(groups);
        }
    }

    private static DateConf rewrite2Between(DateConf dateInfo) {
        DateConf dateInfoNew = new DateConf();
        BeanUtils.copyProperties(dateInfo, dateInfoNew);
        if (Objects.nonNull(dateInfo) && DateConf.DateMode.RECENT.equals(dateInfo.getDateMode())) {
            int unit = dateInfo.getUnit();
            int days = 1;
            switch (dateInfo.getPeriod()) {
                case Constants.DAY:
                    days = 1;
                    break;
                case Constants.WEEK:
                    days = 7;
                    break;
                case Constants.MONTH:
                    days = 30;
                    break;
                case Constants.YEAR:
                    days = 365;
                    break;
                default:
                    break;
            }
            String startDate = LocalDate.now().plusDays(-(unit * days)).toString();
            String endDate = LocalDate.now().plusDays(-1).toString();
            dateInfoNew.setDateMode(DateConf.DateMode.BETWEEN);
            dateInfoNew.setStartDate(startDate);
            dateInfoNew.setEndDate(endDate);
        }
        return dateInfoNew;
    }

    public static QueryMultiStructReq buildMultiStructReq(SemanticParseInfo parseInfo) {
        QueryStructReq queryStructReq = buildStructReq(parseInfo);
        QueryMultiStructReq queryMultiStructReq = new QueryMultiStructReq();
        List<QueryStructReq> queryStructReqs = Lists.newArrayList();
        for (Filter dimensionFilter : queryStructReq.getDimensionFilters()) {
            QueryStructReq req = new QueryStructReq();
            BeanUtils.copyProperties(queryStructReq, req);
            req.setDataSetId(parseInfo.getDataSetId());
            req.setDimensionFilters(Lists.newArrayList(dimensionFilter));
            req.setSqlInfo(parseInfo.getSqlInfo());
            queryStructReqs.add(req);
        }
        queryMultiStructReq.setQueryStructReqs(queryStructReqs);
        return queryMultiStructReq;
    }

    /**
     * convert to QueryS2SQLReq
     *
     * @param querySql
     * @param dataSetId
     * @return
     */
    public static QuerySqlReq buildS2SQLReq(String querySql, Long dataSetId) {
        QuerySqlReq querySQLReq = new QuerySqlReq();
        if (Objects.nonNull(querySql)) {
            querySQLReq.setSql(querySql);
        }
        querySQLReq.setDataSetId(dataSetId);
        return querySQLReq;
    }

    public static QuerySqlReq buildS2SQLReq(SqlInfo sqlInfo, Long dataSetId) {
        QuerySqlReq querySQLReq = new QuerySqlReq();
        if (Objects.nonNull(sqlInfo.getCorrectedS2SQL())) {
            querySQLReq.setSql(sqlInfo.getCorrectedS2SQL());
        }
        querySQLReq.setSqlInfo(sqlInfo);
        querySQLReq.setDataSetId(dataSetId);
        return querySQLReq;
    }

    private static List<Aggregator> getAggregatorByMetric(AggregateTypeEnum aggregateType, SchemaElement metric) {
        if (metric == null) {
            return Collections.emptyList();
        }

        String agg = determineAggregator(aggregateType, metric);
        return Collections.singletonList(new Aggregator(metric.getBizName(), AggOperatorEnum.of(agg)));
    }

    private static String determineAggregator(AggregateTypeEnum aggregateType, SchemaElement metric) {
        if (aggregateType == null || aggregateType.equals(AggregateTypeEnum.NONE)
                || AggOperatorEnum.COUNT_DISTINCT.name().equalsIgnoreCase(metric.getDefaultAgg())) {
            return StringUtils.defaultIfBlank(metric.getDefaultAgg(), "");
        }
        return aggregateType.name();
    }

    private static void addDateDimension(SemanticParseInfo parseInfo) {
        if (parseInfo == null || parseInfo.getDateInfo() == null) {
            return;
        }

        if (shouldSkipAddingDateDimension(parseInfo)) {
            return;
        }

        String dateField = getDateField(parseInfo.getDateInfo());
        if (isDateFieldAlreadyPresent(parseInfo, dateField)) {
            return;
        }

        SchemaElement dimension = new SchemaElement();
        dimension.setBizName(dateField);

        if (QueryManager.isMetricQuery(parseInfo.getQueryMode())) {
            addDimension(parseInfo, dimension);
        }
    }

    private static boolean shouldSkipAddingDateDimension(SemanticParseInfo parseInfo) {
        return parseInfo.getAggType() != null
                && (parseInfo.getAggType().equals(AggregateTypeEnum.MAX)
                || parseInfo.getAggType().equals(AggregateTypeEnum.MIN))
                && !CollectionUtils.isEmpty(parseInfo.getDimensions());
    }

    private static boolean isDateFieldAlreadyPresent(SemanticParseInfo parseInfo, String dateField) {
        return parseInfo.getDimensions().stream()
                .anyMatch(dimension -> dimension.getBizName().equalsIgnoreCase(dateField));
    }

    private static void addDimension(SemanticParseInfo parseInfo, SchemaElement dimension) {
        List<String> timeDimensions = Arrays.asList(TimeDimensionEnum.DAY.getName(),
                TimeDimensionEnum.WEEK.getName(), TimeDimensionEnum.MONTH.getName());
        Set<SchemaElement> dimensions = parseInfo.getDimensions().stream()
                .filter(d -> !timeDimensions.contains(d.getBizName().toLowerCase()))
                .collect(Collectors.toSet());
        dimensions.add(dimension);
        parseInfo.setDimensions(dimensions);
    }

    public static Set<Order> getOrder(Set<Order> existingOrders,
            AggregateTypeEnum aggregator, SchemaElement metric) {
        if (existingOrders != null && !existingOrders.isEmpty()) {
            return existingOrders;
        }

        if (metric == null) {
            return Collections.emptySet();
        }

        Set<Order> orders = new LinkedHashSet<>();
        if (aggregator == AggregateTypeEnum.TOPN
                || aggregator == AggregateTypeEnum.MAX
                || aggregator == AggregateTypeEnum.MIN) {
            Order order = new Order();
            order.setColumn(metric.getBizName());
            order.setDirection("desc");
            orders.add(order);
        }

        return orders;
    }

    public static String getDateField(DateConf dateConf) {
        if (Objects.isNull(dateConf)) {
            return "";
        }
        String dateField = TimeDimensionEnum.DAY.getName();
        if (Constants.MONTH.equals(dateConf.getPeriod())) {
            dateField = TimeDimensionEnum.MONTH.getName();
        }
        if (Constants.WEEK.equals(dateConf.getPeriod())) {
            dateField = TimeDimensionEnum.WEEK.getName();
        }
        return dateField;
    }

    public static QueryStructReq buildStructRatioReq(SemanticParseInfo parseInfo, SchemaElement metric,
            AggOperatorEnum aggOperatorEnum) {
        QueryStructReq queryStructReq = buildStructReq(parseInfo);
        queryStructReq.setQueryType(QueryType.METRIC);
        queryStructReq.setOrders(new ArrayList<>());
        List<Aggregator> aggregators = new ArrayList<>();
        Aggregator ratioRoll = new Aggregator(metric.getBizName(), aggOperatorEnum);
        aggregators.add(ratioRoll);
        queryStructReq.setAggregators(aggregators);
        return queryStructReq;
    }

}
