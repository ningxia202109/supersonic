package com.tencent.supersonic.common.config;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.EmbeddingStoreConfig;
import com.tencent.supersonic.common.pojo.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("EmbeddingStoreParameterConfig")
@Slf4j
public class EmbeddingStoreParameterConfig extends ParameterConfig {
    public static final Parameter EMBEDDING_STORE_PROVIDER =
            new Parameter("s2.embedding.store.provider", "",
                    "向量库类型", "",
                    "string", "向量库配置");

    public static final Parameter EMBEDDING_STORE_BASE_URL =
            new Parameter("s2.embedding.store.base.url", "",
                    "BaseUrl", "",
                    "string", "向量库配置");

    public static final Parameter EMBEDDING_STORE_API_KEY =
            new Parameter("s2.embedding.store.api.key", "",
                    "ApiKey", "",
                    "string", "向量库配置");
    public static final Parameter EMBEDDING_STORE_PERSIST_PATH =
            new Parameter("s2.embedding.store.persist.path", "/tmp",
                    "持久化路径", "",
                    "string", "向量库配置");

    public static final Parameter EMBEDDING_STORE_TIMEOUT =
            new Parameter("s2.embedding.store.timeout", "60",
                    "超时时间(秒)", "",
                    "number", "向量库配置");

    @Override
    public List<Parameter> getSysParameters() {
        return Lists.newArrayList(
                EMBEDDING_STORE_PROVIDER, EMBEDDING_STORE_BASE_URL, EMBEDDING_STORE_API_KEY,
                EMBEDDING_STORE_PERSIST_PATH, EMBEDDING_STORE_TIMEOUT
        );
    }

    public EmbeddingStoreConfig convert() {
        String provider = getParameterValue(EMBEDDING_STORE_PROVIDER);
        String baseUrl = getParameterValue(EMBEDDING_STORE_BASE_URL);
        String apiKey = getParameterValue(EMBEDDING_STORE_API_KEY);
        String persistPath = getParameterValue(EMBEDDING_STORE_PERSIST_PATH);
        String timeOut = getParameterValue(EMBEDDING_STORE_TIMEOUT);

        return EmbeddingStoreConfig.builder()
                .provider(provider)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .persistPath(persistPath)
                .timeOut(Long.valueOf(timeOut))
                .build();
    }
}