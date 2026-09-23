package com.zhu.scope.boot.http.bean.request;

/** {@code POST /api/knowledge/retrieve}。{@code source} 空则全库。 */
public record KnowledgeRetrieveRequest(String query, Integer topK, String source) {}
