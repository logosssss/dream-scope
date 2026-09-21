package com.zhu.scope.knowledge;

import java.util.List;

/**
 * 把口语问句扩成多条检索句。返回应包含原句或等价句；空列表表示只用原句。
 */
@FunctionalInterface
public interface QueryRewritePort {

    List<String> expand(String query);
}
