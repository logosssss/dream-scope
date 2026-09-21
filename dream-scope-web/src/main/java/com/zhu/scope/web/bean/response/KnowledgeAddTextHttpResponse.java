package com.zhu.scope.web.bean.response;

/** 入库成功后的稳定 id，与请求里的 source / docType 回显。 */
public record KnowledgeAddTextHttpResponse(String id, String source, String docType) {}
