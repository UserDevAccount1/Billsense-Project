import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import ConnectionHealth from '../views/ConnectionHealth.vue'
import GitNexus from '../views/GitNexus.vue'
import ApkManagement from '../views/ApkManagement.vue'
import AppTesting from '../views/AppTesting.vue'
import Billy from '../views/Billy.vue'
import Thesis from '../views/Thesis.vue'
import SupportTickets from '../views/SupportTickets.vue'
import Notifications from '../views/Notifications.vue'
import Content from '../views/Content.vue'
import Login from '../views/Login.vue'
import Users from '../views/Users.vue'
import ScanReports from '../views/ScanReports.vue'
import Cases from '../views/Cases.vue'
import VotingPosts from '../views/VotingPosts.vue'
import MLModels from '../views/MLModels.vue'
import Settings from '../views/Settings.vue'
import { isAuthenticated } from '../services/auth.js'

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true } },
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/billy', name: 'Billy', component: Billy },
  { path: '/scan-reports', name: 'ScanReports', component: ScanReports },
  { path: '/users', name: 'Users', component: Users },
  { path: '/ml-models', name: 'MLModels', component: MLModels },
  { path: '/apk-management', name: 'ApkManagement', component: ApkManagement },
  { path: '/app-testing', name: 'AppTesting', component: AppTesting },
  { path: '/connection-health', name: 'ConnectionHealth', component: ConnectionHealth },
  { path: '/gitnexus', name: 'GitNexus', component: GitNexus },
  { path: '/cases', name: 'Cases', component: Cases },
  { path: '/voting-posts', name: 'VotingPosts', component: VotingPosts },
  { path: '/thesis', name: 'Thesis', component: Thesis },
  { path: '/support', name: 'SupportTickets', component: SupportTickets },
  { path: '/notifications', name: 'Notifications', component: Notifications },
  { path: '/content', name: 'Content', component: Content },
  { path: '/settings', name: 'Settings', component: Settings }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Global auth gate: every non-public route requires a session.
router.beforeEach((to) => {
  if (to.meta.public) {
    if (to.name === 'Login' && isAuthenticated()) return { path: '/' }
    return true
  }
  if (!isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
