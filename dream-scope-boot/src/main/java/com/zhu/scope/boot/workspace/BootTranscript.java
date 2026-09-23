package com.zhu.scope.boot.workspace;

import io.agentscope.harness.agent.transcript.FilesystemTranscriptStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 把对话记录写到工作区 transcripts 目录，而不是文件系统内部存储。 */
public final class BootTranscript {

    public static final String DIRECTORY = "transcripts";

    private BootTranscript() {}

    public static FilesystemTranscriptStore open(Path workspace) {
        Path dir = workspace.resolve(DIRECTORY);
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("transcript dir failed: " + dir, ex);
        }
        return new FilesystemTranscriptStore(dir);
    }
}
