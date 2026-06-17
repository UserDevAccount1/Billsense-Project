import { database, ref } from './firebase.js'
import { get, set, remove, push, onValue } from 'firebase/database'

const THESIS_PATH = 'thesis_versions'

/**
 * Fetch all thesis versions from Firebase RTDB
 */
export async function fetchThesisVersions() {
  try {
    const snapshot = await get(ref(database, THESIS_PATH))
    if (!snapshot.exists()) return []
    const data = snapshot.val()
    return Object.entries(data)
      .map(([id, val]) => ({ id, ...val }))
      .sort((a, b) => b.versionNumber - a.versionNumber)
  } catch (e) {
    console.error('Failed to fetch thesis versions:', e)
    return []
  }
}

/**
 * Save a new thesis version
 * @param {Object} version - { versionNumber, author, changesSummary, sections }
 */
export async function saveThesisVersion(version) {
  try {
    const newRef = push(ref(database, THESIS_PATH))
    const record = {
      id: newRef.key,
      versionNumber: version.versionNumber,
      date: new Date().toISOString(),
      author: version.author || 'Admin',
      changesSummary: version.changesSummary || '',
      sections: version.sections || {}
    }
    await set(newRef, record)
    return record
  } catch (e) {
    console.error('Failed to save thesis version:', e)
    throw e
  }
}

/**
 * Fetch the latest (current) thesis version
 */
export async function fetchCurrentThesis() {
  const versions = await fetchThesisVersions()
  return versions.length > 0 ? versions[0] : null
}

/**
 * Delete a thesis version by id
 * @param {string} id - Firebase key
 */
export async function deleteThesisVersion(id) {
  try {
    await remove(ref(database, `${THESIS_PATH}/${id}`))
  } catch (e) {
    console.error('Failed to delete thesis version:', e)
    throw e
  }
}

/**
 * Listen for real-time updates to thesis versions
 * @param {Function} callback - receives sorted array of versions
 * @returns {Function} unsubscribe function
 */
export function onThesisVersionsChange(callback) {
  const unsub = onValue(ref(database, THESIS_PATH), (snapshot) => {
    if (!snapshot.exists()) {
      callback([])
      return
    }
    const data = snapshot.val()
    const versions = Object.entries(data)
      .map(([id, val]) => ({ id, ...val }))
      .sort((a, b) => b.versionNumber - a.versionNumber)
    callback(versions)
  })
  return unsub
}

/**
 * Get the default empty thesis structure
 */
export function getDefaultSections() {
  return {
    chapter1_background: { title: 'Background of the Study', content: '' },
    chapter1_framework: { title: 'Theoretical/Conceptual Framework', content: '' },
    chapter1_paradigm: { title: 'Paradigm of the Study', content: '' },
    chapter1_statement: { title: 'Statement of the Problem and Hypotheses', content: '' },
    chapter2_design: { title: 'Research Design and Methodology', content: '' },
    chapter2_scope: { title: 'Scope of the Study', content: '' },
    chapter2_population: { title: 'Population and Locale of the Study', content: '' },
    chapter2_tools: { title: 'Data Gathering Tools', content: '' },
    chapter2_procedure: { title: 'Data Gathering Procedure', content: '' },
    chapter2_treatment: { title: 'Treatment of Data', content: '' },
    chapter2_ethics: { title: 'Ethical Considerations', content: '' },
    references: { title: 'References', content: '' }
  }
}
