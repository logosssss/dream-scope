package com.zhu.scope.boot.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 工作区根目录的 tools.json。没有文件时从 classpath 拷一份，已有文件不覆盖。 */
public final class BootTools {

    public static final String FILE = "tools.json";

    static final String RESOURCE = "/workspace/tools.json";

    private BootTools() {}

    public static void ensure(Path workspace) {
        Path file = workspace.resolve(FILE);
        if (Files.exists(file)) {
            return;
        }
        try (InputStream in = BootTools.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("tools file missing: " + RESOURCE);
            }
            Files.createDirectories(workspace);
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("tools seed failed: " + file, ex);
        }
    }
}
