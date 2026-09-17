package com.zhu.scope.knowledge;

/**
 * 一条检索命中。{@code docType} 供过滤；空表示未标注。
 */
public record RetrieveHit(String id, String text, double score, String source, String docType) {

    public RetrieveHit {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        source = source == null ? "" : source;
        docType = docType == null ? "" : docType;
    }
}
