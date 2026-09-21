package com.zhu.scope.web.bean.request;

/**
 * {@code POST /api/knowledge/texts}。{@code text} 必填；{@code id} 空则由检索实现生成。
 */
public record KnowledgeAddTextHttpRequest(String id, String text, String source, String docType) {}
