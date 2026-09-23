package com.zhu.scope.boot.http.controller;

import com.zhu.scope.boot.http.bean.request.KnowledgeRetrieveRequest;
import com.zhu.scope.boot.http.bean.request.KnowledgeTextRequest;
import com.zhu.scope.boot.http.bean.response.KnowledgeDeleteResponse;
import com.zhu.scope.boot.http.bean.response.KnowledgeFileResponse;
import com.zhu.scope.boot.http.bean.response.KnowledgeRetrieveHitResponse;
import com.zhu.scope.boot.http.bean.response.KnowledgeRetrieveResponse;
import com.zhu.scope.boot.http.bean.response.KnowledgeSourceResponse;
import com.zhu.scope.boot.http.bean.response.KnowledgeTextResponse;
import com.zhu.scope.boot.knowledge.file.BootKnowledgeFiles;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 知识库入库。写入的文本和 {@code agentId=knowledge} 共用同一份索引。 */
@RestController
public class BootKnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(BootKnowledgeController.class);

    private final BootKnowledgeIndex index;

    public BootKnowledgeController(BootKnowledgeIndex index) {
        this.index = index;
    }

    @PostMapping("/api/knowledge/texts")
    public KnowledgeTextResponse addText(@RequestBody KnowledgeTextRequest body) {
        if (body == null || body.text() == null || body.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text required");
        }
        String source = body.source() == null ? "" : body.source().trim();
        String id = index.add(body.id(), body.text(), source);
        log.info("knowledge addText id={} source={} chars={}", id, source, body.text().length());
        return new KnowledgeTextResponse(id, source);
    }

    @PostMapping(value = "/api/knowledge/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeFileResponse addFile(
            @RequestParam("file") MultipartFile file, @RequestParam(value = "id", required = false) String id) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file required");
        }
        if (file.getSize() > BootKnowledgeFiles.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file too large");
        }
        String filename = BootKnowledgeFiles.basename(file.getOriginalFilename());
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename required");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file read failed", ex);
        }
        try {
            List<String> ids = index.addFile(id, filename, bytes);
            log.info("knowledge addFile name={} chunks={}", filename, ids.size());
            return new KnowledgeFileResponse(filename, ids);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @PostMapping("/api/knowledge/retrieve")
    public KnowledgeRetrieveResponse retrieve(@RequestBody KnowledgeRetrieveRequest body) {
        if (body == null || body.query() == null || body.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query required");
        }
        int topK = body.topK() == null || body.topK() <= 0 ? 5 : Math.min(body.topK(), 8);
        String source = body.source() == null ? "" : body.source().trim();
        List<KnowledgeRetrieveHitResponse> hits = new ArrayList<>();
        for (BootKnowledgeIndex.Hit hit : index.retrieve(body.query(), topK, source)) {
            hits.add(new KnowledgeRetrieveHitResponse(hit.id(), hit.text(), hit.score(), hit.source()));
        }
        log.info("knowledge retrieve topK={} source={} hits={}", topK, source, hits.size());
        return new KnowledgeRetrieveResponse(body.query(), topK, List.copyOf(hits));
    }

    @GetMapping("/api/knowledge/sources")
    public List<KnowledgeSourceResponse> sources() {
        List<KnowledgeSourceResponse> out = new ArrayList<>();
        for (BootKnowledgeIndex.Source source : index.sources()) {
            out.add(new KnowledgeSourceResponse(source.name(), source.count()));
        }
        return List.copyOf(out);
    }

    @DeleteMapping("/api/knowledge/sources")
    public KnowledgeDeleteResponse deleteSource(@RequestParam("source") String source) {
        if (source == null || source.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source required");
        }
        String src = source.trim();
        int removed = index.deleteBySource(src);
        log.info("knowledge deleteSource source={} removed={}", src, removed);
        return new KnowledgeDeleteResponse(src, removed);
    }
}
