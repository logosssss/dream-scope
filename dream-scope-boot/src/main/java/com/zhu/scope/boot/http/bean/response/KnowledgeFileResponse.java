package com.zhu.scope.boot.http.bean.response;

import java.util.List;

/** 文件解析入库后的块 id。 */
public record KnowledgeFileResponse(String filename, List<String> ids) {}
