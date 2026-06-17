import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import ConnectionHealth from '../views/ConnectionHealth.vue'
import GitNexus from '../views/GitNexus.vue'
import ApkManagement from '../views/ApkManagement.vue'
import AppTesting from '../views/AppTesting.vue'
import SessionReports from '../views/SessionReports.vue'
import Thesis from '../views/Thesis.vue'
import ScanResults from '../views/ScanResults.vue'
import Users from '../views/Users.vue'
import MLModels from '../views/MLModels.vue'
import StandardScans from '../views/StandardScans.vue'
import MultiScans from '../views/MultiScans.vue'
import VideoScans from '../views/VideoScans.vue'
import Detections from '../views/Detections.vue'
import VotingPosts from '../views/VotingPosts.vue'
import CasesEvidence from '../views/CasesEvidence.vue'
import Announcements from '../views/Announcements.vue'
import EducationalContent from '../views/EducationalContent.vue'
import Notifications from '../views/Notifications.vue'
import SupportTickets from '../views/SupportTickets.vue'
import Agents from '../views/Agents.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login, meta: { public: true } },
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/connection-health', name: 'ConnectionHealth', component: ConnectionHealth },
  { path: '/gitnexus', name: 'GitNexus', component: GitNexus },
  { path: '/apk-management', name: 'ApkManagement', component: ApkManagement },
  { path: '/app-testing', name: 'AppTesting', component: AppTesting },
  { path: '/session-reports', name: 'SessionReports', component: SessionReports },
  { path: '/thesis', name: 'Thesis', component: Thesis },
  { path: '/scan-results', name: 'ScanResults', component: ScanResults },
  { path: '/users', name: 'Users', component: Users },
  { path: '/ml-models', name: 'MLModels', component: MLModels },
  // New scan pages
  { path: '/standard-scans', name: 'StandardScans', component: StandardScans },
  { path: '/multi-scans', name: 'MultiScans', component: MultiScans },
  { path: '/video-scans', name: 'VideoScans', component: VideoScans },
  { path: '/detections', name: 'Detections', component: Detections },
  // Community pages
  { path: '/voting-posts', name: 'VotingPosts', component: VotingPosts },
  { path: '/cases-evidence', name: 'CasesEvidence', component: CasesEvidence },
  // Content pages
  { path: '/announcements', name: 'Announcements', component: Announcements },
  { path: '/educational-content', name: 'EducationalContent', component: EducationalContent },
  { path: '/notifications', name: 'Notifications', component: Notifications },
  // Support pages
  { path: '/support-tickets', name: 'SupportTickets', component: SupportTickets },
  // AI & ML pages
  { path: '/agents', name: 'Agents', component: Agents }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Auth guard — protect all routes except login
router.beforeEach((to, from, next) => {
  const isAuthenticated =
    sessionStorage.getItem('billsense_admin_auth') === 'true' ||
    localStorage.getItem('billsense_admin_auth') === 'true'

  if (to.meta.public) {
    // Public route (login) — redirect to dashboard if already authenticated
    if (isAuthenticated) {
      next('/')
    } else {
      next()
    }
  } else {
    // Protected route — redirect to login if not authenticated
    if (isAuthenticated) {
      next()
    } else {
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }
})

export default router
