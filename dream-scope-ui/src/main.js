import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ChatView from './views/ChatView.vue'
import KnowledgeView from './views/KnowledgeView.vue'
import NacosView from './views/NacosView.vue'
import A2aView from './views/A2aView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: ChatView },
    { path: '/knowledge', component: KnowledgeView },
    { path: '/nacos', component: NacosView },
    { path: '/a2a', component: A2aView },
  ],
})

createApp(App).use(router).mount('#app')
