package com.zhu.scope.boot.http.bean.response;

import java.util.List;

/** 直接检索的命中列表，不经过对话模型。 */
public record KnowledgeRetrieveResponse(String query, int topK, List<KnowledgeRetrieveHitResponse> hits) {}
