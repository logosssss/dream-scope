package com.zhu.scope.web.bean.request;

/** {@code POST /api/knowledge/retrieve}。{@code source} 空则全库。 */
public record KnowledgeRetrieveHttpRequest(String query, Integer topK, String source) {}
