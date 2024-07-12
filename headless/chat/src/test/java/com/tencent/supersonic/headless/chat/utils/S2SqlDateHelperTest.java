package com.tencent.supersonic.headless.chat.utils;


import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.enums.QueryType;
import com.tencent.supersonic.common.pojo.enums.TimeMode;
import com.tencent.supersonic.common.util.DateUtils;
import com.tencent.supersonic.headless.api.pojo.DataSetSchema;
import com.tencent.supersonic.headless.api.pojo.QueryConfig;
import com.tencent.supersonic.headless.api.pojo.SchemaElement;
import com.tencent.supersonic.headless.api.pojo.SemanticSchema;
import com.tencent.supersonic.headless.api.pojo.TimeDefaultConfig;
import com.tencent.supersonic.headless.chat.ChatQueryContext;
import com.tencent.supersonic.headless.chat.corrector.S2SqlDateHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Disabled
class S2SqlDateHelperTest {

    @Test
    void getReferenceDate() {
        Long dataSetId = 1L;
        ChatQueryContext chatQueryContext = buildQueryContext(dataSetId);

        String referenceDate = S2SqlDateHelper.getReferenceDate(chatQueryContext, null);
        Assert.assertEquals(referenceDate, DateUtils.getBeforeDate(0));

        referenceDate = S2SqlDateHelper.getReferenceDate(chatQueryContext, dataSetId);
        Assert.assertEquals(referenceDate, DateUtils.getBeforeDate(0));

        DataSetSchema dataSetSchema = chatQueryContext.getSemanticSchema().getDataSetSchemaMap().get(dataSetId);
        QueryConfig queryConfig = dataSetSchema.getQueryConfig();
        TimeDefaultConfig timeDefaultConfig = new TimeDefaultConfig();
        timeDefaultConfig.setTimeMode(TimeMode.LAST);
        timeDefaultConfig.setPeriod(Constants.DAY);
        timeDefaultConfig.setUnit(20);
        queryConfig.getTagTypeDefaultConfig().setTimeDefaultConfig(timeDefaultConfig);

        referenceDate = S2SqlDateHelper.getReferenceDate(chatQueryContext, dataSetId);
        Assert.assertEquals(referenceDate, DateUtils.getBeforeDate(20));

        timeDefaultConfig.setUnit(1);
        referenceDate = S2SqlDateHelper.getReferenceDate(chatQueryContext, dataSetId);
        Assert.assertEquals(referenceDate, DateUtils.getBeforeDate(1));

        timeDefaultConfig.setUnit(-1);
        referenceDate = S2SqlDateHelper.getReferenceDate(chatQueryContext, dataSetId);
        Assert.assertNull(referenceDate);
    }

    @Test
    void getStartEndDate() {
        Long dataSetId = 1L;
        ChatQueryContext chatQueryContext = buildQueryContext(dataSetId);

        Pair<String, String> startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, null, QueryType.DETAIL);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(0));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(0));

        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.DETAIL);
        Assert.assertNotNull(startEndDate.getLeft());
        Assert.assertNotNull(startEndDate.getRight());

        DataSetSchema dataSetSchema = chatQueryContext.getSemanticSchema().getDataSetSchemaMap().get(dataSetId);
        QueryConfig queryConfig = dataSetSchema.getQueryConfig();
        TimeDefaultConfig timeDefaultConfig = new TimeDefaultConfig();
        timeDefaultConfig.setTimeMode(TimeMode.LAST);
        timeDefaultConfig.setPeriod(Constants.DAY);
        timeDefaultConfig.setUnit(20);
        queryConfig.getTagTypeDefaultConfig().setTimeDefaultConfig(timeDefaultConfig);
        queryConfig.getMetricTypeDefaultConfig().setTimeDefaultConfig(timeDefaultConfig);

        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.DETAIL);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(20));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(20));

        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.METRIC);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(20));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(20));

        timeDefaultConfig.setUnit(2);
        timeDefaultConfig.setTimeMode(TimeMode.RECENT);
        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.METRIC);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(2));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(1));

        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.DETAIL);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(2));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(1));

        timeDefaultConfig.setUnit(-1);
        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.METRIC);
        Assert.assertNull(startEndDate.getLeft());
        Assert.assertNull(startEndDate.getRight());

        timeDefaultConfig.setTimeMode(TimeMode.LAST);
        timeDefaultConfig.setPeriod(Constants.DAY);
        timeDefaultConfig.setUnit(5);
        startEndDate = S2SqlDateHelper.getStartEndDate(chatQueryContext, dataSetId, QueryType.METRIC);
        Assert.assertEquals(startEndDate.getLeft(), DateUtils.getBeforeDate(5));
        Assert.assertEquals(startEndDate.getRight(), DateUtils.getBeforeDate(5));
    }

    private ChatQueryContext buildQueryContext(Long dataSetId) {
        ChatQueryContext chatQueryContext = new ChatQueryContext();
        List<DataSetSchema> dataSetSchemaList = new ArrayList<>();
        DataSetSchema dataSetSchema = new DataSetSchema();
        QueryConfig queryConfig = new QueryConfig();
        dataSetSchema.setQueryConfig(queryConfig);
        SchemaElement schemaElement = new SchemaElement();
        schemaElement.setDataSet(dataSetId);
        dataSetSchema.setDataSet(schemaElement);
        dataSetSchemaList.add(dataSetSchema);

        SemanticSchema semanticSchema = new SemanticSchema(dataSetSchemaList);
        chatQueryContext.setSemanticSchema(semanticSchema);
        return chatQueryContext;
    }
}