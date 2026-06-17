/**
 * Vector Search Engine for Thesis Documents
 *
 * Uses TF-IDF vectorization + cosine similarity for semantic search.
 * Works entirely in browser — no external APIs needed.
 *
 * Features:
 * - Chunking: splits text into overlapping chunks for better search
 * - TF-IDF: term frequency-inverse document frequency vectorization
 * - Cosine similarity: ranks results by relevance
 * - Semantic grouping: finds related concepts, not just exact matches
 */

export class ThesisSearchEngine {
  constructor() {
    this.chunks = []       // { id, section, text, words, vector }
    this.vocabulary = {}   // word -> index
    this.idf = {}          // word -> idf score
    this.vocabSize = 0
    this.ready = false
  }

  /**
   * Index thesis sections — call once after loading content
   */
  buildIndex(sections, sectionMap) {
    this.chunks = []
    const allDocs = []

    for (const [key, content] of Object.entries(sections)) {
      if (!content || !content.trim()) continue
      const title = sectionMap[key]?.title || key

      // Chunk the text with overlap
      const textChunks = this.chunkText(content, 150, 30) // 150 words per chunk, 30 word overlap

      textChunks.forEach((chunk, idx) => {
        const words = this.tokenize(chunk)
        this.chunks.push({
          id: `${key}-${idx}`,
          section: key,
          sectionTitle: title,
          text: chunk,
          words: words,
          chunkIndex: idx,
          startChar: content.indexOf(chunk.substring(0, 50))
        })
        allDocs.push(words)
      })
    }

    // Build vocabulary
    this.vocabulary = {}
    this.vocabSize = 0
    const docFreq = {} // how many docs contain each word

    for (const words of allDocs) {
      const unique = new Set(words)
      for (const w of unique) {
        if (!this.vocabulary.hasOwnProperty(w)) {
          this.vocabulary[w] = this.vocabSize++
        }
        docFreq[w] = (docFreq[w] || 0) + 1
      }
    }

    // Compute IDF
    const N = allDocs.length
    this.idf = {}
    for (const [word, df] of Object.entries(docFreq)) {
      this.idf[word] = Math.log((N + 1) / (df + 1)) + 1 // smoothed IDF
    }

    // Compute TF-IDF vectors for all chunks
    for (const chunk of this.chunks) {
      chunk.vector = this.computeTfIdf(chunk.words)
    }

    this.ready = true
    console.log(`[VectorSearch] Indexed ${this.chunks.length} chunks from ${Object.keys(sections).length} sections, vocabulary: ${this.vocabSize} words`)
  }

  /**
   * Search — returns ranked results with relevance scores
   */
  search(query, topK = 20) {
    if (!this.ready || !query.trim()) return []

    const queryWords = this.tokenize(query)
    const queryVector = this.computeTfIdf(queryWords)

    // Compute cosine similarity with all chunks
    const results = this.chunks
      .map(chunk => ({
        ...chunk,
        score: this.cosineSimilarity(queryVector, chunk.vector),
        exactMatch: chunk.text.toLowerCase().includes(query.toLowerCase())
      }))
      .filter(r => r.score > 0.01 || r.exactMatch)
      .sort((a, b) => {
        // Exact matches first, then by score
        if (a.exactMatch && !b.exactMatch) return -1
        if (!a.exactMatch && b.exactMatch) return 1
        return b.score - a.score
      })
      .slice(0, topK)

    // Highlight matched terms in results
    return results.map(r => ({
      id: r.id,
      section: r.section,
      sectionTitle: r.sectionTitle,
      text: r.text,
      score: Math.round(r.score * 100),
      exactMatch: r.exactMatch,
      chunkIndex: r.chunkIndex,
      highlight: this.highlightTerms(r.text, queryWords, query)
    }))
  }

  /**
   * Find related sections — semantic similarity between sections
   */
  findRelatedSections(sectionKey) {
    const sectionChunks = this.chunks.filter(c => c.section === sectionKey)
    if (!sectionChunks.length) return []

    // Average vector for the section
    const avgVector = this.averageVectors(sectionChunks.map(c => c.vector))

    // Find chunks from OTHER sections that are similar
    return this.chunks
      .filter(c => c.section !== sectionKey)
      .map(c => ({
        section: c.section,
        sectionTitle: c.sectionTitle,
        score: this.cosineSimilarity(avgVector, c.vector),
        snippet: c.text.substring(0, 200) + '...'
      }))
      .filter(r => r.score > 0.1)
      .sort((a, b) => b.score - a.score)
      .slice(0, 10)
  }

  // ── Internal methods ──

  chunkText(text, chunkSize = 150, overlap = 30) {
    const words = text.split(/\s+/)
    const chunks = []
    for (let i = 0; i < words.length; i += chunkSize - overlap) {
      const chunk = words.slice(i, i + chunkSize).join(' ')
      if (chunk.trim()) chunks.push(chunk)
      if (i + chunkSize >= words.length) break
    }
    return chunks
  }

  tokenize(text) {
    return text.toLowerCase()
      .replace(/[^\w\s'-]/g, ' ')
      .split(/\s+/)
      .filter(w => w.length > 2)
      .filter(w => !STOP_WORDS.has(w))
  }

  computeTfIdf(words) {
    const tf = {}
    for (const w of words) {
      tf[w] = (tf[w] || 0) + 1
    }
    const vector = new Float32Array(this.vocabSize)
    const maxTf = Math.max(...Object.values(tf), 1)
    for (const [word, count] of Object.entries(tf)) {
      if (this.vocabulary.hasOwnProperty(word)) {
        const idx = this.vocabulary[word]
        const normalizedTf = count / maxTf
        vector[idx] = normalizedTf * (this.idf[word] || 1)
      }
    }
    return vector
  }

  cosineSimilarity(a, b) {
    let dot = 0, normA = 0, normB = 0
    for (let i = 0; i < a.length; i++) {
      dot += a[i] * b[i]
      normA += a[i] * a[i]
      normB += b[i] * b[i]
    }
    const denom = Math.sqrt(normA) * Math.sqrt(normB)
    return denom > 0 ? dot / denom : 0
  }

  averageVectors(vectors) {
    if (!vectors.length) return new Float32Array(this.vocabSize)
    const avg = new Float32Array(this.vocabSize)
    for (const v of vectors) {
      for (let i = 0; i < v.length; i++) avg[i] += v[i]
    }
    for (let i = 0; i < avg.length; i++) avg[i] /= vectors.length
    return avg
  }

  highlightTerms(text, queryWords, fullQuery) {
    let html = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

    // Highlight exact phrase first
    if (fullQuery.length > 2) {
      const escaped = fullQuery.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      html = html.replace(new RegExp(`(${escaped})`, 'gi'),
        '<mark class="search-hl-exact">$1</mark>')
    }

    // Then highlight individual terms
    for (const word of queryWords) {
      if (word.length < 3) continue
      const escaped = word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      html = html.replace(
        new RegExp(`(?<!<[^>]*)(\\b${escaped}\\w{0,3}\\b)(?![^<]*>)`, 'gi'),
        '<mark class="search-hl-term">$1</mark>'
      )
    }

    return html
  }
}

// Common English stop words
const STOP_WORDS = new Set([
  'the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'had', 'her',
  'was', 'one', 'our', 'out', 'has', 'have', 'been', 'some', 'them', 'than',
  'its', 'over', 'such', 'that', 'this', 'with', 'will', 'each', 'from',
  'they', 'were', 'which', 'their', 'said', 'what', 'about', 'would', 'make',
  'like', 'just', 'into', 'also', 'could', 'more', 'these', 'when', 'other',
  'there', 'then', 'most', 'only', 'very', 'being', 'those', 'after', 'does',
  'where', 'while', 'should', 'through', 'between', 'before', 'during',
  'without', 'however', 'because', 'against', 'both', 'same', 'able', 'may',
  'use', 'using', 'used', 'well', 'often', 'many', 'much'
])

export default ThesisSearchEngine
