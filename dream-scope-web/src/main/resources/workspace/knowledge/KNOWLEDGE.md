# dream-scope

基于 AgentScope 2.0 的模块化单体 Agent 运行时。下面是每轮都会注入的产品事实；更细的条目用 retrieve，不要编造未写出的内容。

## 调用

- `POST /api/agents/invoke` 同步调用
- `POST /api/agents/stream` SSE
- 默认 `agentId` 为 `chat`；`knowledge` 做检索；`task` 未接线

## 会话

生产会话走 Redis。请求同时带 `sessionId` 与 `userId` 时按该二元组续聊；只传一个等于无会话。

## 工作区

人格见根目录 `AGENTS.md`。本文件是领域知识入口；没有单独的附件可 `read_file`（HTTP 进程未开放工作区文件工具）。
