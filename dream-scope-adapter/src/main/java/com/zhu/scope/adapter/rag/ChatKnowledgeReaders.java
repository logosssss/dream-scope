package com.zhu.scope.adapter.rag;

import com.zhu.scope.rag.KnowledgeTextFile;
import io.agentscope.core.rag.model.Document;
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
import java.util.List;
import java.util.Set;

/**
 * 官方 Reader：{@code TextReader} / {@code PDFReader} / {@code WordReader} / {@code TikaReader}。
 *
 * <p>PDF/Word 必须 {@link ReaderInput#fromPath}。{@link ReaderInput#fromFile} 内部是 {@code Files.readString}，
 * 二进制会坏。文本走 {@link ReaderInput#fromString}。
 *
 * @see <a href="https://java.agentscope.io/v2/en/integration/rag/simple.html">Simple Knowledge</a>
 */
@SuppressWarnings({"deprecation", "removal"})
final class ChatKnowledgeReaders {

    /** 手册示例是 512。整本书会切出上千块，每块一次 embedding，所以放大到约两千字。 */
    static final int CHUNK_SIZE = 2000;

    static final int OVERLAP = 200;

    private static final Set<String> TEXT = Set.of("txt", "md", "markdown", "rst");

    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp");

    private ChatKnowledgeReaders() {}

    static List<Document> read(String filename, byte[] content) {
        return read(filename, content, CHUNK_SIZE, OVERLAP);
    }

    static List<Document> read(String filename, byte[] content, int chunkSize, int overlap) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content required");
        }
        String suffix = KnowledgeTextFile.suffix(filename);
        if (IMAGES.contains(suffix)) {
            throw new IllegalArgumentException("image ingest needs multimodal embedding");
        }
        int size = chunkSize > 0 ? chunkSize : CHUNK_SIZE;
        int over = Math.max(0, overlap);
        Reader reader = readerFor(suffix, size, over);
        if (reader instanceof TextReader) {
            return reader.read(ReaderInput.fromString(new String(content, StandardCharsets.UTF_8)))
                    .block(Duration.ofSeconds(30));
        }
        String base = KnowledgeTextFile.basename(filename);
        String safe = base.isBlank() ? "upload.bin" : base.replaceAll("[^A-Za-z0-9._\\-]", "_");
        Path tmp = null;
        try {
            tmp = Files.createTempFile("dream-scope-rag-", "-" + safe);
            Files.write(tmp, content);
            return reader.read(ReaderInput.fromPath(tmp)).block(Duration.ofSeconds(30));
        } catch (IOException ex) {
            throw new IllegalStateException("knowledge reader temp file failed", ex);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件
                }
            }
        }
    }

    static String readerName(String filename) {
        Reader reader = readerFor(KnowledgeTextFile.suffix(filename), CHUNK_SIZE, OVERLAP);
        return reader.getClass().getSimpleName();
    }

    private static Reader readerFor(String suffix, int chunkSize, int overlap) {
        if (TEXT.contains(suffix)) {
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
}
