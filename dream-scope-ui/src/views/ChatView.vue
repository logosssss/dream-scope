<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { invokeAgent, streamAgent } from '../api.js'

const agents = [
  { id: 'chat', label: 'chat · 对话' },
  { id: 'knowledge', label: 'knowledge · 知识检索' },
]
const examples = [
  { label: '当前时间', text: '现在服务器是几点？' },
  { label: '计算', text: '计算 (12+8)*3/2' },
  { label: '知识检索', text: 'dream-scope 的调用方式是什么？请先检索再按编号引用。' },
  {
    label: '会议纪要',
    text: '把下面讨论整理成会议纪要：张三说下周发布对话页，李四负责知识库上传，周五验收。',
  },
  {
    label: '摘要',
    text: '请总结下面这段话：dream-scope 用 ReActAgent 做对话，工具包括时间、计算、HTTP 和知识检索，天气和航班交给子 Agent。',
  },
  { label: '天气', text: '上海明天天气怎么样？' },
  {
    label: '计划',
    focus: 'plan',
    text: '这是复杂任务。必须先调用 plan_enter，再用 plan_write 写下步骤，然后 plan_exit，最后按计划执行：调用 getCurrentTime，再调用 calculate 计算 6*7。',
  },
  {
    label: 'MCP',
    focus: 'mcp',
    text: '请调用工具 mcp__demo__echo，参数 text 填 hello。',
  },
  {
    label: '结构化',
    text: '用结构化字段介绍上海：城市名和一句话摘要。',
    structured: true,
    schema: `{
  "type": "object",
  "properties": {
    "city": { "type": "string" },
    "summary": { "type": "string" }
  },
  "required": ["city", "summary"]
}`,
  },
]

const agentId = ref('chat')
const sessionId = ref('')
const userId = ref('')
const input = ref('')
const imageUrls = ref('')
const structured = ref(false)
const schemaText = ref('')
const busy = ref(false)
const error = ref('')
const output = ref('')
const tokenMeta = ref('')
const planActive = ref(false)
const steps = ref([])
const dataText = ref('')
const started = ref(false)
const focus = ref('')
let streamAbort = null

const canSend = computed(() => input.value.trim().length > 0 && !busy.value)
const tools = computed(() => pairCalls('tool'))
const skills = computed(() => pairCalls('skill'))
const subagents = computed(() => pairCalls('subagent'))
const mcpCalls = computed(() => pairCalls('mcp'))
const planCalls = computed(() => pairCalls('plan'))
const hints = computed(() => steps.value.filter((step) => step.type === 'hint'))
const errors = computed(() => steps.value.filter((step) => step.type === 'error'))
const showPlan = computed(
  () => focus.value === 'plan' || planActive.value || hints.value.length > 0 || planCalls.value.length > 0,
)
const showMcp = computed(() => focus.value === 'mcp' || mcpCalls.value.length > 0)

function laneOf(name) {
  const tool = name || ''
  if (tool.startsWith('mcp__') || tool.startsWith('mcp_')) {
    return 'mcp'
  }
  if (tool === 'agent_spawn' || tool.includes('spawn')) {
    return 'subagent'
  }
  if (tool.includes('skill')) {
    return 'skill'
  }
  if (tool.startsWith('plan_')) {
    return 'plan'
  }
  if (tool) {
    return 'tool'
  }
  return ''
}

function pairCalls(lane) {
  const cards = []
  for (const step of steps.value) {
    if (step.type !== 'toolCall' && step.type !== 'toolResult') {
      continue
    }
    if (laneOf(step.name) !== lane) {
      continue
    }
    if (step.type === 'toolCall') {
      cards.push({ name: step.name, input: step.text, output: '' })
      continue
    }
    const open = cards.findLast((card) => card.name === step.name && card.output === '')
    if (open) {
      open.output = step.text
    } else {
      cards.push({ name: step.name, input: '', output: step.text })
    }
  }
  return cards
}

function payload() {
  if (!sessionId.value.trim()) {
    sessionId.value = crypto.randomUUID()
  }
  const body = {
    agentId: agentId.value.trim() || 'chat',
    sessionId: sessionId.value.trim(),
    userId: userId.value.trim(),
    input: input.value,
  }
  const images = imageUrls.value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (images.length) {
    body.imageUrls = images
  }
  if (structured.value) {
    body.structured = true
    if (schemaText.value.trim()) {
      body.jsonSchema = JSON.parse(schemaText.value)
    }
  }
  return body
}

function resetResult() {
  error.value = ''
  output.value = ''
  tokenMeta.value = ''
  planActive.value = false
  steps.value = []
  dataText.value = ''
  started.value = true
}

function rememberTokens(inputTokens, outputTokens) {
  tokenMeta.value = `in ${inputTokens ?? 0} · out ${outputTokens ?? 0}`
}

function showResult(result) {
  output.value = result.output || ''
  planActive.value = !!result.planActive
  rememberTokens(result.inputTokens, result.outputTokens)
  steps.value = Array.isArray(result.trace) ? result.trace : []
  dataText.value = result.data ? JSON.stringify(result.data, null, 2) : ''
}

function readBody() {
  try {
    return payload()
  } catch {
    error.value = 'JSON Schema 不是合法 JSON'
    return null
  }
}

async function runSync() {
  resetResult()
  const body = readBody()
  if (!body) {
    started.value = false
    return
  }
  busy.value = true
  try {
    showResult(await invokeAgent(body))
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

async function runStream() {
  resetResult()
  const body = readBody()
  if (!body) {
    started.value = false
    return
  }
  busy.value = true
  streamAbort = new AbortController()
  let deltas = ''
  try {
    await streamAgent(
      body,
      (name, event) => {
        if (name === 'textDelta') {
          deltas += event.text || ''
          output.value = deltas
        } else if (name === 'toolCall' || name === 'toolResult' || name === 'hint' || name === 'error') {
          steps.value = steps.value.concat({
            type: name,
            name: event.toolName || '',
            text: event.input || event.output || event.text || event.message || '',
          })
        } else if (name === 'done') {
          if (event.finalOutput) {
            output.value = event.finalOutput
          }
          planActive.value = !!event.planActive
          rememberTokens(event.inputTokens, event.outputTokens)
          dataText.value = event.data ? JSON.stringify(event.data, null, 2) : ''
        }
      },
      streamAbort.signal,
    )
  } catch (ex) {
    if (ex.name !== 'AbortError') {
      error.value = ex.message
    }
  } finally {
    busy.value = false
    streamAbort = null
  }
}

async function submitSync() {
  focus.value = ''
  await runSync()
}

async function submitStream() {
  focus.value = ''
  await runStream()
}

async function useExample(example) {
  if (busy.value) {
    return
  }
  input.value = example.text
  structured.value = !!example.structured
  schemaText.value = example.schema || ''
  focus.value = example.focus || ''
  agentId.value = 'chat'
  if (example.structured) {
    await runSync()
  } else {
    await runStream()
  }
}

function stopStream() {
  streamAbort?.abort()
}

onBeforeUnmount(stopStream)
</script>

<template>
  <section>
    <h1>对话</h1>
    <p class="sub">一次调用里的工具、技能、子 Agent、计划和结构化结果会分开显示。同一会话再发一句会接着上一轮。</p>
    <div class="grid two">
      <form class="panel stack" @submit.prevent="submitSync">
        <div>
          <p class="trace-meta">示例</p>
          <div class="row">
            <button
              v-for="example in examples"
              :key="example.label"
              type="button"
              class="quiet"
              :disabled="busy"
              @click="useExample(example)"
            >
              {{ example.label }}
            </button>
          </div>
        </div>
        <label>
          Agent
          <select v-model="agentId" name="agentId">
            <option v-for="agent in agents" :key="agent.id" :value="agent.id">{{ agent.label }}</option>
          </select>
        </label>
        <label>
          会话
          <input v-model="sessionId" name="sessionId" autocomplete="off" placeholder="空着会自动生成，便于接着聊" />
        </label>
        <label>
          用户
          <input v-model="userId" name="userId" autocomplete="off" />
        </label>
        <label>
          输入
          <textarea v-model="input" name="input" required />
        </label>
        <label>
          图片 URL，一行一个
          <textarea v-model="imageUrls" name="imageUrls" />
        </label>
        <label class="check">
          <input v-model="structured" type="checkbox" />
          结构化输出
        </label>
        <label v-if="structured">
          JSON Schema
          <textarea v-model="schemaText" name="jsonSchema" spellcheck="false" />
        </label>
        <div class="row">
          <button type="submit" :disabled="!canSend">同步调用</button>
          <button type="button" class="quiet" :disabled="!canSend" @click="submitStream">流式</button>
          <button type="button" class="quiet" :disabled="!busy" @click="stopStream">停止</button>
        </div>
        <p class="status" :class="{ error: !!error }" role="status">{{ error }}</p>
      </form>

      <div class="stack">
        <p v-if="!started" class="sub">还没有调用。点左侧示例，或自己输入后走流式。</p>
        <div v-else class="row" aria-label="本次出现的能力">
          <span class="chip" :class="{ on: !!output }">回复</span>
          <span class="chip" :class="{ on: tools.length > 0 }">工具 {{ tools.length || '' }}</span>
          <span class="chip" :class="{ on: skills.length > 0 }">技能 {{ skills.length || '' }}</span>
          <span class="chip" :class="{ on: subagents.length > 0 }">子 Agent {{ subagents.length || '' }}</span>
          <span class="chip" :class="{ on: mcpCalls.length > 0 }">MCP {{ mcpCalls.length || '' }}</span>
          <span class="chip" :class="{ on: planActive || hints.length > 0 || planCalls.length > 0 }">计划</span>
          <span class="chip" :class="{ on: !!dataText }">结构化</span>
        </div>

        <section v-if="output || tokenMeta" class="panel stack">
          <h2>回复</h2>
          <p v-if="tokenMeta" class="sub">{{ tokenMeta }}</p>
          <pre v-if="output">{{ output }}</pre>
        </section>

        <section v-if="tools.length" class="panel stack">
          <h2>工具</h2>
          <ul class="plain">
            <li v-for="(card, index) in tools" :key="`tool-${index}`">
              <div class="trace-meta">{{ card.name }}</div>
              <pre v-if="card.input">{{ card.input }}</pre>
              <pre v-if="card.output">{{ card.output }}</pre>
            </li>
          </ul>
        </section>

        <section v-if="skills.length" class="panel stack">
          <h2>技能</h2>
          <ul class="plain">
            <li v-for="(card, index) in skills" :key="`skill-${index}`">
              <div class="trace-meta">{{ card.name }}</div>
              <pre v-if="card.input">{{ card.input }}</pre>
              <pre v-if="card.output">{{ card.output }}</pre>
            </li>
          </ul>
        </section>

        <section v-if="subagents.length" class="panel stack">
          <h2>子 Agent</h2>
          <ul class="plain">
            <li v-for="(card, index) in subagents" :key="`sub-${index}`">
              <div class="trace-meta">{{ card.name }}</div>
              <pre v-if="card.input">{{ card.input }}</pre>
              <pre v-if="card.output">{{ card.output }}</pre>
            </li>
          </ul>
        </section>

        <section v-if="mcpCalls.length" class="panel stack">
          <h2>MCP</h2>
          <ul class="plain">
            <li v-for="(card, index) in mcpCalls" :key="`mcp-${index}`">
              <div class="trace-meta">{{ card.name }}</div>
              <pre v-if="card.input">{{ card.input }}</pre>
              <pre v-if="card.output">{{ card.output }}</pre>
            </li>
          </ul>
        </section>
        <section v-else-if="showMcp" class="panel stack">
          <h2>MCP</h2>
          <p class="sub">这次没有 mcp__ 调用。本机演示工具是 mcp__demo__echo，随 8091 听在 8093。若工具表里没有它，重启 8091 后再开一个新会话。</p>
        </section>

        <section v-if="showPlan" class="panel stack">
          <h2>计划</h2>
          <p v-if="planActive" class="sub">planActive</p>
          <p v-if="!planCalls.length && !hints.length" class="sub">这次没有 plan_enter、plan_write、plan_exit。服务端计划模式默认开着，模型仍可能把普通问答跳过。</p>
          <ul v-if="planCalls.length" class="plain">
            <li v-for="(card, index) in planCalls" :key="`plan-${index}`">
              <div class="trace-meta">{{ card.name }}</div>
              <pre v-if="card.input">{{ card.input }}</pre>
              <pre v-if="card.output">{{ card.output }}</pre>
            </li>
          </ul>
          <ul v-if="hints.length" class="plain">
            <li v-for="(hint, index) in hints" :key="`hint-${index}`">{{ hint.text }}</li>
          </ul>
        </section>

        <section v-if="dataText" class="panel stack">
          <h2>结构化</h2>
          <pre>{{ dataText }}</pre>
        </section>

        <section v-if="errors.length" class="panel stack">
          <h2>错误</h2>
          <ul class="plain">
            <li v-for="(item, index) in errors" :key="`err-${index}`">{{ item.text }}</li>
          </ul>
        </section>
      </div>
    </div>
  </section>
</template>
