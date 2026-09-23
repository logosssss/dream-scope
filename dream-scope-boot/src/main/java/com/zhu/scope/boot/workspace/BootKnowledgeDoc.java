package com.zhu.scope.boot.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 工作区 knowledge/KNOWLEDGE.md。没有文件时从 classpath 拷一份，已有文件不覆盖。 */
public final class BootKnowledgeDoc {

    public static final String FILE = "knowledge/KNOWLEDGE.md";

    static final String RESOURCE = "/workspace/knowledge/KNOWLEDGE.md";

    private BootKnowledgeDoc() {}

    public static void ensure(Path workspace) {
        Path file = workspace.resolve(FILE);
        if (Files.exists(file)) {
            return;
        }
        try (InputStream in = BootKnowledgeDoc.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("knowledge doc missing: " + RESOURCE);
            }
            Files.createDirectories(file.getParent());
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("knowledge doc seed failed: " + file, ex);
        }
    }
}
