import { database, ref } from './firebase.js'
import { get, set, remove, push, onValue } from 'firebase/database'

const REPORTS_PATH = 'session_reports'

export async function fetchReports() {
  const snapshot = await get(ref(database, REPORTS_PATH))
  if (!snapshot.exists()) return []
  const data = snapshot.val()
  return Object.keys(data).map(key => ({ id: key, ...data[key] }))
    .sort((a, b) => new Date(b.date) - new Date(a.date))
}

export function listenReports(callback) {
  const reportsRef = ref(database, REPORTS_PATH)
  return onValue(reportsRef, (snapshot) => {
    if (!snapshot.exists()) { callback([]); return }
    const data = snapshot.val()
    const list = Object.keys(data).map(key => ({ id: key, ...data[key] }))
      .sort((a, b) => new Date(b.date) - new Date(a.date))
    callback(list)
  })
}

export async function saveReport(report) {
  const reportsRef = ref(database, REPORTS_PATH)
  const newRef = push(reportsRef)
  const reportData = {
    ...report,
    id: newRef.key,
    date: new Date().toISOString(),
    generatedBy: 'claude-agent'
  }
  await set(newRef, reportData)
  return reportData
}

export async function updateReport(reportId, updates) {
  await set(ref(database, `${REPORTS_PATH}/${reportId}`), updates)
}

export async function deleteReport(reportId) {
  await remove(ref(database, `${REPORTS_PATH}/${reportId}`))
}

export function generateSessionId() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `SESSION-${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
}
