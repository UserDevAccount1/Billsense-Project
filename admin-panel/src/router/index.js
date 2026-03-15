import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ConnectionHealth from '../views/ConnectionHealth.vue'
import GitNexus from '../views/GitNexus.vue'

const routes = [
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/connection-health', name: 'ConnectionHealth', component: ConnectionHealth },
  { path: '/gitnexus', name: 'GitNexus', component: GitNexus }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
