# dream-scope

基于 [AgentScope Java 2.0](https://java.agentscope.io/) 的模块化单体 Agent 运行时。

业务契约放在 `dream-scope-domain`（禁止依赖 `io.agentscope.*`），框架装配关在 `dream-scope-adapter`。当前主路径：同步 `POST /api/agents/invoke`（`HarnessAgent.call` 语义，内部走 `streamEvents` 累积 `Done`）与流式 `POST /api/agents/stream`（SSE）。chat 带 workspace / Middleware 挂点。

本仓库版本 `0.1.0-SNAPSHOT`。版本锁在根 POM / `dream-scope-bom`：Spring Boot BOM + Spring Cloud Alibaba **2025.0.0.0** + `agentscope-dependencies-bom`。

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Java（`maven.compiler.release`） | 21 |
| 构建 | Maven | 3.9+ |
| 运行时 | Spring Boot 3（Jakarta） | **3.5.5** |
| 配置 / 发现 | Spring Cloud Alibaba Nacos Config + Discovery（默认关） | **2025.0.0.0** |
| HTTP | `spring-boot-starter-web` | 随 Boot |
| 观测 | Actuator `health` / `info` / `metrics` / `prometheus`；chat 挂 `OtelTracingMiddleware`（配了 OTLP 才导出） | 随 Boot；OTel 随 AgentScope BOM |
| Agent 框架 | [AgentScope Java](https://java.agentscope.io/) `agentscope-core` + `agentscope-harness` + `agentscope-extensions-redis`（`RedisDistributedStore`） | **2.0.3** |
| Redis | `redis:7-alpine`（Docker Compose）+ Jedis | 随 AgentScope BOM |
| 模型供应商 | `agentscope-extensions-model-dashscope`（默认 `dashscope:qwen-plus`，`DASHSCOPE_API_KEY`） | **2.0.3** |
| 测试 | JUnit Jupiter；adapter 另用 Mockito；web 用 `spring-boot-starter-test` | JUnit **5.12.2**；Mockito 随 Boot BOM |

构建插件：`maven-compiler-plugin` 3.14.0、`maven-surefire-plugin` 3.5.3、`spring-boot-maven-plugin` 3.5.5。

## 模块

| 模块 | 职责 |
|---|---|
| `dream-scope-bom` | 对外版本锁（Spring Boot / Spring Cloud Alibaba / AgentScope / 本仓库模块） |
| `dream-scope-domain` | SPI、请求响应、Agent 注册表；零框架依赖 |
| `dream-scope-adapter` | AgentScope 装配（模型、消息编解码、后续 Harness / 状态） |
| `dream-scope-knowledge` | RAG 实现，只依赖 domain |
| `dream-scope-web` | **默认产品入口**：手写 `PortsConfig` + HTTP / Actuator / SSE（端口 `8091`） |
| `dream-scope-boot` | **可选对照入口**：官方 `agentscope-spring-boot-starter`（端口 `8092`），不替代 web |

内置 `agentId`：`chat`（默认）、`knowledge`（只检索，不调模型）。配了 `dream-scope.a2a.remote-url` 或 Nacos A2A 发现时还有 `a2a`。`task` 预留，调用返回 `404`。chat 也可调 `retrieve`。索引是进程内关键词，启动时写入几条演示文案，没有向量库。

## 环境

- JDK 21
- Maven 3.9+
- Redis（必选）。本地：`docker compose up -d redis`
- Nacos 3.x（可选，默认全关）。三套开关互不绑死：AgentScope AI（prompt / A2A）、配置中心、服务发现。本地：`docker compose up -d nacos`

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
# 可选 Nacos 3.x：docker compose up -d nacos
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
# 对照：官方 starter（端口 8092，无 Redis / Harness）
# mvn -pl dream-scope-boot -am spring-boot:run
```

默认端口 `8091`。观测：

```bash
curl http://localhost:8091/actuator/health
curl http://localhost:8091/actuator/metrics
curl http://localhost:8091/actuator/prometheus
```

链路：chat 已挂手册里的 `OtelTracingMiddleware`（`invoke_agent` / `chat` / `execute_tool`）。未配 `OTEL_EXPORTER_OTLP_ENDPOINT` 时是 no-op。导出示例：

```bash
export OTEL_SERVICE_NAME=dream-scope
export OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4318
export OTEL_TRACES_EXPORTER=otlp
```

## 两种 Spring 集成

| | `dream-scope-web`（默认） | `dream-scope-boot`（对照） |
|---|---|---|
| 装配 | 手写 `PortsConfig` | 官方 starters（见下表） |
| Agent | `HarnessAgent`（workspace / Redis / Plan / Skill / MCP） | starter 默认 `ReActAgent`（内存 Memory、空 Toolkit） |
| 配置前缀 | `dream-scope.*` | `agentscope.*` |
| 端口 | `8091` | `8092` |
| 依赖 | web **不要**依赖 boot，避免两套 Bean 叠在同一进程 | 独立可启动模块；`io.agentscope` 与 adapter 一样允许出现 |

`dream-scope-boot` 已挂上 2.0.3 官方 starter。厂商只按 `agentscope.model.provider` 启用一个 Model（默认 `dashscope`）。

| Starter | 开关 / 入口 |
|---|---|
| `agentscope-spring-boot-starter` | `agentscope.agent.enabled` |
| `*-dashscope / openai / anthropic / gemini / ollama` | `agentscope.model.provider` + 各 `agentscope.<厂商>.*` |
| `chat-completions-web-starter` | `POST /v1/chat/completions`（`agentscope.chat-completions`） |
| `agui-spring-boot-starter` | `/agui`（默认 agent id = `agentscopeReActAgent`） |
| `admin-spring-boot-starter` | `/v1/admin`，`agentscope.admin.enabled=true`，写操作默认关 |
| `a2a-spring-boot-starter` | `agentscope.a2a.server.enabled` |
| `nacos-spring-boot-starter` | `agentscope.a2a.nacos.enabled` / `agentscope.nacos.prompt.enabled`，**必须显式 true**（没 Nacos 会起不来） |
| SCA `nacos-config` / `nacos-discovery` | `DREAM_SCOPE_NACOS_CONFIG_ENABLED` / `DREAM_SCOPE_NACOS_DISCOVERY_ENABLED`，默认 false；与上面 AI starter 独立 |

对照入口另外还有 `POST /api/agents/invoke` 与 `/stream`。DashScope Key：`DASHSCOPE_API_KEY`。没有 Harness，也就没有 Redis / 子 Agent / 技能 / Plan Mode。

```bash
mvn -pl dream-scope-boot -am spring-boot:run
curl -s http://localhost:8092/api/agents/invoke \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"你好\"}"
```

## 调用

`POST /api/agents/invoke`，JSON：

| 字段 | 说明 |
|---|---|
| `input` | 文本；与 `imageUrls` 至少一个非空，否则 `400` |
| `imageUrls` | 可选。http/https 图片 URL 列表，最多 8 个。视觉模型（如 `dashscope:qwen-vl-plus`）才能看图 |
| `structured` | 可选。`true` 时走 Harness 结构化输出（`stream(..., Map.class)` / JSON Schema），响应带 `data` 对象 |
| `jsonSchema` | 可选。JSON Schema 对象；出现则视为 `structured=true` |
| `agentId` | 可选，默认 `chat`；未知或未接线 id 返回 `404` |
| `sessionId` / `userId` | 可选。成对传入时，会话写入 Redis（`dream-scope.redis`），同槽位可续聊 |

模型超时返回 `504`，供应商/运行失败返回 `502`。

chat 已注册演示工具（无 Spring）：`getCurrentTime`、`calculate`（四则运算）、`httpGet`（仅 http/https，截断响应体）、`retrieve`（进程内关键词检索，返回 `[1]` 编号资料）。模型需要时会走 ReAct 调工具。Harness 默认的工作区 `read_file`/`write_file`/`shell` **已关闭**，避免 HTTP 进程在本机执行文件和命令。

默认开启 Plan Mode：模型可自行 `plan_enter` / `plan_write` / `plan_exit`（计划文件写在工作区 `plans/`）。不提供 HTTP 手动进入，也不做人审确认；计划模式里仍然不允许 Shell。普通问答模型不进计划则行为与原来相同。`/invoke` 与 SSE `done` 带 `planActive`。

chat 挂了 `OtelTracingMiddleware` + `LoggingMiddleware`（无 Spring 注解）。日志打 `onAgent` / `onModelCall` / `onActing` 的 start/complete（含 `agentId`、`sessionId`、耗时）。看控制台即可。

```bash
curl -s http://localhost:8091/api/agents/invoke \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"你好\"}"
```

需已导出 `DASHSCOPE_API_KEY`。`output` 为模型回复，例如：

```json
{"agentId":"chat","output":"你好，有什么可以帮你的？","inputTokens":0,"outputTokens":0,"data":null,"planActive":false}
```

流式：`POST /api/agents/stream`，请求体与 `/invoke` 相同，响应 `text/event-stream`。每条 SSE `data` 是 JSON，`event` 为 `textDelta` / `toolCall` / `toolResult` / `hint` / `done` / `error`。`done` 的 JSON 含 `finalOutput`、`inputTokens`、`outputTokens`（多次模型调用会累加）、`planActive`；结构化请求成功时还有 `data` 对象。`done` 之后连接结束。SSE 超时 = `chat-timeout` + 30s。

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
| `dream-scope.workspace-dir` | Harness 工作区（`AGENTS.md` / `subagents/*.md` / `skills/*/SKILL.md` 等本地文件），默认 `.agentscope/workspace` |
| `dream-scope.compaction.trigger-messages` | 对话条数达到该值触发压缩，默认 `30` |
| `dream-scope.compaction.keep-messages` | 压缩后保留最近原文条数，默认 `10` |
| `dream-scope.plan-mode.enabled` / `DREAM_SCOPE_PLAN_MODE_ENABLED` | 是否 `enablePlanMode`，默认 `true` |
| `dream-scope.plan-mode.directory` / `DREAM_SCOPE_PLAN_MODE_DIRECTORY` | 计划文件相对工作区目录，默认 `plans` |
| `dream-scope.mcp.servers` | 可选。工作区 `tools.json` 的 MCP 列表；仅 http/https 的 streamableHttp 或 sse |
| `dream-scope.a2a.enabled` / `DREAM_SCOPE_A2A_ENABLED` | 是否暴露 Agent Card 与 `/a2a`，默认 `true` |
| `dream-scope.a2a.public-url` / `DREAM_SCOPE_A2A_PUBLIC_URL` | 写入 Agent Card 的对外根地址，默认 `http://127.0.0.1:8091` |
| `dream-scope.a2a.remote-url` / `DREAM_SCOPE_A2A_REMOTE_URL` | 可选。well-known 发现远端；Nacos discovery 开启时忽略 |
| `dream-scope.nacos.enabled` / `DREAM_SCOPE_NACOS_ENABLED` | AgentScope AI 总开关。默认 `false`。true 时连 Nacos 3.x AI（8848 + gRPC 9848） |
| `dream-scope.nacos.server-addr` / `NACOS_SERVER_ADDR` | 默认 `127.0.0.1:8848`（AI 与 Spring Cloud 共用） |
| `dream-scope.nacos.namespace` / `NACOS_NAMESPACE` | AI 用。默认 `public`（填 namespaceId） |
| `dream-scope.nacos.prompt.enabled` | 启动时拉 prompt，**拼在**内置 SYS_PROMPT 后；改 Nacos 后要重启 |
| `dream-scope.nacos.prompt.sys-prompt-key` | 默认 `dream-scope-chat` |
| `dream-scope.nacos.a2a.registry-enabled` | `postEndpointReady` 时把本机 Agent Card 注册进 Nacos |
| `dream-scope.nacos.a2a.discovery-enabled` | 注册 `agentId=a2a`，用 Nacos 发现（优先于 `a2a.remote-url`） |
| `dream-scope.nacos.a2a.discovery-agent-name` | 默认 `dream-scope-chat`，须与 Nacos 里的 Agent 名一致 |
| `spring.cloud.nacos.config.enabled` / `DREAM_SCOPE_NACOS_CONFIG_ENABLED` | 配置中心。默认 `false`。Data ID = `{spring.application.name}.yaml`，示例见 `docs/nacos/` |
| `spring.cloud.nacos.discovery.enabled` / `DREAM_SCOPE_NACOS_DISCOVERY_ENABLED` | 服务发现。默认 `false`。注册名 = `spring.application.name`（web=`dream-scope`，boot=`dream-scope-boot`） |
| `NACOS_CLOUD_NAMESPACE` | Spring Cloud 配置/发现的 namespaceId；空=public。不要填名字 `public` |

`DREAM_SCOPE_MODEL_KNOWLEDGE` 预留：knowledge 当前只检索、不调模型。

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

## 技能

工作区 `skills/<name>/SKILL.md` 由 Harness 自动扫描（无需 `.skillRepository`）。启动时把 classpath `workspace/skills/` 拷到 `dream-scope.workspace-dir/skills/`（覆盖同名 bundled）。模型看到 `<available_skills>` 后，用内置 `load_skill_through_path` 读正文。

当前 bundled：`meeting-notes`（会议纪要，纯指令，无脚本）。HTTP 进程仍关闭文件工具和 Shell，技能里不要放 `scripts/`。

自定义：在工作区 `skills/<name>/SKILL.md` 再放一份（YAML 至少 `name` + `description`），或改 web 模块 `src/main/resources/workspace/skills/` 后重启。Git / Nacos Skill 市场本期不接（2.0.3 无 `nacos-skill` 工件）。

## MCP

Harness 在 `build()` 时读工作区 `tools.json` 的 `mcpServers`。启动会先拷 bundled 空文件，再按 `dream-scope.mcp.servers` 覆写。只允许 `streamableHttp` / `sse`（http/https），**禁止 stdio**（HTTP 进程不拉本地 MCP 子进程）。配了之后模型会看到 `mcp__{name}__{tool}`。未配服务器则工具表不变。

```yaml
dream-scope:
  mcp:
    servers:
      - name: weather
        transport: streamableHttp
        url: https://example.com/mcp
```

## A2A

不引入官方 a2a / nacos Spring starter（对照入口在 boot）。本进程作为 A2A Server：

- `GET /.well-known/agent-card.json`
- `POST /a2a`（JSON-RPC `message/send` → 内置 chat）

配 `dream-scope.a2a.remote-url` 时额外注册 `agentId=a2a`，走 well-known Card。配 `dream-scope.nacos.a2a.discovery-enabled=true`（且 `nacos.enabled=true`）时改为 `NacosAgentCardResolver`，发现名 `dream-scope.nacos.a2a.discovery-agent-name`。Nacos 发现优先于 remote-url。

`dream-scope.nacos.a2a.registry-enabled=true` 时，`ApplicationReady` 的 `postEndpointReady` 会把 Card / 端点写进 Nacos AI。`dream-scope.a2a.enabled=false` 时关掉本机 Card / `/a2a`。

Nacos 默认关。本地：

```bash
docker compose up -d nacos
# 控制台可选：http://127.0.0.1:18080
# AI（prompt / A2A Card）
export DREAM_SCOPE_NACOS_ENABLED=true
export DREAM_SCOPE_NACOS_PROMPT_ENABLED=true
export DREAM_SCOPE_NACOS_A2A_REGISTRY_ENABLED=true
# 配置中心 + 服务发现（Spring Cloud Alibaba，与 AI 开关独立）
export DREAM_SCOPE_NACOS_CONFIG_ENABLED=true
export DREAM_SCOPE_NACOS_DISCOVERY_ENABLED=true
```

Prompt 在 Nacos AI 里建 key=`dream-scope-chat` 的模板。web 会把它拼到内置工具/子 Agent 系统提示后面；Harness 的 `sysPrompt` 只在 `build()` 时写入，改 Nacos 后要重启。boot 对照走官方 `agentscope.nacos.prompt.enabled`，是**整段替换** `agentscope.agent.sys-prompt`。

配置中心 Data ID：`dream-scope.yaml` / `dream-scope-boot.yaml`（Group `DEFAULT_GROUP`）。启动时 `optional:nacos:` 导入，覆盖本地 yml；改 `dream-scope.model.*` / 超时后仍要重启（Harness 只 `build()` 一次）。服务发现打开后看控制台，或 `GET /api/nacos/status`、`GET /api/nacos/instances/{serviceId}`（发现关则空列表）。

## Roadmap

HITL、knowledge 调模型、入库/向量检索。

不做：计费、管理后台。

## License

[Apache License 2.0](LICENSE)
