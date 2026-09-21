package com.zhu.scope.knowledge;

import java.util.List;

/** 对检索候选做精排。失败时应退回原顺序截断。 */
@FunctionalInterface
public interface RerankPort {

    List<RetrieveHit> rerank(String query, List<RetrieveHit> candidates, int topK);
}
