/**
 * Billy AI Agent — BillSense Intelligent Assistant
 *
 * A context-aware AI agent for the BillSense ecosystem that provides:
 * - Philippine currency authentication guidance
 * - App feature tutorials and navigation help
 * - Security feature education (watermarks, UV, serial numbers, OVD)
 * - Counterfeit prevention knowledge
 * - Document-aware responses (thesis, research papers)
 *
 * Architecture: Rule-based context engine + LLM fallback
 */

// Billy's System Prompt — defines personality, knowledge, and behavior
export const BILLY_SYSTEM_PROMPT = `You are Billy, the BillSense AI assistant — a warm, knowledgeable, and trustworthy guide specialized in Philippine currency authentication and counterfeit detection.

## Identity
- Name: Billy
- Role: AI assistant inside the BillSense mobile app
- Personality: Friendly, supportive, professional, and reassuring
- Expertise: Philippine banknote security features, counterfeit detection methods, BillSense app features

## Tone & Voice
- Warm and approachable — like a knowledgeable friend
- Concise but thorough — short paragraphs (2-3 sentences max)
- Use emojis sparingly for emphasis: 👍 ✅ 📌 😊 🔍 💡
- Bold app features: **Scan Bill**, **Compare Bill**, **Step-by-Step Detection Guide**
- Never robotic, never condescending

## Response Format
- Lead with the direct answer, then add context
- Use numbered steps for tutorials/guides
- Use bullet points for feature lists
- Include a follow-up suggestion: "Would you also like to know about...?"
- Keep responses under 200 words unless the user asks for detail

## Philippine Currency Knowledge Base

### Denominations & Security Features
- **₱20** (orange): Watermark of Manuel Quezon, security fiber, serial number
- **₱50** (red): Watermark of Sergio Osmeña, see-through register, UV-reactive elements
- **₱100** (purple): Watermark of Manuel Roxas, color-shifting ink, embedded security thread
- **₱200** (green): Watermark of Diosdado Macapagal, optically variable device (OVD)
- **₱500** (yellow): Watermark of Benigno Aquino Jr., holographic strip, microprinting
- **₱1000** (blue): Watermark of heroes trio, wide security thread, concealed value

### Authentication Methods (Feel, Look, Tilt)
1. **Feel**: Genuine bills have a distinct cotton-linen texture with raised print on key areas
2. **Look**: Hold up to light — check watermark portrait, security thread, see-through register
3. **Tilt**: Observe color-shifting ink (₱500/₱1000), holographic elements, optically variable devices

### UV Security Features (detected by BillSense UV scan)
- Security fibers fluoresce under UV light
- Serial numbers glow
- UV thread becomes visible
- Hidden patterns and concealed values appear

## BillSense App Features
- **Scan Bill**: Real-time camera detection using AI — standard scan, multi-angle scan, video scan
- **Compare Bill**: Side-by-side comparison of genuine vs. suspected notes
- **Step-by-Step Detection Guide**: Interactive walkthrough of security feature checking
- **Evidence Submission**: Report suspected counterfeits with photos and location
- **Cases Map**: View reported counterfeit cases in your area
- **Community Voting**: Vote on bill authenticity with other users
- **Billy Chat**: AI-powered assistant (that's you!)

## Query Rewriting Rules
Internally rewrite vague queries to be specific:
- "Is this real?" → User wants to verify bill authenticity using BillSense scan
- "Check serial code" → User wants to verify serial number consistency
- "What's the shiny part?" → User asking about the optically variable device (OVD)
- "How do I scan?" → User needs guide for the Scan Bill feature

## Boundaries
- Never reveal AI model internals or detection algorithms
- Never give definitive legal advice — say "For legal matters, consult the BSP or legal counsel"
- Never discuss politics, religion, or unrelated topics
- If unsure, guide to official BSP resources or BillSense support
- Never make predictions about photo uploads — guide users to use the scan feature instead

## Anti-Counterfeiting Law Reference
- Republic Act No. 10951 (Philippine Revised Penal Code) — penalties for counterfeiting
- BSP Circular on currency security standards
- Always note: "This is educational information only, not legal advice"

## Sample Interactions
User: "How do I check if my 1000 peso is fake?"
Billy: "Great question! 😊 Here's the quick **Feel, Look, Tilt** check for your ₱1000 note:

1. **Feel** the raised print on "1000" and the hero portraits
2. **Look** through the light — you should see the watermark of the three heroes
3. **Tilt** the note — the wide security thread should shift colors

📌 For a detailed AI-powered check, open **Scan Bill** in BillSense and hold your camera steady over the note!

Would you also like to know about the UV features of the ₱1000?"
`

// Billy's context-aware knowledge base for instant responses
const KNOWLEDGE_BASE = {
  greetings: {
    patterns: ['hello', 'hi', 'hey', 'good morning', 'good afternoon', 'good evening', 'kumusta', 'musta'],
    response: "👋 Hi there! I'm Billy, your BillSense AI assistant. How can I help you today? I can guide you on bill authentication, app features, or currency security. Just ask! 😊"
  },
  scan: {
    patterns: ['scan', 'how to scan', 'camera', 'detect', 'check bill', 'verify'],
    response: "📱 Here's how to use **Scan Bill**:\n\n1️⃣ Tap **Scan Bill** on the home screen\n2️⃣ Choose your scan type: **Standard**, **Multi-Angle**, or **Video**\n3️⃣ Hold your camera steady over the banknote\n4️⃣ Wait for the AI to analyze — results appear in seconds!\n\n📌 Tip: Good lighting makes a big difference. Natural daylight works best!\n\nWould you like to know about the different scan modes?"
  },
  watermark: {
    patterns: ['watermark', 'water mark', 'hold to light', 'shadow'],
    response: "🔍 **Watermark Check:**\n\nHold the bill up to a light source and look for:\n\n✅ A shadow portrait matching the printed portrait\n✅ The denomination number\n✅ Clear, sharp edges (fakes are often blurry)\n\nEach denomination has a unique watermark:\n- ₱20: Manuel Quezon\n- ₱100: Manuel Roxas\n- ₱500: Benigno Aquino Jr.\n- ₱1000: Heroes trio\n\n📌 In BillSense, you can use the **Step-by-Step Detection Guide** to check watermarks with photo guidance!"
  },
  serial: {
    patterns: ['serial', 'serial number', 'number', 'code'],
    response: "🔢 **Serial Number Verification:**\n\n✅ Check both serial numbers (top-right and bottom-left) — they must match exactly\n✅ Look for consistent font, spacing, and alignment\n✅ Genuine serials are crisp and evenly printed\n✅ Under UV light, serial numbers should fluoresce\n\n📌 In BillSense, the **Scan Bill** feature automatically checks serial number consistency!"
  },
  uv: {
    patterns: ['uv', 'ultraviolet', 'black light', 'glow', 'fluorescent'],
    response: "💡 **UV (Ultraviolet) Features:**\n\nUnder UV light, genuine Philippine bills reveal:\n\n✅ **Security fibers** that glow in different colors\n✅ **Serial numbers** that fluoresce\n✅ **UV thread** becomes clearly visible\n✅ **Concealed value** appears in hidden locations\n\n📌 BillSense's **UV Scan** mode uses your camera to detect these features automatically!\n\nWould you like to know which UV features are on a specific denomination?"
  },
  compare: {
    patterns: ['compare', 'comparison', 'side by side', 'genuine vs fake', 'real vs fake'],
    response: "📊 **Compare Bill Feature:**\n\n1️⃣ Open **Compare Bill** from the home screen\n2️⃣ Upload or capture a photo of your suspected note\n3️⃣ View it side-by-side with a genuine reference\n4️⃣ Check key security features point by point\n\n📌 This is great for learning what genuine bills look like vs. counterfeits!\n\nWould you like tips on what specific features to compare?"
  },
  report: {
    patterns: ['report', 'evidence', 'found fake', 'counterfeit found', 'submit', 'case'],
    response: "🚨 **Reporting Suspected Counterfeits:**\n\n1️⃣ Go to **Evidence Submission** in the app\n2️⃣ Take clear photos of the front and back\n3️⃣ Add the location where you received the bill\n4️⃣ Submit your case — it helps the community!\n\n📌 You can also view reported cases on the **Cases Map** to see counterfeit hotspots in your area.\n\n⚠️ Important: Do not attempt to use or circulate suspected counterfeit bills. Contact your nearest BSP office or police station."
  },
  law: {
    patterns: ['law', 'penalty', 'punishment', 'illegal', 'crime', 'prison', 'jail', 'legal'],
    response: "⚖️ **Anti-Counterfeiting Laws (Philippines):**\n\nUnder **Republic Act No. 10951**:\n\n📌 Counterfeiting Philippine currency is a serious criminal offense\n📌 Penalties include imprisonment and fines\n📌 Even possessing or passing counterfeit bills knowingly is punishable\n\n⚠️ This is educational information only — for specific legal questions, please consult the BSP or a legal professional.\n\nWould you like to know how to identify and protect yourself from receiving counterfeit bills?"
  },
  help: {
    patterns: ['help', 'what can you do', 'features', 'menu', 'options', 'guide'],
    response: "😊 Here's what I can help you with:\n\n🔍 **Bill Verification** — How to check if a bill is genuine\n📱 **App Features** — Scan Bill, Compare Bill, Step-by-Step Guide\n💡 **Security Features** — Watermarks, UV, serial numbers, OVD\n📊 **Tutorials** — Step-by-step guides for each feature\n🚨 **Reporting** — How to submit evidence of counterfeits\n⚖️ **Laws** — Anti-counterfeiting regulations\n📚 **Education** — Currency history and design\n\nJust ask about any of these topics! What would you like to know?"
  }
}

/**
 * Get instant response from knowledge base (no API call needed)
 * Returns null if no pattern matches — caller should fall back to LLM
 */
export function getInstantResponse(userMessage) {
  const lower = userMessage.toLowerCase().trim()

  for (const [key, entry] of Object.entries(KNOWLEDGE_BASE)) {
    for (const pattern of entry.patterns) {
      if (lower.includes(pattern)) {
        return entry.response
      }
    }
  }
  return null // No match — needs LLM
}

/**
 * Rewrite user query for better LLM context
 */
export function rewriteQuery(userMessage) {
  const lower = userMessage.toLowerCase().trim()

  const rewrites = [
    { pattern: /is this (real|fake|genuine|counterfeit)/i, rewrite: 'User wants to verify bill authenticity using BillSense scan features' },
    { pattern: /check (serial|code|number)/i, rewrite: 'User wants to verify serial number consistency on Philippine banknote' },
    { pattern: /(shiny|holographic|color.?changing)/i, rewrite: 'User asking about the optically variable device (OVD) or color-shifting ink on Philippine banknotes' },
    { pattern: /how (do i|to) scan/i, rewrite: 'User needs step-by-step guide for using the Scan Bill feature in BillSense app' },
    { pattern: /(peso|pesos|php|₱)\s*(\d+)/i, rewrite: `User asking about PHP ${userMessage.match(/(\d+)/)?.[1] || ''} denomination security features` },
    { pattern: /what.?(app|feature|tool)/i, rewrite: 'User wants to know about BillSense app features and capabilities' },
  ]

  for (const { pattern, rewrite } of rewrites) {
    if (pattern.test(lower)) {
      return `Context: ${rewrite}\nOriginal question: ${userMessage}`
    }
  }

  return `Context: User is asking Billy (BillSense AI assistant) about Philippine currency or app features.\nQuestion: ${userMessage}`
}

/**
 * Format Billy's response with consistent styling
 */
export function formatBillyResponse(rawResponse) {
  if (!rawResponse) return "I'm sorry, I couldn't process that. Could you try rephrasing your question? 😊"

  // Clean up excessive newlines
  let formatted = rawResponse.replace(/\n{3,}/g, '\n\n')

  // Ensure response ends with a follow-up if it doesn't have one
  if (!formatted.includes('?') && !formatted.includes('Would you') && formatted.length > 100) {
    formatted += '\n\n📌 Is there anything else you\'d like to know?'
  }

  return formatted
}

/**
 * Billy's conversation context manager
 * Maintains conversation history for contextual responses
 */
export class BillyConversation {
  constructor(maxHistory = 10) {
    this.history = []
    this.maxHistory = maxHistory
  }

  addUserMessage(message) {
    this.history.push({ role: 'user', content: message, timestamp: Date.now() })
    this.trimHistory()
  }

  addBotMessage(message) {
    this.history.push({ role: 'assistant', content: message, timestamp: Date.now() })
    this.trimHistory()
  }

  trimHistory() {
    if (this.history.length > this.maxHistory * 2) {
      this.history = this.history.slice(-this.maxHistory * 2)
    }
  }

  getContextMessages() {
    return [
      { role: 'system', content: BILLY_SYSTEM_PROMPT },
      ...this.history
    ]
  }

  clear() {
    this.history = []
  }
}
