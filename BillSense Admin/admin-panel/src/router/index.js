import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ConnectionHealth from '../views/ConnectionHealth.vue'
import GitNexus from '../views/GitNexus.vue'
import ApkManagement from '../views/ApkManagement.vue'
import AppTesting from '../views/AppTesting.vue'
import Billy from '../views/Billy.vue'

const routes = [
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/billy', name: 'Billy', component: Billy },
  { path: '/connection-health', name: 'ConnectionHealth', component: ConnectionHealth },
  { path: '/gitnexus', name: 'GitNexus', component: GitNexus },
  { path: '/apk-management', name: 'ApkManagement', component: ApkManagement },
  { path: '/app-testing', name: 'AppTesting', component: AppTesting }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
