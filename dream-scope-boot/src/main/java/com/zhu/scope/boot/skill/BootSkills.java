package com.zhu.scope.boot.skill;

import io.agentscope.core.skill.repository.FileSystemSkillRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 工作区技能。目录里没有示例时，从 classpath 的 boot-echo 拷一份，已有文件不覆盖。 */
public final class BootSkills {

    public static final String DEMO = "boot-echo";

    static final String DEMO_RESOURCE = "/skills/boot-echo/SKILL.md";

    private BootSkills() {}

    public static void ensure(Path workspace, String directory) {
        seed(workspace.resolve(relative(directory)));
    }

    public static FileSystemSkillRepository open(Path workspace, String directory) {
        Path dir = workspace.resolve(relative(directory));
        seed(dir);
        return new FileSystemSkillRepository(dir);
    }

    static void seed(Path dir) {
        Path file = dir.resolve(DEMO).resolve("SKILL.md");
        if (Files.exists(file)) {
            return;
        }
        try (InputStream in = BootSkills.class.getResourceAsStream(DEMO_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("skill example missing: " + DEMO_RESOURCE);
            }
            Files.createDirectories(file.getParent());
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("skill seed failed: " + file, ex);
        }
    }

    private static String relative(String directory) {
        if (directory == null || directory.isBlank()) {
            return "skills";
        }
        return directory.trim();
    }
}
