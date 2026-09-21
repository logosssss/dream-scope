package com.zhu.scope.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内入库任务表。重启清空。 */
public final class KnowledgeIngestTracker {

    private final Map<String, KnowledgeIngestJob> jobs = new ConcurrentHashMap<>();

    public KnowledgeIngestJob accept(String filename, String source) {
        String jobId = UUID.randomUUID().toString().replace("-", "");
        KnowledgeIngestJob job = new KnowledgeIngestJob(jobId, filename, source, "accepted", 0, 0, List.of(), "");
        jobs.put(jobId, job);
        return job;
    }

    public void update(KnowledgeIngestJob job) {
        if (job == null || job.jobId() == null || job.jobId().isBlank()) {
            return;
        }
        jobs.put(job.jobId(), job);
    }

    public Optional<KnowledgeIngestJob> find(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(jobId));
    }
}
