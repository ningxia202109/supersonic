package com.tencent.supersonic.headless.server.aspect;


import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.auth.api.authorization.pojo.AuthRes;
import com.tencent.supersonic.auth.api.authorization.pojo.DimensionFilter;
import com.tencent.supersonic.auth.api.authorization.request.QueryAuthResReq;
import com.tencent.supersonic.auth.api.authorization.response.AuthorizedResourceResp;
import com.tencent.supersonic.auth.api.authorization.service.AuthService;
import com.tencent.supersonic.common.jsqlparser.SqlAddHelper;
import com.tencent.supersonic.common.pojo.Filter;
import com.tencent.supersonic.common.pojo.QueryAuthorization;
import com.tencent.supersonic.common.pojo.enums.AuthType;
import com.tencent.supersonic.common.pojo.enums.FilterOperatorEnum;
import com.tencent.supersonic.common.pojo.enums.SensitiveLevelEnum;
import com.tencent.supersonic.common.pojo.exception.InvalidArgumentException;
import com.tencent.supersonic.common.pojo.exception.InvalidPermissionException;
import com.tencent.supersonic.headless.api.pojo.request.QuerySqlReq;
import com.tencent.supersonic.headless.api.pojo.request.QueryStructReq;
import com.tencent.supersonic.headless.api.pojo.request.SchemaFilterReq;
import com.tencent.supersonic.headless.api.pojo.request.SemanticQueryReq;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.api.pojo.response.SemanticQueryResp;
import com.tencent.supersonic.headless.api.pojo.response.SemanticSchemaResp;
import com.tencent.supersonic.headless.api.pojo.MetaFilter;
import com.tencent.supersonic.headless.server.utils.QueryStructUtils;
import com.tencent.supersonic.headless.server.service.ModelService;
import com.tencent.supersonic.headless.server.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Component
@Aspect
@Slf4j
public class S2DataPermissionAspect {

    @Autowired
    private QueryStructUtils queryStructUtils;
    @Autowired
    private ModelService modelService;
    @Autowired
    private SchemaService schemaService;
    @Autowired
    private AuthService authService;

    @Pointcut("@annotation(com.tencent.supersonic.headless.server.annotation.S2DataPermission)")
    private void s2PermissionCheck() {
    }

    @Around("s2PermissionCheck()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        //1. check args
        Object[] objects = joinPoint.getArgs();
        boolean needQueryData = true;
        SemanticQueryReq queryReq = null;
        if (objects[0] instanceof SemanticQueryReq) {
            queryReq = (SemanticQueryReq) objects[0];
        }
        if (queryReq == null) {
            throw new InvalidArgumentException("queryReq is not Invalid");
        }
        if (!queryReq.isNeedAuth()) {
            log.info("needAuth is false, there is no need to check permissions.");
            return joinPoint.proceed();
        }
        User user = (User) objects[1];
        if (Objects.isNull(user) || StringUtils.isEmpty(user.getName())) {
            throw new RuntimeException("please provide user information");
        }

        SemanticSchemaResp semanticSchemaResp = getSemanticSchemaResp(queryReq);
        List<Long> modelIds = getModelIds(semanticSchemaResp);

        //2. determine whether admin of the model
        if (checkModelAdmin(user, modelIds)) {
            return joinPoint.proceed();
        }
        //3. determine whether the model is visible to cur user
        checkModelVisible(user, modelIds);

        //4. get permissions auth to cur user
        AuthorizedResourceResp authorizedResource = getAuthorizedResource(user, modelIds);

        //5. check col permission
        if (needQueryData) {
            checkColPermission(queryReq, authorizedResource, modelIds, semanticSchemaResp);
        }
        //6. check row permission
        checkRowPermission(queryReq, authorizedResource);

        //7. add hint to user
        Object result = joinPoint.proceed();
        if (result instanceof SemanticQueryResp) {
            addHint(modelIds, (SemanticQueryResp) result, authorizedResource);
        }
        return result;
    }

    private void checkColPermission(SemanticQueryReq semanticQueryReq, AuthorizedResourceResp authorizedResource,
                                    List<Long> modelIds, SemanticSchemaResp semanticSchemaResp) {
        // get high sensitive fields in query
        Set<String> bizNamesInQueryReq = getBizNameInQueryReq(semanticQueryReq, semanticSchemaResp);
        Set<String> sensitiveBizNamesByModel = getHighSensitiveBizNamesByModelId(semanticSchemaResp);
        Set<String> sensitiveBizNameInQuery = bizNamesInQueryReq.parallelStream()
                .filter(sensitiveBizNamesByModel::contains).collect(Collectors.toSet());

        //get high sensitive field cur user has been authed
        Set<String> sensitiveBizNameUserAuthed = authorizedResource.getAuthResList()
                .stream().map(AuthRes::getName).collect(Collectors.toSet());
        sensitiveBizNameInQuery.removeAll(sensitiveBizNameUserAuthed);
        if (!CollectionUtils.isEmpty(sensitiveBizNameInQuery)) {
            Set<String> sensitiveResNames = semanticSchemaResp.getNameFromBizNames(sensitiveBizNameInQuery);
            List<String> modelAdmin = modelService.getModelAdmin(modelIds.get(0));
            String message = String.format("存在以下敏感资源:%s您暂无权限，请联系管理员%s申请",
                    sensitiveResNames, modelAdmin);
            throw new InvalidPermissionException(message);
        }
    }

    private void checkRowPermission(SemanticQueryReq queryReq,
                                      AuthorizedResourceResp authorizedResource) {
        if (queryReq instanceof QuerySqlReq) {
            doRowPermission((QuerySqlReq) queryReq, authorizedResource);
        }
        if (queryReq instanceof QueryStructReq) {
            doRowPermission((QueryStructReq) queryReq, authorizedResource);
        }
    }

    private Set<String> getBizNameInQueryReq(SemanticQueryReq queryReq, SemanticSchemaResp semanticSchemaResp) {
        if (queryReq instanceof QuerySqlReq) {
            return queryStructUtils.getBizNameFromSql((QuerySqlReq) queryReq, semanticSchemaResp);
        }
        if (queryReq instanceof QueryStructReq) {
            return queryStructUtils.getBizNameFromStruct((QueryStructReq) queryReq);
        }
        throw new InvalidArgumentException("queryReq is not Invalid:" + queryReq);
    }

    private SemanticSchemaResp getSemanticSchemaResp(SemanticQueryReq semanticQueryReq) {
        SchemaFilterReq filter = new SchemaFilterReq();
        filter.setModelIds(semanticQueryReq.getModelIds());
        filter.setDataSetId(semanticQueryReq.getDataSetId());
        return schemaService.fetchSemanticSchema(filter);
    }

    private List<Long> getModelIds(SemanticSchemaResp semanticSchemaResp) {
        return semanticSchemaResp.getModelResps().stream()
                .map(ModelResp::getId).collect(Collectors.toList());
    }

    private void doRowPermission(QuerySqlReq querySqlReq, AuthorizedResourceResp authorizedResource) {
        log.debug("start doRowPermission logic");
        StringJoiner joiner = new StringJoiner(" OR ");
        List<String> dimensionFilters = new ArrayList<>();
        if (!CollectionUtils.isEmpty(authorizedResource.getFilters())) {
            authorizedResource.getFilters().stream()
                    .forEach(filter -> dimensionFilters.addAll(filter.getExpressions()));
        }

        if (CollectionUtils.isEmpty(dimensionFilters)) {
            log.debug("dimensionFilters is empty");
            return;
        }

        dimensionFilters.stream().forEach(filter -> {
            if (StringUtils.isNotEmpty(filter) && StringUtils.isNotEmpty(filter.trim())) {
                joiner.add(" ( " + filter + " ) ");
            }
        });
        try {
            Expression expression = CCJSqlParserUtil.parseCondExpression(" ( " + joiner + " ) ");
            if (StringUtils.isNotEmpty(joiner.toString())) {
                String sql = SqlAddHelper.addWhere(querySqlReq.getSql(), expression);
                log.info("before doRowPermission, queryS2SQLReq:{}", querySqlReq.getSql());
                querySqlReq.setSql(sql);
                log.info("after doRowPermission, queryS2SQLReq:{}", querySqlReq.getSql());
            }
        } catch (JSQLParserException jsqlParserException) {
            log.info("jsqlParser has an exception:{}", jsqlParserException.toString());
        }

    }

    private void doRowPermission(QueryStructReq queryStructReq, AuthorizedResourceResp authorizedResource) {
        log.debug("start doRowPermission logic");
        StringJoiner joiner = new StringJoiner(" OR ");
        List<String> dimensionFilters = new ArrayList<>();
        if (!CollectionUtils.isEmpty(authorizedResource.getFilters())) {
            authorizedResource.getFilters().stream()
                    .forEach(filter -> dimensionFilters.addAll(filter.getExpressions()));
        }

        if (CollectionUtils.isEmpty(dimensionFilters)) {
            log.debug("dimensionFilters is empty");
            return;
        }

        dimensionFilters.stream().forEach(filter -> {
            if (StringUtils.isNotEmpty(filter) && StringUtils.isNotEmpty(filter.trim())) {
                joiner.add(" ( " + filter + " ) ");
            }
        });

        if (StringUtils.isNotEmpty(joiner.toString())) {
            log.info("before doRowPermission, queryStructReq:{}", queryStructReq);
            Filter filter = new Filter("", FilterOperatorEnum.SQL_PART, joiner.toString());
            List<Filter> filters = Objects.isNull(queryStructReq.getOriginalFilter()) ? new ArrayList<>()
                    : queryStructReq.getOriginalFilter();
            filters.add(filter);
            queryStructReq.setDimensionFilters(filters);
            log.info("after doRowPermission, queryStructReq:{}", queryStructReq);
        }

    }

    public boolean checkModelAdmin(User user, List<Long> modelIds) {
        List<ModelResp> modelListAdmin = modelService.getModelListWithAuth(user, null, AuthType.ADMIN);
        if (CollectionUtils.isEmpty(modelListAdmin)) {
            return false;
        } else {
            Set<Long> modelAdmins = modelListAdmin.stream().map(ModelResp::getId).collect(Collectors.toSet());
            return !CollectionUtils.isEmpty(modelAdmins) && modelAdmins.containsAll(modelIds);
        }
    }

    public void checkModelVisible(User user, List<Long> modelIds) {
        List<Long> modelListVisible = modelService.getModelListWithAuth(user, null, AuthType.VISIBLE)
                .stream().map(ModelResp::getId).collect(Collectors.toList());
        List<Long> modelIdCopied = new ArrayList<>(modelIds);
        modelIdCopied.removeAll(modelListVisible);
        if (!CollectionUtils.isEmpty(modelIdCopied)) {
            MetaFilter metaFilter = new MetaFilter();
            metaFilter.setIds(modelIdCopied);
            List<ModelResp> modelResps = modelService.getModelList(metaFilter);
            ModelResp modelResp = modelResps.stream().findFirst().orElse(null);
            if (modelResp == null) {
                throw new InvalidArgumentException("查询的模型不存在");
            }
            String message = String.format("您没有模型[%s]权限，请联系管理员%s开通", modelResp.getName(), modelResp.getAdmins());
            throw new InvalidPermissionException(message);
        }
    }

    public Set<String> getHighSensitiveBizNamesByModelId(SemanticSchemaResp semanticSchemaResp) {
        Set<String> highSensitiveCols = new HashSet<>();
        if (!CollectionUtils.isEmpty(semanticSchemaResp.getDimensions())) {
            semanticSchemaResp.getDimensions().stream().filter(dimSchemaResp ->
                            SensitiveLevelEnum.HIGH.getCode().equals(dimSchemaResp.getSensitiveLevel()))
                    .forEach(dim -> highSensitiveCols.add(dim.getBizName()));
        }
        if (!CollectionUtils.isEmpty(semanticSchemaResp.getMetrics())) {
            semanticSchemaResp.getMetrics().stream().filter(metricSchemaResp ->
                            SensitiveLevelEnum.HIGH.getCode().equals(metricSchemaResp.getSensitiveLevel()))
                    .forEach(metric -> highSensitiveCols.add(metric.getBizName()));
        }
        return highSensitiveCols;
    }

    public AuthorizedResourceResp getAuthorizedResource(User user, List<Long> modelIds) {
        QueryAuthResReq queryAuthResReq = new QueryAuthResReq();
        queryAuthResReq.setModelIds(modelIds);
        AuthorizedResourceResp authorizedResource = fetchAuthRes(queryAuthResReq, user);
        log.info("user:{}, domainId:{}, after queryAuthorizedResources:{}",
                user.getName(), modelIds, authorizedResource);
        return authorizedResource;
    }

    private AuthorizedResourceResp fetchAuthRes(QueryAuthResReq queryAuthResReq, User user) {
        log.info("queryAuthResReq:{}", queryAuthResReq);
        return authService.queryAuthorizedResources(queryAuthResReq, user);
    }

    public void addHint(List<Long> modelIds, SemanticQueryResp queryResultWithColumns,
                                  AuthorizedResourceResp authorizedResource) {
        List<DimensionFilter> filters = authorizedResource.getFilters();
        if (CollectionUtils.isEmpty(filters)) {
            return;
        }
        List<String> admins = modelService.getModelAdmin(modelIds.get(0));

        if (!CollectionUtils.isEmpty(filters)) {
            ModelResp modelResp = modelService.getModel(modelIds.get(0));
            List<String> exprList = new ArrayList<>();
            List<String> descList = new ArrayList<>();
            filters.stream().forEach(filter -> {
                if (StringUtils.isNotEmpty(filter.getDescription())) {
                    descList.add(filter.getDescription());
                }
                exprList.add(filter.getExpressions().toString());
            });
            String promptInfo = "当前结果已经过行权限过滤，详细过滤条件如下:%s, 申请权限请联系管理员%s";
            String message = String.format(promptInfo, CollectionUtils.isEmpty(descList) ? exprList : descList, admins);
            queryResultWithColumns.setQueryAuthorization(
                    new QueryAuthorization(modelResp.getName(), exprList, descList, message));
        }
    }

}
