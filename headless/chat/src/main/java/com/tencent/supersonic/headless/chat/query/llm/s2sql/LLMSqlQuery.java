package com.tencent.supersonic.headless.chat.query.llm.s2sql;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.headless.api.pojo.DataSetSchema;
import com.tencent.supersonic.headless.api.pojo.SqlInfo;
import com.tencent.supersonic.headless.api.pojo.request.SemanticQueryReq;
import com.tencent.supersonic.headless.chat.query.QueryManager;
import com.tencent.supersonic.headless.chat.query.llm.LLMSemanticQuery;
import com.tencent.supersonic.headless.chat.utils.QueryReqBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LLMSqlQuery extends LLMSemanticQuery {

    public static final String QUERY_MODE = "LLM_S2SQL";

    public LLMSqlQuery() {
        QueryManager.register(this);
    }

    @Override
    public String getQueryMode() {
        return QUERY_MODE;
    }

    @Override
    public SemanticQueryReq buildSemanticQueryReq() {
        return QueryReqBuilder.buildS2SQLReq(parseInfo.getSqlInfo(), parseInfo.getDataSetId());
    }

    @Override
    public void initS2Sql(DataSetSchema dataSetSchema, User user) {
        SqlInfo sqlInfo = parseInfo.getSqlInfo();
        sqlInfo.setCorrectedS2SQL(sqlInfo.getParsedS2SQL());
    }
}
