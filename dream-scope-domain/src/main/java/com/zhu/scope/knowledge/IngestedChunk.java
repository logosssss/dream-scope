package com.zhu.scope.knowledge;

/** 一次入库写出的块。Hybrid 用正文做关键词镜像，向量端口不必再持有关键词索引。 */
public record IngestedChunk(String id, String text, String source, String docType) {}
