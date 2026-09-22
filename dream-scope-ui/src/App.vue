<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
const links = [
  { to: '/chat', label: '对话' },
  { to: '/knowledge', label: '知识库' },
  { to: '/nacos', label: 'Nacos' },
  { to: '/a2a', label: 'A2A' },
]
</script>

<template>
  <div class="shell">
    <header class="top">
      <div>
        <p class="mark">dream-scope</p>
        <p class="hint">接口在 8091，这个页面只负责调用。</p>
      </div>
      <nav class="nav" aria-label="功能">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :aria-current="route.path === link.to ? 'page' : undefined"
        >
          {{ link.label }}
        </RouterLink>
      </nav>
    </header>
    <main>
      <RouterView />
    </main>
  </div>
</template>
