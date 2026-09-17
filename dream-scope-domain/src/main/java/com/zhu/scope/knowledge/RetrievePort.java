package com.zhu.scope.knowledge;

import java.util.List;

/**
 * 检索契约。实现放 knowledge 模块；Agent 只消费格式化后的字符串。
 */
public interface RetrievePort {

    List<RetrieveHit> retrieve(String query, int topK);
}
