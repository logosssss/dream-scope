package com.zhu.scope.knowledge;

import java.util.List;

/** 向量化失败，但 Reader 已抽出正文，可回退关键词。 */
public final class EmbeddingIngestException extends IllegalStateException {

    private final String baseId;

    private final String source;

    private final String docType;

    private final List<String> texts;

    public EmbeddingIngestException(
            String message, String baseId, String source, String docType, List<String> texts, Throwable cause) {
        super(message, cause);
        this.baseId = baseId == null ? "" : baseId;
        this.source = source == null ? "" : source;
        this.docType = docType == null ? "" : docType;
        this.texts = texts == null ? List.of() : List.copyOf(texts);
    }

    public String baseId() {
        return baseId;
    }

    public String source() {
        return source;
    }

    public String docType() {
        return docType;
    }

    public List<String> texts() {
        return texts;
    }
}
