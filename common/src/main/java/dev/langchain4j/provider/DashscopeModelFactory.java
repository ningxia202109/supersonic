package dev.langchain4j.provider;

import com.tencent.supersonic.common.config.ChatModelConfig;
import com.tencent.supersonic.common.config.EmbeddingModelConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Service
public class DashscopeModelFactory implements ModelFactory, InitializingBean {
    public static final String PROVIDER = "DASHSCOPE";

    @Override
    public ChatLanguageModel createChatModel(ChatModelConfig modelConfig) {
        return QwenChatModel.builder()
                .baseUrl(modelConfig.getBaseUrl())
                .apiKey(modelConfig.getApiKey())
                .modelName(modelConfig.getModelName())
                .temperature(modelConfig.getTemperature() == null ? 0L :
                        modelConfig.getTemperature().floatValue())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(EmbeddingModelConfig embeddingModelConfig) {
        return QwenEmbeddingModel.builder()
                .apiKey(embeddingModelConfig.getApiKey())
                .modelName(embeddingModelConfig.getModelName())
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        ModelProvider.add(PROVIDER, this);
    }
}
