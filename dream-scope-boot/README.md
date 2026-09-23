# dream-scope-boot

AgentScope 2.0.3 的可运行入口，端口 `8092`。11 个官方 Spring Boot starter 都在依赖里。本模块不依赖 `dream-scope-domain`、`dream-scope-adapter`。

进程里有两条对话，工具表共用：

| 路径 | Agent | 用来做什么 |
|---|---|---|
| `POST /api/agents/invoke`、`/stream` | `HarnessAgent`（`agentId=chat`） | 计划、Redis 会话、工作区、技能、知识检索 |
| `POST /v1/chat/completions`、`/agui` | starter 的 `ReActAgent` | OpenAI 兼容接口和 AG-UI |

`agentId=knowledge` 只检索，不调对话模型。未知 `agentId` 返回 404。

环境变量见 `.env.example`。Spring Boot 不会自动加载 `.env`。

## 运行

需要 JDK 21。在仓库根目录：

```bash
set DASHSCOPE_API_KEY=你的密钥
mvn -pl dream-scope-boot -am spring-boot:run
```

没有密钥时，DashScope 创建 Model 会失败，进程起不来。Redis 连不上时，带对话的 Agent 起不来。

```text
GET http://127.0.0.1:8092/api/starters
```

## 官方 starter

同一时间只启用一家模型，现在是 DashScope。

| Starter | 现在 | 入口 |
|---|---|---|
| `agentscope-spring-boot-starter` | 开 | bean `agentscopeReActAgent` |
| `agentscope-dashscope-spring-boot-starter` | 开 | `agentscope.model.provider=dashscope` |
| OpenAI / Anthropic / Gemini / Ollama | 关 | 见「换厂商」 |
| `agentscope-chat-completions-web-starter` | 开 | `POST /v1/chat/completions` |
| `agentscope-agui-spring-boot-starter` | 开 | `/agui`，默认 agent `agentscopeReActAgent` |
| `agentscope-admin-spring-boot-starter` | 开 | `GET /v1/admin/sessions`，写操作关 |
| `agentscope-a2a-spring-boot-starter` | 开 | `GET /.well-known/agent-card.json` |
| `agentscope-nacos-spring-boot-starter` | 部分开 | prompt 关；A2A 注册开（`agentscope.a2a.nacos.enabled`） |

Nacos 没起来时，把 A2A 注册和下面的技能库关掉，进程才能启动。

## chat

`POST /api/agents/invoke` 与 `/stream` 走 Harness。请求体带 `input`。`userId` 和 `sessionId` 成对才写入 Redis，键前缀 `dream-scope-boot:`。只带其中一个等于没有会话。

```text
POST http://127.0.0.1:8092/api/agents/invoke
Content-Type: application/json
```

```json
{ "input": "你好", "userId": "u1", "sessionId": "s1" }
```

工作区默认在临时目录 `dream-scope-boot/workspace`。下列文件只在缺失时从 classpath 拷入，已有文件不覆盖。

| 能力 | 配置 | 说明 |
|---|---|---|
| 计划 | `dream-scope.plan.max-iters` 默认 10 | `plan_enter` / `plan_write` / `plan_exit`，权限直接放行 |
| 压缩 | `compaction.trigger-messages` 30，`keep-messages` 10 | 超长会话留下最近若干条 |
| 采样 | `dream-scope.model.temperature` / `top-p` / `max-tokens` | 都空则用模型默认 |
| 备用模型 | `dream-scope.model.fallback` | 空则不切换；与主模型同名则忽略 |
| 只读文件 | `read_file` `list_files` `grep_files` `glob_files` | `write_file`、`edit_file`、shell 不提供 |
| 联网 | `tools.json` 拒绝 `web_fetch`、`web_search` | |
| 记忆 | `MEMORY.md` | `memory_search` / `memory_get` / `memory_save`，以及 `session_search` / `session_list` / `session_history` |
| 记录 | 目录 `transcripts` | 只记 invoke / stream |
| 技能 | 目录 `skills`，示例 `boot-echo` | 另见 Nacos 技能库 |
| 子 Agent | `summarizer` | 不超过三句的中文摘要 |
| 截断 | `dream-scope.eviction.*` | 超长工具结果预览后写入 `large_tool_results`，用 `read_file` 读回 |
| 人格与事实 | `AGENTS.md`、`knowledge/KNOWLEDGE.md` | 每轮注入 |

官方 `POST /v1/chat/completions` 不走计划模式、Redis、工作区文件和上面的截断。它和 invoke 共用工具表。

```text
POST http://127.0.0.1:8092/v1/chat/completions
Content-Type: application/json
```

```json
{ "messages": [ { "role": "user", "content": "你好" } ] }
```

演示 MCP 在 `127.0.0.1:8094/mcp`，工具 `mcp__boot__echo`，只读。`dream-scope.mcp.demo-enabled=false` 时不启动。

## knowledge

先关键词。配了 `DASHSCOPE_API_KEY` 才做向量补召回，最低分 `dream-scope.knowledge.score-threshold`（默认 0.3），关键词命中不受这道门槛影响。配了 `DREAM_SCOPE_RAG_PG_JDBC_URL` 时写入 PostgreSQL 表 `dream_scope_boot_rag`，否则用内存索引。

| 方法 | 路径 | 作用 |
|---|---|---|
| POST | `/api/knowledge/texts` | 写入正文 |
| POST | `/api/knowledge/files` | 解析 txt / pdf / docx 等再入库；同名文件先删旧块 |
| POST | `/api/knowledge/retrieve` | 直接检索，`source` 空则全库 |
| GET | `/api/knowledge/sources` | 看来源 |
| DELETE | `/api/knowledge/sources?source=` | 按来源删除 |

chat 上的 `retrieve` 和这里共用同一份索引。

## 扩展

这两个不是官方 starter。

| 扩展 | 现在 | 作用 |
|---|---|---|
| `agentscope-extensions-nacos-skill` | 开 | 技能名在 `agentscope.nacos.skill.names`（现为 `boot-echo`）。命名空间 `NACOS_NAMESPACE`，空则 `public` |
| `agentscope-extensions-a2a-client` | 开 | `POST /api/a2a/invoke`，body `{ "input": "你好" }`。`url` 现为 `http://127.0.0.1:8092` |

A2A 客户端创建时不访问远端。调用时才去拉对方的 `/.well-known/agent-card.json`。

## 换厂商

把 `agentscope.model.provider` 改成目标厂商，只把这一家 `enabled` 设为 `true`。密钥用 `OPENAI_API_KEY`、`ANTHROPIC_API_KEY`、`GEMINI_API_KEY`。Ollama 用 `OLLAMA_BASE_URL`，默认 `http://127.0.0.1:11434`。

## 配置中心

`spring.cloud.nacos.config.enabled` 和 `discovery.enabled` 默认关闭。这和上面的 Nacos prompt、A2A 注册、技能库是不同的开关。
