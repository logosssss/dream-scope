<script setup>
import { computed, onMounted, ref } from 'vue'
import { a2aSend, a2aText, agentCard } from '../api.js'

const card = ref(null)
const cardError = ref('')
const text = ref('')
const reply = ref('')
const raw = ref('')
const sendError = ref('')
const busy = ref(false)

const cardJson = computed(() => (card.value ? JSON.stringify(card.value, null, 2) : ''))

onMounted(loadCard)

async function loadCard() {
  cardError.value = ''
  try {
    card.value = await agentCard()
  } catch (ex) {
    card.value = null
    cardError.value = ex.message
  }
}

async function send() {
  sendError.value = ''
  reply.value = ''
  raw.value = ''
  busy.value = true
  try {
    const response = await a2aSend(text.value)
    raw.value = JSON.stringify(response, null, 2)
    reply.value = a2aText(response)
  } catch (ex) {
    sendError.value = ex.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="stack">
    <div class="row">
      <h1>A2A</h1>
      <button type="button" class="quiet" @click="loadCard">刷新 Card</button>
    </div>
    <section class="panel stack">
      <h2>Agent Card</h2>
      <p class="status error" role="status">{{ cardError }}</p>
      <p v-if="!card && !cardError" class="sub">正在读取。</p>
      <pre v-if="cardJson">{{ cardJson }}</pre>
    </section>
    <form class="panel stack" @submit.prevent="send">
      <h2>message/send</h2>
      <label>
        文本
        <textarea v-model="text" required />
      </label>
      <div class="row">
        <button type="submit" :disabled="busy || !text.trim()">发送</button>
      </div>
      <p class="status error" role="status">{{ sendError }}</p>
      <pre v-if="reply">{{ reply }}</pre>
      <pre v-if="raw">{{ raw }}</pre>
    </form>
  </section>
</template>
