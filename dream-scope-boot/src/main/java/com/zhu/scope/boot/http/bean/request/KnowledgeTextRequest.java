package com.zhu.scope.boot.http.bean.request;

/** {@code POST /api/knowledge/texts}。{@code text} 必填，{@code id} 空则生成。 */
public record KnowledgeTextRequest(String id, String text, String source) {}
