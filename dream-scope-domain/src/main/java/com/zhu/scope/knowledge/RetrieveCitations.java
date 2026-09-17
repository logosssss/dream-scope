package com.zhu.scope.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 把命中编成带编号的参考资料，与回答里的 {@code [1]} 对齐。
 */
public final class RetrieveCitations {

    private RetrieveCitations() {}

    public static String format(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        List<String> blocks = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            blocks.add("[" + (i + 1) + "] " + hits.get(i).text());
        }
        return String.join("\n---\n", blocks);
    }
}
