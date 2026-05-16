<template>
  <div>
    <div class="page-header">
      <h1>Thesis Validator</h1>
      <p>Version control · full-document view · validation scoring · panel comment management</p>
    </div>

    <div class="content">
      <div class="tabbar">
        <button :class="{ on: tab==='doc' }" @click="tab='doc'">
          <span class="material-icons">description</span> Versions <em>{{ versions.length }}</em>
        </button>
        <button :class="{ on: tab==='full' }" @click="tab='full'">
          <span class="material-icons">menu_book</span> Full Document
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
      </div>

      <div v-if="loading" class="state">Loading thesis data…</div>
      <div v-else-if="error" class="state err"><span class="material-icons">error</span> {{ error }}</div>

      <!-- ===== VERSIONS ===== -->
      <div v-else-if="tab==='doc'" class="doc">
        <div class="doc-side">
          <label class="lbl">Version</label>
          <select v-model="selVer" class="sel">
            <option v-for="v in versions" :key="v._key" :value="v._key">
              v{{ v.versionNumber }} — {{ (v.date||'').slice(0,10) }} — {{ v.author }}
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

      <!-- ===== FULL DOCUMENT (template viewer) ===== -->
      <div v-else-if="tab==='full'" class="full">
        <div class="full-bar">
          <select v-model="selVer" class="sel sm">
            <option v-for="v in versions" :key="v._key" :value="v._key">v{{ v.versionNumber }} — {{ v.author }}</option>
          </select>
          <input v-model="q" class="sel sm" placeholder="search whole document…" />
        </div>
        <div class="paper">
          <h2 class="paper-title">BillSense — Thesis Document (v{{ curVersion && curVersion.versionNumber }})</h2>
          <div v-for="key in orderedSectionKeys" :key="key" class="paper-sec">
            <h3>{{ sectionTitle(key) }}</h3>
            <div class="paper-body" v-html="highlight(stripHtml(curVersion.sections[key].content))"></div>
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
                v{{ v.versionNumber }} — {{ v.author }} — {{ (v.date||'').slice(0,10) }}
              </option>
            </select>
          </div>
          <span class="material-icons cmp-arrow">arrow_forward</span>
          <div class="cmp-pick">
            <label class="lbl">After</label>
            <select v-model="cmpB" class="sel sm">
              <option v-for="v in versions" :key="v._key" :value="v._key">
                v{{ v.versionNumber }} — {{ v.author }} — {{ (v.date||'').slice(0,10) }}
              </option>
            </select>
          </div>
          <div class="cmp-legend">
            <span class="lg add">added</span><span class="lg del">removed</span>
            <span class="cmp-sum">{{ changedSections.length }} of {{ allCmpKeys.length }} sections changed</span>
          </div>
        </div>

        <div v-if="cmpA === cmpB" class="state">Pick two different versions to compare.</div>
        <div v-else class="cmp-secs">
          <div v-for="k in allCmpKeys" :key="k" class="cmp-sec" :class="{ unchanged: !isChanged(k) }">
            <div class="cmp-sec-head" @click="toggleSec(k)">
              <span class="material-icons">{{ openCmp[k] ? 'expand_more' : 'chevron_right' }}</span>
              <strong>{{ cmpTitle(k) }}</strong>
              <span class="cmp-tag" :class="isChanged(k) ? 'chg' : 'same'">
                {{ isChanged(k) ? changeStat(k) : 'no change' }}
              </span>
            </div>
            <div v-if="openCmp[k]" class="cmp-diff" v-html="diffHtml(k)"></div>
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
                <div v-if="aiKey(req._key,pi,ci)" class="ai-sug">
                  <span class="material-icons">auto_awesome</span>
                  <span>{{ aiSug[aiKey(req._key,pi,ci)] }}</span>
                </div>
              </div>
              <div class="cmt-act">
                <button class="ico" :disabled="aiBusy" @click="suggest(req,pi,ci,c)" title="AI: how to address">
                  <span class="material-icons">auto_awesome</span>
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

    <!-- New Version modal -->
    <div v-if="nv" class="modal" @click.self="nv=null">
      <div class="modal-box">
        <div class="modal-head">
          <strong>New Version (v{{ nv.versionNumber }})</strong>
          <button class="ico" @click="nv=null"><span class="material-icons">close</span></button>
        </div>
        <div class="nv-body">
          <input v-model="nv.author" class="sel" placeholder="Author" />
          <input v-model="nv.changesSummary" class="sel" placeholder="Changes summary" />
          <label class="lbl">Edit section</label>
          <select v-model="nv.editKey" class="sel">
            <option v-for="k in Object.keys(nv.sections)" :key="k" :value="k">{{ sectionTitle(k) }}</option>
          </select>
          <textarea v-model="nv.sections[nv.editKey].content" class="nv-text" rows="12"></textarea>
          <button class="save" @click="saveNewVersion" :disabled="nvSaving">
            <span class="material-icons">{{ nvSaving ? 'hourglass_top' : 'save' }}</span>
            {{ nvSaving ? 'Saving…' : 'Save as v'+nv.versionNumber }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast" :class="toast.type">
      <span class="material-icons">{{ toast.type==='err'?'error':'check_circle' }}</span>{{ toast.msg }}
    </div>
  </div>
</template>

<script>
import { value, patch } from '../services/db.js'
import { chat, hasGeminiKey } from '../services/gemini.js'

export default {
  name: 'Thesis',
  data() {
    return {
      tab:'doc', loading:true, error:'', toast:null,
      versions:[], panelRequests:[], selVer:'', selSec:'', q:'',
      nv:null, nvSaving:false,
      aiSug:{}, aiBusy:false, aiOk:false,
      cmpA:'', cmpB:'', openCmp:{}
    }
  },
  computed: {
    curVersion(){ return this.versions.find(v=>v._key===this.selVer)||null },
    cmpVerA(){ return this.versions.find(v=>v._key===this.cmpA)||null },
    cmpVerB(){ return this.versions.find(v=>v._key===this.cmpB)||null },
    allCmpKeys(){
      const a=this.cmpVerA&&this.cmpVerA.sections||{}
      const b=this.cmpVerB&&this.cmpVerB.sections||{}
      return [...new Set([...Object.keys(a),...Object.keys(b)])]
    },
    changedSections(){ return this.allCmpKeys.filter(k=>this.isChanged(k)) },
    orderedSectionKeys(){
      const s=this.curVersion&&this.curVersion.sections; if(!s)return []
      return Object.keys(s)
    },
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
        detail:empty.length?`${empty.length} thin/empty: ${empty.map(([k])=>this.sectionTitle(k)).join(', ')}`:'All 12 sections populated'})
      const refs=v.sections['references']
      out.push({key:'refs',label:'References present',pass:!!refs&&this.wordCount(refs.content)>50,warn:false,
        detail:refs?`${this.wordCount(refs.content)} words in References`:'No references section'})
      const ch1=secs.filter(([k])=>k.startsWith('chapter1')).length
      const ch2=secs.filter(([k])=>k.startsWith('chapter2')).length
      out.push({key:'struct',label:'Chapter structure',pass:ch1>=3&&ch2>=5,warn:ch1<3||ch2<5,
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
      if(this.versions.length){
        this.selVer=this.versions[this.versions.length-1]._key
        const s=this.curVersion&&this.curVersion.sections
        if(s)this.selSec=Object.keys(s)[0]
        // Compare defaults: oldest (before) vs newest (after)
        this.cmpA=this.versions[0]._key
        this.cmpB=this.versions[this.versions.length-1]._key
      }
      this.aiOk = await hasGeminiKey()
    } catch(e){ this.error=e.message } finally { this.loading=false }
  },
  methods: {
    // ---- Compare (before / after) ----
    cmpContent(ver,k){
      const s=ver&&ver.sections&&ver.sections[k]
      return s?this.stripHtml(s.content):''
    },
    cmpTitle(k){
      const va=this.cmpVerA&&this.cmpVerA.sections&&this.cmpVerA.sections[k]
      const vb=this.cmpVerB&&this.cmpVerB.sections&&this.cmpVerB.sections[k]
      return (vb&&vb.title)||(va&&va.title)||
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
    // Word-level LCS diff -> tokens [{t:'eq|add|del', s}]
    diffWords(before,after){
      const A=before.split(/(\s+)/), B=after.split(/(\s+)/)
      const n=A.length, m=B.length
      // LCS table (cap to keep it cheap on huge sections)
      if(n*m>1500000){ // too big for full LCS; coarse fallback
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
    diffHtml(k){
      const a=this.cmpContent(this.cmpVerA,k), b=this.cmpContent(this.cmpVerB,k)
      if(a===b) return '<span class="d-eq">'+this.esc(b||'(empty)')+'</span>'
      return this.diffWords(a,b).map(tok=>{
        const s=this.esc(tok.s)
        if(tok.t==='add') return '<span class="d-add">'+s+'</span>'
        if(tok.t==='del') return '<span class="d-del">'+s+'</span>'
        return '<span class="d-eq">'+s+'</span>'
      }).join('')
    },
    sectionTitle(k){
      const t=this.curVersion&&this.curVersion.sections&&this.curVersion.sections[k]&&this.curVersion.sections[k].title
      if(t)return t
      return String(k||'—').replace(/_/g,' ').replace(/\b\w/g,c=>c.toUpperCase()).replace(/Chapter(\d)/,'Chapter $1 —')
    },
    stripHtml(s){ if(!s)return ''; return String(s).replace(/<[^>]+>/g,' ').replace(/&nbsp;/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/[ \t]{2,}/g,' ').replace(/\n{3,}/g,'\n\n').trim() },
    esc(s){ return String(s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])) },
    highlight(text){
      const e=this.esc(text)
      if(!this.q)return e
      const rx=new RegExp('('+this.q.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+')','gi')
      return e.replace(rx,'<mark>$1</mark>')
    },
    matchCount(k){
      const s=this.curVersion&&this.curVersion.sections&&this.curVersion.sections[k]
      if(!s||!this.q)return 0
      const m=this.stripHtml(s.content).match(new RegExp(this.q.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'gi'))
      return m?m.length:0
    },
    wordCount(s){ return this.stripHtml(s).split(/\s+/).filter(Boolean).length },
    reqStats(req){ const a=(req.panelists||[]).flatMap(p=>p.comments||[]); const d=a.filter(c=>['resolved','rejected'].includes(c.status)).length; return `${d}/${a.length} resolved` },
    cClass(s){ const v=(s||'pending').toLowerCase(); return v==='resolved'?'ok':v==='addressed'?'warn':v==='rejected'?'mut':'pend' },
    notify(m,t='ok'){ this.toast={msg:m,type:t}; setTimeout(()=>this.toast=null,3000) },
    async setReqStatus(req,status){ try{ await patch(`thesis_panel_requests/${req._key}`,{status}); req.status=status; this.notify(`Request → ${status}`) }catch(e){ this.notify(e.message,'err') } },
    async setCommentStatus(req,pi,ci,status){
      const panelists=JSON.parse(JSON.stringify(req.panelists||[]))
      if(!panelists[pi]||!panelists[pi].comments||!panelists[pi].comments[ci])return
      panelists[pi].comments[ci].status=status
      try{ await patch(`thesis_panel_requests/${req._key}`,{panelists}); req.panelists=panelists; this.notify(`Comment → ${status}`) }catch(e){ this.notify(e.message,'err') }
    },
    aiKey(rk,pi,ci){ const k=`${rk}:${pi}:${ci}`; return this.aiSug[k]?k:'' },
    async suggest(req,pi,ci,c){
      if(!this.aiOk){ this.notify('AI not configured','err'); return }
      this.aiBusy=true
      const k=`${req._key}:${pi}:${ci}`
      try{
        const r=await chat({
          systemPrompt:'You advise a thesis student how to address a defense-panel comment. Reply in 2 sentences max, concrete and actionable.',
          userMessage:`Section: ${this.sectionTitle(c.section)}\nPanel comment: "${c.text}"\nHow should the student address this?`,
          generationConfig:{temperature:0.5,maxOutputTokens:200}
        })
        this.aiSug={...this.aiSug,[k]:r.text.trim()}
      }catch(e){ this.notify('AI: '+e.message,'err') } finally { this.aiBusy=false }
    },
    startNewVersion(){
      const cur=this.curVersion; if(!cur)return
      const nextN=Math.max(...this.versions.map(v=>v.versionNumber||0))+1
      const sections=JSON.parse(JSON.stringify(cur.sections||{}))
      this.nv={ versionNumber:nextN, author:'', changesSummary:'',
        sections, editKey:Object.keys(sections)[0] }
    },
    async saveNewVersion(){
      if(!this.nv)return
      if(!this.nv.author.trim()){ this.notify('Author required','err'); return }
      this.nvSaving=true
      const rec={ versionNumber:this.nv.versionNumber, author:this.nv.author.trim(),
        changesSummary:this.nv.changesSummary.trim()||`Revision v${this.nv.versionNumber}`,
        date:new Date().toISOString(), sections:this.nv.sections }
      const key=`v${this.nv.versionNumber}_${Date.now()}`
      try{
        await patch('thesis_versions',{[key]:rec})
        this.versions=[...this.versions,{_key:key,...rec}].sort((a,b)=>(a.versionNumber||0)-(b.versionNumber||0))
        this.selVer=key; this.nv=null
        this.notify(`Version v${rec.versionNumber} saved`)
      }catch(e){ this.notify(e.message,'err') } finally { this.nvSaving=false }
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
.state { color:var(--text-muted); padding:2rem; text-align:center; }
.state.err { color:#f87171; display:flex; gap:.5rem; justify-content:center; }
.doc { display:grid; grid-template-columns:300px 1fr; gap:1.25rem; }
@media (max-width:860px){ .doc { grid-template-columns:1fr; } }
.doc-side { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:12px; padding:1rem; align-self:start; }
.lbl { display:block; font-size:.72rem; text-transform:uppercase; letter-spacing:.04em; color:var(--text-muted); margin:.7rem 0 .35rem; }
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
.full-bar { display:flex; gap:.6rem; margin-bottom:1rem; }
.paper { background:#f8f7f3; color:#1a1a1a; border-radius:10px; padding:2.5rem 3rem; max-height:72vh; overflow-y:auto; }
.paper-title { text-align:center; font-size:1.3rem; margin:0 0 1.5rem; color:#111; }
.paper-sec { margin-bottom:1.75rem; }
.paper-sec h3 { color:#1a2a4a; border-bottom:2px solid #1a2a4a; padding-bottom:.25rem; }
.paper .paper-body { color:#222; }
.paper :deep(mark) { background:#ffe08a; }
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
.cmt-main { flex:1; }
.cmt-text { font-size:.85rem; line-height:1.45; }
.ai-sug { display:flex; gap:.4rem; margin-top:.4rem; font-size:.8rem; color:#a5b4fc; background:rgba(99,102,241,.1); padding:.45rem .6rem; border-radius:6px; }
.ai-sug .material-icons { font-size:.9rem; flex-shrink:0; }
.cmt-act { display:flex; align-items:center; gap:.35rem; flex-shrink:0; }
.ico { background:none; border:0; color:var(--text-muted); cursor:pointer; padding:.2rem; }
.ico:hover:not(:disabled){ color:#a5b4fc; } .ico:disabled{ opacity:.4; cursor:not-allowed; }
.ico .material-icons { font-size:1rem; }
.modal { position:fixed; inset:0; background:rgba(0,0,0,.7); display:flex; align-items:center; justify-content:center; z-index:999; padding:2rem; }
.modal-box { background:var(--bg-card); border:1px solid rgba(255,255,255,.1); border-radius:12px; width:100%; max-width:640px; max-height:85vh; display:flex; flex-direction:column; }
.modal-head { display:flex; justify-content:space-between; align-items:center; padding:1rem 1.25rem; border-bottom:1px solid rgba(255,255,255,.08); }
.nv-body { padding:1rem 1.25rem; overflow-y:auto; display:flex; flex-direction:column; gap:.6rem; }
.nv-text { width:100%; background:rgba(0,0,0,.25); border:1px solid rgba(255,255,255,.1); border-radius:8px; padding:.6rem; color:inherit; font-size:.84rem; line-height:1.5; box-sizing:border-box; resize:vertical; }
.save { background:#ffa31a; color:#0f172a; border:0; border-radius:8px; padding:.6rem; font-weight:600; cursor:pointer; display:flex; align-items:center; justify-content:center; gap:.4rem; }
.save:disabled { opacity:.5; cursor:not-allowed; } .save .material-icons { font-size:1.1rem; }
.toast { position:fixed; bottom:1.5rem; right:1.5rem; display:flex; align-items:center; gap:.5rem; padding:.7rem 1.1rem; border-radius:8px; font-size:.87rem; z-index:1000; }
.toast.ok { background:rgba(34,197,94,.18); color:#4ade80; border:1px solid rgba(34,197,94,.3); }
.toast.err { background:rgba(248,113,113,.18); color:#f87171; border:1px solid rgba(248,113,113,.3); }
.toast .material-icons { font-size:1.05rem; }

/* Compare (before / after) */
.cmp-bar { display:flex; align-items:flex-end; gap:1rem; flex-wrap:wrap; margin-bottom:1.25rem; }
.cmp-pick { display:flex; flex-direction:column; }
.cmp-arrow { color:var(--text-muted); margin-bottom:.4rem; }
.cmp-legend { display:flex; align-items:center; gap:.6rem; margin-left:auto; font-size:.78rem; color:var(--text-muted); flex-wrap:wrap; }
.lg { padding:.12rem .5rem; border-radius:999px; font-size:.72rem; }
.lg.add { background:rgba(34,197,94,.18); color:#4ade80; }
.lg.del { background:rgba(248,113,113,.18); color:#f87171; }
.cmp-sum { margin-left:.4rem; }
.cmp-secs { display:flex; flex-direction:column; gap:.6rem; }
.cmp-sec { background:var(--bg-card); border:1px solid rgba(255,255,255,.06); border-radius:10px; }
.cmp-sec.unchanged { opacity:.55; }
.cmp-sec-head { display:flex; align-items:center; gap:.5rem; padding:.7rem 1rem; cursor:pointer; }
.cmp-sec-head .material-icons { font-size:1.1rem; color:var(--text-muted); }
.cmp-sec-head strong { flex:1; font-size:.9rem; }
.cmp-tag { font-size:.7rem; padding:.12rem .5rem; border-radius:999px; }
.cmp-tag.chg { background:rgba(255,163,26,.18); color:#ffa31a; }
.cmp-tag.same { background:rgba(148,163,184,.15); color:#94a3b8; }
.cmp-diff { padding:1rem 1.25rem; border-top:1px solid rgba(255,255,255,.05); white-space:pre-wrap;
  font-size:.88rem; line-height:1.7; max-height:60vh; overflow-y:auto; }
.cmp-diff :deep(.d-add) { background:rgba(34,197,94,.22); color:#86efac; }
.cmp-diff :deep(.d-del) { background:rgba(248,113,113,.22); color:#fca5a5; text-decoration:line-through; }
.cmp-diff :deep(.d-eq) { color:var(--text); }
</style>
