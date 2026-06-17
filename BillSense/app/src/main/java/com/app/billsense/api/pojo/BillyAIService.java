package com.app.billsense.api.pojo;

import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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

    public interface BillyCallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    /**
     * Get Billy's response — tries local knowledge base first,
     * falls back to API if no match found.
     */
    public static void getResponse(String userMessage, @NonNull BillyCallback callback) {
        // Try local knowledge base first (instant response)
        String localResponse = getLocalResponse(userMessage);
        if (localResponse != null) {
            callback.onSuccess(localResponse);
            return;
        }

        // Fall back to Billy API endpoint
        fetchFromAPI(userMessage, callback);
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
                    "📌 How it works: deep-learning object detection (YOLOv8) trained on 3,113 " +
                    "verified authentic & counterfeit bills — real-time, no perfect lighting needed.\n\n" +
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
            // Rewrite query for better context
            String contextualQuery = rewriteQuery(userMessage);

            JSONObject json = new JSONObject();
            json.put("message", contextualQuery);
            json.put("original_message", userMessage);
            json.put("agent", "billy");

            // Use the BillSense API for Billy responses
            String apiUrl = com.app.billsense.BuildConfig.API_BASE_URL + "/api/billy-chat";

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject responseJson = new JSONObject(responseBody);
                            String billyResponse = responseJson.optString("response",
                                    "I'm processing your question. Could you try rephrasing it? \uD83D\uDE0A");
                            callback.onSuccess(billyResponse);
                        } catch (Exception e) {
                            // API returned unexpected format — use fallback
                            callback.onSuccess(getFallbackResponse(userMessage));
                        }
                    } else {
                        // API error — use intelligent fallback
                        callback.onSuccess(getFallbackResponse(userMessage));
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "API call failed: " + e.getMessage());
                    // Network error — use intelligent fallback
                    callback.onSuccess(getFallbackResponse(userMessage));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building request: " + e.getMessage());
            callback.onSuccess(getFallbackResponse(userMessage));
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
        for (String pattern : patterns) {
            if (input.contains(pattern)) return true;
        }
        return false;
    }
}
