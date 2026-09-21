package com.zhu.scope.web.bean.response;

import java.util.List;

public record KnowledgeRetrieveHttpResponse(String query, int topK, List<KnowledgeRetrieveHitHttp> hits) {}
