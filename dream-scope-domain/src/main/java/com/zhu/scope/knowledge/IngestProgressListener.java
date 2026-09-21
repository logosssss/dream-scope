package com.zhu.scope.knowledge;

/** 文件入库进度。{@code stage} 为 {@code reading} / {@code embedding}。 */
@FunctionalInterface
public interface IngestProgressListener {

    void onProgress(String stage, int done, int total);
}
