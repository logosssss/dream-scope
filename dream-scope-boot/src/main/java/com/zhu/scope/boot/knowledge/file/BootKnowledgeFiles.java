package com.zhu.scope.boot.knowledge.file;

import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TableFormat;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.TikaReader;
import io.agentscope.core.rag.reader.WordReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 官方 Reader 抽正文。文本走字符串，PDF / Word / 其它走临时文件。 */
@SuppressWarnings({"deprecation", "removal"})
public final class BootKnowledgeFiles {

    public static final int MAX_BYTES = 20 * 1024 * 1024;

    private static final Set<String> TEXT = Set.of(
            "txt", "md", "markdown", "json", "csv", "yml", "yaml", "log", "properties", "xml", "html", "htm");

    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp");

    private BootKnowledgeFiles() {}

    public static List<String> read(String filename, byte[] content, int chunkSize, int overlap) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content required");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("file too large");
        }
        String suffix = suffix(filename);
        if (IMAGES.contains(suffix)) {
            throw new IllegalArgumentException("image ingest is not supported");
        }
        int size = chunkSize > 0 ? chunkSize : 2000;
        int over = Math.max(0, overlap);
        Reader reader = readerFor(suffix, size, over);
        List<Document> docs;
        if (reader instanceof TextReader) {
            docs = reader.read(ReaderInput.fromString(decodeUtf8(content))).block(Duration.ofSeconds(30));
        } else {
            docs = readPath(reader, filename, content);
        }
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (Document doc : docs) {
            DocumentMetadata meta = doc.getMetadata();
            String text = meta == null ? "" : meta.getContentText();
            if (text != null && !text.isBlank()) {
                texts.add(text.trim());
            }
        }
        return List.copyOf(texts);
    }

    public static String basename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String name = filename.replace('\\', '/').trim();
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    public static String stemId(String filename) {
        String base = basename(filename);
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String cleaned = stem.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "file" : cleaned;
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

    private static List<Document> readPath(Reader reader, String filename, byte[] content) {
        String base = basename(filename);
        String safe = base.isBlank() ? "upload.bin" : base.replaceAll("[^A-Za-z0-9._\\-]", "_");
        Path tmp = null;
        try {
            tmp = Files.createTempFile("dream-scope-boot-rag-", "-" + safe);
            Files.write(tmp, content);
            return reader.read(ReaderInput.fromPath(tmp)).block(Duration.ofSeconds(30));
        } catch (IOException ex) {
            throw new IllegalStateException("knowledge reader temp file failed", ex);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件留给系统清
                }
            }
        }
    }

    private static Reader readerFor(String suffix, int chunkSize, int overlap) {
        if (TEXT.contains(suffix) || suffix.isEmpty()) {
            return new TextReader(chunkSize, SplitStrategy.PARAGRAPH, overlap);
        }
        if ("pdf".equals(suffix)) {
            return new PDFReader(chunkSize, SplitStrategy.PARAGRAPH, overlap);
        }
        if ("doc".equals(suffix) || "docx".equals(suffix)) {
            return new WordReader(chunkSize, SplitStrategy.PARAGRAPH, overlap, true, true, TableFormat.MARKDOWN);
        }
        return new TikaReader();
    }

    private static String suffix(String filename) {
        String base = basename(filename);
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        return base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String decodeUtf8(byte[] bytes) {
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
}
