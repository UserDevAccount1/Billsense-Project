import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ConnectionHealth from '../views/ConnectionHealth.vue'
import GitNexus from '../views/GitNexus.vue'
import ApkManagement from '../views/ApkManagement.vue'
import AppTesting from '../views/AppTesting.vue'
import Billy from '../views/Billy.vue'
import Login from '../views/Login.vue'
import { isAuthenticated } from '../services/auth.js'

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true } },
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

// Global auth gate: every non-public route requires a session.
router.beforeEach((to) => {
  if (to.meta.public) {
    // Already signed in? skip the login page.
    if (to.name === 'Login' && isAuthenticated()) return { path: '/' }
    return true
  }
  if (!isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
