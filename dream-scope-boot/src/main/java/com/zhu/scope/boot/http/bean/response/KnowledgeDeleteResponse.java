package com.zhu.scope.boot.http.bean.response;

/** 按来源删除后实际去掉的块数。 */
public record KnowledgeDeleteResponse(String source, int removed) {}
