package com.zhu.scope.knowledge;

/** 知识库里的一份来源及其块数。 */
public record KnowledgeSource(String source, int chunks) {

    public KnowledgeSource {
        source = source == null ? "" : source;
        chunks = Math.max(0, chunks);
    }
}
