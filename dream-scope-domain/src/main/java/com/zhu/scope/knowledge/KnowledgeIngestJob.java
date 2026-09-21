package com.zhu.scope.knowledge;

import java.util.List;

/**
 * 文件入库任务状态。{@code accepted} → {@code reading}/{@code embedding} → {@code done}/{@code failed}。
 */
public record KnowledgeIngestJob(
        String jobId,
        String filename,
        String source,
        String status,
        int chunks,
        int doneChunks,
        List<String> ids,
        String error) {

    public KnowledgeIngestJob {
        ids = ids == null ? List.of() : List.copyOf(ids);
        status = status == null ? "accepted" : status;
        error = error == null ? "" : error;
        filename = filename == null ? "" : filename;
        source = source == null ? "" : source;
    }

    public KnowledgeIngestJob withStatus(String next) {
        return new KnowledgeIngestJob(jobId, filename, source, next, chunks, doneChunks, ids, error);
    }

    public KnowledgeIngestJob withProgress(int total, int done) {
        return new KnowledgeIngestJob(jobId, filename, source, status, total, done, ids, error);
    }

    public KnowledgeIngestJob withDone(List<String> nextIds) {
        int total = nextIds == null ? 0 : nextIds.size();
        return new KnowledgeIngestJob(jobId, filename, source, "done", total, total, nextIds, "");
    }

    public KnowledgeIngestJob withFailed(String message) {
        return new KnowledgeIngestJob(jobId, filename, source, "failed", chunks, doneChunks, ids, message);
    }
}
