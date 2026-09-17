# dream-scope

基于 [AgentScope Java 2.0](https://java.agentscope.io/) 的模块化单体 Agent 运行时。

业务契约放在 `dream-scope-domain`（禁止依赖 `io.agentscope.*`），框架装配关在 `dream-scope-adapter`。当前主路径是同步 HTTP 调用；chat 先走 echo 桩，ReAct / 真实模型接线在 adapter 侧继续补。

## 模块

| 模块 | 职责 |
|---|---|
| `dream-scope-bom` | 对外版本锁（Spring Boot / AgentScope / 本仓库模块） |
| `dream-scope-domain` | SPI、请求响应、Agent 注册表；零框架依赖 |
| `dream-scope-adapter` | AgentScope 装配（模型、消息编解码、后续 Harness / 状态） |
| `dream-scope-knowledge` | RAG 实现，只依赖 domain |
| `dream-scope-web` | 唯一可启动模块：HTTP / Actuator（后续 SSE） |

内置 `agentId`：`chat`（默认）、`knowledge`、`task`。

## 环境

- JDK 21
- Maven 3.9+

## 本地运行

```bash
git clone https://github.com/logosssss/dream-scope.git
cd dream-scope
```

复制环境变量模板（真实 Key 不要入库）：

```bash
cp .env.example .env
```

进程**不会**自动加载 `.env`。echo 桩不需要 Key；接真实模型时把变量导出到当前 shell，例如：

```bash
export DASHSCOPE_API_KEY=your-key
# 可选
export DREAM_SCOPE_MODEL_DEFAULT=dashscope:qwen-plus
```

Windows PowerShell：

```powershell
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
| `input` | 必填，空白返回 `400` |
| `agentId` | 可选，默认 `chat`；未知 id 返回 `404` |
| `sessionId` / `userId` | 可选，会话与用户 |

```bash
curl -s http://localhost:8091/api/agents/invoke \
  -H "Content-Type: application/json" \
  -d "{\"input\":\"你好\"}"
```

当前 chat 为 echo，响应形如：

```json
{"agentId":"chat","output":"echo:你好"}
```

## 配置

见 [`.env.example`](.env.example)。常用项：

| 变量 | 含义 |
|---|---|
| `DASHSCOPE_API_KEY` / `DEEPSEEK_API_KEY` | 模型供应商 Key |
| `DREAM_SCOPE_MODEL_DEFAULT` | 默认模型 id（`ModelRegistry` 字符串） |
| `DREAM_SCOPE_MODEL_CHAT` / `DREAM_SCOPE_MODEL_KNOWLEDGE` | 按 Agent 覆盖模型 |

本地覆盖可用 `application-local.yml`（已 gitignore）。

## Roadmap

HITL、记忆压缩、分阶段 RAG、子 Agent、MCP。

不做：计费、管理后台、A2A/Nacos。

## License

[Apache License 2.0](LICENSE)
