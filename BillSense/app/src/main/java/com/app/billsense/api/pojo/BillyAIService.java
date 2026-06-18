package com.app.billsense.api.pojo;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Billy AI Service — BillSense Intelligent Assistant
 *
 * A context-aware AI agent that provides Philippine currency authentication
 * guidance, app feature tutorials, and counterfeit prevention knowledge.
 *
 * Uses a local knowledge base for instant responses and falls back to
 * the BillSense API for complex queries.
 */
public class BillyAIService {
    private static final String TAG = "BillyAIService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // --- Conversation memory (multi-turn) ---
    private static final List<String[]> HISTORY = new ArrayList<>();   // each entry = {role, text}
    private static final int MAX_HISTORY = 10;                          // last ~5 exchanges

    /** Clear memory — call when a new chat session starts. */
    public static void resetConversation() {
        synchronized (HISTORY) { HISTORY.clear(); }
    }

    private static void recordTurn(String user, String model) {
        synchronized (HISTORY) {
            HISTORY.add(new String[]{"user", user});
            HISTORY.add(new String[]{"model", model});
            while (HISTORY.size() > MAX_HISTORY) HISTORY.remove(0);
        }
    }

    // --- RAG: thesis knowledge retrieval (assets/billy_thesis.json) ---
    private static final List<String[]> THESIS_CHUNKS = new ArrayList<>();  // each = {section, text}
    private static volatile boolean thesisLoaded = false;

    /** Load + chunk the bundled thesis once, so Billy can retrieve real research details. */
    public static void initThesis(Context ctx) {
        if (thesisLoaded || ctx == null) return;
        try {
            InputStream is = ctx.getAssets().open("billy_thesis.json");
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            JSONObject sections = new JSONObject(sb.toString()).optJSONObject("sections");
            if (sections != null) {
                java.util.Iterator<String> keys = sections.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    JSONObject sec = sections.optJSONObject(k);
                    if (sec == null) continue;
                    String title = sec.optString("title", k);
                    for (String chunk : chunkText(sec.optString("content", ""), 600)) {
                        if (chunk.trim().length() > 40) THESIS_CHUNKS.add(new String[]{title, chunk.trim()});
                    }
                }
            }
            thesisLoaded = true;
            Log.i(TAG, "Thesis knowledge loaded: " + THESIS_CHUNKS.size() + " chunks");
        } catch (Exception e) {
            Log.w(TAG, "Could not load thesis knowledge: " + e.getMessage());
        }
    }

    private static List<String> chunkText(String text, int target) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        StringBuilder cur = new StringBuilder();
        for (String p : text.split("\\n+")) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (cur.length() + p.length() > target && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            while (p.length() > target * 2) { out.add(p.substring(0, target)); p = p.substring(target); }
            cur.append(cur.length() > 0 ? " " : "").append(p);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** Retrieve the most relevant thesis chunks for a query (keyword overlap). */
    private static String retrieveThesis(String query) {
        if (!thesisLoaded || THESIS_CHUNKS.isEmpty() || query == null) return null;
        String[] qWords = query.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+");
        List<double[]> scored = new ArrayList<>();   // {index, score}
        for (int i = 0; i < THESIS_CHUNKS.size(); i++) {
            String text = THESIS_CHUNKS.get(i)[1].toLowerCase();
            double score = 0;
            for (String w : qWords) {
                if (w.length() < 4) continue;        // skip short words / stopwords
                int idx = 0;
                while ((idx = text.indexOf(w, idx)) >= 0) { score++; idx += w.length(); }
            }
            if (score > 0) scored.add(new double[]{i, score});
        }
        if (scored.isEmpty()) return null;
        Collections.sort(scored, (a, b) -> Double.compare(b[1], a[1]));
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (double[] s : scored) {
            if (used >= 3 || sb.length() > 1800) break;
            String[] ch = THESIS_CHUNKS.get((int) s[0]);
            sb.append("• [").append(ch[0]).append("] ").append(ch[1]).append("\n");
            used++;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // Real AI backend: the cPanel server-side Gemini proxy (key stays on the server).
    // NOTE: this is the cPanel host, NOT the Cloud Run API_BASE_URL.
    private static final String GEMINI_PROXY = "https://billsense.dev-environment.site/api/gemini/chat";

    // v2: server-side Billy brain (FAISS RAG over the docs + guardrails + Gemini 3.1) on Cloud Run.
    private static final String BILLY_ENDPOINT =
            com.app.billsense.BuildConfig.API_BASE_URL + "/api/billy/chat";
    // Optional last-scan context for the "Explain my scan" feature.
    private static volatile String scanContext = null;
    public static void setScanContext(String s) { scanContext = s; }

    // Billy's persona + knowledge. Used for every AI answer so Billy is accurate
    // and never invents denominations, features, laws, or research details.
    private static final String SYSTEM_PROMPT =
            "You are Billy, the friendly AI assistant inside the BillSense Android app — a Philippine peso " +
            "counterfeit-detection app. Be warm, concise and practical: short paragraphs, bullet points, and a " +
            "clear next step. Plain English or light Taglish is fine. Use emojis sparingly (💵 🔍 ✅). " +
            "Only answer from the knowledge below; if you don't know, say so and point to Scan Bill or the BSP. " +
            "Never invent denominations, security features, laws, statistics, researcher names, model details, or numbers.\n\n" +

            "WHAT BILLSENSE IS:\n" +
            "An AI-driven mobile app that tells genuine Philippine banknotes from counterfeits in real time — " +
            "New Generation Currency (NGC), Enhanced NGC, and the newer POLYMER notes (₱1000 polymer, and the " +
            "2024 polymer ₱500/₱100/₱50). Built for everyday users and small businesses (MSMEs) who rely on manual checks.\n\n" +

            "THE RESEARCH (answer accurately if asked who made it / about the study / about Joy):\n" +
            "BillSense is an undergraduate research / thesis led by Joy Canutab with co-researchers (Canutab et al.) " +
            "at the University of the Cordilleras, Baguio City, Philippines. Goal: an accessible AI tool that reduces " +
            "losses from counterfeit cash. Theoretical frameworks: Human Error Theory, Routine Activity Theory, and " +
            "Computer Vision Theory; built with a design-thinking methodology (prototype → user feedback → expert " +
            "evaluation). Aligned with UN Sustainable Development Goal 16 (Target 16.4 — reducing illicit financial flows).\n\n" +

            "THE TECHNOLOGY (be precise):\n" +
            "BillSense uses **YOLOv8**, a convolutional-neural-network (CNN) **object-detection** model. It does NOT use " +
            "ORB feature-matching, and it is not a plain image classifier — detection and localisation are done by YOLOv8. " +
            "If asked 'CNN or ORB?': the answer is YOLOv8 (CNN-based object detection); ORB is not used. " +
            "Six YOLO models run on a cloud FastAPI service (Google Cloud Run): (1) denomination, (2) a security-feature " +
            "detector, (3) optically variable ink (OVI), (4) optically variable device (OVD), (5) enhanced value panel (EVP), " +
            "and (6) a security-feature counterfeit model. There is also an on-device TFLite fallback for offline use. " +
            "The denomination model was retrained on a merged YOLOv8 + COCO Philippine-banknote dataset that includes polymer notes.\n\n" +

            "HOW A SCAN WORKS (current 'real measurement' logic):\n" +
            "The camera frame goes to the cloud YOLO models, which detect the denomination and which security features are " +
            "present. A measurement layer then computes a calibrated 0–100 AUTHENTICITY SCORE from: feature coverage + " +
            "detection confidence + image quality. It also checks each feature's PLACEMENT against a genuine reference layout, " +
            "and on Multi-Scan it measures OVI/OVD COLOUR-SHIFT across angles. Verdict tiers: GENUINE, LIKELY GENUINE, " +
            "NEEDS_RESCAN (when the photo is too blurry/dark), and COUNTERFEIT (only when a real forgery marker is found, " +
            "e.g. a FALSE enhanced value panel). A genuine note is never flagged counterfeit just for low feature coverage.\n\n" +

            "SCAN MODES:\n" +
            "• Standard — quick single photo (denomination + visible features). \n" +
            "• Multi-Scan — several angles; this is how tilt features like OVI/OVD colour-shift get verified.\n" +
            "• Video Scan — continuous detection.  • Upload Image — analyse a saved photo.\n" +
            "Plus real-time live scanning, Scan History, and reporting suspected fakes via Evidence Submission / Cases.\n\n" +

            "SECURITY FEATURES (NGC / Enhanced NGC) and the BSP 'Feel, Look, Tilt' method:\n" +
            "watermark (shadow portrait + denomination, seen against light), embedded security thread, see-through register, " +
            "concealed value, optically variable ink (OVI), optically variable device (OVD) patch, matching serial numbers, " +
            "the enhanced value panel (EVP) on ₱500 & ₱1000, embossed/tactile marks for the visually impaired, and microprint.\n\n" +

            "HONEST LIMITS (say these when relevant):\n" +
            "A single front-lit phone photo cannot capture the watermark/see-through (need back-light), OVI/OVD colour-shift " +
            "(need tilt → use Multi-Scan), or UV features (a phone has no UV light). BillSense guides the check; the BSP is the " +
            "final authority. Law (RA 10951; Revised Penal Code Art. 168) is for education only — not legal advice.\n\n" +

            "If asked something unrelated to currency, BillSense, or the research, gently steer back.";

    public interface BillyCallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    /**
     * Get Billy's response — tries local knowledge base first,
     * falls back to API if no match found.
     */
    public static void getResponse(String userMessage, @NonNull BillyCallback callback) {
        // Instant, offline answers only for trivial greetings / thanks.
        String quick = getQuickLocalResponse(userMessage);
        if (quick != null) {
            callback.onSuccess(quick);
            return;
        }
        // Everything substantive goes to the AI (Gemini) with Billy's full knowledge base,
        // so answers are intelligent and current. The canned KB is used only as an OFFLINE
        // fallback (see offlineAnswer inside fetchFromAPI).
        fetchFromAPI(userMessage, callback);
    }

    /** Instant offline answers ONLY for greetings / thanks — everything else uses the AI. */
    private static String getQuickLocalResponse(String userMessage) {
        String lower = userMessage.toLowerCase().trim();
        if (matchesAny(lower, "hello", "hi", "hey", "good morning", "good afternoon",
                "good evening", "kumusta", "musta")) {
            return "👋 Hi! I'm Billy, your BillSense assistant.\n\n" +
                    "Ask me about checking a peso bill, using the app (Standard / Multi-Scan / Video), " +
                    "the security features, how the AI model works, or the research behind BillSense.\n\n" +
                    "What would you like to know? 🔍";
        }
        if (matchesAny(lower, "thank", "thanks", "salamat", "thank you")) {
            return "You're welcome! 😊 Stay safe and always Feel, Look, Tilt your bills.\n\n" +
                    "Tap Scan Bill whenever you want a quick check. ✅";
        }
        return null;
    }

    /** Offline answer: canned KB → offline thesis RAG → generic helpful fallback. */
    private static String offlineAnswer(String userMessage) {
        String kb = getLocalResponse(userMessage);
        if (kb != null) return kb;
        String excerpt = retrieveThesis(userMessage);
        if (excerpt != null) {
            return "I'm offline right now, but here's what the BillSense research says:\n\n" + excerpt
                    + "\nReconnect for a full answer. 🔍";
        }
        return getFallbackResponse(userMessage);
    }

    /**
     * Local knowledge base — instant responses without network call
     */
    private static String getLocalResponse(String userMessage) {
        String lower = userMessage.toLowerCase().trim();

        // Greetings
        if (matchesAny(lower, "hello", "hi", "hey", "good morning", "good afternoon", "kumusta", "musta")) {
            return "\uD83D\uDC4B Hi there! I'm Billy, your BillSense AI assistant.\n\n" +
                    "\uD83D\uDE0A How can I help you today? I can guide you on:\n\n" +
                    "• Bill authentication & security features\n" +
                    "• Using the BillSense app (Scan, Compare, Report)\n" +
                    "• Counterfeit prevention tips\n" +
                    "• Philippine currency knowledge\n\n" +
                    "Just ask anything! \uD83E\uDDD0";
        }

        // Scan feature guide
        if (matchesAny(lower, "how to scan", "scan bill", "camera scan", "scan feature")) {
            return "\uD83D\uDCF1 Here's how to use Scan Bill:\n\n" +
                    "1️⃣ Tap Scan Bill on the home screen\n" +
                    "2️⃣ Choose your scan type:\n" +
                    "   • Standard — quick single-frame scan\n" +
                    "   • Multi-Angle — captures multiple views\n" +
                    "   • Video — continuous detection\n" +
                    "3️⃣ Hold your camera steady over the banknote\n" +
                    "4️⃣ Wait for the AI to analyze — results appear in seconds!\n\n" +
                    "\uD83D\uDCCC Tip: Good lighting makes a big difference. Natural daylight works best!\n\n" +
                    "Would you like to know about each scan mode in detail?";
        }

        // Watermark check
        if (matchesAny(lower, "watermark", "hold to light", "shadow portrait")) {
            return "\uD83D\uDD0D Watermark Check:\n\n" +
                    "Hold the bill up to a light source and look for:\n\n" +
                    "✅ A shadow portrait matching the printed portrait\n" +
                    "✅ The denomination number\n" +
                    "✅ Clear, sharp edges (fakes are often blurry)\n\n" +
                    "Each denomination has a unique watermark:\n" +
                    "• ₱20: Manuel Quezon\n" +
                    "• ₱50: Sergio Osmeña\n" +
                    "• ₱100: Manuel Roxas\n" +
                    "• ₱200: Diosdado Macapagal\n" +
                    "• ₱500: Benigno Aquino Jr.\n" +
                    "• ₱1000: Heroes trio\n\n" +
                    "\uD83D\uDCCC Use the Step-by-Step Detection Guide for photo guidance!";
        }

        // Serial number
        if (matchesAny(lower, "serial", "serial number", "number check")) {
            return "\uD83D\uDD22 Serial Number Verification:\n\n" +
                    "✅ Both serial numbers (top-right and bottom-left) must match exactly\n" +
                    "✅ Look for consistent font, spacing, and alignment\n" +
                    "✅ Genuine serials are crisp and evenly printed\n" +
                    "✅ Under UV light, serial numbers should fluoresce\n\n" +
                    "\uD83D\uDCCC BillSense's Scan Bill feature automatically checks serial number consistency!";
        }

        // UV features
        if (matchesAny(lower, "uv", "ultraviolet", "black light", "glow", "fluorescent")) {
            return "\uD83D\uDCA1 UV (Ultraviolet) Features:\n\n" +
                    "Under UV light, genuine Philippine bills reveal:\n\n" +
                    "✅ Security fibers that glow in different colors\n" +
                    "✅ Serial numbers that fluoresce\n" +
                    "✅ UV thread becomes clearly visible\n" +
                    "✅ Concealed value appears in hidden locations\n\n" +
                    "\uD83D\uDCCC BillSense's UV Scan mode detects these features automatically!\n\n" +
                    "Would you like to know which UV features are on a specific denomination?";
        }

        // Compare feature
        if (matchesAny(lower, "compare", "comparison", "side by side", "genuine vs fake")) {
            return "\uD83D\uDCCA Compare Bill Feature:\n\n" +
                    "1️⃣ Open Compare Bill from the home screen\n" +
                    "2️⃣ Upload or capture a photo of your suspected note\n" +
                    "3️⃣ View it side-by-side with a genuine reference\n" +
                    "4️⃣ Check key security features point by point\n\n" +
                    "\uD83D\uDCCC Great for learning what genuine bills look like vs. counterfeits!\n\n" +
                    "Would you like tips on specific features to compare?";
        }

        // Report / Evidence
        if (matchesAny(lower, "report", "evidence", "found fake", "counterfeit found", "submit case")) {
            return "\uD83D\uDEA8 Reporting Suspected Counterfeits:\n\n" +
                    "1️⃣ Go to Evidence Submission in the app\n" +
                    "2️⃣ Take clear photos of the front and back\n" +
                    "3️⃣ Add the location where you received the bill\n" +
                    "4️⃣ Submit your case — it helps the community!\n\n" +
                    "\uD83D\uDCCC View reported cases on the Cases Map to see counterfeit hotspots.\n\n" +
                    "⚠️ Important: Do not circulate suspected counterfeit bills. Contact your nearest BSP office or police station.";
        }

        // Feel Look Tilt method
        if (matchesAny(lower, "feel look tilt", "how to check", "is this real", "is this fake",
                "genuine", "authenticate", "verify bill", "check bill")) {
            return "\uD83E\uDDD0 Use the Feel, Look, Tilt method:\n\n" +
                    "1️⃣ FEEL — Genuine bills have a distinct cotton-linen texture with raised print on key areas (denomination, portraits)\n\n" +
                    "2️⃣ LOOK — Hold up to light:\n" +
                    "   • Watermark portrait should be clear\n" +
                    "   • Security thread should be embedded (not printed on)\n" +
                    "   • See-through register should align perfectly\n\n" +
                    "3️⃣ TILT — Observe:\n" +
                    "   • Color-shifting ink (₱500/₱1000)\n" +
                    "   • Holographic elements\n" +
                    "   • Optically variable devices\n\n" +
                    "\uD83D\uDCCC For AI-powered verification, use Scan Bill in BillSense!\n\n" +
                    "Which denomination would you like to check?";
        }

        // Law / Legal
        if (matchesAny(lower, "law", "penalty", "punishment", "illegal", "crime", "prison", "legal")) {
            return "⚖️ Anti-Counterfeiting Laws (Philippines):\n\n" +
                    "Under Republic Act No. 10951:\n\n" +
                    "\uD83D\uDCCC Counterfeiting Philippine currency is a serious criminal offense\n" +
                    "\uD83D\uDCCC Penalties include imprisonment and fines\n" +
                    "\uD83D\uDCCC Even possessing or passing counterfeit bills knowingly is punishable\n\n" +
                    "⚠️ This is educational information only — for specific legal questions, please consult the BSP or a legal professional.\n\n" +
                    "Would you like to know how to protect yourself from receiving counterfeit bills?";
        }

        // How the AI model works (YOLO/CNN vs ORB, the current trained model)
        if (matchesAny(lower, "model", "yolo", "cnn", "orb", "algorithm", "neural network",
                "deep learning", "machine learning", "object detection", "how does it work",
                "how it works", "how it detect", "how does the app detect", "how is it trained", "trained")) {
            return "🤖 How BillSense detects bills:\n\n" +
                    "BillSense uses YOLOv8 — a convolutional neural network (CNN) for object detection. " +
                    "It does NOT use ORB feature-matching; the detection is done by YOLOv8.\n\n" +
                    "• Six YOLO models run on a cloud service: denomination, security-feature detector, OVI, " +
                    "OVD, enhanced value panel, and a security-feature counterfeit model — plus an on-device " +
                    "TFLite fallback for offline use.\n" +
                    "• The denomination model was retrained on Philippine banknotes including the new polymer notes.\n" +
                    "• A measurement layer gives a 0–100 authenticity score from feature coverage + detection " +
                    "confidence + image quality, checks each feature's placement, and (on Multi-Scan) measures " +
                    "OVI/OVD colour-shift across angles.\n\n" +
                    "Verdicts: GENUINE, LIKELY GENUINE, NEEDS_RESCAN (blurry/dark), or COUNTERFEIT (only on a " +
                    "real forgery sign). Tap Scan Bill to try it! 🔍";
        }

        // Research / About BillSense (who made it, the thesis behind it)
        if (matchesAny(lower, "who made", "who created", "who developed", "who built", "who conducted",
                "researcher", "research", "thesis", "canutab", "cordillera", "university",
                "behind billsense", "about billsense", "creator", "developer", "study")) {
            return "🎓 About BillSense (the research):\n\n" +
                    "BillSense is a research/thesis project by Joy Canutab and co-researchers " +
                    "(Canutab et al.) at the University of the Cordilleras, Baguio City, Philippines.\n\n" +
                    "📌 Goal: an AI-driven mobile app that detects counterfeit vs. genuine " +
                    "Philippine banknotes (NGC & ENGC series), helping everyday users and small " +
                    "businesses (MSMEs) who struggle with manual checks.\n\n" +
                    "📌 How it works: YOLOv8 (a CNN object-detection model — not ORB) running six models " +
                    "on a cloud service plus an on-device fallback, retrained on Philippine banknotes including " +
                    "the new polymer notes — real-time, no perfect lighting needed.\n\n" +
                    "📌 Foundations: Human Error Theory, Routine Activity Theory, and Computer " +
                    "Vision Theory, built with a design-thinking methodology (prototype → user feedback → " +
                    "expert evaluation).\n\n" +
                    "📌 Impact: aligned with UN Sustainable Development Goal 16 (Target 16.4 — " +
                    "reducing illicit financial flows and organized crime).\n\n" +
                    "Want to see it in action? Tap Scan Bill! 🔍";
        }

        // Help / What can you do
        if (matchesAny(lower, "help", "what can you do", "features", "menu", "options", "guide me")) {
            return "\uD83D\uDE0A Here's what I can help you with:\n\n" +
                    "\uD83D\uDD0D Bill Verification — How to check if a bill is genuine\n" +
                    "\uD83D\uDCF1 App Features — Scan Bill, Compare Bill, Step-by-Step Guide\n" +
                    "\uD83D\uDCA1 Security Features — Watermarks, UV, serial numbers, OVD\n" +
                    "\uD83D\uDCCA Tutorials — Step-by-step guides for each feature\n" +
                    "\uD83D\uDEA8 Reporting — How to submit evidence of counterfeits\n" +
                    "⚖️ Laws — Anti-counterfeiting regulations\n" +
                    "\uD83D\uDCDA Education — Currency history and design\n" +
                    "🎓 About BillSense — The research & team behind the app\n\n" +
                    "Just ask about any of these topics! What would you like to know?";
        }

        // Specific denominations
        if (lower.contains("1000") || lower.contains("one thousand")) {
            return "\uD83D\uDCB5 ₱1000 Note Security Features:\n\n" +
                    "✅ Watermark: Three heroes (Abad Santos, Lim, Escoda)\n" +
                    "✅ Wide holographic security thread with color shift\n" +
                    "✅ Color-shifting ink on denomination number\n" +
                    "✅ Microprinting around portrait area\n" +
                    "✅ Concealed value visible under UV\n" +
                    "✅ Serial number fluorescence under UV\n\n" +
                    "\uD83D\uDCCC The ₱1000 has the most security features. Use Scan Bill for AI-powered verification!";
        }

        if (lower.contains("500") || lower.contains("five hundred")) {
            return "\uD83D\uDCB5 ₱500 Note Security Features:\n\n" +
                    "✅ Watermark: Benigno Aquino Jr.\n" +
                    "✅ Holographic security strip\n" +
                    "✅ Color-shifting ink (gold to green)\n" +
                    "✅ Microprinting details\n" +
                    "✅ UV-reactive security fibers\n" +
                    "✅ Embossed denomination for the visually impaired\n\n" +
                    "\uD83D\uDCCC Tip: The color-shifting ink is one of the hardest features to counterfeit!";
        }

        // Thank you
        if (matchesAny(lower, "thank", "thanks", "salamat", "thank you")) {
            return "You're welcome! \uD83D\uDE0A Happy to help!\n\n" +
                    "Remember, you can always come back if you have more questions about bill verification or the BillSense app.\n\n" +
                    "Stay safe and always check your bills! ✅";
        }

        return null; // No local match
    }

    /**
     * Fetch response from Billy API endpoint
     */
    private static void fetchFromAPI(String userMessage, @NonNull BillyCallback callback) {
        try {
            // Conversation memory: send prior turns so Billy handles follow-ups.
            JSONArray historyArr = new JSONArray();
            synchronized (HISTORY) {
                for (String[] turn : HISTORY) {
                    historyArr.put(new JSONObject().put("role", turn[0]).put("text", turn[1]));
                }
            }
            // Server does the FAISS retrieval + guardrails + Gemini 3.1; we just send the question.
            JSONObject json = new JSONObject();
            json.put("message", userMessage);
            json.put("history", historyArr);
            if (scanContext != null && !scanContext.isEmpty()) {
                json.put("scanContext", scanContext);
            }

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BILLY_ENDPOINT)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Billy HTTP " + response.code() + " len=" + bodyStr.length()
                            + " body[0:160]=" + (bodyStr.length() > 160 ? bodyStr.substring(0, 160) : bodyStr));
                    if (response.isSuccessful() && !bodyStr.isEmpty()) {
                        try {
                            JSONObject jr = new JSONObject(bodyStr);
                            String answer = jr.optString("answer", "");
                            if (answer.isEmpty()) {
                                Log.w(TAG, "Billy: empty answer field — falling back");
                                callback.onSuccess(offlineAnswer(userMessage));
                                return;
                            }
                            recordTurn(userMessage, answer);   // memory keeps the clean answer
                            // Append source citations (the documents Billy drew from)
                            JSONArray srcs = jr.optJSONArray("sources");
                            if (srcs != null && srcs.length() > 0) {
                                StringBuilder sb = new StringBuilder(answer).append("\n\n📚 Sources: ");
                                for (int i = 0; i < srcs.length(); i++) {
                                    if (i > 0) sb.append(" · ");
                                    sb.append(srcs.optString(i));
                                }
                                answer = sb.toString();
                            }
                            callback.onSuccess(answer);
                        } catch (Exception e) {
                            Log.w(TAG, "Billy: parse error — falling back: " + e.getMessage());
                            callback.onSuccess(offlineAnswer(userMessage));
                        }
                    } else {
                        Log.w(TAG, "Billy: non-200/empty (code=" + response.code() + ") — falling back");
                        callback.onSuccess(offlineAnswer(userMessage));
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Billy endpoint failed: " + e.getMessage());
                    callback.onSuccess(offlineAnswer(userMessage));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building Billy request: " + e.getMessage());
            callback.onSuccess(offlineAnswer(userMessage));
        }
    }

    /** Extract the assistant text from Google's Gemini response JSON. */
    private static String extractGeminiText(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return null;
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return null;
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                sb.append(parts.getJSONObject(i).optString("text", ""));
            }
            String out = sb.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Intelligent fallback when API is unavailable
     */
    private static String getFallbackResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("bill") || lower.contains("money") || lower.contains("peso") || lower.contains("currency")) {
            return "\uD83D\uDE0A That's a great question about currency!\n\n" +
                    "I can help you with:\n" +
                    "• Checking bill authenticity (try asking \"How to check if my bill is real?\")\n" +
                    "• Security features (ask about \"watermark\", \"UV\", or \"serial number\")\n" +
                    "• Using the Scan Bill feature\n\n" +
                    "\uD83D\uDCCC Could you be more specific so I can give you the best answer?";
        }

        return "\uD83E\uDDD0 I appreciate your question!\n\n" +
                "I specialize in Philippine currency verification and the BillSense app. Here are some things I can help with:\n\n" +
                "• \uD83D\uDD0D \"How to check if a bill is genuine?\"\n" +
                "• \uD83D\uDCF1 \"How to use Scan Bill?\"\n" +
                "• \uD83D\uDCA1 \"What are the security features of ₱1000?\"\n" +
                "• \uD83D\uDCCA \"How to compare bills?\"\n\n" +
                "Try asking one of these! \uD83D\uDE0A";
    }

    /**
     * Rewrite vague queries for better API context
     */
    private static String rewriteQuery(String userMessage) {
        String lower = userMessage.toLowerCase().trim();

        if (lower.matches(".*is this (real|fake|genuine).*")) {
            return "User wants to verify bill authenticity using BillSense. Original: " + userMessage;
        }
        if (lower.matches(".*(check|verify) (serial|code|number).*")) {
            return "User wants to verify serial number on Philippine banknote. Original: " + userMessage;
        }
        if (lower.matches(".*(shiny|holographic|color.?changing).*")) {
            return "User asking about optically variable device (OVD) on Philippine banknotes. Original: " + userMessage;
        }

        return userMessage;
    }

    private static boolean matchesAny(String input, String... patterns) {
        // Word-aware matching. Single-word patterns must match a WHOLE word so short
        // tokens like "hi"/"uv" don't match "this"/"Philippine"/"survey". Multi-word
        // phrases ("how to scan") still match as substrings.
        String padded = " " + input.replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (String pattern : patterns) {
            if (pattern.contains(" ")) {
                if (input.contains(pattern)) return true;       // phrase
            } else if (padded.contains(" " + pattern + " ")) {
                return true;                                    // whole word
            }
        }
        return false;
    }
}
