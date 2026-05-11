"""BillSense deep technical analysis PDF generator."""
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    KeepTogether, ListFlowable, ListItem
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import os, datetime

OUT = os.path.join(os.path.dirname(__file__), "BillSense_Deep_Analysis.pdf")

# ---------- styles ----------
ss = getSampleStyleSheet()
H1 = ParagraphStyle('H1', parent=ss['Heading1'], fontSize=20, leading=24,
                   textColor=colors.HexColor('#1A237E'), spaceBefore=12, spaceAfter=10)
H2 = ParagraphStyle('H2', parent=ss['Heading2'], fontSize=14, leading=18,
                   textColor=colors.HexColor('#283593'), spaceBefore=14, spaceAfter=6)
H3 = ParagraphStyle('H3', parent=ss['Heading3'], fontSize=11, leading=14,
                   textColor=colors.HexColor('#3949AB'), spaceBefore=8, spaceAfter=4)
BODY = ParagraphStyle('Body', parent=ss['BodyText'], fontSize=10, leading=14,
                     alignment=TA_JUSTIFY, spaceAfter=6)
SMALL = ParagraphStyle('Small', parent=ss['BodyText'], fontSize=8.5, leading=11,
                      textColor=colors.HexColor('#555'))
CODE = ParagraphStyle('Code', parent=ss['Code'], fontSize=8.5, leading=11,
                     backColor=colors.HexColor('#F4F6FA'),
                     textColor=colors.HexColor('#0F172A'),
                     leftIndent=8, rightIndent=8, borderPadding=4, borderColor=colors.HexColor('#E2E8F0'),
                     borderWidth=0.5, spaceAfter=8)
COVER_TITLE = ParagraphStyle('CT', parent=ss['Title'], fontSize=28, leading=32,
                            textColor=colors.HexColor('#0D1B5C'), alignment=TA_CENTER)
COVER_SUB = ParagraphStyle('CS', parent=ss['Normal'], fontSize=14, leading=18,
                          textColor=colors.HexColor('#3949AB'), alignment=TA_CENTER)

def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 8)
    canvas.setFillColor(colors.HexColor('#94A3B8'))
    canvas.drawString(0.75*inch, 0.55*inch, "BillSense - Deep Technical Analysis")
    canvas.drawRightString(LETTER[0]-0.75*inch, 0.55*inch, f"Page {doc.page}")
    canvas.setStrokeColor(colors.HexColor('#E2E8F0'))
    canvas.line(0.75*inch, 0.72*inch, LETTER[0]-0.75*inch, 0.72*inch)
    canvas.restoreState()

def section_table(data, col_widths=None, header=True):
    tbl = Table(data, colWidths=col_widths, hAlign='LEFT')
    style = [
        ('FONT', (0,0), (-1,-1), 'Helvetica', 9),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('GRID', (0,0), (-1,-1), 0.4, colors.HexColor('#CBD5E1')),
        ('LEFTPADDING', (0,0), (-1,-1), 5),
        ('RIGHTPADDING', (0,0), (-1,-1), 5),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('ROWBACKGROUNDS', (0, 1 if header else 0), (-1,-1),
         [colors.white, colors.HexColor('#F8FAFC')]),
    ]
    if header:
        style += [
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#1A237E')),
            ('TEXTCOLOR', (0,0), (-1,0), colors.white),
            ('FONT', (0,0), (-1,0), 'Helvetica-Bold', 9.5),
        ]
    tbl.setStyle(TableStyle(style))
    return tbl

def bullet_list(items):
    return ListFlowable(
        [ListItem(Paragraph(t, BODY), leftIndent=14) for t in items],
        bulletType='bullet', bulletColor=colors.HexColor('#3949AB'),
        leftIndent=14, bulletFontSize=8
    )

# ---------- content ----------
story = []

# Cover
story.append(Spacer(1, 1.4*inch))
story.append(Paragraph("BillSense", COVER_TITLE))
story.append(Spacer(1, 0.15*inch))
story.append(Paragraph("Deep Technical Analysis & System Audit", COVER_SUB))
story.append(Spacer(1, 0.6*inch))
story.append(section_table([
    ["Domain", "Philippine currency counterfeit detection"],
    ["Platform", "Android (Java) + Vue admin dashboard + FastAPI ML backend"],
    ["Backend", "Google Cloud Run (Asia Southeast 2)"],
    ["ML Stack", "YOLOv8 OBB - 6 specialized models"],
    ["Firebase Project", "bill-sense-aec6b"],
    ["Report Generated", datetime.date.today().isoformat()],
], col_widths=[2.0*inch, 4.3*inch], header=False))
story.append(Spacer(1, 0.4*inch))
story.append(Paragraph(
    "Prepared by Claude Code (claude-opus-4-7) - automated codebase audit covering "
    "architecture, security posture, deployment health, and remediation paths.",
    SMALL))
story.append(PageBreak())

# 1. Executive Summary
story.append(Paragraph("1. Executive Summary", H1))
story.append(Paragraph(
    "BillSense is a counterfeit-banknote detection system targeting Philippine peso bills. "
    "An Android client captures imagery, sends it to a FastAPI service hosted on Cloud Run, "
    "which runs an ensemble of six YOLOv8 OBB models to score authenticity against denomination, "
    "security features, optically variable ink (OVI), optically variable device (OVD), enhanced "
    "value panel (EVP), and direct counterfeit classification. Results route into Firebase RTDB "
    "and Storage. A Vue 3 / Vite admin panel provides operational visibility, APK build automation "
    "via a host-side Node dev-server, and a Puppeteer-backed GitNexus agent for repository workflows.",
    BODY))
story.append(Paragraph("Top findings", H3))
story.append(bullet_list([
    "<b>Production dashboard offline:</b> https://billsense.dev-environment.site/ returns the default Phusion Passenger placeholder. The Vue build was never deployed; the cPanel Node app stub shadows it.",
    "<b>Local ML container not runnable as-is:</b> the docker-compose API service pulls from a private GCP Artifact Registry that needs interactive <code>gcloud auth login</code>, plus model weights and a Firebase service-account key that are not in the repo.",
    "<b>Cloud Run backend is healthy</b> but reports <code>models_loaded: false</code> on cold health checks - models lazy-load on first scan.",
    "<b>No CI/CD existed</b> before this audit. A GitHub Actions workflow has been added to deploy the admin panel to cPanel via FTPS on every push to main.",
    "<b>Secret hygiene concerns:</b> Firebase Web API key embedded in source, MAPS_API_KEY in local.properties, no .env scaffold for VITE_ keys.",
]))
story.append(PageBreak())

# 2. System Architecture
story.append(Paragraph("2. System Architecture", H1))
story.append(Paragraph(
    "Three-tier system: a thick Android client, a stateless Cloud Run inference API, "
    "and a Firebase data plane shared across mobile and admin surfaces. An operator-facing "
    "Vue dashboard and two helper services (dev-server, GitNexus agent) sit outside the user "
    "data path and exist purely for developer ergonomics.",
    BODY))

story.append(Paragraph("2.1 Component map", H2))
story.append(section_table([
    ["Component", "Tech", "Runtime", "Purpose"],
    ["Android app", "Java 11, minSdk 24, AGP 8.11.1", "User devices", "Capture, scan, history, FCM, account, chatbot"],
    ["FastAPI backend", "Python, FastAPI v17, OpenCV, YOLOv8", "Cloud Run asia-southeast2", "REST + WebSocket inference, 6-model ensemble"],
    ["Admin panel", "Vue 3.5, Vite 6, Firebase 11 SDK", "Static SPA (cPanel)", "Health, GitNexus, APK ops, app testing"],
    ["Dev server", "Node 20 native (no deps)", "Host process, port 3003", "ADB, Gradle build, APK distribute"],
    ["GitNexus agent", "Express + Puppeteer", "Docker, port 3002", "Headless GitHub clone automation"],
    ["Firebase", "RTDB + Storage + Messaging", "Google managed", "Scan history, app config, FCM topics"],
], col_widths=[1.3*inch, 1.6*inch, 1.4*inch, 2.2*inch]))

story.append(Paragraph("2.2 Data flow", H2))
story.append(Paragraph(
    "<b>Scan path:</b> Android camera frame &rarr; JPEG base64 over WSS to "
    "<code>/ws/standard-scan</code> or REST to <code>/api/standard-scan</code> &rarr; FastAPI loads "
    "models on demand &rarr; OBB detection per model &rarr; numbered annotation overlay &rarr; result + "
    "annotated image to Firebase Storage, summary to RTDB &rarr; client renders verdict.",
    BODY))
story.append(Paragraph(
    "<b>Admin path:</b> Browser SPA &rarr; Firebase Web SDK reads RTDB directly &rarr; UI panels poll "
    "Cloud Run /api/health, localhost:8080 (local container), localhost:3003 (dev-server), "
    "localhost:3002 (gitnexus), GitHub API. No backend-for-frontend layer.",
    BODY))
story.append(PageBreak())

# 3. Android client
story.append(Paragraph("3. Android Client", H1))
story.append(Paragraph(
    "113 Java source files across 9 packages under com.app.billsense. Build configured for "
    "Java 11, ViewBinding (no DataBinding, no findViewById), CameraX, OkHttp/Retrofit for "
    "network, Glide for image loading, Material Design components.",
    BODY))
story.append(Paragraph("3.1 Package structure", H2))
story.append(section_table([
    ["Package", "Responsibility"],
    ["activities", "Top-level screens: Login, Home, Detection, Profile, Cases, Compare, Education, Chatbot, AddVoting"],
    ["adapters", "RecyclerView adapters for scan history, evidence, cases lists"],
    ["api", "Retrofit interfaces and request/response DTOs"],
    ["fcm", "Firebase Cloud Messaging receivers and topic management"],
    ["fragments", "Tab fragments inside Home / Dashboard"],
    ["interfaces", "Listener and callback contracts"],
    ["model", "POJOs for users, scans, evidence, cases"],
    ["scan", "CurrencyApiService, RealTimeScanManager (WebSocket), image preprocessing"],
    ["utils", "Permission helpers, image utils, formatters, validators"],
], col_widths=[1.4*inch, 5.0*inch]))

story.append(Paragraph("3.2 Key activities", H2))
story.append(bullet_list([
    "<b>MainActivity / DispatchActivity</b> - splash and routing to login or home.",
    "<b>LoginActivity</b> - Firebase Auth, country-code picker, FCM token registration.",
    "<b>HomeActivity</b> - tabbed shell hosting fragments for scan, history, profile.",
    "<b>DetectionActivity</b> - the core scan flow; uses CameraX preview, frame capture, base64 encode at 80% JPEG quality, sends over WebSocket for real-time multi-bill scanning.",
    "<b>CompareBillActivity</b> - side-by-side comparison view for educational training.",
    "<b>EducationalContentActivity</b> - bundled materials on security features.",
    "<b>EvidenceActivity</b> / <b>CasesActivity</b> - field-officer workflow for filing counterfeit incidents.",
    "<b>ChatBotActivity</b> - the Billy AI agent surface for user Q&amp;A.",
]))

story.append(Paragraph("3.3 Critical scan integration", H2))
story.append(Paragraph("<code>scan/pojo/CurrencyApiService.java</code>", CODE))
story.append(Paragraph(
    "REST client wrapping standard-scan, multi-scan, video-scan. Multipart upload, "
    "configurable timeouts.",
    BODY))
story.append(Paragraph("<code>scan/pojo/RealTimeScanManager.java</code>", CODE))
story.append(Paragraph(
    "OkHttp WebSocket client. Auto-starts on connection (no START_SCAN handshake). "
    "Pushes base64 JPEG frames at the configured cadence and parses incoming detection "
    "payloads onto the main thread for overlay rendering.",
    BODY))
story.append(PageBreak())

# 4. ML backend
story.append(Paragraph("4. ML Inference Backend", H1))
story.append(Paragraph(
    "FastAPI service deployed to Cloud Run (asia-southeast2). Single-process, thread-pool "
    "of 4 workers, CORS open (<code>allow_origins=['*']</code>). Six YOLOv8 OBB models loaded "
    "lazily on first request, with a dummy-model fallback for resilience in dev.",
    BODY))

story.append(Paragraph("4.1 Endpoints", H2))
story.append(section_table([
    ["Method", "Path", "Purpose"],
    ["POST", "/api/standard-scan", "One-shot single-image authenticity scan"],
    ["POST", "/api/multi-scan", "Detect and score multiple bills in one image"],
    ["POST", "/api/video-scan", "Frame-by-frame scan of an uploaded video"],
    ["GET", "/api/health", "Liveness, model load state, Firebase reachability"],
    ["GET", "/api/real-time-status", "Active WebSocket connection metrics"],
    ["WSS", "/ws/standard-scan", "Real-time single-bill stream"],
    ["WSS", "/ws/real-multi-scan", "Real-time multi-bill stream"],
    ["WSS", "/ws/real-video-scan", "Real-time video frame stream"],
], col_widths=[0.7*inch, 1.9*inch, 3.7*inch]))

story.append(Paragraph("4.2 Model ensemble", H2))
story.append(section_table([
    ["Model file", "Role", "Output classes (abridged)"],
    ["denomination2.pt", "Bill value classifier", "20, 50, 100, 200, 500, 1000"],
    ["security_best.pt", "Security feature detector", "watermark, security thread, serial number, value, concealed value, see-through mark"],
    ["ovi.pt", "Optically variable ink", "OVI present / absent / suspicious"],
    ["ovd.pt", "Optically variable device", "OVD foil regions"],
    ["evp.pt", "Enhanced value panel", "500/1000 EVP variants, false-positive guard"],
    ["counterfeit_best.pt", "Direct counterfeit classifier", "UV-thread, missing markers, anomalies"],
], col_widths=[1.5*inch, 1.7*inch, 3.1*inch]))

story.append(Paragraph("4.3 Notable internals", H2))
story.append(bullet_list([
    "Custom JSON encoder for numpy types prevents serialization crashes but converts large arrays unguarded - potential DoS surface.",
    "ConnectionManager tracks WebSocket lifecycle and assigns numeric IDs; client-supplied <code>client_id</code> is not validated.",
    "Numbered-annotation system maps detections to feature numbers 1-9 for overlay rendering.",
    "Firebase init is optional - if service account is missing, a DummyFirebaseClient stub takes over and scans run without persistence.",
]))
story.append(PageBreak())

# 5. Admin dashboard
story.append(Paragraph("5. Admin Dashboard", H1))
story.append(Paragraph(
    "Single-page Vue 3 application. Five views, Firebase Web SDK for direct RTDB and Storage "
    "reads, polling-based health checks against the various backends. Builds to static files in "
    "<code>dist/</code> served by nginx in Docker, or by cPanel after deploy.",
    BODY))

story.append(Paragraph("5.1 Views and routes", H2))
story.append(section_table([
    ["Route", "View", "What it does"],
    ["/", "Dashboard.vue", "Connection health tiles for the four core service surfaces"],
    ["/connection-health", "ConnectionHealth.vue", "Deep service view: Firebase, GCP, Docker, Mobile, GitNexus, GitHub, Claude Code, MCP, emulator"],
    ["/gitnexus", "GitNexus.vue", "Trigger Puppeteer auto-clone, manage AI keys"],
    ["/apk-management", "ApkManagement.vue", "Build / list / download APKs via dev-server"],
    ["/app-testing", "AppTesting.vue", "Run scripted ADB checks against the live emulator"],
], col_widths=[1.5*inch, 1.6*inch, 3.2*inch]))

story.append(Paragraph("5.2 Service modules", H2))
story.append(bullet_list([
    "<code>services/firebase.js</code> - initializes the Firebase app with hard-coded web config.",
    "<code>services/healthCheck.js</code> - probes Cloud Run /api/health, localhost:8080, GitHub, Firebase RTDB, and a synthesized claude-code skill set.",
    "<code>services/modelConnectionCheck.js</code> - exercises Firebase ML model accessibility, GCP storage, mobile emulator presence.",
]))

story.append(Paragraph("5.3 Backend dependencies the SPA expects", H2))
story.append(section_table([
    ["URL", "Purpose", "Status (audit run)"],
    ["http://localhost:3000", "Admin SPA itself", "OK (docker)"],
    ["http://localhost:3002", "GitNexus agent /api/auto-clone", "OK (docker)"],
    ["http://localhost:3003", "Dev-server APK/build/distribute", "OK (host node)"],
    ["http://localhost:8080", "Local Docker ML API", "OFFLINE - needs gcloud auth"],
    ["Cloud Run /api/health", "Production ML inference", "OK, models lazy"],
    ["bill-sense-aec6b RTDB", "Firebase Realtime Database", "OK"],
    ["billsense.dev-environment.site", "Production dashboard", "PLACEHOLDER - not deployed"],
], col_widths=[2.1*inch, 2.3*inch, 1.9*inch]))
story.append(PageBreak())

# 6. Deployment + CI/CD
story.append(Paragraph("6. Deployment & CI/CD", H1))

story.append(Paragraph("6.1 Current state", H2))
story.append(bullet_list([
    "<b>Mobile:</b> Manual gradle assembleDebug/installDebug from BillSense/. Firebase App Distribution wiring exists in dev-server but is unauthenticated.",
    "<b>Cloud Run API:</b> Image is pushed to <code>asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest</code>. No automation found - assumed manual <code>gcloud run deploy</code>.",
    "<b>Admin panel:</b> No automation before this audit. cPanel was hosting the Phusion Passenger default page because the Vue build was never uploaded.",
    "<b>GitNexus / dev-server:</b> Local-only; no remote deploy needed.",
]))

story.append(Paragraph("6.2 New CI/CD: admin -> cPanel", H2))
story.append(Paragraph(
    "Added <code>.github/workflows/deploy-admin-cpanel.yml</code>. On every push to main "
    "that touches the admin panel, GitHub Actions installs deps, injects VITE_ env vars, "
    "runs <code>npm run build</code>, writes a SPA-aware <code>.htaccess</code> for cPanel/Apache, "
    "and pushes <code>dist/</code> via FTPS using <code>SamKirkland/FTP-Deploy-Action@v4.3.5</code>.",
    BODY))
story.append(Paragraph("Required secrets and variables", H3))
story.append(section_table([
    ["Kind", "Name", "Example"],
    ["Secret", "CPANEL_FTP_HOST", "ftp.dev-environment.site"],
    ["Secret", "CPANEL_FTP_USER", "cpaneluser@dev-environment.site"],
    ["Secret", "CPANEL_FTP_PASS", "your cPanel password"],
    ["Variable", "CPANEL_REMOTE_DIR", "/public_html/billsense/"],
    ["Variable", "VITE_GITNEXUS_REPO", "UserDevAccount1/Billsense-Project"],
    ["Secret", "VITE_GEMINI_API_KEY", "optional - GitNexus AI"],
    ["Secret", "VITE_OPENAI_API_KEY", "optional - GitNexus AI"],
    ["Secret", "VITE_GITHUB_PAT", "optional - GitNexus auto-clone"],
], col_widths=[0.9*inch, 2.0*inch, 3.4*inch]))

story.append(Paragraph("6.3 Why FTPS instead of an MCP", H2))
story.append(Paragraph(
    "There is no production-grade Model Context Protocol server for cPanel today. cPanel exposes UAPI "
    "and WHM API over HTTP Basic Auth - building a custom MCP wrapper is feasible but high-effort and "
    "not the right abstraction for a single deploy job. GitHub Actions with FTPS is the standard "
    "pattern that every cPanel-hosted SPA uses, runs unattended, and integrates with the rest of the "
    "GitHub-native developer flow.",
    BODY))

story.append(Paragraph("6.4 cPanel cutover checklist", H2))
story.append(bullet_list([
    "Stop and remove the Node.js app in cPanel &rarr; <i>Setup Node.js App</i>. Otherwise Phusion Passenger continues to shadow the static files.",
    "Confirm the document root for billsense.dev-environment.site under cPanel File Manager.",
    "Create an FTP account scoped to that document root (cPanel &rarr; FTP Accounts).",
    "Add the secrets and variables above to the GitHub repo.",
    "Push a no-op change to <code>BillSense Admin/admin-panel/</code> or trigger the workflow manually.",
    "Hit https://billsense.dev-environment.site/ - should render the Vue dashboard.",
]))
story.append(PageBreak())

# 7. Security
story.append(Paragraph("7. Security Posture", H1))
story.append(section_table([
    ["Severity", "Finding", "Action"],
    ["High", "CORS allow_origins='*' on FastAPI; any origin can call inference endpoints", "Restrict to known Android package + admin domain"],
    ["High", "Firebase service-account fallback to DummyFirebaseClient silently disables persistence", "Fail loud in production, gate on env"],
    ["High", "VITE_ secrets shipped to browser if set (Gemini, OpenAI, GitHub PAT)", "Proxy through a backend, never ship third-party keys to the SPA"],
    ["Med", "WebSocket client_id is user-supplied and unvalidated", "Allowlist regex, length cap"],
    ["Med", "MAPS_API_KEY stored in local.properties, easy to leak", "Move to gradle secrets plugin or build env"],
    ["Med", "Hard-coded Firebase Web API key in services/firebase.js", "Acceptable for Web SDK if domain restrictions are set in GCP; verify Firebase project authorized domains"],
    ["Low", "Puppeteer runs with --no-sandbox in gitnexus-agent", "Acceptable inside docker; document trust boundary"],
    ["Low", "Numpy JSON encoder serializes unbounded arrays", "Cap response size; reject oversize tensors"],
], col_widths=[0.7*inch, 3.5*inch, 2.2*inch]))
story.append(PageBreak())

# 8. Risks & gaps
story.append(Paragraph("8. Operational Gaps", H1))
story.append(bullet_list([
    "<b>No automated tests in CI.</b> Android Espresso tests exist but no workflow runs them.",
    "<b>No monitoring on Cloud Run.</b> No Cloud Logging filters, no alerting on error rate or cold-start failures.",
    "<b>Models are not versioned.</b> All six .pt files live alongside the image with no manifest of training run / dataset hash.",
    "<b>service_account.json is a placeholder in the repo</b> - any environment that depends on it will silently fall back to DummyFirebaseClient.",
    "<b>No rollback strategy documented</b> for either Cloud Run or cPanel deploys.",
    "<b>Two API base URLs in use</b> (asia-southeast2 and asia-east1 legacy). Risk of split-brain if both stay live.",
]))
story.append(PageBreak())

# 9. Recommendations
story.append(Paragraph("9. Recommendations", H1))

story.append(Paragraph("9.1 Immediate (this week)", H2))
story.append(bullet_list([
    "Land the new cPanel workflow, add the secrets, retire the Phusion Passenger default app, confirm the dashboard renders.",
    "Move VITE_GEMINI_API_KEY / VITE_OPENAI_API_KEY / VITE_GITHUB_PAT behind a tiny proxy (Cloud Functions or a small Express endpoint) so they are never embedded in a public bundle.",
    "Restrict FastAPI CORS to the Android app's deployed origin set and the admin domain.",
    "Decide between the asia-southeast2 and asia-east1 endpoints; retire the loser.",
]))

story.append(Paragraph("9.2 Near term (this month)", H2))
story.append(bullet_list([
    "Add a Cloud Run deploy workflow (separate from cPanel one) that builds the API image with Cloud Build and runs gcloud run deploy on tag.",
    "Wire Espresso and basic Python pytest into a CI workflow gating PRs.",
    "Publish a model card per .pt file: dataset, training run hash, evaluation metrics.",
    "Make the Firebase service-account requirement explicit - fail startup in prod if missing.",
]))

story.append(Paragraph("9.3 Longer term", H2))
story.append(bullet_list([
    "Replace Firebase RTDB with Firestore or Cloud SQL if reporting / analytics needs grow (RTDB is fine for FCM-style fanout, not for analytics).",
    "Move model loading out of process to a TorchServe / Triton sidecar so the API stays warm and scaling is decoupled from inference.",
    "Add structured logging across the stack (Cloud Logging, FCM event log, scan-result audit trail).",
    "Treat the admin panel as a privileged tool: add Firebase Auth gating and IAM-style claims before the dashboard exposes operational controls beyond read-only health.",
]))
story.append(PageBreak())

# 10. Files produced
story.append(Paragraph("10. Artifacts Produced by This Audit", H1))
story.append(section_table([
    ["Path", "Purpose"],
    [".github/workflows/deploy-admin-cpanel.yml", "GitHub Actions workflow that builds and FTPS-deploys the admin panel to cPanel"],
    [".github/workflows/README.md", "cPanel cutover guide and secret setup checklist"],
    ["docker/docker-compose.yml", "Admin context path restored; billsense-api removed from admin depends_on"],
    ["pdf-output/BillSense_Deep_Analysis.pdf", "This document"],
    ["pdf-output/generate_report.py", "ReportLab source for the document - rerunnable"],
], col_widths=[2.6*inch, 3.8*inch]))

story.append(Spacer(1, 0.4*inch))
story.append(Paragraph("End of report.", SMALL))

# build
doc = SimpleDocTemplate(
    OUT, pagesize=LETTER,
    leftMargin=0.75*inch, rightMargin=0.75*inch,
    topMargin=0.8*inch, bottomMargin=0.9*inch,
    title="BillSense - Deep Technical Analysis",
    author="Claude Code",
)
doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
print(f"Wrote {OUT}")
