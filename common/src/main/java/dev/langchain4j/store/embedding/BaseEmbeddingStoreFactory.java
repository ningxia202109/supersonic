package dev.langchain4j.store.embedding;

import dev.langchain4j.data.segment.TextSegment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseEmbeddingStoreFactory implements EmbeddingStoreFactory {
    protected static final Map<String, EmbeddingStore<TextSegment>> collectionNameToStore = new ConcurrentHashMap<>();

    public EmbeddingStore<TextSegment> create(String collectionName) {
        return collectionNameToStore.computeIfAbsent(collectionName, this::createEmbeddingStore);
    }

    public abstract EmbeddingStore<TextSegment> createEmbeddingStore(String collectionName);
}