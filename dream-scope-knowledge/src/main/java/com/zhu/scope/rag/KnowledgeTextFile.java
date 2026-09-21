package com.zhu.scope.rag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文本文件入库：只认常见文本后缀，UTF-8，按字符切块。不解析 PDF / Office。
 */
public final class KnowledgeTextFile {

    public static final int MAX_BYTES = 20 * 1024 * 1024;

    public static final int CHUNK_CHARS = 1500;

    public static final int CHUNK_OVERLAP = 200;

    private static final Set<String> SUFFIXES = Set.of(
            "txt", "md", "markdown", "json", "csv", "yml", "yaml", "log", "properties", "xml", "html", "htm");

    private KnowledgeTextFile() {}

    public static String basename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String name = filename.replace('\\', '/').trim();
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    public static boolean supported(String filename) {
        return SUFFIXES.contains(suffix(filename));
    }

    public static String suffix(String filename) {
        String base = basename(filename);
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String stemId(String filename) {
        String base = basename(filename);
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String cleaned = stem.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "file" : cleaned;
    }

    public static String decodeUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        for (byte b : bytes) {
            if (b == 0) {
                throw new IllegalArgumentException("binary file not supported");
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    public static List<String> chunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String body = text.trim();
        if (body.length() <= CHUNK_CHARS) {
            return List.of(body);
        }
        List<String> out = new ArrayList<>();
        int step = CHUNK_CHARS - CHUNK_OVERLAP;
        for (int start = 0; start < body.length(); start += step) {
            int end = Math.min(body.length(), start + CHUNK_CHARS);
            out.add(body.substring(start, end));
            if (end >= body.length()) {
                break;
            }
        }
        return List.copyOf(out);
    }

    public static List<String> chunkIds(String baseId, int count) {
        if (count <= 0) {
            return List.of();
        }
        String base = baseId == null || baseId.isBlank() ? "file" : baseId.trim();
        if (count == 1) {
            return List.of(base);
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(base + "-" + i);
        }
        return List.copyOf(ids);
    }
}
