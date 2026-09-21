package com.zhu.scope.web.bean.response;

import java.util.List;

/** 文件已收下。{@code status=accepted} 时看 {@code jobId} 查进度。 */
public record KnowledgeAddFileHttpResponse(
        String jobId, String filename, String source, String docType, List<String> ids, String status) {}
