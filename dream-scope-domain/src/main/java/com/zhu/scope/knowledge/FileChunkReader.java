package com.zhu.scope.knowledge;

import java.util.List;

/** 二进制文件（PDF 等）抽成文本块，供 embedding 失败后的关键词降级。 */
@FunctionalInterface
public interface FileChunkReader {

    List<String> read(String filename, byte[] content);
}
