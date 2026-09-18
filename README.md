# dream-scope

基于 [AgentScope Java 2.0](https://java.agentscope.io/) 的模块化单体 Agent 运行时。

业务契约放在 `dream-scope-domain`（禁止依赖 `io.agentscope.*`），框架装配关在 `dream-scope-adapter`。当前主路径：同步 `POST /api/agents/invoke`（`HarnessAgent.call` 语义，内部走 `streamEvents` 累积 `Done`）与流式 `POST /api/agents/stream`（SSE）。chat 带 workspace / Middleware 挂点。

本仓库版本 `0.1.0-SNAPSHOT`。版本锁在根 POM / `dream-scope-bom`：Spring Boot BOM + `agentscope-dependencies-bom`。

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Java（`maven.compiler.release`） | 21 |
| 构建 | Maven | 3.9+ |
| 运行时 | Spring Boot 3（Jakarta） | **3.5.5** |
| HTTP | `spring-boot-starter-web` | 随 Boot |
| 观测 | `spring-boot-starter-actuator`（`health` / `info`） | 随 Boot |
| Agent 框架 | [AgentScope Java](https://java.agentscope.io/) `agentscope-core` + `agentscope-harness` + `agentscope-extensions-redis`（`RedisDistributedStore`） | **2.0.3** |
| Redis | `redis:7-alpine`（Docker Compose）+ Jedis | 随 AgentScope BOM |
| 模型供应商 | `agentscope-extensions-model-dashscope`（默认 `dashscope:qwen-plus`，`DASHSCOPE_API_KEY`） | **2.0.3** |
| 测试 | JUnit Jupiter；adapter 另用 Mockito；web 用 `spring-boot-starter-test` | JUnit **5.12.2**；Mockito 随 Boot BOM |

构建插件：`maven-compiler-plugin` 3.14.0、`maven-surefire-plugin` 3.5.3、`spring-boot-maven-plugin` 3.5.5。

## 模块

| 模块 | 职责 |
|---|---|
| `dream-scope-bom` | 对外版本锁（Spring Boot / AgentScope / 本仓库模块） |
| `dream-scope-domain` | SPI、请求响应、Agent 注册表；零框架依赖 |
| `dream-scope-adapter` | AgentScope 装配（模型、消息编解码、后续 Harness / 状态） |
| `dream-scope-knowledge` | RAG 实现，只依赖 domain |
| `dream-scope-web` | 唯一可启动模块：HTTP / Actuator / SSE |

内置 `agentId`：`chat`（已接线，默认）。`knowledge` / `task` 仅为预留 id，调用返回 `404`。

## 环境

- JDK 21
- Maven 3.9+
- Redis（必选）。本地：`docker compose up -d redis`

## 本地运行

```bash
git clone https://github.com/logosssss/dream-scope.git
cd dream-scope
```

复制环境变量模板（真实 Key 不要入库）：

```bash
cp .env.example .env
```

进程**不会**自动加载 `.env`。配置走 Spring `Environment`（环境变量、`application.yml`、`application-local.yml`）。chat 启动时解析模型并连接 Redis，需要 Key，例如：

```bash
docker compose up -d redis
export DASHSCOPE_API_KEY=your-key
# 可选
export DREAM_SCOPE_MODEL_CHAT=dashscope:qwen-plus
export DREAM_SCOPE_REDIS_URI=redis://127.0.0.1:6379
```

或写在已 gitignore 的 `application-local.yml`，启动时加 `--spring.profiles.active=local`：

```yaml
DASHSCOPE_API_KEY: your-key
dream-scope:
  model:
    chat: dashscope:qwen-plus
  redis:
    uri: redis://127.0.0.1:6379
    key-prefix: "dream-scope:"
```

Windows PowerShell：

```powershell
docker compose up -d redis
Copy-Item .env.example .env
$env:DASHSCOPE_API_KEY = "your-key"
```

测试与启动：

```bash
mvn -q test
mvn -pl dream-scope-web -am spring-boot:run
```

默认端口 `8091`。健康检查：

```bash
curl http://localhost:8091/actuator/health
```

## 调用

`POST /api/agents/invoke`，JSON：

| 字段 | 说明 |
|---|---|
| `input` | 文本；与 `imageUrls` 至少一个非空，否则 `400` |
| `imageUrls` | 可选。http/https 图片 URL 列表，最多 8 个。视觉模型（如 `dashscope:qwen-vl-plus`）才能看图 |
| `agentId` | 可选，默认 `chat`；未知或未接线 id 返回 `404` |
| `sessionId` / `userId` | 可选。成对传入时，会话写入 Redis（`dream-scope.redis`），同槽位可续聊 |

模型超时返回 `504`，供应商/运行失败返回 `502`。

chat 已注册演示工具（无 Spring）：`getCurrentTime`、`calculate`（四则运算）、`httpGet`（仅 http/https，截断响应体）。模型需要时会走 ReAct 调工具。Harness 默认的工作区 `read_file`/`write_file`/`shell` **已关闭**，避免 HTTP 进程在本机执行文件和命令。

chat 挂了 `LoggingMiddleware`（SLF4J，无 Spring 注解）：一次调用会打 `onAgent` / `onModelCall` / `onActing` 的 start/complete（含 `agentId`、`sessionId`、耗时）。看控制台即可，不另加日志库。

```bash
curl -s http://localhost:8091/api/agents/invoke \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"你好\"}"
```

需已导出 `DASHSCOPE_API_KEY`。`output` 为模型回复，例如：

```json
{"agentId":"chat","output":"你好，有什么可以帮你的？","inputTokens":0,"outputTokens":0}
```

流式：`POST /api/agents/stream`，请求体与 `/invoke` 相同，响应 `text/event-stream`。每条 SSE `data` 是 JSON，`event` 为 `textDelta` / `toolCall` / `toolResult` / `done` / `error`。`done` 的 JSON 含 `finalOutput`、`inputTokens`、`outputTokens`（多次模型调用会累加）。`done` 之后连接结束。SSE 超时 = `chat-timeout` + 30s。

```bash
curl -N http://localhost:8091/api/agents/stream \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"你好\"}"
```

## 配置

Spring 绑定前缀 `dream-scope`；同名环境变量（relaxed binding）也会生效。见 [`.env.example`](.env.example) 与 `application.yml`。

| 配置 / 环境变量 | 含义 |
|---|---|
| `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` 等 | 按模型前缀选用的供应商 Key（也可写在 yml） |
| `dream-scope.model.chat` / `DREAM_SCOPE_MODEL_CHAT` | chat 模型 id；空白则用 default |
| `dream-scope.model.default` / `DREAM_SCOPE_MODEL_DEFAULT` | 回退模型 id，默认 `dashscope:qwen-plus` |
| `dream-scope.model.fallback` / `DREAM_SCOPE_MODEL_FALLBACK` | 可选。主模型失败时的备用，走 `.fallbackModel`。与主模型相同则忽略。视觉场景主模型用 `dashscope:qwen-vl-plus` 之类 |
| `dream-scope.model.temperature` / `DREAM_SCOPE_MODEL_TEMPERATURE` | 可选。写入 `GenerateOptions`；不配则用模型默认。[0, 2] |
| `dream-scope.model.top-p` / `DREAM_SCOPE_MODEL_TOP_P` | 可选。nucleus sampling；(0, 1] |
| `dream-scope.model.max-tokens` / `DREAM_SCOPE_MODEL_MAX_TOKENS` | 可选。单次生成最大 token，正整数 |
| `dream-scope.chat-timeout` | 同步 / 流式调用超时，默认 `120s`；SSE 连接超时为此值 + 30s |
| `dream-scope.redis.uri` / `DREAM_SCOPE_REDIS_URI` | Redis 连接，默认 `redis://127.0.0.1:6379`。启动时 ping，连不上则进程起不来 |
| `dream-scope.redis.key-prefix` / `DREAM_SCOPE_REDIS_KEY_PREFIX` | Redis key 前缀，默认 `dream-scope:` |
| `dream-scope.workspace-dir` | Harness 工作区（`AGENTS.md` / `subagents/*.md` 等本地文件），默认 `.agentscope/workspace` |
| `dream-scope.compaction.trigger-messages` | 对话条数达到该值触发压缩，默认 `30` |
| `dream-scope.compaction.keep-messages` | 压缩后保留最近原文条数，默认 `10` |

`DREAM_SCOPE_MODEL_KNOWLEDGE` 预留给尚未接线的 knowledge Agent。

## 子 Agent

两种声明可以同时生效，Harness 在 `build()` 时合并：

| 方式 | 落点 | 适合 |
|---|---|---|
| Markdown | 工作区 `subagents/<agent-id>.md`（文件名即 `agent_id`） | 提示词要可改、不发版 |
| 编程式 | `HarnessAgent.builder().subagents(...)`，见 adapter `ChatSubagents` | 规格跟代码走、字段要类型安全 |

还有 `.subagentFactory(name, Function)` 动态工厂，本期不接。

### Markdown（文件）

启动时把 classpath `workspace/subagents/` 拷到 `dream-scope.workspace-dir/subagents/`（覆盖同名 bundled 文件）。`.agentscope/` 已 gitignore，所以必须靠这次拷贝落盘。

| 文件 | agent_id | 作用 |
|---|---|---|
| `weather-agent.md` | `weather-agent` | 天气查询演示，系统提示要求返回固定假数据 |
| `flight-agent.md` | `flight-agent` | 航班查询演示，同样返回固定假数据 |

格式（`AgentSpecLoader`）：YAML front matter（`---` 包围）至少要有 `description`；正文是子 Agent 系统提示词（不要写 `workspace.path`，否则正文会被忽略）。可选：`maxIters`、`tools`、`model`、`workspace.mode`（`isolated` / `shared`）。`tools: "[]"` 在 2.0.3 里仍会**继承**父 Agent 工具（空列表与缺省一样）；要白名单才写非空 `tools`。

自定义：在工作区 `subagents/` 再放 `<agent-id>.md`，或改 web 模块 `src/main/resources/workspace/subagents/` 后重启。

### 编程式（代码）

`ChatSubagents.programmatic()` 当前声明：

| agent_id | 作用 |
|---|---|
| `summarizer` | 把原文缩成不超过三句的中文摘要，禁止调工具 |

对应 API：`SubagentDeclaration.builder().name(...).description(...).inlineAgentsBody(...).maxIters(4)`，再 `.subagents(list)`。不要和 Markdown 用同一个 `agent_id`。

主 Agent 会：天气 → `weather-agent`，航班 → `flight-agent`，总结/摘要 → `summarizer`，工具名都是 `agent_spawn`。

```bash
curl -s http://localhost:8091/api/agents/invoke \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"帮我查一下大阪明天的天气\"}"
```

控制台 `LoggingMiddleware` 的 `onActing` 会打出 `tools=agent_spawn`。

## Roadmap

HITL、分阶段 RAG、MCP。

不做：计费、管理后台、A2A/Nacos。

## License

[Apache License 2.0](LICENSE)
