package com.tencent.supersonic.chat.server.util;

import com.tencent.supersonic.common.config.ModelConfig;
import com.tencent.supersonic.common.pojo.exception.InvalidArgumentException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.provider.ModelProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class LLMConnHelper {
    public static boolean testConnection(ModelConfig modelConfig) {
        try {
            if (modelConfig == null || modelConfig.getChatModel() == null
                    || StringUtils.isBlank(modelConfig.getChatModel().getBaseUrl())) {
                return false;
            }
            ChatLanguageModel chatLanguageModel = ModelProvider.getChatModel(modelConfig);
            String response = chatLanguageModel.generate("Hi there");
            return StringUtils.isNotEmpty(response) ? true : false;
        } catch (Exception e) {
            log.warn("connect to llm failed:", e);
            throw new InvalidArgumentException(e.getMessage());
        }
    }
}
