<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  addKnowledgeFile,
  addKnowledgeText,
  deleteKnowledgeSource,
  knowledgeJob,
  knowledgeSources,
  retrieveKnowledge,
} from '../api.js'

const text = ref('')
const textSource = ref('')
const textType = ref('note')
const textId = ref('')
const fileSource = ref('')
const fileType = ref('file')
const fileId = ref('')
const file = ref(null)
const query = ref('')
const topK = ref(5)
const retrieveSource = ref('')

const textStatus = ref('')
const textError = ref('')
const fileStatus = ref('')
const fileError = ref('')
const job = ref(null)
const retrieveError = ref('')
const hits = ref(null)
const sources = ref([])
const sourceError = ref('')
const sourceNote = ref('')
const busyText = ref(false)
const busyFile = ref(false)
const busyRetrieve = ref(false)
let pollTimer = 0

onMounted(loadSources)
onBeforeUnmount(() => window.clearTimeout(pollTimer))

function onFile(event) {
  file.value = event.target.files?.[0] || null
}

async function submitText() {
  textError.value = ''
  textStatus.value = ''
  busyText.value = true
  try {
    const saved = await addKnowledgeText({
      id: textId.value.trim(),
      text: text.value,
      source: textSource.value.trim(),
      docType: textType.value.trim(),
    })
    textStatus.value = `已写入 ${saved.id || ''}`
    text.value = ''
    await loadSources()
  } catch (ex) {
    textError.value = ex.message
  } finally {
    busyText.value = false
  }
}

async function submitFile() {
  fileError.value = ''
  fileStatus.value = ''
  job.value = null
  if (!file.value) {
    fileError.value = '先选择文件'
    return
  }
  busyFile.value = true
  try {
    const accepted = await addKnowledgeFile(file.value, {
      id: fileId.value.trim(),
      source: fileSource.value.trim(),
      docType: fileType.value.trim(),
    })
    fileStatus.value = `已接受 ${accepted.filename}`
    await watchJob(accepted.jobId)
  } catch (ex) {
    fileError.value = ex.message
  } finally {
    busyFile.value = false
  }
}

async function watchJob(jobId) {
  window.clearTimeout(pollTimer)
  const current = await knowledgeJob(jobId)
  job.value = current
  if (current.status === 'done' || current.status === 'failed') {
    await loadSources()
    return
  }
  pollTimer = window.setTimeout(() => watchJob(jobId), 800)
}

async function search() {
  retrieveError.value = ''
  hits.value = null
  busyRetrieve.value = true
  try {
    const body = { query: query.value, topK: Number(topK.value) || 5 }
    if (retrieveSource.value.trim()) {
      body.source = retrieveSource.value.trim()
    }
    const result = await retrieveKnowledge(body)
    hits.value = result.hits || []
  } catch (ex) {
    retrieveError.value = ex.message
  } finally {
    busyRetrieve.value = false
  }
}

async function loadSources() {
  sourceError.value = ''
  try {
    sources.value = (await knowledgeSources()) || []
  } catch (ex) {
    sourceError.value = ex.message
  }
}

async function removeSource(source) {
  if (!window.confirm(`删除来源 ${source}？`)) {
    return
  }
  sourceError.value = ''
  sourceNote.value = ''
  try {
    const result = await deleteKnowledgeSource(source)
    sourceNote.value = `已删除 ${result.deleted} 块`
    await loadSources()
  } catch (ex) {
    sourceError.value = ex.message
  }
}
</script>

<template>
  <section class="stack">
    <h1>知识库</h1>

    <div class="grid two">
      <form class="panel stack" @submit.prevent="submitText">
        <h2>写入文本</h2>
        <label>
          正文
          <textarea v-model="text" required />
        </label>
        <label>
          来源
          <input v-model="textSource" />
        </label>
        <label>
          类型
          <input v-model="textType" />
        </label>
        <label>
          id，可空
          <input v-model="textId" />
        </label>
        <div class="row">
          <button type="submit" :disabled="busyText || !text.trim()">写入</button>
        </div>
        <p class="status" :class="{ error: !!textError, ok: !!textStatus && !textError }" role="status">
          {{ textError || textStatus }}
        </p>
      </form>

      <form class="panel stack" @submit.prevent="submitFile">
        <h2>上传文件</h2>
        <label>
          文件
          <input type="file" @change="onFile" />
        </label>
        <label>
          来源，空则用文件名
          <input v-model="fileSource" />
        </label>
        <label>
          类型
          <input v-model="fileType" />
        </label>
        <label>
          id，可空
          <input v-model="fileId" />
        </label>
        <div class="row">
          <button type="submit" :disabled="busyFile">上传</button>
        </div>
        <p class="status" :class="{ error: !!fileError, ok: !!fileStatus && !fileError }" role="status">
          {{ fileError || fileStatus }}
        </p>
        <p v-if="job" class="sub">
          {{ job.status }}
          <span v-if="job.chunks"> · {{ job.doneChunks }}/{{ job.chunks }}</span>
          <span v-if="job.error"> · {{ job.error }}</span>
        </p>
      </form>
    </div>

    <form class="panel stack" @submit.prevent="search">
      <h2>检索</h2>
      <label>
        问句
        <input v-model="query" required />
      </label>
      <div class="grid two">
        <label>
          topK
          <input v-model="topK" type="number" min="1" />
        </label>
        <label>
          只查这个来源
          <input v-model="retrieveSource" />
        </label>
      </div>
      <div class="row">
        <button type="submit" :disabled="busyRetrieve || !query.trim()">检索</button>
      </div>
      <p class="status error" role="status">{{ retrieveError }}</p>
      <p v-if="hits && !hits.length" class="sub">没有命中。</p>
      <ul v-if="hits && hits.length" class="plain">
        <li v-for="hit in hits" :key="hit.id">
          <div class="hit-meta">{{ hit.score }} · {{ hit.source || '无来源' }} · {{ hit.docType || '无类型' }}</div>
          <div>{{ hit.text }}</div>
        </li>
      </ul>
    </form>

    <section class="panel">
      <div class="row">
        <h2>来源</h2>
        <button type="button" class="quiet" @click="loadSources">刷新</button>
      </div>
      <p class="status" :class="{ error: !!sourceError, ok: !!sourceNote && !sourceError }" role="status">
        {{ sourceError || sourceNote }}
      </p>
      <p v-if="!sourceError && !sources.length" class="sub">当前进程里还没有来源。重启后向量库里的旧数据不一定出现在这里。</p>
      <ul v-else class="plain">
        <li v-for="item in sources" :key="item.source" class="row">
          <span>{{ item.source || '（空来源）' }} · {{ item.chunks }} 块</span>
          <button type="button" class="danger" @click="removeSource(item.source)">删除</button>
        </li>
      </ul>
    </section>
  </section>
</template>
