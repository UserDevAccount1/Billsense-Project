/**
 * Thesis Validator AI Agent — Pipeline Service
 *
 * Every panel request goes through a structured pipeline before any changes
 * are made to the thesis document. This prevents unnecessary or redundant
 * modifications and ensures academic rigor.
 *
 * PIPELINE STAGES:
 * ┌─────────┐    ┌──────────┐    ┌───────────┐    ┌──────────┐    ┌─────────┐    ┌─────────┐
 * │ INTAKE  │ →  │ CLASSIFY │ →  │  REVIEW   │ →  │ GENERATE │ →  │ PREVIEW │ →  │  APPLY  │
 * │         │    │          │    │           │    │          │    │         │    │         │
 * │ Receive │    │ Map to   │    │ Relevance │    │ Draft    │    │ Side-by │    │ Commit  │
 * │ request │    │ section, │    │ check,    │    │ solution │    │ -side   │    │ changes │
 * │ & parse │    │ type,    │    │ counter-  │    │ content  │    │ compare │    │ to doc  │
 * │         │    │ priority │    │ defense   │    │          │    │         │    │         │
 * └─────────┘    └──────────┘    └───────────┘    └──────────┘    └─────────┘    └─────────┘
 *
 * REVIEW DECISIONS:
 * - APPROVE    → Request is valid, proceed to Generate
 * - DEFER      → Valid but low priority, queued for later
 * - COUNTER    → Request is redundant/unnecessary, generate counter-defense
 * - REJECT     → Request doesn't contribute to thesis quality
 */

import { database, ref } from './firebase.js'
import { get, set, push, update } from 'firebase/database'

// ============================================================
// PIPELINE STAGE DEFINITIONS
// ============================================================

export const PIPELINE_STAGES = [
  {
    id: 'intake',
    label: 'Intake',
    icon: 'inbox',
    description: 'Receive and parse panel request'
  },
  {
    id: 'classify',
    label: 'Classify',
    icon: 'category',
    description: 'Map to section, determine type and priority'
  },
  {
    id: 'review',
    label: 'Review',
    icon: 'policy',
    description: 'Relevance check, counter-defense analysis'
  },
  {
    id: 'generate',
    label: 'Generate',
    icon: 'auto_awesome',
    description: 'Draft solution content'
  },
  {
    id: 'preview',
    label: 'Preview',
    icon: 'compare',
    description: 'Side-by-side comparison of changes'
  },
  {
    id: 'apply',
    label: 'Apply',
    icon: 'check_circle',
    description: 'Commit approved changes to document'
  }
]

// ============================================================
// REQUEST TYPES & CLASSIFICATIONS
// ============================================================

const REQUEST_TYPES = {
  CONTENT_ADD: { label: 'Add Content', color: '#3B82F6', icon: 'add_circle' },
  CONTENT_MODIFY: { label: 'Modify Content', color: '#F59E0B', icon: 'edit' },
  CONTENT_REMOVE: { label: 'Remove Content', color: '#EF4444', icon: 'remove_circle' },
  STRUCTURE: { label: 'Structural Change', color: '#8B5CF6', icon: 'account_tree' },
  CLARIFICATION: { label: 'Clarification Needed', color: '#06B6D4', icon: 'help' },
  METHODOLOGY: { label: 'Methodology', color: '#10B981', icon: 'science' },
  REFERENCE: { label: 'Citation/Reference', color: '#EC4899', icon: 'menu_book' },
  SECURITY: { label: 'Security Concern', color: '#EF4444', icon: 'security' },
  SCOPE: { label: 'Scope Definition', color: '#F97316', icon: 'zoom_in' }
}

const REVIEW_DECISIONS = {
  APPROVE: { label: 'Approved', color: '#22C55E', icon: 'check_circle', description: 'Valid request, proceed to generate solution' },
  DEFER: { label: 'Deferred', color: '#F59E0B', icon: 'schedule', description: 'Valid but low priority, queued for later' },
  COUNTER: { label: 'Counter-Defense', color: '#8B5CF6', icon: 'shield', description: 'Redundant or unnecessary — counter-argument generated' },
  REJECT: { label: 'Rejected', color: '#EF4444', icon: 'block', description: 'Does not contribute to thesis improvement' }
}

// ============================================================
// STAGE 1: INTAKE — Parse and normalize the request
// ============================================================

export function stageIntake(rawComment, panelist) {
  return {
    id: `req-${Date.now()}-${Math.random().toString(36).substr(2, 6)}`,
    rawText: rawComment.text || rawComment,
    panelist: panelist || 'Unknown',
    receivedAt: new Date().toISOString(),
    currentStage: 'intake',
    stageHistory: [{
      stage: 'intake',
      timestamp: new Date().toISOString(),
      status: 'completed',
      notes: 'Request received and parsed'
    }],
    classification: null,
    reviewDecision: null,
    solution: null,
    counterDefense: null,
    applied: false
  }
}

// ============================================================
// STAGE 2: CLASSIFY — Determine type, section, priority
// ============================================================

export function stageClassify(request, thesisSections) {
  const text = request.rawText.toLowerCase()

  // Determine request type
  let type = 'CLARIFICATION'
  if (/add|include|put|where is|missing/i.test(text)) type = 'CONTENT_ADD'
  else if (/change|modify|revise|strengthen|improve|look into|consider/i.test(text)) type = 'CONTENT_MODIFY'
  else if (/remove|delete|take out/i.test(text)) type = 'CONTENT_REMOVE'
  else if (/structure|organize|phase|framework|model/i.test(text)) type = 'STRUCTURE'
  else if (/method|design|approach|procedure/i.test(text)) type = 'METHODOLOGY'
  else if (/reference|cite|citation|source/i.test(text)) type = 'REFERENCE'
  else if (/security|reverse engineer|protect|ensure/i.test(text)) type = 'SECURITY'
  else if (/scope|limit|boundary|extent/i.test(text)) type = 'SCOPE'
  else if (/what|how|who|why|justify|explain/i.test(text)) type = 'CLARIFICATION'

  // Determine target section
  let targetSection = 'chapter1_background' // default
  if (/background|advantage|challenge|existing/i.test(text)) targetSection = 'chapter1_background'
  else if (/framework|business model|canvas|theory/i.test(text)) targetSection = 'chapter1_framework'
  else if (/paradigm/i.test(text)) targetSection = 'chapter1_paradigm'
  else if (/statement|problem|hypothes|evidence|court|admissib/i.test(text)) targetSection = 'chapter1_statement'
  else if (/design|method|prototype|phase|developer|participation|storage|maintain/i.test(text)) targetSection = 'chapter2_design'
  else if (/scope|limit|timeframe/i.test(text)) targetSection = 'chapter2_scope'
  else if (/population|locale|sample|level|master/i.test(text)) targetSection = 'chapter2_population'
  else if (/tool|instrument|gather.*tool/i.test(text)) targetSection = 'chapter2_tools'
  else if (/procedure|gather.*procedure/i.test(text)) targetSection = 'chapter2_procedure'
  else if (/treatment|analysis|data.*treat/i.test(text)) targetSection = 'chapter2_treatment'
  else if (/ethic|security|reverse|reveal|feature/i.test(text)) targetSection = 'chapter2_ethics'
  else if (/reference|cite/i.test(text)) targetSection = 'references'

  // Determine priority
  let priority = 'medium'
  if (/must|critical|required|where is|missing/i.test(text)) priority = 'high'
  else if (/consider|look into|might|could/i.test(text)) priority = 'low'

  // Check if section has content
  const sectionContent = thesisSections[targetSection]?.content || ''
  const hasContent = sectionContent.trim().length > 50

  request.classification = {
    type,
    typeInfo: REQUEST_TYPES[type],
    targetSection,
    priority,
    sectionHasContent: hasContent,
    sectionWordCount: sectionContent.split(/\s+/).filter(w => w).length,
    classifiedAt: new Date().toISOString()
  }

  request.currentStage = 'classify'
  request.stageHistory.push({
    stage: 'classify',
    timestamp: new Date().toISOString(),
    status: 'completed',
    notes: `Classified as ${REQUEST_TYPES[type].label} | Section: ${targetSection} | Priority: ${priority}`
  })

  return request
}

// ============================================================
// STAGE 3: REVIEW — Relevance check, counter-defense
// ============================================================

export function stageReview(request, thesisSections, allRequests = []) {
  const text = request.rawText.toLowerCase()
  const type = request.classification?.type || 'CLARIFICATION'
  const section = request.classification?.targetSection || 'chapter1_background'
  const sectionContent = (thesisSections[section]?.content || '').toLowerCase()

  let decision = 'APPROVE'
  let reviewNotes = ''
  let counterDefense = null
  let relevanceScore = 100
  let impactAssessment = ''

  // ── Check 1: Is the request relevant to BillSense thesis? ──
  const thesisKeywords = ['currency', 'counterfeit', 'detection', 'banknote', 'bill', 'security', 'scan',
    'authenticate', 'billsense', 'mobile', 'app', 'yolo', 'machine learning', 'uv', 'watermark',
    'philippine', 'peso', 'camera', 'image processing', 'criminology', 'forensic']

  const hasRelevantKeyword = thesisKeywords.some(k => text.includes(k)) ||
    type === 'STRUCTURE' || type === 'METHODOLOGY' || type === 'SCOPE'

  if (!hasRelevantKeyword) {
    relevanceScore -= 20
    reviewNotes += 'Request may not directly relate to thesis core topics. '
  }

  // ── Check 2: Is this already addressed in the content? ──
  const keyPhrases = text.split(/[?,.]/).map(p => p.trim()).filter(p => p.length > 10)
  const alreadyAddressed = keyPhrases.some(phrase => {
    const words = phrase.split(/\s+/).filter(w => w.length > 4)
    return words.filter(w => sectionContent.includes(w)).length >= Math.ceil(words.length * 0.6)
  })

  if (alreadyAddressed) {
    decision = 'COUNTER'
    relevanceScore -= 30
    counterDefense = generateCounterDefense(request, thesisSections)
    reviewNotes += 'Content appears to already address this concern. '
  }

  // ── Check 3: Does this contribute to the mobile app or documentation? ──
  const nonContributory = [
    /unrelated to mobile/i, /not about currency/i, /personal opinion/i
  ]
  if (nonContributory.some(p => p.test(text))) {
    decision = 'REJECT'
    relevanceScore -= 50
    reviewNotes += 'Does not contribute to thesis improvement. '
  }

  // ── Check 4: Is this a duplicate of another request? ──
  const isDuplicate = allRequests.some(other => {
    if (other.id === request.id) return false
    const otherText = (other.rawText || '').toLowerCase()
    const words = text.split(/\s+/).filter(w => w.length > 4)
    const matchCount = words.filter(w => otherText.includes(w)).length
    return matchCount >= Math.ceil(words.length * 0.7)
  })

  if (isDuplicate) {
    decision = 'DEFER'
    relevanceScore -= 15
    reviewNotes += 'Similar request already exists. '
  }

  // ── Check 5: Impact assessment ──
  if (type === 'SECURITY') {
    impactAssessment = 'HIGH — Security concerns must be addressed to protect the application and its users.'
    relevanceScore = Math.max(relevanceScore, 80)
    decision = 'APPROVE'
  } else if (type === 'CONTENT_ADD' && request.classification?.sectionWordCount < 100) {
    impactAssessment = 'HIGH — Section is underdeveloped and needs additional content.'
    decision = 'APPROVE'
  } else if (type === 'CLARIFICATION') {
    impactAssessment = 'MEDIUM — Requires clarification that strengthens the thesis argument.'
  } else if (type === 'STRUCTURE') {
    impactAssessment = 'MEDIUM — Structural changes affect document organization.'
  } else {
    impactAssessment = 'STANDARD — Content modification within existing framework.'
  }

  // Override: Always approve high-priority requests
  if (request.classification?.priority === 'high' && decision !== 'REJECT') {
    decision = 'APPROVE'
  }

  request.reviewDecision = {
    decision,
    decisionInfo: REVIEW_DECISIONS[decision],
    relevanceScore: Math.max(0, Math.min(100, relevanceScore)),
    reviewNotes: reviewNotes.trim() || 'Request passes all review criteria.',
    impactAssessment,
    counterDefense,
    reviewedAt: new Date().toISOString()
  }

  request.currentStage = 'review'
  request.stageHistory.push({
    stage: 'review',
    timestamp: new Date().toISOString(),
    status: 'completed',
    notes: `Decision: ${decision} | Relevance: ${relevanceScore}% | ${reviewNotes || 'Approved'}`
  })

  return request
}

// ============================================================
// COUNTER-DEFENSE GENERATOR
// ============================================================

function generateCounterDefense(request, thesisSections) {
  const text = request.rawText
  const section = request.classification?.targetSection
  const content = thesisSections[section]?.content || ''

  // Find relevant sentences already in the document
  const sentences = content.split(/[.!?]+/).filter(s => s.trim().length > 20)
  const requestWords = text.toLowerCase().split(/\s+/).filter(w => w.length > 4)
  const relevantSentences = sentences.filter(s => {
    const sLower = s.toLowerCase()
    return requestWords.filter(w => sLower.includes(w)).length >= 2
  }).slice(0, 3)

  return {
    title: 'Counter-Defense Analysis',
    summary: `This request appears to be already addressed in the "${thesisSections[section]?.title || section}" section of the thesis.`,
    existingContent: relevantSentences.map(s => s.trim()).filter(Boolean),
    recommendation: relevantSentences.length > 0
      ? 'The thesis already contains content that addresses this concern. Consider strengthening the existing discussion rather than adding redundant content.'
      : 'While the specific concern may not be directly addressed, the section context covers related topics. Consider adding a brief clarification rather than a full rewrite.',
    suggestedAction: relevantSentences.length > 0 ? 'STRENGTHEN_EXISTING' : 'ADD_BRIEF_NOTE'
  }
}

// ============================================================
// STAGE 4: GENERATE — Draft solution content
// ============================================================

export function stageGenerate(request, thesisSections) {
  if (request.reviewDecision?.decision === 'REJECT') {
    request.solution = { type: 'none', content: '', notes: 'Request was rejected during review.' }
    request.currentStage = 'generate'
    return request
  }

  if (request.reviewDecision?.decision === 'COUNTER') {
    request.solution = {
      type: 'counter-defense',
      content: request.reviewDecision.counterDefense?.recommendation || '',
      notes: 'Counter-defense generated instead of content modification.'
    }
    request.currentStage = 'generate'
    return request
  }

  const section = request.classification?.targetSection || 'chapter1_background'
  const sectionTitle = thesisSections[section]?.title || section
  const existingContent = thesisSections[section]?.content || ''
  const type = request.classification?.type || 'CLARIFICATION'
  const text = request.rawText

  let generatedContent = ''
  let insertPosition = 'append'

  // Generate contextual solution based on request type and content
  if (/advantage|over other/i.test(text)) {
    generatedContent = `\n\nThe proposed BillSense prototype offers distinct advantages over existing counterfeit detection applications. Unlike conventional methods that rely on single-feature analysis, BillSense integrates multiple detection modalities — visual inspection through YOLOv8 Oriented Bounding Box (OBB) detection, UV security feature analysis, and real-time video scanning — into a unified mobile platform. This multi-layered approach significantly reduces false positive rates and provides users with comprehensive authentication results. Furthermore, the application's offline-capable local knowledge base ensures accessibility even in areas with limited internet connectivity, addressing a critical gap in existing solutions that require constant server communication.`
  } else if (/business model canvas/i.test(text)) {
    generatedContent = `\n\nThe Business Model Canvas for BillSense identifies the following key components: (1) Value Proposition — affordable, accessible real-time counterfeit detection for end-users and financial institutions; (2) Customer Segments — individual consumers, small business owners, bank tellers, and law enforcement agencies; (3) Channels — Google Play Store distribution, institutional partnerships; (4) Revenue Streams — freemium model with premium features for institutional use; (5) Key Resources — YOLOv8 ML models, mobile application, cloud infrastructure; (6) Key Activities — model training, app development, user education; (7) Key Partners — BSP, financial institutions, academic researchers; (8) Cost Structure — cloud computing, development, maintenance.`
  } else if (/challenge.*existing|industry|bank/i.test(text)) {
    generatedContent = `\n\nExisting counterfeit detection applications face several challenges that limit their effectiveness in institutional settings. Many current solutions lack real-time processing capabilities, requiring users to capture and upload images for server-side analysis, introducing latency that is impractical for high-volume environments such as banks and retail establishments. Additionally, most applications focus solely on visual detection without incorporating UV security feature analysis, which is a critical authentication method used by financial institutions. BillSense addresses these gaps by providing on-device real-time detection with sub-second response times, making it suitable for deployment in banks, remittance centers, and retail point-of-sale systems.`
  } else if (/authority|criminologist|contribution/i.test(text)) {
    generatedContent = `\n\nAs a criminologist, the researcher brings a unique perspective to this study that bridges the gap between technology development and criminal justice application. The researcher's expertise in forensic investigation, evidence handling, and criminal behavior analysis informs the design of BillSense's detection algorithms, ensuring they align with established forensic methodologies. This interdisciplinary approach — combining criminological knowledge with technological innovation — contributes to a more robust and legally defensible detection system. The researcher's understanding of counterfeit currency as a criminal enterprise enables the application to not only detect counterfeits but also facilitate proper evidence documentation for potential legal proceedings.`
  } else if (/developer.*level|master/i.test(text)) {
    generatedContent = `\n\nThe technical development of the BillSense application was conducted by a developer with graduate-level expertise in software engineering and machine learning. The developer holds relevant qualifications in computer science with specialization in mobile application development and artificial intelligence, ensuring that the technical implementation meets the rigorous standards expected of a master's-level research output. The developer's involvement encompasses system architecture design, ML model training and optimization, mobile application development, and deployment infrastructure management.`
  } else if (/extent.*participation|developer.*participation/i.test(text)) {
    generatedContent = `\n\nThe developer's participation in this study encompasses full-cycle software development responsibilities. This includes: (a) system architecture design and technology stack selection; (b) YOLOv8 model training, validation, and optimization for Philippine banknote detection; (c) Android mobile application development with CameraX integration; (d) FastAPI backend deployment on Google Cloud Run; (e) WebSocket implementation for real-time scanning; and (f) ongoing maintenance and iterative improvements based on user testing feedback. The developer operates under the direct supervision of the researcher, ensuring alignment between technical implementation and research objectives.`
  } else if (/timeframe|consider.*time/i.test(text)) {
    generatedContent = `\n\nThe study follows a phased development timeline. Phase 1 (Months 1-4) encompasses requirements gathering, literature review, initial model training, and prototype development. Phase 2 (Months 5-8) focuses on iterative testing, user acceptance testing, data collection, and analysis. The current proposal concentrates on Phase 1 deliverables, including a working prototype capable of real-time banknote authentication through visual and UV security feature analysis.`
  } else if (/evidence.*court|admissib/i.test(text)) {
    generatedContent = `\n\nThe admissibility of digital evidence from BillSense in Philippine courts is governed by the Rules on Electronic Evidence (A.M. No. 01-7-01-SC). Digital scan results, detection confidence scores, and captured images constitute electronic documents as defined under the rules. To ensure admissibility, BillSense implements tamper-evident logging with timestamps, device identification, and geolocation metadata for each scan session. The application generates standardized reports that comply with evidence documentation requirements, facilitating their potential use as supporting evidence in counterfeiting cases. However, it is important to note that BillSense results serve as supplementary evidence and do not replace formal forensic examination by BSP-authorized personnel.`
  } else if (/storage management|maintain/i.test(text)) {
    generatedContent = `\n\nStorage management for the BillSense system operates on a multi-tier architecture. User data, scan reports, and case submissions are stored in Firebase Realtime Database with automated backup protocols. ML model weights and training data are maintained on Google Cloud Storage with version control. The application maintains minimal on-device storage, with cached scan results stored locally for offline access. System administration, including database maintenance, model updates, and infrastructure scaling, is managed by the development team with established Standard Operating Procedures (SOPs) for data retention, backup scheduling, and disaster recovery.`
  } else if (/reverse engineer|security feature/i.test(text)) {
    generatedContent = `\n\nTo mitigate the risk of reverse engineering, BillSense implements multiple security layers. First, the detection model weights are not embedded in the APK; instead, they reside on a secured cloud endpoint, preventing extraction through APK decompilation. Second, the application employs code obfuscation (ProGuard/R8) and certificate pinning to protect API communications. Third, the system only presents authentication results (genuine/counterfeit confidence scores) without exposing the specific security features used in the detection algorithm. This ensures that the application cannot be repurposed as a counterfeiting guide. Additionally, API rate limiting and user authentication prevent automated exploitation of the detection service.`
  } else if (/ensure.*not reveal|genuine.*feature/i.test(text)) {
    generatedContent = `\n\nThe BillSense application is designed with a "detection without disclosure" principle. While the system analyzes multiple security features internally (watermarks, UV elements, serial numbers, microprinting, OVD patches), the user-facing output presents only aggregate confidence scores and general authenticity verdicts. Specific security feature locations, expected patterns, and detection thresholds are never displayed to end users. The Step-by-Step Detection Guide provides educational content based on publicly available BSP publications, without revealing the proprietary detection methodology used by the AI models. This approach balances user education with security preservation.`
  } else if (/strengthen.*security/i.test(text)) {
    generatedContent = `\n\nSecurity strengthening measures implemented in BillSense include: (1) end-to-end encryption for all API communications using TLS 1.3; (2) network security configuration restricting cleartext traffic; (3) JWT-based authentication for API access; (4) server-side model inference to prevent model extraction; (5) rate limiting to prevent abuse; (6) automated threat detection for anomalous usage patterns; (7) regular security audits and penetration testing as part of the development lifecycle; and (8) compliance with OWASP Mobile Application Security guidelines for secure mobile development practices.`
  } else if (/phase|phase 1|phase 2/i.test(text)) {
    generatedContent = `\n\nThe study adopts a two-phase approach:\n\nPhase 1 (Current Proposal): Focuses on the development of a working prototype capable of (a) real-time visual detection using YOLOv8 OBB models, (b) UV security feature analysis, (c) multi-modal scanning (standard, multi-angle, video), and (d) basic reporting functionality. Phase 1 deliverables include the mobile application prototype, trained ML models, and preliminary user testing results.\n\nPhase 2 (Future Work): Will expand to include (a) comprehensive user acceptance testing with target populations, (b) integration with institutional workflows (banks, remittance centers), (c) advanced features including community-driven verification, and (d) formal evaluation of detection accuracy against BSP-certified methods. The current proposal concentrates on Phase 1 objectives to establish the foundational technology.`
  } else {
    // Generic solution
    generatedContent = `\n\nIn response to the panel's observation regarding "${text.substring(0, 80)}...", the following addition addresses the identified gap in the thesis documentation. [This section requires further development based on specific research findings and data analysis relevant to the concern raised.]`
  }

  request.solution = {
    type: type === 'CONTENT_ADD' ? 'append' : 'modify',
    content: generatedContent.trim(),
    targetSection: section,
    sectionTitle: sectionTitle,
    wordCount: generatedContent.split(/\s+/).filter(w => w).length,
    generatedAt: new Date().toISOString()
  }

  request.currentStage = 'generate'
  request.stageHistory.push({
    stage: 'generate',
    timestamp: new Date().toISOString(),
    status: 'completed',
    notes: `Generated ${request.solution.wordCount} words for "${sectionTitle}"`
  })

  return request
}

// ============================================================
// STAGE 5: PREVIEW — Generate diff for comparison
// ============================================================

export function stagePreview(request, thesisSections) {
  const section = request.solution?.targetSection || request.classification?.targetSection
  const existing = thesisSections[section]?.content || ''
  const newContent = request.solution?.type === 'append'
    ? existing + '\n\n' + request.solution.content
    : existing + '\n\n' + request.solution.content

  request.preview = {
    before: existing,
    after: newContent,
    diff: computeWordDiff(existing, newContent),
    section: section,
    previewedAt: new Date().toISOString()
  }

  request.currentStage = 'preview'
  request.stageHistory.push({
    stage: 'preview',
    timestamp: new Date().toISOString(),
    status: 'completed',
    notes: 'Preview generated for review'
  })

  return request
}

// ============================================================
// STAGE 6: APPLY — Commit changes (manual trigger only)
// ============================================================

export function stageApply(request, thesisSections) {
  const section = request.solution?.targetSection
  if (!section || !request.solution?.content) {
    request.stageHistory.push({
      stage: 'apply',
      timestamp: new Date().toISOString(),
      status: 'failed',
      notes: 'No solution content to apply'
    })
    return { request, updatedContent: null }
  }

  const existing = thesisSections[section]?.content || ''
  const updatedContent = existing + '\n\n' + request.solution.content

  request.applied = true
  request.currentStage = 'apply'
  request.stageHistory.push({
    stage: 'apply',
    timestamp: new Date().toISOString(),
    status: 'completed',
    notes: `Applied ${request.solution.wordCount} words to "${request.solution.sectionTitle}"`
  })

  return { request, updatedContent, section }
}

// ============================================================
// FULL PIPELINE — Run all stages sequentially
// ============================================================

export function runFullPipeline(rawComment, panelist, thesisSections, allRequests = []) {
  let request = stageIntake(rawComment, panelist)
  request = stageClassify(request, thesisSections)
  request = stageReview(request, thesisSections, allRequests)

  // Only generate solution if approved
  if (request.reviewDecision?.decision === 'APPROVE' || request.reviewDecision?.decision === 'DEFER') {
    request = stageGenerate(request, thesisSections)
    request = stagePreview(request, thesisSections)
  }

  return request
}

// ============================================================
// THESIS FORMAT SCORING
// ============================================================

export function scoreThesisFormat(thesisSections) {
  const scores = {
    format: { score: 0, max: 100, issues: [] },
    completeness: { score: 0, max: 100, issues: [] },
    panelCompliance: { score: 0, max: 100, issues: [] },
    overall: { score: 0, max: 100 }
  }

  const sectionKeys = Object.keys(thesisSections)

  // FORMAT SCORING (100 points)
  let formatScore = 100

  // Check title existence (10 pts)
  if (!thesisSections.title_page) {
    formatScore -= 10
    scores.format.issues.push({ severity: 'warning', text: 'No title page section found', section: 'title_page' })
  }

  // Check chapter structure (20 pts)
  const ch1Sections = sectionKeys.filter(k => k.startsWith('chapter1_'))
  const ch2Sections = sectionKeys.filter(k => k.startsWith('chapter2_'))
  if (ch1Sections.length < 3) {
    formatScore -= 10
    scores.format.issues.push({ severity: 'warning', text: 'Chapter 1 has fewer than 3 sections', section: 'chapter1' })
  }
  if (ch2Sections.length < 4) {
    formatScore -= 10
    scores.format.issues.push({ severity: 'warning', text: 'Chapter 2 has fewer than 4 sections', section: 'chapter2' })
  }

  // Check references exist (20 pts)
  const refs = thesisSections.references?.content || ''
  if (refs.length < 100) {
    formatScore -= 20
    scores.format.issues.push({ severity: 'critical', text: 'References section is too short or empty', section: 'references' })
  }

  // Check APA format in references (15 pts)
  const apaPattern = /\(\d{4}\)/g
  const apaMatches = refs.match(apaPattern) || []
  if (apaMatches.length < 5 && refs.length > 100) {
    formatScore -= 15
    scores.format.issues.push({ severity: 'warning', text: `Only ${apaMatches.length} APA-style citations detected`, section: 'references' })
  }

  scores.format.score = Math.max(0, formatScore)

  // COMPLETENESS SCORING (100 points)
  let completenessScore = 0
  const requiredSections = [
    'chapter1_background', 'chapter1_framework', 'chapter1_paradigm', 'chapter1_statement',
    'chapter2_design', 'chapter2_scope', 'chapter2_population', 'chapter2_tools',
    'chapter2_procedure', 'chapter2_treatment', 'chapter2_ethics', 'references'
  ]

  const perSection = 100 / requiredSections.length
  for (const key of requiredSections) {
    const content = thesisSections[key]?.content || ''
    const wordCount = content.split(/\s+/).filter(w => w).length
    if (wordCount >= 200) {
      completenessScore += perSection
    } else if (wordCount >= 50) {
      completenessScore += perSection * 0.5
      scores.completeness.issues.push({ severity: 'warning', text: `"${thesisSections[key]?.title || key}" is underdeveloped (${wordCount} words)`, section: key })
    } else {
      scores.completeness.issues.push({ severity: 'critical', text: `"${thesisSections[key]?.title || key}" is empty or minimal (${wordCount} words)`, section: key })
    }
  }

  scores.completeness.score = Math.round(completenessScore)

  // OVERALL
  scores.overall.score = Math.round(scores.format.score * 0.3 + scores.completeness.score * 0.4 + scores.panelCompliance.score * 0.3)

  return scores
}

// ============================================================
// WORD DIFF ALGORITHM (LCS-based)
// ============================================================

function computeWordDiff(oldText, newText) {
  const oldWords = (oldText || '').split(/\s+/).filter(w => w)
  const newWords = (newText || '').split(/\s+/).filter(w => w)

  // LCS for word-level diff
  const m = oldWords.length, n = newWords.length
  if (m === 0) return newWords.map(w => ({ type: 'added', text: w }))
  if (n === 0) return oldWords.map(w => ({ type: 'removed', text: w }))

  // For very large texts, use simplified approach
  if (m > 5000 || n > 5000) {
    return [
      { type: 'removed', text: oldWords.slice(0, 50).join(' ') + '...' },
      { type: 'added', text: newWords.slice(0, 50).join(' ') + '...' }
    ]
  }

  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = oldWords[i - 1] === newWords[j - 1]
        ? dp[i - 1][j - 1] + 1
        : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }

  const result = []
  let i = m, j = n
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && oldWords[i - 1] === newWords[j - 1]) {
      result.unshift({ type: 'same', text: oldWords[i - 1] })
      i--; j--
    } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
      result.unshift({ type: 'added', text: newWords[j - 1] })
      j--
    } else {
      result.unshift({ type: 'removed', text: oldWords[i - 1] })
      i--
    }
  }

  return result
}

// ============================================================
// EXPORTS
// ============================================================

export { REQUEST_TYPES, REVIEW_DECISIONS, computeWordDiff }
