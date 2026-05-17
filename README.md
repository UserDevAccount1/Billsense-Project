# Thesis Validator

> Feature branch `thesis-validator`. This README documents **only** the
> Thesis Validator page of the BillSense admin dashboard. For the whole
> project see `main` and `PROJECT_RECORD/`.

The Thesis Validator is a Vue 3 page in the BillSense admin dashboard for
managing the thesis document: version control, before/after‑edit comparison,
inline editing, validation scoring, and an AI panel‑defense assistant.

- **Source:** `BillSense Admin/admin-panel/src/views/Thesis.vue`
- **Route:** `/thesis` (sidebar “Thesis Validator”)
- **Data (Firebase RTDB, via the authenticated cPanel proxy `/api/db/*`):**
  - `thesis_versions/<key>` — `{ versionNumber, versionLabel, author,
    changesSummary, date, source?, sections{ <key>:{title,content} },
    beforeSections? }`
  - `thesis_panel_requests/<key>` — `{ title, status, date, panelists[
    { name, comments[ { text, section, status, aiDefense? } ] } ] }`
- **AI:** Gemini via the server‑side proxy `/api/gemini/*` (no key in the
  browser). Bundled foundation: `src/assets/canutab-thesis-foundation.json`.

## Canonical structure (the “foundation”)

Every version is rendered, edited and compared in one fixed order so the
document is stable across versions and imports:

`chapter1_introduction` · `chapter1_theoretical` · `chapter1_problem` ·
`chapter2_methodology` · `chapter3_results` · `chapter4_conclusion` ·
`references`

Legacy/seed versions that used different section keys still render — their
keys are preserved as extra sections after the canonical ones.

## Tabs & features

### Versions
Version selector (shows `vN · label — date — author`), per‑section keyword
search with match counts and highlight, section list, content viewer.

### Document (view + edit)
Read the full document; **Edit document** turns every section into a
textarea. Save with a mode selector:
- **New version (vN)** — writes a new immutable version, then jumps to the
  before/after diff.
- **Update current (vX)** — overwrites the selected version in place (no
  duplicate); keeps number/label/author.

### Compare (Before / After) — two windows
- **Two version windows.** Each has its own version dropdown and a
  **Before / After toggle**.
- **Before/After is per‑version edit history**, *not* two different
  versions: `After` = the version’s current content; `Before` = the
  snapshot captured **just before its last edit** (`beforeSections`). A
  version that was never edited reports “before = after — not edited yet”.
- **“Changes in vX”** sets Window 1 = that version *before* the edit and
  Window 2 = the same version *after* the edit, so you see exactly what you
  changed in that one document.
- **Section filter dropdown** — a button opens a categorised menu (All
  sections + every section, with a “changed” badge) to focus one section.
- **Section navigator** — in Edit mode a sticky chip bar (title + live word
  count) scrolls to and focuses a section.
- Word‑level LCS diff, side‑by‑side or inline, cross‑version search with
  highlight and per‑section/version hit counts, filter chips (All / Changed
  / Unchanged / Search matches).
- **Edit After** — make the Window 2 version editable in place; save as a
  new version or update it (same mode selector as the Document tab).

### Validation
Five checks → a score %: all sections populated, references present,
chapter structure, no placeholder text, document length.

### Panel Comments — AI defense assistant
For each panelist comment, **Generate** calls Gemini with a labelled
`[DEFENSE]/[APP]/[DOC]/[SECTION]/[REVISED]` template (robust parser). The
card shows: a counter‑argument **Defense**, **Enhance app**, **Enhance
document**, the **target section**, and a proposed **revised excerpt**.
- **Generate‑once & persisted** — the result is saved onto the comment
  (`aiDefense`) and re‑hydrated on load; the button becomes **Regenerate**
  (with a confirm) and shows a “saved” badge.
- **Apply to section** — stages a new version with the revision injected
  into the AI‑identified section, with the modal pointed there.
- **Selective batch** — a sticky toolbar with a checkbox per comment,
  *Select all / Only without defense / Clear* and **Generate selected (N)**
  (runs sequentially, persists each).
- A draggable on‑screen **AI reference panel** collects all defenses.

### Versioning utilities
- **New Version** — clone the current version; type a **version number**
  and optional **label/name**; edit a section or import a file.
- **Import** — `.txt/.md/.html` is auto‑segmented into the canonical
  chapters (heading detection + Turnitin‑report cleanup); `.json` with a
  `sections` object is used as‑is; the CANUTAB `.pdf` loads the pre‑cleaned
  bundled foundation. You type the version number/label before saving.
- **Import CANUTAB PDF** — one‑click stage of the bundled cleaned thesis.
- **Delete all versions** — confirm modal → wipes `thesis_versions` so a
  fresh thesis can be imported (panel comments/defenses are kept).

## Local development

```bash
cd "BillSense Admin/admin-panel"
npm install
npm run dev      # Vite dev server (default :3001)
npm run build    # production build → dist/
```

Reads/writes go through the cPanel proxy (`src/services/db.js`,
`src/services/gemini.js`); the browser never holds a Firebase or Gemini key.

## Deployment

Three surfaces kept in parity (see `PROJECT_RECORD/03-DEPLOYMENT.md`):

- **cPanel** — GitHub Actions clean‑slate FTPS deploy on push to `main`.
- **Firebase Hosting** — `firebase deploy --only hosting` (SA auth).
- **Docker `billsense-admin`** — manual `docker compose build --no-cache
  billsense-admin` then `up -d` (not in CI — rebuild after source changes).

Always verify local‑first (Vite + browser) before deploying.
