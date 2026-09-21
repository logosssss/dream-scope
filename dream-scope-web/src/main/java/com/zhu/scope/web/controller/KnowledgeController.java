package com.zhu.scope.web.controller;

import com.zhu.scope.adapter.LogText;
import com.zhu.scope.knowledge.KnowledgeIngestJob;
import com.zhu.scope.knowledge.KnowledgeIngestTracker;
import com.zhu.scope.knowledge.KnowledgeSource;
import com.zhu.scope.knowledge.RetrieveHit;
import com.zhu.scope.knowledge.RetrievePort;
import com.zhu.scope.rag.KnowledgeTextFile;
import com.zhu.scope.web.bean.request.KnowledgeAddTextHttpRequest;
import com.zhu.scope.web.bean.request.KnowledgeRetrieveHttpRequest;
import com.zhu.scope.web.bean.response.KnowledgeAddFileHttpResponse;
import com.zhu.scope.web.bean.response.KnowledgeAddTextHttpResponse;
import com.zhu.scope.web.bean.response.KnowledgeDeleteSourceHttpResponse;
import com.zhu.scope.web.bean.response.KnowledgeRetrieveHitHttp;
import com.zhu.scope.web.bean.response.KnowledgeRetrieveHttpResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识入库与检索。只认 domain {@link RetrievePort}，不碰 {@code io.agentscope}。
 */
@RestController
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final RetrievePort retrievePort;

    private final KnowledgeIngestTracker ingestTracker;

    private final ExecutorService fileIngest = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "dream-scope-rag-ingest");
        thread.setDaemon(true);
        return thread;
    });

    public KnowledgeController(RetrievePort retrievePort, KnowledgeIngestTracker ingestTracker) {
        this.retrievePort = retrievePort;
        this.ingestTracker = ingestTracker;
    }

    @PreDestroy
    void shutdownFileIngest() {
        fileIngest.shutdown();
    }

    @PostMapping("/api/knowledge/texts")
    public KnowledgeAddTextHttpResponse addText(@RequestBody KnowledgeAddTextHttpRequest body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text required");
        }
        String source = body.source() == null ? "" : body.source();
        String docType = body.docType() == null ? "" : body.docType();
        log.info(
                "http knowledge addText id={} source={} docType={} chars={} preview={}",
                body.id(),
                source,
                docType,
                LogText.chars(body.text()),
                LogText.preview(body.text()));
        try {
            String id = retrievePort.addText(body.id(), body.text(), source, docType);
            log.info("http knowledge addText done id={} source={} docType={}", id, source, docType);
            return new KnowledgeAddTextHttpResponse(id, source, docType);
        } catch (UnsupportedOperationException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            log.warn("http knowledge addText failed: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    /** 文件走 multipart。解析和向量化在后台，避免整本书堵住这次 HTTP。 */
    @PostMapping(value = "/api/knowledge/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public KnowledgeAddFileHttpResponse addFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "docType", required = false) String docType) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }
        if (file.getSize() > KnowledgeTextFile.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file too large");
        }
        String filename = KnowledgeTextFile.basename(file.getOriginalFilename());
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename required");
        }
        String src = source == null || source.isBlank() ? filename : source.trim();
        String type = docType == null || docType.isBlank() ? "file" : docType.trim();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file read failed", ex);
        }
        KnowledgeIngestJob job = ingestTracker.accept(filename, src);
        log.info(
                "http knowledge addFile accepted jobId={} name={} source={} docType={} bytes={}",
                job.jobId(),
                filename,
                src,
                type,
                bytes.length);
        byte[] payload = bytes;
        String fileId = id;
        fileIngest.execute(() -> ingestFile(job.jobId(), fileId, filename, payload, src, type));
        return new KnowledgeAddFileHttpResponse(job.jobId(), filename, src, type, List.of(), "accepted");
    }

    @GetMapping("/api/knowledge/jobs/{jobId}")
    public KnowledgeIngestJob job(@PathVariable("jobId") String jobId) {
        return ingestTracker
                .find(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
    }

    @PostMapping("/api/knowledge/retrieve")
    public KnowledgeRetrieveHttpResponse retrieve(@RequestBody KnowledgeRetrieveHttpRequest body) {
        if (body == null || body.query() == null || body.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query required");
        }
        int topK = body.topK() == null || body.topK() <= 0 ? 5 : body.topK();
        String source = body.source() == null ? "" : body.source().trim();
        log.info(
                "http knowledge retrieve topK={} source={} chars={} preview={}",
                topK,
                source,
                LogText.chars(body.query()),
                LogText.preview(body.query()));
        List<RetrieveHit> hits = source.isEmpty()
                ? retrievePort.retrieve(body.query(), topK)
                : retrievePort.retrieve(body.query(), topK, source);
        List<KnowledgeRetrieveHitHttp> out = new ArrayList<>(hits.size());
        for (RetrieveHit hit : hits) {
            out.add(new KnowledgeRetrieveHitHttp(hit.id(), hit.text(), hit.score(), hit.source(), hit.docType()));
        }
        log.info(
                "http knowledge retrieve done hits={} topScore={} topSource={}",
                out.size(),
                out.isEmpty() ? 0.0 : out.get(0).score(),
                out.isEmpty() ? "" : out.get(0).source());
        return new KnowledgeRetrieveHttpResponse(body.query(), topK, List.copyOf(out));
    }

    @GetMapping("/api/knowledge/sources")
    public List<KnowledgeSource> sources() {
        List<KnowledgeSource> list = retrievePort.sources();
        log.info("http knowledge sources count={}", list.size());
        return list;
    }

    @DeleteMapping("/api/knowledge/sources")
    public KnowledgeDeleteSourceHttpResponse deleteSource(@RequestParam("source") String source) {
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source required");
        }
        String src = source.trim();
        int deleted = retrievePort.deleteBySource(src);
        log.info("http knowledge delete source={} deleted={}", src, deleted);
        return new KnowledgeDeleteSourceHttpResponse(src, deleted);
    }

    private void ingestFile(
            String jobId, String id, String filename, byte[] bytes, String source, String docType) {
        KnowledgeIngestJob job = ingestTracker.find(jobId).orElse(null);
        if (job != null) {
            ingestTracker.update(job.withStatus("reading"));
        }
        try {
            retrievePort.setIngestProgressListener((stage, done, total) -> {
                ingestTracker
                        .find(jobId)
                        .ifPresent(current -> ingestTracker.update(
                                current.withStatus(stage == null ? current.status() : stage).withProgress(total, done)));
                log.info("http knowledge ingest progress jobId={} stage={} {}/{}", jobId, stage, done, total);
            });
            List<String> ids = retrievePort.addFile(id, filename, bytes, source, docType);
            KnowledgeIngestJob done = ingestTracker
                    .find(jobId)
                    .orElse(new KnowledgeIngestJob(jobId, filename, source, "done", 0, 0, List.of(), ""))
                    .withDone(ids);
            ingestTracker.update(done);
            log.info(
                    "http knowledge addFile done jobId={} name={} ids={} status=done",
                    jobId,
                    filename,
                    ids.size());
        } catch (RuntimeException ex) {
            KnowledgeIngestJob failed = ingestTracker
                    .find(jobId)
                    .orElse(new KnowledgeIngestJob(jobId, filename, source, "failed", 0, 0, List.of(), ""))
                    .withFailed(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            ingestTracker.update(failed);
            log.warn("http knowledge addFile failed jobId={} name={}: {}", jobId, filename, ex.getMessage(), ex);
        } finally {
            retrievePort.setIngestProgressListener(null);
        }
    }
}
