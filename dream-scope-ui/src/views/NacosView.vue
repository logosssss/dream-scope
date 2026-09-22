<script setup>
import { onMounted, ref } from 'vue'
import { nacosInstances, nacosStatus } from '../api.js'

const status = ref(null)
const error = ref('')
const serviceId = ref('')
const instances = ref(null)
const instanceError = ref('')

onMounted(load)

async function load() {
  error.value = ''
  try {
    status.value = await nacosStatus()
  } catch (ex) {
    error.value = ex.message
  }
}

async function loadInstances(id) {
  serviceId.value = id
  instanceError.value = ''
  instances.value = null
  try {
    instances.value = (await nacosInstances(id)) || []
  } catch (ex) {
    instanceError.value = ex.message
  }
}
</script>

<template>
  <section class="stack">
    <div class="row">
      <h1>Nacos</h1>
      <button type="button" class="quiet" @click="load">刷新</button>
    </div>
    <p class="status error" role="status">{{ error }}</p>
    <section v-if="status" class="panel stack">
      <p class="sub">应用 {{ status.application || '—' }} · 地址 {{ status.serverAddr || '未配置' }}</p>
      <ul class="plain">
        <li>配置中心 {{ status.configEnabled ? '开' : '关' }}</li>
        <li>服务发现 {{ status.discoveryEnabled ? '开' : '关' }}</li>
        <li>AI Prompt {{ status.aiEnabled ? '开' : '关' }}</li>
      </ul>
      <h2>服务</h2>
      <p v-if="!status.services || !status.services.length" class="sub">没有发现服务。发现关闭时这里是空的。</p>
      <div v-else class="row">
        <button
          v-for="name in status.services"
          :key="name"
          type="button"
          class="quiet"
          @click="loadInstances(name)"
        >
          {{ name }}
        </button>
      </div>
    </section>
    <section v-if="serviceId" class="panel stack">
      <h2>{{ serviceId }} 的实例</h2>
      <p class="status error" role="status">{{ instanceError }}</p>
      <p v-if="instances && !instances.length" class="sub">没有实例。</p>
      <ul v-if="instances && instances.length" class="plain">
        <li v-for="item in instances" :key="item.instanceId || item.uri">
          <div>{{ item.host }}:{{ item.port }}</div>
          <div class="hit-meta">{{ item.uri }}</div>
        </li>
      </ul>
    </section>
  </section>
</template>
