package com.tencent.supersonic.headless.chat.parser.llm;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.config.PromptConfig;
import com.tencent.supersonic.common.pojo.SqlExemplar;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMResp;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class OnePassSCSqlGenStrategy extends SqlGenStrategy {

    private static final String INSTRUCTION = ""
            + "#Role: You are a data analyst experienced in SQL languages.\n"
            + "#Task: You will be provided a natural language query asked by business users,"
            + "please convert it to a SQL query so that relevant answer could be returned to the user "
            + "by executing the SQL query against underlying database.\n"
            + "#Rules:"
            + "1.ALWAYS use `数据日期` as the date field."
            + "2.ALWAYS use `datediff()` as the date function."
            + "3.DO NOT specify date filter in the where clause if not explicitly mentioned in the query."
            + "4.ONLY respond with the converted SQL statement.\n"
            + "#Exemplars:\n{{exemplar}}"
            + "#Question:{{question}} #Schema:{{schema}} #SQL:";

    @Override
    public LLMResp generate(LLMReq llmReq) {
        //1.recall exemplars
        keyPipelineLog.info("OnePassSCSqlGenStrategy llmReq:\n{}", llmReq);
        List<List<SqlExemplar>> exemplarsList = promptHelper.getFewShotExemplars(llmReq);

        //2.generate sql generation prompt for each self-consistency inference
        Map<Prompt, List<SqlExemplar>> prompt2Exemplar = new HashMap<>();
        for (List<SqlExemplar> exemplars : exemplarsList) {
            Prompt prompt = generatePrompt(llmReq, exemplars);
            prompt2Exemplar.put(prompt, exemplars);
        }

        //3.perform multiple self-consistency inferences parallelly
        Map<Prompt, String> prompt2Output = new ConcurrentHashMap<>();
        prompt2Exemplar.keySet().parallelStream().forEach(prompt -> {
                    keyPipelineLog.info("OnePassSCSqlGenStrategy reqPrompt:\n{}", prompt.toUserMessage());
                    ChatLanguageModel chatLanguageModel = getChatLanguageModel(llmReq.getModelConfig());
                    Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());
                    String result = response.content().text();
                    prompt2Output.put(prompt, result);
                    keyPipelineLog.info("OnePassSCSqlGenStrategy modelResp:\n{}", result);
                }
        );

        //4.format response.
        Pair<String, Map<String, Double>> sqlMapPair = ResponseHelper.selfConsistencyVote(
                Lists.newArrayList(prompt2Output.values()));
        LLMResp llmResp = new LLMResp();
        llmResp.setQuery(promptHelper.buildAugmentedQuestion(llmReq));
        llmResp.setDbSchema(promptHelper.buildSchemaStr(llmReq));
        llmResp.setSqlOutput(sqlMapPair.getLeft());
        //TODO: should use the same few-shot exemplars as the one chose by self-consistency vote
        llmResp.setSqlRespMap(ResponseHelper.buildSqlRespMap(exemplarsList.get(0), sqlMapPair.getRight()));

        return llmResp;
    }

    private Prompt generatePrompt(LLMReq llmReq, List<SqlExemplar> fewshotExampleList) {
        StringBuilder exemplarsStr = new StringBuilder();
        for (SqlExemplar exemplar : fewshotExampleList) {
            String exemplarStr = String.format("#Question:%s #Schema:%s #SQL:%s\n",
                    exemplar.getQuestion(), exemplar.getDbSchema(), exemplar.getSql());
            exemplarsStr.append(exemplarStr);
        }
        String dataSemanticsStr = promptHelper.buildSchemaStr(llmReq);
        String questionAugmented = promptHelper.buildAugmentedQuestion(llmReq);

        Map<String, Object> variable = new HashMap<>();
        variable.put("exemplar", exemplarsStr);
        variable.put("question", questionAugmented);
        variable.put("schema", dataSemanticsStr);

        // use custom prompt template if provided.
        PromptConfig promptConfig = llmReq.getPromptConfig();
        String prompTemplate = INSTRUCTION;
        if (promptConfig != null && StringUtils.isNotBlank(promptConfig.getPromptTemplate())) {
            prompTemplate = promptConfig.getPromptTemplate();
        }
        return PromptTemplate.from(prompTemplate).apply(variable);
    }

    @Override
    public void afterPropertiesSet() {
        SqlGenStrategyFactory.addSqlGenerationForFactory(LLMReq.SqlGenType.ONE_PASS_SELF_CONSISTENCY, this);
    }
}
