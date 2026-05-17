<template>
  <div>
    <div class="page-header">
      <h1>Thesis Validator</h1>
      <p>Version control · editable document · before/after compare · AI panel-defense · validation scoring</p>
    </div>

    <div class="content">
      <div class="tabbar">
        <button :class="{ on: tab==='doc' }" @click="tab='doc'">
          <span class="material-icons">description</span> Versions <em>{{ versions.length }}</em>
        </button>
        <button :class="{ on: tab==='full' }" @click="tab='full'">
          <span class="material-icons">menu_book</span> Document {{ editing ? '(editing)' : '' }}
        </button>
        <button :class="{ on: tab==='compare' }" @click="tab='compare'">
          <span class="material-icons">compare_arrows</span> Compare (Before / After)
        </button>
        <button :class="{ on: tab==='valid' }" @click="tab='valid'">
          <span class="material-icons">fact_check</span> Validation <em>{{ score }}%</em>
        </button>
        <button :class="{ on: tab==='panel' }" @click="tab='panel'">
          <span class="material-icons">rate_review</span> Panel Comments <em>{{ openComments }}/{{ totalComments }}</em>
        </button>
        <button class="newv" @click="startNewVersion" :disabled="!curVersion" title="Create a new version from the current one">
          <span class="material-icons">add</span> New Version
        </button>
        <button class="impv" @click="importFoundation" :disabled="fImporting"
                title="Import CANUTAB-THESIS (2) (1).pdf as a new version">
          <span class="material-icons">{{ fImporting ? 'hourglass_top' : 'upload_file' }}</span>
          {{ fImporting ? 'Importing…' : 'Import CANUTAB PDF' }}
        </button>
        <button class="delv" @click="confirmDel=true" :disabled="!versions.length||delBusy"
                title="Delete ALL thesis versions from the database">
          <span class="material-icons">{{ delBusy ? 'hourglass_top' : 'delete_forever' }}</span>
          {{ delBusy ? 'Deleting…' : 'Delete all versions' }}
        </button>
      </div>

      <div v-if="loading" class="state">Loading thesis data…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>

      <!-- ===== VERSIONS ===== -->
      <div v-else-if="tab==='doc'" class="doc">
        <div class="doc-side">
          <label class="lbl">Version</label>
          <select v-model="selVer" class="sel">
            <option v-for="v in versions" :key="v._key" :value="v._key">
              {{ vlabel(v) }}
            </option>
          </select>
          <p class="chg" v-if="curVersion">{{ curVersion.changesSummary }}</p>
          <label class="lbl">Search document</label>
          <input v-model="q" class="sel" placeholder="keyword…" />
          <label class="lbl">Sections{{ q ? ' (matches)' : '' }}</label>
          <ul class="seclist">
            <li v-for="key in shownSections" :key="key"
                :class="{ on: selSec===key }" @click="selSec=key">
              {{ sectionTitle(key) }}
              <em v-if="q">{{ matchCount(key) }}</em>
            </li>
          </ul>
        </div>
        <div class="doc-main">
          <template v-if="curSection">
            <h3>{{ sectionTitle(selSec) }}</h3>
            <div class="secmeta">{{ wordCount(curSection.content) }} words · v{{ curVersion.versionNumber }} · {{ curVersion.author }}</div>
            <div class="seccontent" v-html="highlight(stripHtml(curSection.content))"></div>
          </template>
          <div v-else class="state">Select a section.</div>
        </div>
      </div>

      <!-- ===== DOCUMENT (view + edit) ===== -->
      <div v-else-if="tab==='full'" class="full">
        <div class="full-bar">
          <select v-model="selVer" class="sel sm" :disabled="editing">
            <option v-for="v in versions" :key="v._key" :value="v._key">{{ vlabel(v) }}</option>
          </select>
          <input v-model="q" class="sel sm" placeholder="search whole document…" />
          <button v-if="!editing" class="edit-btn" @click="startEdit" :disabled="!curVersion">
            <span class="material-icons">edit</span> Edit document
          </button>
          <template v-else>
            <button class="edit-btn save" @click="saveEditAsVersion" :disabled="editSaving">
              <span class="material-icons">{{ editSaving ? 'hourglass_top' : 'save' }}</span>
              {{ editSaving ? 'Saving…' : 'Save as v'+nextVersionNumber }}
            </button>
            <button class="edit-btn ghost" @click="cancelEdit" :disabled="editSaving">
              <span class="material-icons">close</span> Cancel
            </button>
            <span class="edit-note">Editing a working copy — “Save as new version” writes a new immutable version, then shows the diff.</span>
          </template>
        </div>
        <div class="paper" :class="{ editing }">
          <h2 class="paper-title">BillSense — Thesis Document
            (v{{ editing ? nextVersionNumber : (curVersion && curVersion.versionNumber) }}{{ editing ? ' draft' : '' }})</h2>
          <div v-for="key in orderedSectionKeys" :key="key" class="paper-sec">
            <h3>
              {{ sectionTitle(key) }}
              <small v-if="editing" class="wc">{{ wordCount(editDoc[key]) }} words</small>
            </h3>
            <textarea v-if="editing" v-model="editDoc[key]" class="paper-edit" rows="10"
                      :placeholder="'Write '+sectionTitle(key)+'…'"></textarea>
            <div v-else class="paper-body"
                 v-html="highlight(stripHtml(curVersion.sections[key] && curVersion.sections[key].content))"></div>
          </div>
        </div>
      </div>

      <!-- ===== COMPARE (before / after) ===== -->
      <div v-else-if="tab==='compare'" class="cmp">
        <div class="cmp-bar">
          <div class="cmp-pick">
            <label class="lbl">Before</label>
            <select v-model="cmpA" class="sel sm">
              <option v-for="v in versions" :key="v._key" :value="v._key">
                {{ vlabel(v) }}
              </option>
            </select>
          </div>
          <span class="material-icons cmp-arrow">arrow_forward</span>
          <div class="cmp-pick">
            <label class="lbl">After</label>
            <select v-model="cmpB" class="sel sm">
              <option v-for="v in versions" :key="v._key" :value="v._key">
                {{ vlabel(v) }}
              </option>
            </select>
          </div>
          <div class="cmp-legend">
            <button class="cmp-mode" @click="sideBySide=!sideBySide">
              <span class="material-icons">{{ sideBySide ? 'view_agenda' : 'vertical_split' }}</span>
              {{ sideBySide ? 'Inline view' : 'Side-by-side' }}
            </button>
            <span class="lg add">added</span><span class="lg del">removed</span>
            <span class="cmp-sum">{{ changedSections.length }} of {{ allCmpKeys.length }} sections changed</span>
          </div>
        </div>

        <!-- search + filter controls across BOTH versions -->
        <div class="cmp-tools">
          <div class="cmp-search">
            <span class="material-icons">search</span>
            <input v-model="cmpQ" placeholder="Search context across BOTH versions (before & after)…" />
            <button v-if="cmpQ" class="ico" @click="cmpQ=''"><span class="material-icons">close</span></button>
          </div>
          <div class="cmp-filters">
            <button v-for="f in cmpFilters" :key="f.v" class="fchip" :class="{ on: cmpFilter===f.v }"
                    @click="cmpFilter=f.v">{{ f.label }}</button>
          </div>
          <span class="cmp-qsum" v-if="cmpQ">
            “{{ cmpQ }}” — {{ cmpQTotals.before }} in before · {{ cmpQTotals.after }} in after ·
            {{ cmpQSectionHits }} section(s)
          </span>
        </div>

        <div v-if="cmpA === cmpB" class="state">Pick two different versions to compare.</div>
        <div v-else-if="!visibleCmpKeys.length" class="state">No sections match this filter/search.</div>
        <div v-else class="cmp-secs">
          <div v-for="k in visibleCmpKeys" :key="k" class="cmp-sec" :class="{ unchanged: !isChanged(k) }">
            <div class="cmp-sec-head" @click="toggleSec(k)">
              <span class="material-icons">{{ openCmp[k] ? 'expand_more' : 'chevron_right' }}</span>
              <strong>{{ cmpTitle(k) }}</strong>
              <span v-if="cmpQ && cmpKeyHits(k)" class="cmp-hit">{{ cmpKeyHits(k) }} match</span>
              <span class="cmp-tag" :class="isChanged(k) ? 'chg' : 'same'">
                {{ isChanged(k) ? changeStat(k) : 'no change' }}
              </span>
            </div>
            <template v-if="openCmp[k]">
              <div v-if="sideBySide" class="cmp-split">
                <div class="cmp-pane">
                  <div class="cmp-pane-h del">Before — v{{ cmpVerA && cmpVerA.versionNumber }}</div>
                  <div class="cmp-diff" v-html="sidePane(k,'before')"></div>
                </div>
                <div class="cmp-pane">
                  <div class="cmp-pane-h add">After — v{{ cmpVerB && cmpVerB.versionNumber }}</div>
                  <div class="cmp-diff" v-html="sidePane(k,'after')"></div>
                </div>
              </div>
              <div v-else class="cmp-diff" v-html="diffHtml(k)"></div>
            </template>
          </div>
        </div>
      </div>

      <!-- ===== VALIDATION ===== -->
      <div v-else-if="tab==='valid'" class="valid">
        <div class="scorebox" :class="scoreClass">
          <div class="scoreN">{{ score }}%</div>
          <div class="scoreL">{{ scoreLabel }} · v{{ curVersion && curVersion.versionNumber }}</div>
        </div>
        <div class="checks">
          <div v-for="c in validation" :key="c.key" class="chk" :class="c.pass ? 'ok' : (c.warn ? 'warn' : 'bad')">
            <span class="material-icons">{{ c.pass ? 'check_circle' : (c.warn ? 'warning' : 'cancel') }}</span>
            <div>
              <strong>{{ c.label }}</strong>
              <p>{{ c.detail }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== PANEL COMMENTS ===== -->
      <div v-else class="panels">
        <div v-if="!panelRequests.length" class="state">No panel requests.</div>
        <div v-for="req in panelRequests" :key="req._key" class="preq">
          <div class="preq-head">
            <div><strong>{{ req.title || '(untitled request)' }}</strong>
              <span class="src" v-if="req.source">· {{ req.source }}</span></div>
            <select :value="req.status||'pending'" class="sel sm" @change="setReqStatus(req,$event.target.value)">
              <option>pending</option><option>in-review</option><option>addressed</option><option>closed</option>
            </select>
          </div>
          <div class="preq-meta">
            <span><span class="material-icons">event</span>{{ (req.date||'').slice(0,10) }}</span>
            <span><span class="material-icons">groups</span>{{ (req.panelists||[]).length }} panelists</span>
            <span><span class="material-icons">comment</span>{{ reqStats(req) }}</span>
          </div>
          <div v-for="(p,pi) in (req.panelists||[])" :key="pi" class="panelist">
            <div class="pl-name"><span class="material-icons">person</span>{{ p.name || ('Panelist '+(pi+1)) }}</div>
            <div v-if="!(p.comments||[]).length" class="nocmt">No comments.</div>
            <div v-for="(c,ci) in (p.comments||[])" :key="c.id||ci" class="cmt">
              <span class="sec-tag">{{ sectionTitle(c.section) }}</span>
              <div class="cmt-main">
                <span class="cmt-text">{{ c.text }}</span>

                <!-- AI defense card -->
                <div v-if="defKey(req._key,pi,ci)" class="ai-def">
                  <div class="ai-def-h">
                    <span class="material-icons">shield</span> AI Defense — counters &amp; enhancement plan
                    <span v-if="aiDef[defKey(req._key,pi,ci)].savedTs" class="saved-tag">
                      <span class="material-icons">cloud_done</span> saved
                    </span>
                  </div>
                  <div class="ai-def-row"><b>Defense</b><p>{{ aiDef[defKey(req._key,pi,ci)].defense }}</p></div>
                  <div class="ai-def-row"><b>Enhance app</b><p>{{ aiDef[defKey(req._key,pi,ci)].app }}</p></div>
                  <div class="ai-def-row"><b>Enhance document</b><p>{{ aiDef[defKey(req._key,pi,ci)].doc }}</p></div>
                  <div class="ai-def-row">
                    <b>Apply to</b>
                    <p>
                      <span class="sec-point">{{ sectionTitle(aiDef[defKey(req._key,pi,ci)].section) }}</span>
                      <button class="apply-btn"
                              @click="applyDefense(req._key,pi,ci,c)">
                        <span class="material-icons">auto_fix_high</span> Apply to section
                      </button>
                    </p>
                  </div>
                  <details v-if="aiDef[defKey(req._key,pi,ci)].revised">
                    <summary>Proposed revised excerpt</summary>
                    <div class="ai-def-rev">{{ aiDef[defKey(req._key,pi,ci)].revised }}</div>
                  </details>
                </div>
              </div>
              <div class="cmt-act">
                <button class="ico gen" :class="{ regen: defKey(req._key,pi,ci) }"
                        :disabled="aiBusy"
                        @click="onGenerateClick(req,pi,ci,c)"
                        :title="!aiOk ? 'AI not configured' : (defKey(req._key,pi,ci) ? 'Already generated & saved — click to regenerate' : 'Generate AI defense')">
                  <span class="material-icons">{{ aiBusy && aiBusyKey===(req._key+':'+pi+':'+ci) ? 'hourglass_top' : (defKey(req._key,pi,ci) ? 'refresh' : 'auto_awesome') }}</span>
                  {{ defKey(req._key,pi,ci) ? 'Regenerate' : 'Generate' }}
                </button>
                <select :value="c.status||'pending'" class="sel xs" :class="cClass(c.status)"
                        @change="setCommentStatus(req,pi,ci,$event.target.value)">
                  <option>pending</option><option>addressed</option><option>resolved</option><option>rejected</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete-all confirm modal -->
    <div v-if="confirmDel" class="modal" @click.self="confirmDel=false">
      <div class="modal-box sm">
        <div class="modal-head">
          <strong>Delete ALL thesis versions?</strong>
          <button class="ico" @click="confirmDel=false"><span class="material-icons">close</span></button>
        </div>
        <div class="nv-body">
          <p class="warn-txt">
            This permanently removes <b>all {{ versions.length }} version(s)</b>
            from <code>thesis_versions</code> in the database. Panel comments and
            their saved AI defenses are kept. This cannot be undone — afterwards
            you can import a fresh thesis or create a new version.
          </p>
          <div class="del-actions">
            <button class="edit-btn ghost" @click="confirmDel=false" :disabled="delBusy">Cancel</button>
            <button class="del-go" @click="deleteAllVersions" :disabled="delBusy">
              <span class="material-icons">{{ delBusy ? 'hourglass_top' : 'delete_forever' }}</span>
              {{ delBusy ? 'Deleting…' : 'Yes, delete all' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- New Version modal -->
    <div v-if="nv" class="modal" @click.self="nv=null">
      <div class="modal-box">
        <div class="modal-head">
          <strong>{{ nv.fromImport ? 'Import as new version' : 'New Version' }}
            (v{{ nv.versionNumber }}{{ nv.versionLabel ? ' · '+nv.versionLabel : '' }})</strong>
          <button class="ico" @click="nv=null"><span class="material-icons">close</span></button>
        </div>
        <div class="nv-body">
          <div class="nv-verrow">
            <div class="nv-vn">
              <label class="lbl">Version number</label>
              <input v-model.number="nv.versionNumber" type="number" min="1" step="1"
                     class="sel" placeholder="e.g. 4" />
            </div>
            <div class="nv-vl">
              <label class="lbl">Version label / name <em>(optional)</em></label>
              <input v-model="nv.versionLabel" class="sel"
                     placeholder='e.g. "Post-panel revision", "Final draft"' />
            </div>
          </div>
          <p class="hint" v-if="versionNumberTaken(nv.versionNumber)">
            ⚠ v{{ nv.versionNumber }} already exists — saving keeps both so you can still compare them.
          </p>

          <input v-model="nv.author" class="sel" placeholder="Author" />
          <input v-model="nv.changesSummary" class="sel" placeholder="Changes summary (what changed vs the previous version)" />

          <div class="nv-import">
            <label class="lbl">{{ nv.fromImport ? 'Imported file' : 'Create from file' }}</label>
            <input v-if="!nv.fromImport" ref="nvFile" type="file"
                   accept=".txt,.md,.markdown,.html,.htm,.json"
                   class="filein" @change="importFile" />
            <p class="hint">{{ importMsg || (nv.fromImport ? nv.importNote : importHint) }}</p>
          </div>

          <label class="lbl">Edit section <span v-if="nv.pointKey" class="point">→ AI points here</span></label>
          <select v-model="nv.editKey" class="sel">
            <option v-for="k in Object.keys(nv.sections)" :key="k" :value="k">{{ sectionTitle(k) }}</option>
          </select>
          <textarea v-model="nv.sections[nv.editKey].content" class="nv-text" rows="12"></textarea>
          <button class="save" @click="saveNewVersion" :disabled="nvSaving">
            <span class="material-icons">{{ nvSaving ? 'hourglass_top' : 'save' }}</span>
            {{ nvSaving ? 'Saving…' : ('Save as v'+nv.versionNumber + (nv.versionLabel ? ' · '+nv.versionLabel : '')) }}
          </button>
        </div>
      </div>
    </div>

    <!-- Draggable AI Reference panel -->
    <button v-if="!aiPanel.open && aiRefList.length" class="ai-fab" @click="aiPanel.open=true"
            title="Open AI guidance reference">
      <span class="material-icons">auto_awesome</span>
      <em>{{ aiRefList.length }}</em>
    </button>
    <div v-if="aiPanel.open" class="ai-ref" :style="{ left: aiPanel.x+'px', top: aiPanel.y+'px' }">
      <div class="ai-ref-head" @mousedown="startDrag">
        <span class="material-icons">auto_awesome</span>
        <strong>AI Defense Reference</strong>
        <button class="ico" @click="aiPanel.open=false"><span class="material-icons">close</span></button>
      </div>
      <div class="ai-ref-body">
        <div v-if="!aiRefList.length" class="ai-ref-empty">
          Click “Generate” on a panel comment to get an AI defense — it collects here.
        </div>
        <div v-for="(it,i) in aiRefList" :key="i" class="ai-ref-item">
          <div class="ai-ref-q">{{ it.q }}</div>
          <div class="ai-ref-a">{{ it.a }}</div>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type==='err'?'error':'check_circle' }}</span>{{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, patch, remove } from '../services/db.js'
import { chat, hasGeminiKey } from '../services/gemini.js'
import foundationDoc from '../assets/canutab-thesis-foundation.json'

// Canonical thesis skeleton — the "foundation". Every version is rendered,
// edited and compared in this order; new/edited versions are built on it so
// the document structure is stable across versions and imports.
const FOUNDATION = [
  { key:'chapter1_introduction', title:'Chapter 1 — Background of the Study' },
  { key:'chapter1_theoretical',  title:'Chapter 1 — Theoretical Framework' },
  { key:'chapter1_problem',      title:'Chapter 1 — Statement of the Problem' },
  { key:'chapter2_methodology',  title:'Chapter 2 — Design and Methodology' },
  { key:'chapter3_results',      title:'Chapter 3 — Presentation, Analysis, and Interpretation of Data' },
  { key:'chapter4_conclusion',   title:'Chapter 4 — Conclusions and Recommendations' },
  { key:'references',            title:'References' }
]
const FOUND_TITLE = Object.fromEntries(FOUNDATION.map(s => [s.key, s.title]))

export default {
  name: 'Thesis',
  data() {
    return {
      tab:'doc', loading:true, error:'', toast:null,
      versions:[], panelRequests:[], selVer:'', selSec:'', q:'',
      nv:null, nvSaving:false, importMsg:'',
      importHint:'JSON with a "sections" object replaces all sections · .txt/.md/.html fills the selected section below',
      // editable document
      editing:false, editDoc:{}, editSaving:false,
      // AI defense
      aiDef:{}, aiDefQ:{}, aiBusy:false, aiBusyKey:'', aiOk:false,
      aiPanel:{ open:false, x:0, y:0, dx:0, dy:0, dragging:false },
      // compare
      cmpA:'', cmpB:'', openCmp:{}, sideBySide:true,
      cmpQ:'', cmpFilter:'all',
      cmpFilters:[
        { v:'all', label:'All sections' },
        { v:'changed', label:'Changed only' },
        { v:'same', label:'Unchanged' },
        { v:'matches', label:'Search matches' }
      ],
      // foundation import
      fImporting:false,
      // delete-all versions
      confirmDel:false, delBusy:false
    }
  },
  computed: {
    aiRefList(){
      return Object.keys(this.aiDef).map(k=>({
        q:this.aiDefQ[k]||'(panel comment)',
        a:`Defense: ${this.aiDef[k].defense}\nApply to: ${this.sectionTitle(this.aiDef[k].section)}`
      }))
    },
    curVersion(){ return this.versions.find(v=>v._key===this.selVer)||null },
    cmpVerA(){ return this.versions.find(v=>v._key===this.cmpA)||null },
    cmpVerB(){ return this.versions.find(v=>v._key===this.cmpB)||null },
    nextVersionNumber(){
      return this.versions.length ? Math.max(...this.versions.map(v=>v.versionNumber||0))+1 : 1
    },
    // canonical-ordered keys: FOUNDATION first, then any extra keys present
    orderedSectionKeys(){
      const s=this.curVersion&&this.curVersion.sections; if(!s)return []
      const extra=Object.keys(s).filter(k=>!FOUND_TITLE[k])
      return [...FOUNDATION.map(f=>f.key).filter(k=>s[k]!==undefined||true), ...extra]
        .filter((k,i,a)=>a.indexOf(k)===i)
    },
    allCmpKeys(){
      const a=this.cmpVerA&&this.cmpVerA.sections||{}
      const b=this.cmpVerB&&this.cmpVerB.sections||{}
      const present=new Set([...Object.keys(a),...Object.keys(b)])
      const extra=[...present].filter(k=>!FOUND_TITLE[k])
      return [...FOUNDATION.map(f=>f.key).filter(k=>present.has(k)), ...extra]
    },
    changedSections(){ return this.allCmpKeys.filter(k=>this.isChanged(k)) },
    visibleCmpKeys(){
      let ks=this.allCmpKeys
      if(this.cmpFilter==='changed') ks=ks.filter(k=>this.isChanged(k))
      else if(this.cmpFilter==='same') ks=ks.filter(k=>!this.isChanged(k))
      else if(this.cmpFilter==='matches') ks=ks.filter(k=>this.cmpKeyHits(k)>0)
      if(this.cmpQ) ks=ks.filter(k=>this.cmpKeyHits(k)>0)
      return ks
    },
    cmpQTotals(){
      let before=0, after=0
      for(const k of this.allCmpKeys){
        before+=this.countIn(this.cmpContent(this.cmpVerA,k))
        after +=this.countIn(this.cmpContent(this.cmpVerB,k))
      }
      return { before, after }
    },
    cmpQSectionHits(){ return this.allCmpKeys.filter(k=>this.cmpKeyHits(k)>0).length },
    shownSections(){
      if(!this.q) return this.orderedSectionKeys
      return this.orderedSectionKeys.filter(k=>this.matchCount(k)>0)
    },
    curSection(){ const s=this.curVersion&&this.curVersion.sections; return s?s[this.selSec]:null },
    totalComments(){ return this.panelRequests.reduce((n,r)=>n+(r.panelists||[]).reduce((m,p)=>m+(p.comments||[]).length,0),0) },
    openComments(){ return this.panelRequests.reduce((n,r)=>n+(r.panelists||[]).reduce((m,p)=>m+(p.comments||[]).filter(c=>!['resolved','rejected'].includes(c.status)).length,0),0) },
    validation(){
      const v=this.curVersion; if(!v||!v.sections)return []
      const secs=Object.entries(v.sections)
      const out=[]
      const empty=secs.filter(([,s])=>this.wordCount(s.content)<20)
      out.push({key:'nonempty',label:'All sections have content',pass:empty.length===0,warn:false,
        detail:empty.length?`${empty.length} thin/empty: ${empty.map(([k])=>this.sectionTitle(k)).join(', ')}`:`All ${secs.length} sections populated`})
      const refs=v.sections['references']
      out.push({key:'refs',label:'References present',pass:!!refs&&this.wordCount(refs.content)>50,warn:!!refs&&this.wordCount(refs.content)<=50,
        detail:refs?`${this.wordCount(refs.content)} words in References`:'No references section'})
      const ch1=secs.filter(([k])=>k.startsWith('chapter1')).length
      const ch2=secs.filter(([k])=>k.startsWith('chapter2')).length
      out.push({key:'struct',label:'Chapter structure',pass:ch1>=3&&ch2>=1,warn:ch1<3||ch2<1,
        detail:`Chapter 1: ${ch1} sections · Chapter 2: ${ch2} sections`})
      const ph=secs.filter(([,s])=>/lorem ipsum|TODO|TBD|placeholder|xxxx/i.test(this.stripHtml(s.content)))
      out.push({key:'placeholder',label:'No placeholder text',pass:ph.length===0,warn:false,
        detail:ph.length?`Placeholder found in ${ph.length} section(s)`:'No TODO/placeholder markers'})
      const tot=secs.reduce((n,[,s])=>n+this.wordCount(s.content),0)
      out.push({key:'length',label:'Document length',pass:tot>=3000,warn:tot>=1500&&tot<3000,
        detail:`${tot.toLocaleString()} total words`})
      return out
    },
    score(){
      const v=this.validation; if(!v.length)return 0
      const pts=v.reduce((n,c)=>n+(c.pass?1:c.warn?0.5:0),0)
      return Math.round(pts/v.length*100)
    },
    scoreClass(){ return this.score>=80?'ok':this.score>=50?'warn':'bad' },
    scoreLabel(){ return this.score>=80?'Defense-ready':this.score>=50?'Needs revision':'Incomplete' }
  },
  async mounted() {
    try {
      const [tv,pr]=await Promise.all([value('thesis_versions'),value('thesis_panel_requests')])
      this.versions = tv&&typeof tv==='object'
        ? Object.entries(tv).map(([_key,v])=>({_key,...v})).sort((a,b)=>(a.versionNumber||0)-(b.versionNumber||0)) : []
      this.panelRequests = pr&&typeof pr==='object'
        ? Object.entries(pr).map(([_key,r])=>({_key,...r})) : []
      // hydrate previously-generated AI defenses persisted on each comment
      this.hydrateDefenses()
      if(this.versions.length){
        this.selVer=this.versions[this.versions.length-1]._key
        const s=this.curVersion&&this.curVersion.sections
        if(s)this.selSec=this.orderedSectionKeys[0]
        this.cmpA=this.versions[0]._key
        this.cmpB=this.versions[this.versions.length-1]._key
      }
      this.aiOk = await hasGeminiKey()
    } catch(e){ this.error=e.message } finally { this.loading=false }
  },
  beforeUnmount(){
    window.removeEventListener('mousemove',this.onDrag)
    window.removeEventListener('mouseup',this.stopDrag)
  },
  methods: {
    // ---- helpers ----
    rx(s){ return new RegExp(String(s).replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'gi') },
    countIn(text){ if(!this.cmpQ||!text)return 0; const m=String(text).match(this.rx(this.cmpQ)); return m?m.length:0 },
    cmpKeyHits(k){ return this.countIn(this.cmpContent(this.cmpVerA,k))+this.countIn(this.cmpContent(this.cmpVerB,k)) },
    // ---- Compare (before / after) ----
    cmpContent(ver,k){
      const s=ver&&ver.sections&&ver.sections[k]
      return s?this.stripHtml(s.content):''
    },
    cmpTitle(k){
      const va=this.cmpVerA&&this.cmpVerA.sections&&this.cmpVerA.sections[k]
      const vb=this.cmpVerB&&this.cmpVerB.sections&&this.cmpVerB.sections[k]
      return FOUND_TITLE[k]||(vb&&vb.title)||(va&&va.title)||
        String(k||'').replace(/_/g,' ').replace(/\b\w/g,c=>c.toUpperCase()).replace(/Chapter(\d)/,'Chapter $1 —')
    },
    isChanged(k){ return this.cmpContent(this.cmpVerA,k)!==this.cmpContent(this.cmpVerB,k) },
    changeStat(k){
      const a=this.cmpContent(this.cmpVerA,k).split(/\s+/).filter(Boolean).length
      const b=this.cmpContent(this.cmpVerB,k).split(/\s+/).filter(Boolean).length
      const d=b-a
      return d===0?'edited':(d>0?`+${d} words`:`${d} words`)
    },
    toggleSec(k){ this.openCmp={...this.openCmp,[k]:!this.openCmp[k]} },
    diffWords(before,after){
      const A=before.split(/(\s+)/), B=after.split(/(\s+)/)
      const n=A.length, m=B.length
      if(n*m>1500000){
        return before===after?[{t:'eq',s:after}]:[{t:'del',s:before},{t:'add',s:after}]
      }
      const dp=Array.from({length:n+1},()=>new Int32Array(m+1))
      for(let i=n-1;i>=0;i--)for(let j=m-1;j>=0;j--)
        dp[i][j]=A[i]===B[j]?dp[i+1][j+1]+1:Math.max(dp[i+1][j],dp[i][j+1])
      const out=[]; let i=0,j=0
      const push=(t,s)=>{ if(!s)return; const l=out[out.length-1]; if(l&&l.t===t)l.s+=s; else out.push({t,s}) }
      while(i<n&&j<m){
        if(A[i]===B[j]){ push('eq',A[i]); i++; j++ }
        else if(dp[i+1][j]>=dp[i][j+1]){ push('del',A[i]); i++ }
        else { push('add',B[j]); j++ }
      }
      while(i<n){ push('del',A[i]); i++ }
      while(j<m){ push('add',B[j]); j++ }
      return out
    },
    // escape + apply the compare search highlight inside one diff token
    mark(s){
      let e=this.esc(s)
      if(this.cmpQ) e=e.replace(this.rx(this.cmpQ),'<mark>$&</mark>')
      return e
    },
    diffHtml(k){
      const a=this.cmpContent(this.cmpVerA,k), b=this.cmpContent(this.cmpVerB,k)
      if(a===b) return '<span class="d-eq">'+this.mark(b||'(empty)')+'</span>'
      return this.diffWords(a,b).map(tok=>{
        const s=this.mark(tok.s)
        if(tok.t==='add') return '<span class="d-add">'+s+'</span>'
        if(tok.t==='del') return '<span class="d-del">'+s+'</span>'
        return '<span class="d-eq">'+s+'</span>'
      }).join('')
    },
    sidePane(k,which){
      const a=this.cmpContent(this.cmpVerA,k), b=this.cmpContent(this.cmpVerB,k)
      if(a===b) return '<span class="d-eq">'+this.mark((which==='before'?a:b)||'(empty)')+'</span>'
      const keep = which==='before' ? 'del' : 'add'
      return this.diffWords(a,b).map(tok=>{
        if(tok.t==='eq') return '<span class="d-eq">'+this.mark(tok.s)+'</span>'
        if(tok.t===keep) return '<span class="d-'+keep+'">'+this.mark(tok.s)+'</span>'
        return ''
      }).join('')
    },
    sectionTitle(k){
      if(FOUND_TITLE[k])return FOUND_TITLE[k]
      const t=this.curVersion&&this.curVersion.sections&&this.curVersion.sections[k]&&this.curVersion.sections[k].title
      if(t)return t
      return String(k||'—').replace(/_/g,' ').replace(/\b\w/g,c=>c.toUpperCase()).replace(/Chapter(\d)/,'Chapter $1 —')
    },
    stripHtml(s){ if(!s)return ''; return String(s).replace(/<[^>]+>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/[ \t]{2,}/g,' ').replace(/\n{3,}/g,'\n\n').trim() },
    esc(s){ return String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])) },
    highlight(text){
      const e=this.esc(text)
      if(!this.q)return e
      return e.replace(this.rx(this.q),'<mark>$&</mark>')
    },
    matchCount(k){
      const s=this.curVersion&&this.curVersion.sections&&this.curVersion.sections[k]
      if(!s||!this.q)return 0
      const m=this.stripHtml(s.content).match(this.rx(this.q))
      return m?m.length:0
    },
    wordCount(s){ return this.stripHtml(s).split(/\s+/).filter(Boolean).length },
    reqStats(req){ const a=(req.panelists||[]).flatMap(p=>p.comments||[]); const d=a.filter(c=>['resolved','rejected'].includes(c.status)).length; return `${d}/${a.length} resolved` },
    cClass(s){ const v=(s||'pending').toLowerCase(); return v==='resolved'?'ok':v==='addressed'?'warn':v==='rejected'?'mut':'pend' },
    notify(m,t='ok'){ this.toast={msg:m,type:t}; setTimeout(()=>this.toast=null,3500) },
    async setReqStatus(req,status){ try{ await patch(`thesis_panel_requests/${req._key}`,{status}); req.status=status; this.notify(`Request → ${status}`) }catch(e){ this.notify(e.message,'err') } },
    async setCommentStatus(req,pi,ci,status){
      const panelists=JSON.parse(JSON.stringify(req.panelists||[]))
      if(!panelists[pi]||!panelists[pi].comments||!panelists[pi].comments[ci])return
      panelists[pi].comments[ci].status=status
      try{ await patch(`thesis_panel_requests/${req._key}`,{panelists}); req.panelists=panelists; this.notify(`Comment → ${status}`) }catch(e){ this.notify(e.message,'err') }
    },

    // ---- AI panel-defense agent ----
    defKey(rk,pi,ci){ const k=`${rk}:${pi}:${ci}`; return this.aiDef[k]?k:'' },
    bestSectionFor(text){
      // map a comment to the most relevant canonical section by keyword overlap
      const t=(text||'').toLowerCase()
      const map=[
        ['chapter1_problem', /problem|objective|research question|sop|aim|purpose/],
        ['chapter1_theoretical', /theor|framework|literature|related|concept/],
        ['chapter2_methodology', /method|design|sample|participant|data gathering|instrument|interview|ethic|locale|population|qualitative|quantitative/],
        ['chapter3_results', /result|finding|analysis|interpretation|theme|data presentation|accuracy|feature/],
        ['chapter4_conclusion', /conclusion|recommend|future work|implication|summary/],
        ['references', /reference|citation|source|bibliograph/]
      ]
      for(const [k,rx] of map) if(rx.test(t)) return k
      return 'chapter1_introduction'
    },
    // Restore AI defenses that were generated in a previous session and
    // persisted onto each comment in thesis_panel_requests.
    hydrateDefenses(){
      const def={}, defQ={}
      for(const req of this.panelRequests){
        ;(req.panelists||[]).forEach((p,pi)=>{
          ;(p.comments||[]).forEach((c,ci)=>{
            if(c&&c.aiDefense&&typeof c.aiDefense==='object'){
              const k=`${req._key}:${pi}:${ci}`
              def[k]=c.aiDefense
              defQ[k]=`[${this.sectionTitle(c.aiDefense.section)}] ${c.text}`
            }
          })
        })
      }
      this.aiDef=def; this.aiDefQ=defQ
    },
    // Generate-once: a click only ever runs when the user explicitly asks.
    // If a defense already exists it is shown from the DB; clicking again is
    // an explicit "Regenerate".
    onGenerateClick(req,pi,ci,c){
      if(this.aiBusy) return
      if(this.defKey(req._key,pi,ci)){
        if(!window.confirm('A defense is already generated and saved for this comment. Regenerate and overwrite it?')) return
      }
      this.defend(req,pi,ci,c)
    },
    async deleteAllVersions(){
      if(this.delBusy) return
      this.delBusy=true
      try{
        await remove('thesis_versions')
        this.versions=[]; this.selVer=''; this.selSec=''
        this.cmpA=''; this.cmpB=''; this.editing=false; this.editDoc={}
        this.confirmDel=false
        this.tab='doc'
        this.notify('All thesis versions deleted — import or create a new one')
      }catch(e){ this.notify('Delete failed: '+e.message,'err') }
      finally{ this.delBusy=false }
    },
    async defend(req,pi,ci,c){
      if(!this.aiOk){ this.notify('AI not configured','err'); return }
      const k=`${req._key}:${pi}:${ci}`
      this.aiBusy=true; this.aiBusyKey=k
      const fallbackSection=c.section&&FOUND_TITLE[c.section]?c.section:this.bestSectionFor(c.text)
      const curSecText=this.curVersion&&this.curVersion.sections&&this.curVersion.sections[fallbackSection]
        ? this.stripHtml(this.curVersion.sections[fallbackSection].content).slice(0,1800) : ''
      try{
        const r=await chat({
          systemPrompt:
            'You are a thesis-defense advisor for "BillSense", a Philippine peso counterfeit-detection '+
            'mobile app (Android + YOLOv8 ML API). A defense panelist raised a comment/question. '+
            'Reply using EXACTLY this labelled plain-text template and nothing else (no markdown, '+
            'no JSON). Each label on its own line, content below it:\n'+
            '[DEFENSE]\n(2-3 sentences directly countering/defending the thesis against the comment)\n'+
            '[APP]\n(1-2 sentences: a concrete mobile-app enhancement that answers the panelist)\n'+
            '[DOC]\n(1-2 sentences: what to add/clarify in the thesis document)\n'+
            '[SECTION]\n(exactly one of: chapter1_introduction, chapter1_theoretical, chapter1_problem, '+
            'chapter2_methodology, chapter3_results, chapter4_conclusion, references)\n'+
            '[REVISED]\n(a 60-160 word improved passage to insert into that section addressing the comment)\n'+
            '[END]',
          userMessage:
            `Panel comment: "${c.text}"\nTagged section: ${this.sectionTitle(fallbackSection)}\n`+
            `Current text of that section (excerpt):\n${curSecText||'(empty)'}\n\nReply with the template now.`,
          generationConfig:{ temperature:0.55, maxOutputTokens:900 }
        })
        const parsed=this.parseAI(r.text, fallbackSection)
        parsed.savedTs=Date.now()
        this.aiDef={...this.aiDef,[k]:parsed}
        this.aiDefQ={...this.aiDefQ,[k]:`[${this.sectionTitle(parsed.section)}] ${c.text}`}
        if(this.aiPanel.x===0&&this.aiPanel.y===0){ this.aiPanel.x=Math.max(20,window.innerWidth-380); this.aiPanel.y=90 }
        this.aiPanel.open=true
        // persist the generated defense onto the comment so it survives
        // reloads and is shared from the database (generate-once)
        try{
          const panelists=JSON.parse(JSON.stringify(req.panelists||[]))
          if(panelists[pi]&&panelists[pi].comments&&panelists[pi].comments[ci]){
            panelists[pi].comments[ci].aiDefense=parsed
            await patch(`thesis_panel_requests/${req._key}`,{panelists})
            req.panelists=panelists
            this.notify('AI defense generated & saved')
          } else {
            this.notify('AI defense generated (not persisted: comment shape)')
          }
        }catch(e){ this.notify('Generated, but save failed: '+e.message,'err') }
      }catch(e){ this.notify('AI: '+e.message,'err') } finally { this.aiBusy=false; this.aiBusyKey='' }
    },
    parseAI(text,fallbackSection){
      let t=String(text||'').trim()
        .replace(/```[a-z]*/gi,'')
        .replace(/[“”„‟]/g,'"').replace(/[‘’‚‛]/g,"'")

      // Primary: labelled [DEFENSE]/[APP]/[DOC]/[SECTION]/[REVISED] template
      const block=(label,next)=>{
        const re=new RegExp('\\[\\s*'+label+'\\s*\\]\\s*([\\s\\S]*?)\\s*(?=\\['+
          '\\s*(?:'+next.join('|')+')\\s*\\]|$)','i')
        const m=t.match(re); return m?m[1].trim():''
      }
      const d=block('DEFENSE',['APP','DOC','SECTION','REVISED','END'])
      const a=block('APP',['DOC','SECTION','REVISED','END'])
      const o=block('DOC',['SECTION','REVISED','END'])
      let sc=block('SECTION',['REVISED','END']).replace(/[^a-z0-9_]/gi,'').toLowerCase()
      const rv=block('REVISED',['END'])
      if(d||a||o||rv){
        const sec=FOUND_TITLE[sc]?sc:fallbackSection
        return { defense:d||'—', app:a||'—', doc:o||'—', section:sec, revised:rv||'' }
      }

      // Fallback: JSON (in case the model ignored the template)
      let j=null
      try{ j=JSON.parse(t) }catch{
        const m=t.match(/\{[\s\S]*\}/)
        if(m){ try{ j=JSON.parse(m[0]) }catch{
          try{ j=JSON.parse(m[0].replace(/,\s*([}\]])/g,'$1')) }catch{} } }
      }
      if(j&&typeof j==='object'){
        const sec=FOUND_TITLE[j.section]?j.section:fallbackSection
        return {
          defense:String(j.defense||'').trim()||'—',
          app:String(j.app||'').trim()||'—',
          doc:String(j.doc||'').trim()||'—',
          section:sec, revised:String(j.revised||'').trim()
        }
      }
      // Last resort: whole reply is the defense
      return { defense:t.replace(/\[[A-Z]+\]/g,' ').trim().slice(0,700)||'—',
        app:'—', doc:'—', section:fallbackSection, revised:'' }
    },
    // Apply the AI's revised excerpt into a staged New Version, pointing at
    // the exact section the AI identified.
    applyDefense(rk,pi,ci,c){
      const k=`${rk}:${pi}:${ci}`
      const d=this.aiDef[k]; if(!d){ return }
      const cur=this.curVersion; if(!cur){ this.notify('No base version','err'); return }
      const sections=JSON.parse(JSON.stringify(this.buildSkeleton(cur)))
      const target=d.section
      const prev=sections[target]?sections[target].content:''
      const addition = d.revised
        ? `\n\n[Revision addressing panel comment — ${this.sectionTitle(target)}]\n${d.revised}`
        : `\n\n[Note from AI defense] ${d.doc}`
      sections[target]={ title:FOUND_TITLE[target]||this.sectionTitle(target),
        content:(prev?prev:'')+addition }
      this.importMsg=''
      this.nv={
        versionNumber:this.nextVersionNumber, versionLabel:'',
        author:'AI defense apply',
        changesSummary:`Address panel comment in ${this.sectionTitle(target)}: "${(c.text||'').slice(0,80)}"`,
        sections, editKey:target, pointKey:target, fromImport:false, importNote:''
      }
      this.notify(`Staged a new version — review ${this.sectionTitle(target)} and Save`)
    },

    // ---- Draggable AI reference ----
    startDrag(e){
      this.aiPanel.dragging=true
      this.aiPanel.dx=e.clientX-this.aiPanel.x
      this.aiPanel.dy=e.clientY-this.aiPanel.y
      window.addEventListener('mousemove',this.onDrag)
      window.addEventListener('mouseup',this.stopDrag)
    },
    onDrag(e){
      if(!this.aiPanel.dragging)return
      this.aiPanel.x=Math.min(Math.max(0,e.clientX-this.aiPanel.dx),window.innerWidth-340)
      this.aiPanel.y=Math.min(Math.max(0,e.clientY-this.aiPanel.dy),window.innerHeight-120)
    },
    stopDrag(){
      this.aiPanel.dragging=false
      window.removeEventListener('mousemove',this.onDrag)
      window.removeEventListener('mouseup',this.stopDrag)
    },

    // ---- Editable full document ----
    buildSkeleton(ver){
      // a full canonical section map seeded from `ver`
      const src=(ver&&ver.sections)||{}
      const out={}
      for(const f of FOUNDATION){
        const s=src[f.key]
        out[f.key]={ title:f.title, content:(s&&s.content)||'' }
      }
      // keep any non-canonical extra sections too
      for(const [k,v] of Object.entries(src))
        if(!FOUND_TITLE[k]) out[k]={ title:(v&&v.title)||this.sectionTitle(k), content:(v&&v.content)||'' }
      return out
    },
    startEdit(){
      const cur=this.curVersion; if(!cur)return
      const sk=this.buildSkeleton(cur)
      const ed={}
      for(const k of Object.keys(sk)) ed[k]=this.stripHtml(sk[k].content)
      this.editDoc=ed
      this.editing=true
    },
    cancelEdit(){ this.editing=false; this.editDoc={} },
    async saveEditAsVersion(){
      if(!this.curVersion)return
      this.editSaving=true
      const prevKey=this.selVer
      const num=this.nextVersionNumber
      const sections={}
      for(const f of FOUNDATION)
        sections[f.key]={ title:f.title, content:(this.editDoc[f.key]||'').trim() }
      for(const k of Object.keys(this.editDoc))
        if(!FOUND_TITLE[k]) sections[k]={ title:this.sectionTitle(k), content:(this.editDoc[k]||'').trim() }
      const rec={ versionNumber:num, author:(this.curVersion.author||'editor'),
        changesSummary:`Inline document edit (v${num})`,
        date:new Date().toISOString(), sections }
      const key=`v${num}_${Date.now()}`
      try{
        await patch('thesis_versions',{[key]:rec})
        this.versions=[...this.versions,{_key:key,...rec}].sort((a,b)=>(a.versionNumber||0)-(b.versionNumber||0))
        this.editing=false; this.editDoc={}
        this.selVer=key
        // jump straight to the before/after diff: prev → new
        this.cmpA=prevKey; this.cmpB=key
        this.cmpFilter='changed'
        this.tab='compare'
        this.notify(`Saved v${num} — showing changed sections`)
      }catch(e){ this.notify(e.message,'err') } finally { this.editSaving=false }
    },

    // ---- Create version from file ----
    importFile(e){
      const f=e.target.files&&e.target.files[0]; if(!f||!this.nv)return
      const rd=new FileReader()
      rd.onload=()=>{
        const txt=String(rd.result||'')
        const name=(f.name||'').toLowerCase()
        try{
          if(name.endsWith('.json')){
            const j=JSON.parse(txt)
            const secs=j.sections||j
            if(secs&&typeof secs==='object'){
              const out={}
              for(const [k,v] of Object.entries(secs))
                out[k]=typeof v==='string'?{content:v,title:this.sectionTitle(k)}
                  :{content:(v&&v.content)||'',title:(v&&v.title)||this.sectionTitle(k)}
              this.nv.sections={...this.nv.sections,...out}
              this.nv.editKey=Object.keys(this.nv.sections)[0]
              this.importMsg=`Imported ${Object.keys(out).length} section(s) from JSON.`
            } else throw new Error('JSON has no "sections"')
          } else {
            const clean=this.stripHtml(txt)
            this.nv.sections[this.nv.editKey].content=clean
            this.importMsg=`Loaded ${clean.split(/\s+/).filter(Boolean).length} words into "${this.sectionTitle(this.nv.editKey)}".`
          }
        }catch(err){ this.importMsg='Import failed: '+err.message }
      }
      rd.readAsText(f)
    },
    // version selector / option label incl. the user-typed label
    vlabel(v){
      if(!v)return ''
      return `v${v.versionNumber}`+(v.versionLabel?` · ${v.versionLabel}`:'')+
        ` — ${(v.date||'').slice(0,10)} — ${v.author||'—'}`
    },
    versionNumberTaken(n){
      const x=Number(n); if(!x)return false
      return this.versions.some(v=>Number(v.versionNumber)===x)
    },
    startNewVersion(){
      const cur=this.curVersion; if(!cur)return
      const sections=this.buildSkeleton(cur)
      this.importMsg=''
      this.nv={ versionNumber:this.nextVersionNumber, versionLabel:'',
        author:'', changesSummary:'', sections,
        editKey:Object.keys(sections)[0], pointKey:'', fromImport:false, importNote:'' }
    },
    async saveNewVersion(){
      if(!this.nv)return
      const num=parseInt(this.nv.versionNumber,10)
      if(!Number.isFinite(num)||num<1){ this.notify('Enter a valid version number (≥ 1)','err'); return }
      if(!String(this.nv.author||'').trim()){ this.notify('Author required','err'); return }
      this.nv.versionNumber=num
      this.nvSaving=true
      const label=String(this.nv.versionLabel||'').trim()
      const rec={ versionNumber:num, versionLabel:label,
        author:this.nv.author.trim(),
        changesSummary:String(this.nv.changesSummary||'').trim()||`Revision v${num}`,
        date:new Date().toISOString(), sections:this.nv.sections }
      if(this.nv.source)rec.source=this.nv.source
      const key=`v${num}_${Date.now()}`
      try{
        await patch('thesis_versions',{[key]:rec})
        this.versions=[...this.versions,{_key:key,...rec}].sort((a,b)=>(a.versionNumber||0)-(b.versionNumber||0))
        this.selVer=key; this.nv=null
        this.notify(`Saved v${num}${label?' · '+label:''}`)
      }catch(e){ this.notify(e.message,'err') } finally { this.nvSaving=false }
    },

    // ---- Import the CANUTAB PDF foundation: stage the modal so the user
    //      types the version number/label before it is saved ----
    importFoundation(){
      if(this.fImporting||this.nv)return
      const sections={}
      for(const f of FOUNDATION){
        const s=foundationDoc.sections&&foundationDoc.sections[f.key]
        sections[f.key]={ title:f.title, content:(s&&s.content)||'' }
      }
      const words=this.wordCount(Object.values(sections).map(s=>s.content).join(' '))
      this.importMsg=''
      this.nv={
        versionNumber:this.nextVersionNumber, versionLabel:'',
        author:foundationDoc.author||'CANUTAB et al. (imported PDF)',
        changesSummary:foundationDoc.changesSummary||'Imported from CANUTAB-THESIS (2) (1).pdf',
        sections, editKey:Object.keys(sections)[0], pointKey:'',
        fromImport:true,
        importNote:`CANUTAB-THESIS (2) (1).pdf · ${words.toLocaleString()} words · 7 canonical sections. Set the version number/label, then Save.`,
        source:foundationDoc.source||'CANUTAB-THESIS (2) (1).pdf'
      }
    }
  }
}
</script>

<style scoped>
.content { padding:1.5rem 2rem; }
.tabbar { display:flex; gap:.5rem; margin-bottom:1.25rem; flex-wrap:wrap; }
.tabbar button { display:flex; align-items:center; gap:.45rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.08); color:var(--text-muted); padding:.55rem 1rem; border-radius:10px; font-size:.86rem; cursor:pointer; }
.tabbar button.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.tabbar button .material-icons { font-size:1.05rem; } .tabbar button em { font-style:normal; opacity:.7; font-size:.78rem; }
.tabbar .newv { margin-left:auto; background:rgba(34,197,94,.12); color:#4ade80; border-color:rgba(34,197,94,.3); }
.tabbar .impv { background:rgba(99,102,241,.14); color:#a5b4fc; border-color:rgba(99,102,241,.3); }
.tabbar .delv { background:rgba(248,113,113,.12); color:#fca5a5; border-color:rgba(248,113,113,.3); }
.tabbar button:disabled { opacity:.5; cursor:not-allowed; }
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.doc { display:grid; grid-template-columns:300px 1fr; gap:1.25rem; }
@media (max-width:860px){ .doc { grid-template-columns:1fr; } }
.doc-side { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem; align-self:start; }
.lbl { display:block; font-size:.72rem; text-transform:uppercase; letter-spacing:.04em; color:var(--text-muted); margin:.7rem 0 .35rem; }
.point { color:#a5b4fc; text-transform:none; letter-spacing:0; font-size:.7rem; }
.sel { width:100%; background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:8px; padding:.5rem .6rem; color:inherit; font-size:.85rem; cursor:pointer; box-sizing:border-box; }
.sel.sm { width:auto; cursor:auto; } .sel.xs { width:auto; font-size:.74rem; padding:.2rem .4rem; }
.sel.xs.ok{color:#4ade80;} .sel.xs.warn{color:#fbbf24;} .sel.xs.mut{color:#94a3b8;} .sel.xs.pend{color:#f87171;}
.chg { font-size:.8rem; color:var(--text-muted); margin:.6rem 0; line-height:1.45; }
.seclist { list-style:none; margin:.25rem 0 0; padding:0; }
.seclist li { padding:.45rem .6rem; border-radius:7px; font-size:.82rem; cursor:pointer; color:var(--text-muted); display:flex; justify-content:space-between; }
.seclist li:hover { background:rgba(255,255,255,.04); } .seclist li.on { background:rgba(255,163,26,.15); color:#ffa31a; }
.seclist li em { font-style:normal; font-size:.7rem; background:rgba(255,163,26,.2); color:#ffa31a; padding:0 .35rem; border-radius:999px; }
.doc-main { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1.25rem 1.5rem; min-width:0; }
.doc-main h3 { margin:0 0 .3rem; }
.secmeta { font-size:.78rem; color:var(--text-muted); margin-bottom:1rem; }
.seccontent, .paper-body { white-space:pre-wrap; font-size:.9rem; line-height:1.7; color:var(--text); }
.seccontent { max-height:65vh; overflow-y:auto; background:rgba(0,0,0,.18); padding:1rem 1.25rem; border-radius:8px; }
.seccontent :deep(mark), .paper-body :deep(mark) { background:#ffa31a; color:#0f172a; border-radius:2px; }
.full-bar { display:flex; gap:.6rem; margin-bottom:1rem; align-items:center; flex-wrap:wrap; }
.edit-btn { display:flex; align-items:center; gap:.35rem; background:rgba(99,102,241,.15); color:#a5b4fc; border:1px solid rgba(99,102,241,.3); border-radius:8px; padding:.45rem .8rem; font-size:.82rem; cursor:pointer; }
.edit-btn.save { background:#ffa31a; color:#0f172a; border-color:#ffa31a; }
.edit-btn.ghost { background:transparent; color:var(--text-muted); }
.edit-btn:disabled { opacity:.5; cursor:not-allowed; } .edit-btn .material-icons { font-size:1rem; }
.edit-note { font-size:.74rem; color:var(--text-muted); flex:1 1 100%; }
.paper { background:#f8f7f3; color:#1a1a1a; border-radius:10px; padding:2.5rem 3rem; max-height:72vh; overflow-y:auto; }
.paper.editing { background:#fffdf7; }
.paper-title { text-align:center; font-size:1.3rem; margin:0 0 1.5rem; color:#111; }
.paper-sec { margin-bottom:1.75rem; }
.paper-sec h3 { color:#1a2a4a; border-bottom:2px solid #1a2a4a; padding-bottom:.25rem; display:flex; justify-content:space-between; align-items:baseline; }
.paper-sec h3 .wc { font-size:.7rem; color:#6b7280; font-weight:400; }
.paper .paper-body { color:#222; }
.paper :deep(mark) { background:#ffe08a; }
.paper-edit { width:100%; box-sizing:border-box; background:#fff; color:#1a1a1a; border:1px solid #cbd5e1; border-radius:6px; padding:.7rem .9rem; font-size:.86rem; line-height:1.6; resize:vertical; font-family:inherit; }
.valid { display:flex; flex-direction:column; gap:1rem; }
.scorebox { text-align:center; padding:1.5rem; border-radius:12px; border:1px solid; }
.scorebox.ok { background:rgba(34,197,94,.1); border-color:rgba(34,197,94,.3); }
.scorebox.warn { background:rgba(251,191,36,.1); border-color:rgba(251,191,36,.3); }
.scorebox.bad { background:rgba(248,113,113,.1); border-color:rgba(248,113,113,.3); }
.scoreN { font-size:2.6rem; font-weight:700; }
.scorebox.ok .scoreN{color:#4ade80;} .scorebox.warn .scoreN{color:#fbbf24;} .scorebox.bad .scoreN{color:#f87171;}
.scoreL { color:var(--text-muted); font-size:.9rem; margin-top:.25rem; }
.checks { display:flex; flex-direction:column; gap:.6rem; }
.chk { display:flex; gap:.7rem; align-items:flex-start; background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:10px; padding:.85rem 1rem; }
.chk .material-icons { font-size:1.2rem; flex-shrink:0; }
.chk.ok .material-icons{color:#4ade80;} .chk.warn .material-icons{color:#fbbf24;} .chk.bad .material-icons{color:#f87171;}
.chk strong { font-size:.9rem; } .chk p { margin:.2rem 0 0; font-size:.8rem; color:var(--text-muted); }
.panels { display:flex; flex-direction:column; gap:1rem; }
.preq { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem 1.25rem; }
.preq-head { display:flex; justify-content:space-between; align-items:center; gap:1rem; }
.preq-head strong { font-size:.98rem; } .src { color:var(--text-muted); font-size:.78rem; }
.preq-meta { display:flex; gap:1.25rem; font-size:.78rem; color:var(--text-muted); margin:.5rem 0 .85rem; flex-wrap:wrap; }
.preq-meta span { display:flex; align-items:center; gap:.3rem; } .preq-meta .material-icons { font-size:.9rem; }
.panelist { border-top:1px solid rgba(255,255,255,.05); padding-top:.75rem; margin-top:.75rem; }
.pl-name { display:flex; align-items:center; gap:.4rem; font-weight:600; font-size:.87rem; margin-bottom:.5rem; }
.pl-name .material-icons { font-size:1rem; color:#ffa31a; }
.nocmt { font-size:.8rem; color:var(--text-muted); padding-left:1.4rem; }
.cmt { display:flex; align-items:flex-start; gap:.6rem; padding:.5rem 0; border-bottom:1px solid rgba(255,255,255,.03); }
.sec-tag { font-size:.66rem; background:rgba(99,102,241,.18); color:#a5b4fc; padding:.12rem .45rem; border-radius:999px; white-space:nowrap; flex-shrink:0; }
.cmt-main { flex:1; min-width:0; }
.cmt-text { font-size:.85rem; line-height:1.45; }
.ai-def { margin-top:.55rem; background:rgba(99,102,241,.08); border:1px solid rgba(99,102,241,.25); border-radius:8px; padding:.7rem .8rem; }
.ai-def-h { display:flex; align-items:center; gap:.4rem; font-size:.8rem; font-weight:600; color:#a5b4fc; margin-bottom:.5rem; }
.ai-def-h .material-icons { font-size:.95rem; }
.ai-def-row { display:flex; gap:.6rem; font-size:.8rem; margin:.3rem 0; }
.ai-def-row b { flex:0 0 110px; color:var(--text-muted); font-weight:600; }
.ai-def-row p { margin:0; line-height:1.45; color:var(--text); flex:1; display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }
.sec-point { background:rgba(255,163,26,.18); color:#ffa31a; padding:.1rem .5rem; border-radius:999px; font-size:.74rem; }
.apply-btn { display:inline-flex; align-items:center; gap:.3rem; background:#ffa31a; color:#0f172a; border:0; border-radius:6px; padding:.25rem .6rem; font-size:.74rem; font-weight:600; cursor:pointer; }
.apply-btn .material-icons { font-size:.9rem; }
.ai-def details { margin-top:.45rem; font-size:.78rem; color:var(--text-muted); }
.ai-def summary { cursor:pointer; }
.ai-def-rev { margin-top:.4rem; background:rgba(0,0,0,.2); border-radius:6px; padding:.55rem .7rem; line-height:1.5; color:var(--text); white-space:pre-wrap; }
.cmt-act { display:flex; align-items:center; gap:.35rem; flex-shrink:0; flex-direction:column; }
.ico { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.ico:hover:not(:disabled){ color:#a5b4fc; } .ico:disabled{ opacity:.4; cursor:not-allowed; }
.ico .material-icons { font-size:1rem; }
.ico.gen { display:flex; align-items:center; gap:.25rem; font-size:.74rem; background:rgba(99,102,241,.15); color:#a5b4fc; border:1px solid rgba(99,102,241,.3); border-radius:6px; padding:.28rem .55rem; }
.ico.gen.regen { background:rgba(148,163,184,.12); color:#cbd5e1; border-color:rgba(148,163,184,.3); }
.saved-tag { display:inline-flex; align-items:center; gap:.2rem; margin-left:auto; font-size:.68rem; background:rgba(34,197,94,.16); color:#4ade80; padding:.08rem .45rem; border-radius:999px; font-weight:600; }
.saved-tag .material-icons { font-size:.8rem; }
.warn-txt { font-size:.85rem; line-height:1.55; color:var(--text); margin:0 0 .3rem; }
.warn-txt code { background:rgba(255,255,255,.08); padding:0 .3rem; border-radius:4px; font-size:.82rem; }
.del-actions { display:flex; gap:.6rem; justify-content:flex-end; margin-top:.4rem; }
.del-go { display:flex; align-items:center; gap:.35rem; background:#ef4444; color:#fff; border:0; border-radius:8px; padding:.55rem .9rem; font-weight:600; font-size:.85rem; cursor:pointer; }
.del-go:disabled { opacity:.5; cursor:not-allowed; } .del-go .material-icons { font-size:1rem; }
.modal { position:fixed; inset:0; background:rgba(0,0,0,.7); display:flex; align-items:center; justify-content:center; z-index:999; padding:2rem; }
.modal-box { background:var(--bg-card); border:1px solid rgba(255,255,255,.1); border-radius:12px; width:100%; max-width:640px; max-height:85vh; display:flex; flex-direction:column; }
.modal-box.sm { max-width:460px; }
.modal-head { display:flex; justify-content:space-between; align-items:center; padding:1rem 1.25rem; border-bottom:1px solid rgba(255,255,255,.08); }
.nv-body { padding:1rem 1.25rem; overflow-y:auto; display:flex; flex-direction:column; gap:.6rem; }
.nv-verrow { display:flex; gap:.7rem; }
.nv-vn { flex:0 0 130px; } .nv-vl { flex:1; }
.nv-verrow .lbl { margin-top:0; } .nv-verrow .lbl em { font-style:normal; opacity:.6; text-transform:none; }
@media (max-width:520px){ .nv-verrow { flex-direction:column; } .nv-vn { flex:1; } }
.nv-text { width:100%; background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:8px; padding:.6rem; color:inherit; font-size:.84rem; line-height:1.5; box-sizing:border-box; resize:vertical; }
.save { background:#ffa31a; color:#0f172a; border:0; border-radius:8px; padding:.6rem; font-weight:600; cursor:pointer; display:flex; align-items:center; justify-content:center; gap:.4rem; }
.save:disabled { opacity:.5; cursor:not-allowed; } .save .material-icons { font-size:1.1rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }

/* Compare (before / after) */
.cmp-bar { display:flex; align-items:flex-end; gap:1rem; flex-wrap:wrap; margin-bottom:1rem; }
.cmp-pick { display:flex; flex-direction:column; }
.cmp-arrow { color:var(--text-muted); margin-bottom:.4rem; }
.cmp-legend { display:flex; align-items:center; gap:.6rem; margin-left:auto; font-size:.78rem; color:var(--text-muted); flex-wrap:wrap; }
.lg { padding:.12rem .5rem; border-radius:999px; font-size:.72rem; }
.lg.add { background:rgba(34,197,94,.18); color:#4ade80; }
.lg.del { background:rgba(248,113,113,.18); color:#f87171; }
.cmp-sum { margin-left:.4rem; }
.cmp-tools { display:flex; align-items:center; gap:.75rem; flex-wrap:wrap; margin-bottom:1.1rem; padding:.6rem .75rem; background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:10px; }
.cmp-search { display:flex; align-items:center; gap:.4rem; flex:1 1 280px; background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:8px; padding:.35rem .55rem; }
.cmp-search .material-icons { font-size:1rem; color:var(--text-muted); }
.cmp-search input { flex:1; background:none; border:0; color:inherit; font-size:.85rem; outline:none; }
.cmp-filters { display:flex; gap:.35rem; flex-wrap:wrap; }
.fchip { background:rgba(255,255,255,.05); border:1px solid rgba(255,255,255,.1); color:var(--text-muted); border-radius:999px; padding:.25rem .7rem; font-size:.74rem; cursor:pointer; }
.fchip.on { background:rgba(255,163,26,.15); color:#ffa31a; border-color:rgba(255,163,26,.3); }
.cmp-qsum { font-size:.76rem; color:var(--text-muted); flex:1 1 100%; }
.cmp-secs { display:flex; flex-direction:column; gap:.6rem; }
.cmp-sec { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:10px; }
.cmp-sec.unchanged { opacity:.6; }
.cmp-sec-head { display:flex; align-items:center; gap:.5rem; padding:.7rem 1rem; cursor:pointer; }
.cmp-sec-head .material-icons { font-size:1.1rem; color:var(--text-muted); }
.cmp-sec-head strong { flex:1; font-size:.9rem; }
.cmp-hit { font-size:.68rem; background:#ffa31a; color:#0f172a; padding:.1rem .45rem; border-radius:999px; }
.cmp-tag { font-size:.7rem; padding:.12rem .5rem; border-radius:999px; }
.cmp-tag.chg { background:rgba(255,163,26,.18); color:#ffa31a; }
.cmp-tag.same { background:rgba(148,163,184,.15); color:#94a3b8; }
.cmp-diff { padding:1rem 1.25rem; border-top:1px solid rgba(255,255,255,.05); white-space:pre-wrap;
  font-size:.88rem; line-height:1.7; max-height:60vh; overflow-y:auto; }
.cmp-diff :deep(.d-add) { background:rgba(34,197,94,.22); color:#86efac; }
.cmp-diff :deep(.d-del) { background:rgba(248,113,113,.22); color:#fca5a5; text-decoration:line-through; }
.cmp-diff :deep(.d-eq) { color:var(--text); }
.cmp-diff :deep(mark) { background:#ffa31a; color:#0f172a; border-radius:2px; }
.cmp-mode { display:flex; align-items:center; gap:.35rem; background:rgba(99,102,241,.15);
  color:#a5b4fc; border:1px solid rgba(99,102,241,.3); border-radius:999px;
  padding:.25rem .7rem; font-size:.74rem; cursor:pointer; margin-right:.5rem; }
.cmp-mode .material-icons { font-size:.95rem; }
.cmp-split { display:grid; grid-template-columns:1fr 1fr; gap:1px; background:rgba(255,255,255,.06);
  border-top:1px solid rgba(255,255,255,.05); }
@media (max-width:760px){ .cmp-split { grid-template-columns:1fr; } }
.cmp-pane { background:var(--bg-card); display:flex; flex-direction:column; min-width:0; }
.cmp-pane-h { font-size:.74rem; font-weight:600; padding:.4rem .85rem; letter-spacing:.03em;
  position:sticky; top:0; }
.cmp-pane-h.del { background:rgba(248,113,113,.14); color:#fca5a5; }
.cmp-pane-h.add { background:rgba(34,197,94,.14); color:#86efac; }
.cmp-pane .cmp-diff { border-top:0; max-height:60vh; }

/* Create-from-file */
.nv-import { background:rgba(99,102,241,.08); border:1px solid rgba(99,102,241,.2); border-radius:8px; padding:.65rem .8rem; }
.filein { width:100%; font-size:.8rem; color:var(--text-muted); margin-top:.2rem; }
.hint { margin:.4rem 0 0; font-size:.74rem; color:var(--text-muted); line-height:1.4; }

/* Draggable AI Reference */
.ai-fab { position:fixed; right:1.5rem; bottom:1.5rem; z-index:1001; display:flex; align-items:center; gap:.35rem;
  background:#6366f1; color:#fff; border:0; border-radius:999px; padding:.65rem .9rem; cursor:pointer;
  box-shadow:0 6px 20px rgba(99,102,241,.4); }
.ai-fab .material-icons { font-size:1.1rem; } .ai-fab em { font-style:normal; font-size:.78rem; }
.ai-ref { position:fixed; width:340px; max-height:60vh; z-index:1002; background:var(--bg-card);
  border:1px solid rgba(99,102,241,.35); border-radius:12px; box-shadow:0 12px 40px rgba(0,0,0,.5);
  display:flex; flex-direction:column; }
.ai-ref-head { display:flex; align-items:center; gap:.45rem; padding:.65rem .85rem; cursor:move;
  background:rgba(99,102,241,.15); border-radius:12px 12px 0 0; user-select:none; }
.ai-ref-head .material-icons { color:#a5b4fc; font-size:1rem; }
.ai-ref-head strong { flex:1; font-size:.85rem; }
.ai-ref-body { overflow-y:auto; padding:.75rem .85rem; display:flex; flex-direction:column; gap:.7rem; }
.ai-ref-empty { font-size:.8rem; color:var(--text-muted); line-height:1.5; }
.ai-ref-item { border-left:2px solid rgba(99,102,241,.5); padding-left:.6rem; }
.ai-ref-q { font-size:.76rem; color:var(--text-muted); margin-bottom:.25rem; }
.ai-ref-a { font-size:.84rem; line-height:1.5; color:#c7d2fe; white-space:pre-wrap; }
</style>
