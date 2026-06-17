// Test the exact search highlight logic from Thesis.vue
const text = 'Counterfeit currency has long been a formidable challenge. The ability of counterfeiters has been exacerbated by advancements.'
const searchQuery = 'has been exacerbated'

function applySearchHighlight(html, panel) {
  const q = searchQuery.trim()
  if (!q) return html
  const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return html.replace(/(<[^>]*>)|([^<]+)/g, (match, tag, txt) => {
    if (tag) return tag
    if (!txt) return match
    return txt.replace(new RegExp(`(${escaped})`, 'gi'), '<span class="search-match">$1</span>')
  })
}

function renderPlainWithSearch(text, panel) {
  let html = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  html = html.split(/\n\n+/).map(p => `<p style="text-indent:48px">${p}</p>`).join('')
  return applySearchHighlight(html, panel)
}

const result = renderPlainWithSearch(text, 'right')
console.log('Contains search-match:', result.includes('search-match'))
console.log('Result:', result.substring(0, 500))
