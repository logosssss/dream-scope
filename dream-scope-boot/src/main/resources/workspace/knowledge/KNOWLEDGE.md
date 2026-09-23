# dream-scope-boot

下面是每轮都会注入的产品事实。更细的条目用 retrieve，按 [1][2] 引用，不要编造未写出的内容。

## 调用

- 进程端口 8092
- `POST /api/agents/invoke` 同步调用，`POST /api/agents/stream` 为 SSE
- `agentId=chat` 走 Harness；`agentId=knowledge` 只检索，不调对话模型
- 演示工具 `mcp__boot__echo` 只读，监听 127.0.0.1:8094
- 长文本摘要交给 summarizer，不超过三句

## 会话

必须连 Redis。请求同时带 `userId` 和 `sessionId` 才续聊，键前缀 `dream-scope-boot:`。只传其中一个等于没有会话。

## 工作区

人格见根目录 `AGENTS.md`。长期记忆见根目录 `MEMORY.md`，用 memory_search 和 memory_save。可以 read_file、list_files、grep_files、glob_files。不要 write_file、edit_file，也不要执行 shell。过长的工具结果写在 `large_tool_results`，用 read_file 读回。
