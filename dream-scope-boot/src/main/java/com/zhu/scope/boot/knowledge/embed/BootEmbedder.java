package com.zhu.scope.boot.knowledge.embed;

/** 把一段文本变成向量。没有密钥时不提供实现，检索只走关键词。 */
public interface BootEmbedder {

    float[] embed(String text);
}
