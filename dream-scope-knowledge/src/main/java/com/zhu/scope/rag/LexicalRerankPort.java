package com.zhu.scope.rag;

import com.zhu.scope.knowledge.RerankPort;
import com.zhu.scope.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 无外部模型时的词项重叠精排，便于本地与单测。 */
public final class LexicalRerankPort implements RerankPort {

    @Override
    public List<RetrieveHit> rerank(String query, List<RetrieveHit> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            int limit = Math.min(topK, candidates.size());
            return List.copyOf(candidates.subList(0, limit));
        }
        record Scored(RetrieveHit hit, double score) {}
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (RetrieveHit hit : candidates) {
            String hay = hit.text() == null ? "" : hit.text().toLowerCase(Locale.ROOT);
            int matched = 0;
            for (String term : terms) {
                if (hay.contains(term)) {
                    matched++;
                }
            }
            double ratio = matched / (double) terms.size();
            scored.add(new Scored(hit, Math.max(ratio, hit.score())));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int limit = Math.min(topK, scored.size());
        List<RetrieveHit> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            RetrieveHit hit = scored.get(i).hit();
            out.add(new RetrieveHit(hit.id(), hit.text(), scored.get(i).score(), hit.source(), hit.docType()));
        }
        return List.copyOf(out);
    }

    static Set<String> terms(String query) {
        Set<String> out = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        for (String raw : query.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+")) {
            if (raw.length() >= 2) {
                out.add(raw);
            }
        }
        return out;
    }
}
