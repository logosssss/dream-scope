package com.zhu.scope.knowledge;

import java.util.List;

/**
 * 检索契约。实现放 knowledge 模块；Agent 只消费格式化后的字符串。
 */
public interface RetrievePort {

    List<RetrieveHit> retrieve(String query, int topK);

    /**
     * 只在指定 {@code source} 里检索。空 source 与 {@link #retrieve(String, int)} 相同。
     * 默认先多取再过滤；实现可覆盖成精确过滤。
     */
    default List<RetrieveHit> retrieve(String query, int topK, String source) {
        if (source == null || source.isBlank() || topK <= 0) {
            return retrieve(query, topK);
        }
        int fetch = Math.min(Math.max(topK * 8, topK), 64);
        String only = source.trim();
        List<RetrieveHit> hits = retrieve(query, fetch);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RetrieveHit> matched = new java.util.ArrayList<>();
        for (RetrieveHit hit : hits) {
            if (only.equals(hit.source())) {
                matched.add(hit);
                if (matched.size() >= topK) {
                    break;
                }
            }
        }
        return List.copyOf(matched);
    }

    /** 当前进程里见过的来源。重启后向量库里的旧数据不一定出现在这里。 */
    default List<KnowledgeSource> sources() {
        return List.of();
    }

    /**
     * 入库一段文本。{@code id} 空则实现自己生成。{@code text} 空则不入库并返回空串。
     * 只读实现保持默认抛 {@link UnsupportedOperationException}。
     */
    default String addText(String id, String text, String source, String docType) {
        throw new UnsupportedOperationException("addText not supported");
    }

    /**
     * 文件入库。实现侧用官方 Reader 切块；关键词索引只收文本文件。
     * 返回各块 id。同 {@code source} 会先删旧块再写。
     */
    default List<String> addFile(String id, String filename, byte[] content, String source, String docType) {
        throw new UnsupportedOperationException("addFile not supported");
    }

    /** 删掉同一 source 下已有块，避免同名文件重复入库。 */
    default int deleteBySource(String source) {
        return 0;
    }

    /** 大文件入库进度；实现可忽略。 */
    default void setIngestProgressListener(IngestProgressListener listener) {}

    /** pgvector 等有连接时关掉；内存实现空操作。 */
    default void close() {}
}
