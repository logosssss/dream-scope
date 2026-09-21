package com.zhu.scope.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** 检索词项：英文按空白切，中日韩用重叠 2-gram。 */
final class QueryTerms {

    private QueryTerms() {}

    static Set<String> of(String query) {
        Set<String> out = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        for (String raw : lower.split("[\\s\\p{Punct}]+")) {
            if (raw.length() >= 2 && !cjkOnly(raw)) {
                out.add(raw);
            }
        }
        addCjkGrams(lower, out);
        return out;
    }

    private static void addCjkGrams(String text, Set<String> out) {
        int i = 0;
        while (i < text.length()) {
            if (!isCjk(text.charAt(i))) {
                i++;
                continue;
            }
            int j = i + 1;
            while (j < text.length() && isCjk(text.charAt(j))) {
                j++;
            }
            if (j - i == 1) {
                out.add(text.substring(i, j));
            } else {
                for (int k = i; k + 1 < j; k++) {
                    out.add(text.substring(k, k + 2));
                }
            }
            i = j;
        }
    }

    private static boolean cjkOnly(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!isCjk(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
