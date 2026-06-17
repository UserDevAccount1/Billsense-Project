const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
        Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType,
        ShadingType, PageNumber, PageBreak, LevelFormat, ExternalHyperlink,
        TableOfContents } = require('docx');

const border = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const borders = { top: border, bottom: border, left: border, right: border };
const noBorder = { style: BorderStyle.NONE, size: 0 };
const noBorders = { top: noBorder, bottom: noBorder, left: noBorder, right: noBorder };
const cellMargins = { top: 80, bottom: 80, left: 120, right: 120 };

function headerCell(text, width) {
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    shading: { fill: "1A1A2E", type: ShadingType.CLEAR },
    margins: cellMargins,
    children: [new Paragraph({ children: [new TextRun({ text, bold: true, color: "FFFFFF", font: "Arial", size: 20 })] })]
  });
}

function cell(text, width, opts = {}) {
  const fill = opts.fill || undefined;
  const color = opts.color || "333333";
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    shading: fill ? { fill, type: ShadingType.CLEAR } : undefined,
    margins: cellMargins,
    children: [new Paragraph({ children: [new TextRun({ text: String(text), font: "Arial", size: 18, color, bold: opts.bold || false })] })]
  });
}

function statusCell(status, width) {
  const colors = {
    'Completed': { fill: "D4EDDA", color: "155724" },
    'Configured': { fill: "D4EDDA", color: "155724" },
    'Running': { fill: "D4EDDA", color: "155724" },
    'Installed': { fill: "D4EDDA", color: "155724" },
    'Connected': { fill: "D4EDDA", color: "155724" },
    'Critical': { fill: "F8D7DA", color: "721C24" },
    'High': { fill: "FFF3CD", color: "856404" },
    'Medium': { fill: "D1ECF1", color: "0C5460" },
    'Low': { fill: "E2E3E5", color: "383D41" },
    'Pending': { fill: "FFF3CD", color: "856404" },
    'Warning': { fill: "FFF3CD", color: "856404" },
  };
  const c = colors[status] || { fill: "E2E3E5", color: "383D41" };
  return cell(status, width, { fill: c.fill, color: c.color, bold: true });
}

function sectionTitle(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 360, after: 200 },
    children: [new TextRun({ text, font: "Arial", size: 32, bold: true, color: "1A1A2E" })]
  });
}

function subTitle(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, font: "Arial", size: 26, bold: true, color: "2D3748" })]
  });
}

function bodyText(text) {
  return new Paragraph({
    spacing: { after: 120 },
    children: [new TextRun({ text, font: "Arial", size: 20, color: "4A5568" })]
  });
}

// ── Build the document ──

const doc = new Document({
  styles: {
    default: { document: { run: { font: "Arial", size: 20 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: "Arial" },
        paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: "Arial" },
        paragraph: { spacing: { before: 240, after: 120 }, outlineLevel: 1 } },
    ]
  },
  numbering: {
    config: [
      { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "\u2022", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
      { reference: "numbers", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    ]
  },
  sections: [
    // ── COVER PAGE ──
    {
      properties: {
        page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } }
      },
      children: [
        new Paragraph({ spacing: { before: 3000 } }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 },
          children: [new TextRun({ text: "BILLSENSE", font: "Arial", size: 56, bold: true, color: "1A1A2E" })] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 },
          children: [new TextRun({ text: "Project Configuration & Audit Summary", font: "Arial", size: 32, color: "4A5568" })] }),
        new Paragraph({ alignment: AlignmentType.CENTER, border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: "6366F1", space: 1 } },
          spacing: { after: 400 }, children: [] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 },
          children: [new TextRun({ text: "Philippine Currency Counterfeit Detection System", font: "Arial", size: 22, color: "718096", italics: true })] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 },
          children: [new TextRun({ text: "Date: March 22, 2026", font: "Arial", size: 20, color: "718096" })] }),
        new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 },
          children: [new TextRun({ text: "Prepared by: Claude Code AI Agent", font: "Arial", size: 20, color: "718096" })] }),
        new Paragraph({ alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: "Firebase Project: bill-sense-aec6b", font: "Arial", size: 20, color: "718096" })] }),
      ]
    },
    // ── TABLE OF CONTENTS ──
    {
      properties: { page: { size: { width: 12240, height: 15840 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
      headers: {
        default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT,
          children: [new TextRun({ text: "BillSense - Configuration & Audit Report", font: "Arial", size: 16, color: "999999", italics: true })] })] })
      },
      footers: {
        default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: "Page ", font: "Arial", size: 16, color: "999999" }), new TextRun({ children: [PageNumber.CURRENT], font: "Arial", size: 16, color: "999999" })] })] })
      },
      children: [
        new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: "Table of Contents", font: "Arial", size: 32, bold: true, color: "1A1A2E" })] }),
        new TableOfContents("Table of Contents", { hyperlink: true, headingStyleRange: "1-2" }),
        new Paragraph({ children: [new PageBreak()] }),

        // ── 1. ENVIRONMENT SETUP ──
        sectionTitle("1. Environment Setup Summary"),
        bodyText("Complete development environment configured for BillSense Android app, admin panel, and backend services."),

        subTitle("1.1 Development Tools Installed"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [3200, 3200, 2960],
          rows: [
            new TableRow({ children: [headerCell("Tool", 3200), headerCell("Version / Path", 3200), headerCell("Status", 2960)] }),
            new TableRow({ children: [cell("JDK", 3200), cell("Microsoft JDK 17.0.18.8", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("Android SDK", 3200), cell("D:\\Software\\android studio\\Sdk", 3200), statusCell("Configured", 2960)] }),
            new TableRow({ children: [cell("Android Studio", 3200), cell("Panda 2025.3.2 (D:\\Software\\android studio)", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("Node.js", 3200), cell("v22.x (npm included)", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("Firebase CLI", 3200), cell("v15.11.0+ (global)", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("Docker Desktop", 3200), cell("v29.2.1", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("Git", 3200), cell("Latest (VS Code integrated)", 3200), statusCell("Installed", 2960)] }),
            new TableRow({ children: [cell("VS Code", 3200), cell("Primary IDE", 3200), statusCell("Configured", 2960)] }),
          ]
        }),

        subTitle("1.2 Android SDK Components"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [4680, 4680],
          rows: [
            new TableRow({ children: [headerCell("Component", 4680), headerCell("Details", 4680)] }),
            new TableRow({ children: [cell("SDK Location", 4680), cell("D:\\Software\\android studio\\Sdk", 4680)] }),
            new TableRow({ children: [cell("Platform Tools", 4680), cell("adb, fastboot", 4680)] }),
            new TableRow({ children: [cell("Build Tools", 4680), cell("35.0.0, 36.0.0", 4680)] }),
            new TableRow({ children: [cell("Platforms", 4680), cell("API 33 (Android 13), API 35 (Android 15), API 36 (Android 16)", 4680)] }),
            new TableRow({ children: [cell("System Images", 4680), cell("API 33 google_apis_playstore/x86_64, API 36 default/x86_64", 4680)] }),
            new TableRow({ children: [cell("Emulator AVD", 4680), cell("BillSense_Test (Pixel 6, API 33)", 4680)] }),
            new TableRow({ children: [cell("Command-line Tools", 4680), cell("sdkmanager, avdmanager", 4680)] }),
          ]
        }),

        subTitle("1.3 Environment Variables"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [3000, 6360],
          rows: [
            new TableRow({ children: [headerCell("Variable", 3000), headerCell("Value", 6360)] }),
            new TableRow({ children: [cell("ANDROID_HOME", 3000), cell("D:\\Software\\android studio\\Sdk", 6360)] }),
            new TableRow({ children: [cell("ANDROID_SDK_ROOT", 3000), cell("D:\\Software\\android studio\\Sdk", 6360)] }),
            new TableRow({ children: [cell("JAVA_HOME", 3000), cell("C:\\Program Files\\Microsoft\\jdk-17.0.18.8-hotspot", 6360)] }),
            new TableRow({ children: [cell("PATH additions", 3000), cell("Sdk\\platform-tools, Sdk\\emulator, Sdk\\cmdline-tools\\latest\\bin", 6360)] }),
          ]
        }),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 2. MCP CONFIGURATION ──
        sectionTitle("2. MCP Server Configuration"),
        bodyText("Model Context Protocol servers configured for Claude Code and Android Studio integration."),

        subTitle("2.1 Claude Code MCP Servers"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [2400, 3000, 3960],
          rows: [
            new TableRow({ children: [headerCell("Server", 2400), headerCell("Package", 3000), headerCell("Purpose", 3960)] }),
            new TableRow({ children: [cell("mobile", 2400), cell("claude-in-mobile", 3000), cell("Mobile device automation via ADB", 3960)] }),
            new TableRow({ children: [cell("android", 2400), cell("android-mcp-server", 3000), cell("Android emulator/device control", 3960)] }),
            new TableRow({ children: [cell("cpanel-mcp", 2400), cell("cpanel-mcp (uvx)", 3000), cell("cPanel hosting management for billsense.dev-environment.site", 3960)] }),
            new TableRow({ children: [cell("firebase", 2400), cell("(built-in)", 3000), cell("Firebase Realtime DB, Storage, Auth", 3960)] }),
            new TableRow({ children: [cell("supabase", 2400), cell("(built-in)", 3000), cell("Supabase database management", 3960)] }),
            new TableRow({ children: [cell("vercel", 2400), cell("(built-in)", 3000), cell("Vercel deployment management", 3960)] }),
            new TableRow({ children: [cell("cloudflare", 2400), cell("(built-in)", 3000), cell("Cloudflare Workers, D1, KV, R2", 3960)] }),
          ]
        }),

        subTitle("2.2 Android Studio MCP"),
        bodyText("MCP config at: C:\\Users\\Future26\\AppData\\Roaming\\Google\\AndroidStudio2025.3.2\\mcp.json"),
        bodyText("Enable via: File > Settings > Tools > AI > MCP Servers > Enable MCP Servers"),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 3. SERVICES STATUS ──
        sectionTitle("3. Running Services"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [2800, 2000, 2400, 2160],
          rows: [
            new TableRow({ children: [headerCell("Service", 2800), headerCell("Port", 2000), headerCell("Type", 2400), headerCell("Status", 2160)] }),
            new TableRow({ children: [cell("BillSense Admin Panel", 2800), cell("3000", 2000), cell("Docker (nginx)", 2400), statusCell("Running", 2160)] }),
            new TableRow({ children: [cell("GitNexus Agent", 2800), cell("3002", 2000), cell("Docker (node)", 2400), statusCell("Running", 2160)] }),
            new TableRow({ children: [cell("Admin Dev Server", 2800), cell("3001", 2000), cell("Vite dev", 2400), statusCell("Running", 2160)] }),
            new TableRow({ children: [cell("Dev Server API", 2800), cell("3003", 2000), cell("Node HTTP", 2400), statusCell("Configured", 2160)] }),
            new TableRow({ children: [cell("BillSense API (GCP)", 2800), cell("443", 2000), cell("Cloud Run", 2400), statusCell("Running", 2160)] }),
            new TableRow({ children: [cell("Firebase RTDB", 2800), cell("443", 2000), cell("Cloud", 2400), statusCell("Connected", 2160)] }),
          ]
        }),

        subTitle("3.1 Firebase Configuration"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [3500, 5860],
          rows: [
            new TableRow({ children: [headerCell("Property", 3500), headerCell("Value", 5860)] }),
            new TableRow({ children: [cell("Project ID", 3500), cell("bill-sense-aec6b", 5860)] }),
            new TableRow({ children: [cell("Auth Account", 3500), cell("userdeveloper554@gmail.com", 5860)] }),
            new TableRow({ children: [cell("google-services.json", 3500), cell("Downloaded and placed in BillSense/app/", 5860)] }),
            new TableRow({ children: [cell("Android Apps", 3500), cell("com.app.billsense, com.admin.billsense", 5860)] }),
            new TableRow({ children: [cell("Services Used", 3500), cell("Realtime Database, Storage, Cloud Messaging, App Distribution", 5860)] }),
          ]
        }),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 4. MOBILE APP AUDIT ──
        sectionTitle("4. Mobile App Audit Results"),
        bodyText("Comprehensive code audit of 27 Activities across the BillSense Android app. Found 33 issues across 4 severity levels."),

        subTitle("4.1 Severity Summary"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [1800, 1200, 6360],
          rows: [
            new TableRow({ children: [headerCell("Severity", 1800), headerCell("Count", 1200), headerCell("Key Themes", 6360)] }),
            new TableRow({ children: [statusCell("Critical", 1800), cell("6", 1200), cell("Plaintext passwords, hardcoded secrets, service account in APK, StrictMode bypass, cleartext traffic", 6360)] }),
            new TableRow({ children: [statusCell("High", 1800), cell("6", 1200), cell("No Firebase Auth, memory leaks from persistent listeners, disabled minification, verification code exposure", 6360)] }),
            new TableRow({ children: [statusCell("Medium", 1800), cell("10", 1200), cell("Excessive Firebase reads, notifyDataSetChanged in loops, artificial delays, logging secrets", 6360)] }),
            new TableRow({ children: [statusCell("Low", 1800), cell("11", 1200), cell("NPE risks, unused vars, copy-paste text, deprecated APIs, empty handlers", 6360)] }),
          ]
        }),

        subTitle("4.2 Critical Issues (Must Fix)"),
        ...[
          ["#1 Plaintext Password Storage", "FBUtils.java:144, RegisterActivity.java:120", "Passwords stored as plaintext in Firebase RTDB. Direct string comparison for login.", "Hash with bcrypt before storing. Migrate to Firebase Authentication."],
          ["#2 Hardcoded Email Credentials", "EmailSender.java:10", "Gmail app password hardcoded in source code.", "Move to BuildConfig via local.properties or use backend email service."],
          ["#3 Hardcoded API Token", "ChatBotActivity.java:48-49", "Abacus AI deployment token visible in source.", "Move to BuildConfig or fetch from secure backend."],
          ["#4 Service Account in APK", "FcmAuthHelper.java:29", "Firebase service account JSON bundled in APK raw resources.", "Move FCM sending to backend server. Never ship service account in client app."],
          ["#5 StrictMode.permitAll()", "CasesActivity.java:59-61", "Disables all thread policy checks, hiding network-on-main-thread.", "Remove StrictMode override. Move bitmap processing to background thread."],
          ["#6 Cleartext Traffic", "AndroidManifest.xml:27", "usesCleartextTraffic=true allows unencrypted HTTP.", "Use network security config. Only allow cleartext for debug localhost."],
        ].map(([title, file, issue, fix]) => [
          new Paragraph({ spacing: { before: 200, after: 60 }, children: [new TextRun({ text: title, font: "Arial", size: 22, bold: true, color: "721C24" })] }),
          new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: "File: " + file, font: "Arial", size: 18, color: "718096", italics: true })] }),
          new Paragraph({ spacing: { after: 40 }, numbering: { reference: "bullets", level: 0 }, children: [new TextRun({ text: "Issue: " + issue, font: "Arial", size: 18 })] }),
          new Paragraph({ spacing: { after: 80 }, numbering: { reference: "bullets", level: 0 }, children: [new TextRun({ text: "Fix: " + fix, font: "Arial", size: 18, color: "155724" })] }),
        ]).flat(),

        new Paragraph({ children: [new PageBreak()] }),

        subTitle("4.3 High Severity Issues"),
        ...[
          ["#7 No Firebase Authentication", "Custom auth on RTDB with email/password string comparison. RTDB rules likely allow broad read access."],
          ["#8 Memory Leaks", "addValueEventListener used without removeEventListener in FBUtils, ChatBotActivity. Leaks Activity instances."],
          ["#9 Verification Code in Push Notification", "Verification code sent via push notification body, defeating email verification purpose."],
          ["#10 ProGuard Disabled", "isMinifyEnabled=false in release build. APK ships without code obfuscation."],
          ["#11 Deprecated onActivityResult", "RegisterActivity uses deprecated pattern instead of ActivityResultLauncher."],
          ["#12 assert for Null Checks", "assert statements disabled on Android. Provides zero null protection in production."],
        ].map(([title, desc]) => [
          new Paragraph({ spacing: { before: 120, after: 40 }, numbering: { reference: "numbers", level: 0 },
            children: [new TextRun({ text: title, font: "Arial", size: 20, bold: true })] }),
          bodyText(desc),
        ]).flat(),

        subTitle("4.4 Performance Issues (Login Delay Root Causes)"),
        bodyText("The login delay is caused by multiple factors:"),
        ...[
          "HomeActivity fires 4 parallel Firebase reads on every launch (getAllDetectionData, getScanReportData, getUserData, getAllNotifications)",
          "getAllNotifications reads ALL notifications then filters client-side instead of using server-side queries",
          "All listeners use addValueEventListener (persistent) instead of addListenerForSingleValueEvent",
          "notifyDataSetChanged() called inside loops in HomeActivity, SupportHistoryActivity, VotingActivity",
          "ProfileActivity logout has artificial 2.5-second delay via Handler.postDelayed",
          "New OkHttpClient created per CurrencyApiService instance instead of sharing singleton",
          "Retrofit logging at BODY level logs full payloads including tokens",
        ].map(text => new Paragraph({ spacing: { after: 60 }, numbering: { reference: "bullets", level: 0 },
          children: [new TextRun({ text, font: "Arial", size: 18, color: "4A5568" })] })),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 5. ACTIVITIES INVENTORY ──
        sectionTitle("5. Mobile App Activities (27 Total)"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [600, 3200, 5560],
          rows: [
            new TableRow({ children: [headerCell("#", 600), headerCell("Activity", 3200), headerCell("Purpose", 5560)] }),
            ...([
              ["1", "MainActivity", "Launcher/splash - checks login, requests permissions, FCM tokens"],
              ["2", "LoginActivity", "Email/password login via Firebase RTDB"],
              ["3", "RegisterActivity", "User registration with ID photo upload"],
              ["4", "HomeActivity", "Main dashboard - scan reports, notifications, navigation"],
              ["5", "ProfileActivity", "View/edit profile, change password, logout"],
              ["6", "DetectionActivity", "Step-by-step detection process viewer"],
              ["7", "ChatBotActivity", "Abacus AI chatbot with Firebase chat history"],
              ["8", "VotingActivity", "Community voting on bill authenticity"],
              ["9", "AddVotingPostActivity", "Create/edit voting posts with media"],
              ["10", "EducationalContentActivity", "Tutorials, trivia, FAQ tabs"],
              ["11", "EvidenceActivity", "Write cases, view history, compare bills"],
              ["12", "CasesActivity", "Google Maps with case markers"],
              ["13", "SupportActivity", "Customer support ticket management"],
              ["14", "SupportChatActivity", "Chat within a support ticket"],
              ["15", "SupportHistoryActivity", "Archived support tickets"],
              ["16", "DispatchActivity", "Agent dispatch animation"],
              ["17", "CompareBillActivity", "Side-by-side bill comparison (landscape)"],
              ["18", "SearchActivity", "Global search across Firebase collections"],
              ["19", "DetailsActivity", "Generic item detail viewer"],
              ["20", "ViewScanReportActivity", "Scan report fragment host"],
              ["21", "StandardScanActivity", "Real-time standard scan (WebSocket + CameraX)"],
              ["22", "MultiScanActivity", "Multi-angle scan"],
              ["23", "VideoScanActivity", "Video scan"],
              ["24", "StandardPostScanActivity", "Post-scan results for standard"],
              ["25", "MultiPostScanActivity", "Post-scan results for multi"],
              ["26", "VideoPostScanActivity", "Post-scan results for video"],
              ["27", "MapsActivity", "Map fragment"],
            ]).map(([n, name, purpose]) =>
              new TableRow({ children: [cell(n, 600), cell(name, 3200, { bold: true }), cell(purpose, 5560)] })
            )
          ]
        }),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 6. WHAT NEEDS TO RUN ──
        sectionTitle("6. What Needs to Run - Complete Checklist"),

        subTitle("6.1 To Run the Mobile App"),
        ...[
          "Android SDK at D:\\Software\\android studio\\Sdk (ANDROID_HOME set)",
          "JDK 17 at C:\\Program Files\\Microsoft\\jdk-17.0.18.8-hotspot",
          "google-services.json in BillSense/app/ (downloaded from Firebase CLI)",
          "service_account.json in BillSense/app/src/main/res/raw/ (placeholder created - needs real file for FCM)",
          "MAPS_API_KEY=PLACEHOLDER_KEY in local.properties (needs real key for Maps features)",
          "Build: cd BillSense && ./gradlew assembleUserDebug",
          "Install: adb install -r app/build/outputs/apk/user/debug/app-user-debug.apk",
          "Emulator: emulator -avd BillSense_Test -gpu auto",
        ].map(text => new Paragraph({ spacing: { after: 60 }, numbering: { reference: "bullets", level: 0 },
          children: [new TextRun({ text, font: "Arial", size: 18 })] })),

        subTitle("6.2 To Run the Admin Panel"),
        ...[
          "Docker Desktop running",
          "cd docker && docker-compose up -d --build billsense-admin",
          "Admin panel at http://localhost:3000 (Docker) or http://localhost:3001 (Vite dev)",
          "Dev server: cd 'BillSense Admin/admin-panel' && node dev-server.mjs (port 3003)",
          "Login: admin / admin123",
        ].map(text => new Paragraph({ spacing: { after: 60 }, numbering: { reference: "bullets", level: 0 },
          children: [new TextRun({ text, font: "Arial", size: 18 })] })),

        subTitle("6.3 Firebase Requirements"),
        ...[
          "Firebase CLI login: firebase login (userdeveloper554@gmail.com)",
          "Project: bill-sense-aec6b",
          "google-services.json must be present in BillSense/app/",
          "Real service_account.json needed for FCM push notifications",
          "Firebase Realtime Database accessible at bill-sense-aec6b-default-rtdb",
        ].map(text => new Paragraph({ spacing: { after: 60 }, numbering: { reference: "bullets", level: 0 },
          children: [new TextRun({ text, font: "Arial", size: 18 })] })),

        subTitle("6.4 Known Placeholder Files (Need Real Versions)"),
        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [4000, 3200, 2160],
          rows: [
            new TableRow({ children: [headerCell("File", 4000), headerCell("Location", 3200), headerCell("Status", 2160)] }),
            new TableRow({ children: [cell("google-services.json", 4000), cell("BillSense/app/", 3200), statusCell("Completed", 2160)] }),
            new TableRow({ children: [cell("service_account.json", 4000), cell("BillSense/app/src/main/res/raw/", 3200), statusCell("Pending", 2160)] }),
            new TableRow({ children: [cell("MAPS_API_KEY", 4000), cell("BillSense/local.properties", 3200), statusCell("Pending", 2160)] }),
          ]
        }),

        new Paragraph({ children: [new PageBreak()] }),

        // ── 7. RECOMMENDED FIXES ──
        sectionTitle("7. Recommended Fix Priority"),
        bodyText("Ordered by impact and urgency for production readiness:"),

        new Table({
          width: { size: 9360, type: WidthType.DXA },
          columnWidths: [600, 1400, 4400, 2960],
          rows: [
            new TableRow({ children: [headerCell("#", 600), headerCell("Priority", 1400), headerCell("Action", 4400), headerCell("Impact", 2960)] }),
            new TableRow({ children: [cell("1", 600), statusCell("Critical", 1400), cell("Migrate to Firebase Authentication (eliminates plaintext passwords)", 4400), cell("Security + login speed", 2960)] }),
            new TableRow({ children: [cell("2", 600), statusCell("Critical", 1400), cell("Move all secrets to BuildConfig / backend (email, API tokens)", 4400), cell("Security", 2960)] }),
            new TableRow({ children: [cell("3", 600), statusCell("Critical", 1400), cell("Remove service_account.json from APK, move FCM to backend", 4400), cell("Security", 2960)] }),
            new TableRow({ children: [cell("4", 600), statusCell("High", 1400), cell("Fix memory leaks: remove Firebase listeners in onDestroy()", 4400), cell("Stability + battery", 2960)] }),
            new TableRow({ children: [cell("5", 600), statusCell("High", 1400), cell("Enable ProGuard/R8 minification for release builds", 4400), cell("Security + APK size", 2960)] }),
            new TableRow({ children: [cell("6", 600), statusCell("High", 1400), cell("Use addListenerForSingleValueEvent where real-time not needed", 4400), cell("Login speed", 2960)] }),
            new TableRow({ children: [cell("7", 600), statusCell("Medium", 1400), cell("Move notifyDataSetChanged() outside loops", 4400), cell("UI performance", 2960)] }),
            new TableRow({ children: [cell("8", 600), statusCell("Medium", 1400), cell("Use server-side Firebase queries instead of client-side filtering", 4400), cell("Data transfer + speed", 2960)] }),
            new TableRow({ children: [cell("9", 600), statusCell("Medium", 1400), cell("Remove artificial 2.5s logout delay", 4400), cell("UX", 2960)] }),
            new TableRow({ children: [cell("10", 600), statusCell("Medium", 1400), cell("Share singleton OkHttpClient across scan services", 4400), cell("Memory + connections", 2960)] }),
          ]
        }),
      ]
    }
  ]
});

Packer.toBuffer(doc).then(buffer => {
  const outPath = "D:\\Github\\Billsense-Project\\BillSense_Configuration_Audit_Report.docx";
  fs.writeFileSync(outPath, buffer);
  console.log("Report generated: " + outPath);
});
