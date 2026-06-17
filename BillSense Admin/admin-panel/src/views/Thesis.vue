<template>
  <div class="thesis-validator">
    <!-- Top Header -->
    <div class="top-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">school</span> Thesis Validator</h1>
        <p class="subtitle">BILLSENSE: Developing a Camera-Based System for Detecting Counterfeit and Genuine Philippine Currency</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="openPanelRequests">
          <span class="material-icons">rate_review</span>
          Panel Requests
          <span class="badge-count" v-if="pendingRequestCount > 0">{{ pendingRequestCount }}</span>
        </button>
        <button class="btn btn-ghost" @click="openValidation" :disabled="validating">
          <span v-if="validating" class="material-icons spin">sync</span>
          <span v-else class="material-icons">verified</span>
          {{ validating ? 'Validating...' : 'Validate' }}
        </button>
        <button class="btn btn-ghost" @click="showCompare = !showCompare" :class="{ active: showCompare }">
          <span class="material-icons">compare</span>
          {{ showCompare ? 'Exit Compare' : 'Compare Versions' }}
        </button>
        <button class="btn btn-accent" @click="showTemplateViewer = true">
          <span class="material-icons">auto_stories</span>
          Template Viewer
        </button>
        <div class="export-group">
          <button class="btn btn-ghost" @click="exportDocx" :disabled="exporting">
            <span class="material-icons">description</span> DOCX
          </button>
          <button class="btn btn-ghost" @click="exportPdf" :disabled="exporting">
            <span class="material-icons">picture_as_pdf</span> PDF
          </button>
          <button class="btn btn-ghost" @click="printThesis">
            <span class="material-icons">print</span> Print
          </button>
        </div>
        <button class="btn btn-ghost" @click="showCreateVersionModal = true">
          <span class="material-icons">upload_file</span> Create Version
        </button>
        <button class="btn btn-primary" @click="showSaveModal = true" :disabled="saving">
          <span v-if="saving" class="material-icons spin">sync</span>
          <span v-else class="material-icons">save</span>
          {{ saving ? 'Saving...' : 'Save Version' }}
        </button>
      </div>
    </div>

    <!-- Main Layout: Sidebar + Document -->
    <div class="main-layout">
      <!-- Section Navigator Sidebar -->
      <aside class="section-sidebar">
        <h3 class="sidebar-title">Sections</h3>
        <div class="section-list">
          <div
            v-for="(info, key) in SECTION_MAP"
            :key="key"
            class="section-item"
            :class="{ active: activeSection === key }"
            @click="scrollToSection(key)"
          >
            <div class="section-item-header">
              <span class="section-chapter" v-if="info.chapter > 0">Ch {{ info.chapter }}</span>
              <span class="section-chapter ref-badge" v-else>REF</span>
              <span class="section-name">{{ info.title }}</span>
            </div>
            <div class="section-item-badges">
              <span class="word-badge">{{ getWordCount(key) }} words</span>
              <span class="warning-badge" v-if="sectionHasPendingComments(key)" title="Has pending panel comments">
                <span class="material-icons">warning</span>
              </span>
            </div>
          </div>
        </div>
      </aside>

      <!-- Main Content: Side-by-Side Compare View -->
      <div class="document-area compare-main" ref="documentArea">
        <!-- Search Bar -->
        <div class="search-bar">
          <span class="material-icons search-icon">search</span>
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="searchMode === 'exact' ? 'Search exact text...' : 'Semantic search (find related concepts)...'"
            class="search-input"
            @input="performSearch"
            @keydown.enter="jumpToNextMatch"
            @keydown.escape="clearSearch"
          >
          <div class="search-mode-toggle">
            <button class="smode-btn" :class="{ active: searchMode === 'exact' }" @click="searchMode = 'exact'; performSearch()" title="Exact text match">Exact</button>
            <button class="smode-btn" :class="{ active: searchMode === 'semantic' }" @click="searchMode = 'semantic'; performSearch()" title="Semantic / AI search">AI</button>
          </div>
          <span v-if="searchQuery && searchMode === 'exact'" class="search-results-count">
            {{ searchMatchIndex + 1 }} / {{ searchMatches.length }} matches
          </span>
          <span v-if="searchQuery && searchMode === 'semantic'" class="search-results-count">
            {{ semanticResults.length }} results
          </span>
          <button v-if="searchQuery && searchMode === 'exact'" class="search-nav-btn" @click="jumpToPrevMatch" title="Previous">
            <span class="material-icons">keyboard_arrow_up</span>
          </button>
          <button v-if="searchQuery && searchMode === 'exact'" class="search-nav-btn" @click="jumpToNextMatch" title="Next">
            <span class="material-icons">keyboard_arrow_down</span>
          </button>
          <select v-model="searchPanel" class="search-panel-select" @change="performSearch">
            <option value="both">Both Panels</option>
            <option value="left">Previous Only</option>
            <option value="right">Current Only</option>
          </select>
          <button v-if="searchQuery" class="search-nav-btn" @click="clearSearch" title="Clear">
            <span class="material-icons">close</span>
          </button>
        </div>

        <!-- Semantic Search Results Dropdown -->
        <div v-if="searchMode === 'semantic' && semanticResults.length > 0" class="semantic-results">
          <div v-for="(result, ri) in semanticResults" :key="ri" class="semantic-result" @click="scrollToSection(result.section)">
            <div class="sr-header">
              <span class="sr-section">{{ result.sectionTitle }}</span>
              <span class="sr-score">{{ result.score }}% match</span>
            </div>
            <div class="sr-text" v-html="result.highlight"></div>
          </div>
        </div>

        <!-- Compare Controls Bar -->
        <div class="compare-controls-bar">
          <div class="compare-select-group">
            <label>Previous Version:</label>
            <select v-model="compareLeft">
              <option :value="null" disabled>Select version</option>
              <option v-for="v in versionList" :key="'l-'+v.key" :value="v.key">{{ v.label }}</option>
            </select>
          </div>
          <span class="material-icons compare-arrow-icon">compare_arrows</span>
          <div class="compare-select-group">
            <label>Current Version:</label>
            <select v-model="compareRight">
              <option value="current">Current (live)</option>
              <option v-for="v in versionList" :key="'r-'+v.key" :value="v.key">{{ v.label }}</option>
            </select>
          </div>
          <div class="compare-section-filter">
            <label>Filter:</label>
            <select v-model="activeSection">
              <option :value="null">All sections</option>
              <option v-for="(info, key) in SECTION_MAP" :key="key" :value="key">{{ info.title }}</option>
            </select>
          </div>
          <div class="highlight-options">
            <button class="hl-toggle" :class="{ on: highlightEnabled }" @click="highlightEnabled = !highlightEnabled" :title="highlightEnabled ? 'Disable highlights' : 'Enable highlights'">
              <span class="material-icons">{{ highlightEnabled ? 'visibility' : 'visibility_off' }}</span>
            </button>
            <template v-if="highlightEnabled">
              <div class="scope-btns">
                <button class="scope-btn" :class="{ active: diffScope === 'word' }" @click="diffScope = 'word'">Word</button>
                <button class="scope-btn" :class="{ active: diffScope === 'sentence' }" @click="diffScope = 'sentence'">Sentence</button>
                <button class="scope-btn" :class="{ active: diffScope === 'block' }" @click="diffScope = 'block'">Block</button>
              </div>
              <div class="color-pick">
                <label>Add:</label>
                <input type="color" v-model="hlColorAdd" class="color-swatch">
                <label>Del:</label>
                <input type="color" v-model="hlColorDel" class="color-swatch">
              </div>
              <div class="underline-toggle">
                <label class="toggle-label">
                  <input type="checkbox" v-model="hlUnderline">
                  <span>Underline</span>
                </label>
              </div>
            </template>
          </div>
        </div>

        <!-- Side-by-Side Panels -->
        <div class="compare-split">
          <!-- Left Panel — Previous Version (read-only) -->
          <div class="compare-panel-main left">
            <div class="panel-label">
              <span class="material-icons">history</span> Previous Version
            </div>
            <div class="panel-content" ref="leftPanel">
              <template v-for="(info, key) in filteredCompareSections" :key="'cl-'+key">
                <div class="compare-section" :id="'compare-left-' + key">
                  <h4 class="compare-section-title">{{ info.title }}</h4>
                  <div v-html="computedLeftHtml[key]" class="compare-text"></div>
                </div>
              </template>
            </div>
          </div>

          <!-- Right Panel — Current Version (editable) -->
          <div class="compare-panel-main right">
            <div class="panel-label">
              <span class="material-icons">edit_document</span> Current Version
              <button class="edit-mode-btn" @click="editMode = !editMode">
                <span class="material-icons">{{ editMode ? 'visibility' : 'edit' }}</span>
                {{ editMode ? 'View' : 'Edit' }}
              </button>
              <button v-if="editMode && isDirty" class="edit-mode-btn save-inline-btn" @click="showSaveModal = true">
                <span class="material-icons">save</span> Save
              </button>
            </div>
            <div class="panel-content" ref="rightPanel">
              <template v-for="(info, key) in filteredCompareSections" :key="'cr-'+key">
                <div class="compare-section" :id="'section-' + key" :data-section="key" :ref="el => { if (el) sectionRefs[key] = el }">
                  <h4 class="compare-section-title">{{ info.title }}</h4>
                  <template v-if="!editMode">
                    <div v-html="computedRightHtml[key]" class="compare-text"></div>
                  </template>
                  <div v-else class="section-edit">
                    <textarea
                      class="section-textarea"
                      v-model="sections[key]"
                      :placeholder="'Enter ' + info.title + '...'"
                      @input="markDirty"
                    ></textarea>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Side-by-Side Compare Modal -->
    <div v-if="showCompare" class="modal-overlay" @click.self="showCompare = false">
      <div class="floating-window compare-window">
        <div class="fw-header">
          <h2><span class="material-icons">compare</span> Version Comparison</h2>
          <button class="btn-close" @click="showCompare = false"><span class="material-icons">close</span></button>
        </div>
        <div class="compare-controls">
          <div class="compare-select-group">
            <label>Left (older):</label>
            <select v-model="compareLeft">
              <option :value="null" disabled>Select version</option>
              <option v-for="v in versionList" :key="'l-'+v.key" :value="v.key">{{ v.label }}</option>
            </select>
          </div>
          <span class="material-icons compare-arrow">arrow_forward</span>
          <div class="compare-select-group">
            <label>Right (newer):</label>
            <select v-model="compareRight">
              <option :value="null" disabled>Select version</option>
              <option value="current">Current (unsaved)</option>
              <option v-for="v in versionList" :key="'r-'+v.key" :value="v.key">{{ v.label }}</option>
            </select>
          </div>
          <div class="compare-section-filter">
            <label>Section:</label>
            <select v-model="activeSection">
              <option :value="null">All sections</option>
              <option v-for="(info, key) in SECTION_MAP" :key="key" :value="key">{{ info.title }}</option>
            </select>
          </div>
        </div>
        <div class="fw-body compare-body">
          <div class="compare-panels">
            <div class="compare-panel left">
              <div class="compare-panel-header">Left Version</div>
              <div class="compare-panel-content">
                <template v-for="(info, key) in filteredCompareSections" :key="'cl-'+key">
                  <h4 class="compare-section-title">{{ info.title }}</h4>
                  <div v-html="renderDiffLeft(key)" class="compare-text"></div>
                </template>
                <p v-if="!compareLeft" class="empty-state-sm">Select a version on the left</p>
              </div>
            </div>
            <div class="compare-panel right">
              <div class="compare-panel-header">Right Version</div>
              <div class="compare-panel-content">
                <template v-for="(info, key) in filteredCompareSections" :key="'cr-'+key">
                  <h4 class="compare-section-title">{{ info.title }}</h4>
                  <div v-html="renderDiffRight(key)" class="compare-text"></div>
                </template>
                <p v-if="!compareRight" class="empty-state-sm">Select a version on the right</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Save Version Modal -->
    <div v-if="showSaveModal" class="modal-overlay" @click.self="showSaveModal = false">
      <div class="save-modal">
        <div class="save-modal-header">
          <h3><span class="material-icons">save</span> Save New Version</h3>
          <button class="btn-close" @click="showSaveModal = false"><span class="material-icons">close</span></button>
        </div>
        <div class="save-modal-body">
          <div class="save-field">
            <label>Version Title</label>
            <input v-model="saveTitle" type="text" placeholder="e.g. Added Business Model Canvas, Fixed Background section..." class="save-input">
          </div>
          <div class="save-field">
            <label>Author</label>
            <input v-model="saveAuthor" type="text" placeholder="Author name" class="save-input">
          </div>
          <div class="save-field">
            <label>Change Summary</label>
            <textarea v-model="newSummary" rows="3" placeholder="Describe what was changed in this version..." class="save-input save-textarea"></textarea>
          </div>
          <div class="save-info">
            <span class="material-icons">info</span>
            This will be saved as <strong>v{{ nextVersionNumber }}</strong>
          </div>
        </div>
        <div class="save-modal-footer">
          <button class="btn btn-ghost" @click="showSaveModal = false">Cancel</button>
          <button class="btn btn-outline-save" @click="saveCurrentVersion" :disabled="saving" title="Overwrite current version without creating a new one">
            <span v-if="saving" class="material-icons spin">sync</span>
            <span v-else class="material-icons">save</span>
            {{ saving ? 'Saving...' : 'Save Current' }}
          </button>
          <button class="btn btn-primary" @click="saveVersion" :disabled="saving">
            <span v-if="saving" class="material-icons spin">sync</span>
            <span v-else class="material-icons">add_circle</span>
            {{ saving ? 'Saving...' : 'Save as New Version (v' + nextVersionNumber + ')' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Create Version from File Modal -->
    <div v-if="showCreateVersionModal" class="modal-overlay" @click.self="showCreateVersionModal = false">
      <div class="save-modal" style="max-width: 550px;">
        <div class="save-modal-header">
          <h3><span class="material-icons">upload_file</span> Create Version from File</h3>
          <button class="btn-close" @click="showCreateVersionModal = false"><span class="material-icons">close</span></button>
        </div>
        <div class="save-modal-body">
          <div class="file-drop-zone" @dragover.prevent @drop.prevent="handleVersionFileDrop" @click="$refs.versionFileInput.click()">
            <input ref="versionFileInput" type="file" accept=".docx,.pdf" style="display:none" @change="handleVersionFileSelect">
            <span class="material-icons" style="font-size: 36px; color: #3B82F6;">cloud_upload</span>
            <p v-if="!versionFileName">Drop a Word (.docx) or PDF file here</p>
            <p v-else style="color: #22C55E;"><span class="material-icons" style="vertical-align: middle;">check_circle</span> {{ versionFileName }}</p>
            <small>Or click to browse</small>
          </div>
          <div class="save-field">
            <label>Version Title *</label>
            <input v-model="createVersionTitle" type="text" placeholder="e.g. Chapter 1 Revision — Panel Feedback Addressed" class="save-input">
          </div>
          <div class="save-field">
            <label>Author</label>
            <input v-model="createVersionAuthor" type="text" placeholder="Author name" class="save-input">
          </div>
          <div class="save-field">
            <label>Change Summary</label>
            <textarea v-model="createVersionSummary" rows="2" placeholder="What's different in this version?" class="save-input save-textarea"></textarea>
          </div>
          <div class="save-info">
            <span class="material-icons">info</span>
            Will be saved as <strong>v{{ nextVersionNumber }}</strong> — Date: <strong>{{ new Date().toLocaleDateString() }}</strong>
          </div>
        </div>
        <div class="save-modal-footer">
          <button class="btn btn-ghost" @click="showCreateVersionModal = false">Cancel</button>
          <button class="btn btn-primary" @click="createVersionFromFile" :disabled="!versionFileData || !createVersionTitle.trim() || creatingVersion">
            <span v-if="creatingVersion" class="material-icons spin">sync</span>
            <span v-else class="material-icons">check</span>
            {{ creatingVersion ? 'Creating...' : 'Create Version' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Template Viewer — Full formatted thesis document -->
    <div v-if="showTemplateViewer" class="modal-overlay" @click.self="showTemplateViewer = false">
      <div class="floating-window template-window">
        <div class="fw-header">
          <h2><span class="material-icons">auto_stories</span> Thesis Document Preview</h2>
          <div style="display:flex;gap:8px;align-items:center;">
            <button class="btn btn-sm btn-ghost" @click="exportDocx"><span class="material-icons">download</span> DOCX</button>
            <button class="btn btn-sm btn-ghost" @click="exportPdf"><span class="material-icons">picture_as_pdf</span> PDF</button>
            <button class="btn-close" @click="showTemplateViewer = false"><span class="material-icons">close</span></button>
          </div>
        </div>
        <div class="fw-body template-body">
          <!-- Title Page -->
          <div class="tpl-page title-page">
            <div class="tpl-title-content">
              <p>BILLSENSE: DEVELOPING A CAMERA-BASED SYSTEM FOR DETECTING COUNTERFEIT AND GENUINE PHILIPPINE CURRENCY</p>
              <br><br>
              <p style="font-weight:normal;font-size:12pt;">A Thesis Proposal Presented to the</p>
              <p style="font-weight:normal;font-size:12pt;">Faculty of the Graduate School</p>
              <p style="font-weight:normal;font-size:12pt;">University of the Cordilleras</p>
              <br>
              <p style="font-weight:normal;font-size:12pt;">In partial Fulfillment of the Requirements for the Degree</p>
              <p style="font-size:12pt;">MASTER OF SCIENCE IN CRIMINAL JUSTICE WITH SPECIALIZATION IN CRIMINOLOGY</p>
              <br><br>
              <p style="font-weight:normal;font-size:12pt;">by</p>
              <p style="font-size:13pt;">JOY NICOLE CODOD CANUTAB</p>
              <br>
              <p style="font-weight:normal;font-size:12pt;">APRIL 2025</p>
            </div>
            <span class="tpl-page-num">i</span>
          </div>

          <!-- Chapter I -->
          <div class="tpl-page">
            <h2 class="tpl-chapter">Chapter I</h2>
            <h3 class="tpl-chapter-title">THE PROBLEM</h3>
            <template v-for="(info, key) in chapter1Sections" :key="'tpl1-'+key">
              <h4 class="tpl-section-heading">{{ info.title }}</h4>
              <p v-for="(para, pi) in getParagraphs(key)" :key="pi" class="tpl-paragraph">{{ para }}</p>
              <p v-if="!sections[key] || !sections[key].trim()" class="tpl-empty"><em>[Section empty]</em></p>
            </template>
          </div>

          <!-- Chapter II -->
          <div class="tpl-page">
            <h2 class="tpl-chapter">Chapter 2</h2>
            <h3 class="tpl-chapter-title">DESIGN AND METHODOLOGY</h3>
            <template v-for="(info, key) in chapter2Sections" :key="'tpl2-'+key">
              <h4 class="tpl-section-heading">{{ info.title }}</h4>
              <p v-for="(para, pi) in getParagraphs(key)" :key="pi" class="tpl-paragraph">{{ para }}</p>
              <p v-if="!sections[key] || !sections[key].trim()" class="tpl-empty"><em>[Section empty]</em></p>
            </template>
          </div>

          <!-- References -->
          <div class="tpl-page">
            <h4 class="tpl-section-heading" style="text-align:center;">REFERENCES</h4>
            <p v-for="(para, pi) in getParagraphs('references')" :key="pi" class="tpl-reference">{{ para }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Comment Tooltip -->
    <div v-if="tooltip.visible" class="comment-tooltip" :style="{ top: tooltip.y + 'px', left: tooltip.x + 'px' }">
      <div class="tooltip-header">
        <span class="material-icons">comment</span> Panel Comment
      </div>
      <div class="tooltip-body">{{ tooltip.text }}</div>
      <div class="tooltip-from">{{ tooltip.panelist }}</div>
    </div>

    <!-- Panel Requests Floating Window -->
    <div v-if="showPanelModal" class="modal-overlay" @click.self="showPanelModal = false">
      <div class="floating-window panel-window">
        <div class="fw-header">
          <h2><span class="material-icons">rate_review</span> Panel Requests</h2>
          <button class="btn-close" @click="showPanelModal = false"><span class="material-icons">close</span></button>
        </div>
        <div class="fw-body">
          <!-- File Drop Zone -->
          <div
            class="file-drop-zone"
            :class="{ 'drag-over': dragOver, 'parsing': parsingFile }"
            @dragover.prevent="dragOver = true"
            @dragleave="dragOver = false"
            @drop.prevent="handleFileDrop($event)"
          >
            <template v-if="parsingFile">
              <span class="material-icons spin">sync</span>
              <p>Parsing document...</p>
            </template>
            <template v-else>
              <span class="material-icons">upload_file</span>
              <p>Drop a Word (.docx) or PDF file here to import panel comments</p>
              <p class="drop-hint">Or <label class="file-browse-label"><input type="file" accept=".docx,.pdf,.txt" @change="handleFileSelect($event)" hidden>browse files</label></p>
            </template>
          </div>

          <!-- Manual Input -->
          <div class="manual-input-bar">
            <input v-model="newPanelistName" type="text" placeholder="Panelist name (e.g. Dr. Cruz)" class="manual-input name-input">
            <input v-model="newCommentText" type="text" placeholder="Enter comment or suggestion..." class="manual-input comment-input" @keydown.enter="addManualComment">
            <button class="btn btn-sm btn-primary" @click="addManualComment" :disabled="!newCommentText.trim()">
              <span class="material-icons">add</span> Add
            </button>
          </div>

          <div v-if="!panelists.length && !parsingFile" class="empty-state">
            <span class="material-icons empty-icon">inbox</span>
            <p>No panel requests found. Drop a file above or add comments manually.</p>
          </div>
          <div v-for="(panelist, pi) in panelists" :key="pi" class="panelist-accordion">
            <div class="accordion-header" @click="toggleAccordion(pi)">
              <span class="material-icons accordion-arrow" :class="{ rotated: openAccordions[pi] }">chevron_right</span>
              <span class="panelist-name">{{ panelist.name }}</span>
              <span class="comment-count-badge">{{ panelist.comments ? panelist.comments.length : 0 }} comments</span>
            </div>
            <div v-if="openAccordions[pi]" class="accordion-body">
              <div v-for="comment in panelist.comments" :key="comment.id" class="comment-card">
                <div class="comment-text">{{ comment.text }}</div>
                <div class="comment-meta">
                  <span class="comment-section-link" @click="scrollToSection(comment.section); showPanelModal = false">
                    <span class="material-icons">link</span> {{ getSectionTitle(comment.section) }}
                  </span>
                  <span class="status-badge" :class="comment.status">{{ comment.status }}</span>
                </div>
                <div class="comment-actions">
                  <button class="btn btn-sm btn-ghost" @click="generateSolution(comment)">
                    <span class="material-icons">auto_fix_high</span> Generate Solution
                  </button>
                  <button class="btn btn-sm btn-ghost" v-if="comment.solution" @click="previewSolution(comment)">
                    <span class="material-icons">compare</span> Preview
                  </button>
                  <button class="btn btn-sm btn-accent" v-if="comment.solution && comment.status !== 'applied'" @click="applySolution(comment)">
                    <span class="material-icons">check</span> Apply
                  </button>
                  <button class="btn btn-sm btn-view-thesis" v-if="comment.status === 'applied'" @click="viewAppliedInThesis(comment)">
                    <span class="material-icons">visibility</span> View in Thesis
                  </button>
                  <button class="btn btn-sm btn-warning" v-if="comment.status === 'applied'" @click="revertSolution(comment)">
                    <span class="material-icons">undo</span> Revert
                  </button>
                  <button class="btn btn-sm btn-danger" @click="deleteComment(comment, panelist)" title="Delete comment">
                    <span class="material-icons">delete</span>
                  </button>
                </div>
                <!-- Solution Preview (inline side-by-side) -->
                <div v-if="comment.showPreview && comment.solution" class="solution-preview">
                  <div class="preview-col">
                    <h5>Current</h5>
                    <div class="preview-content">{{ getSectionSnippet(comment.section) }}</div>
                  </div>
                  <div class="preview-col suggested">
                    <h5>Suggested</h5>
                    <div class="preview-content">{{ comment.solution }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Validation Floating Window -->
    <div v-if="showValidationModal" class="modal-overlay" @click.self="showValidationModal = false">
      <div class="floating-window validation-window">
        <div class="fw-header">
          <h2><span class="material-icons">verified</span> Thesis Validation Report</h2>
          <button class="btn-close" @click="showValidationModal = false"><span class="material-icons">close</span></button>
        </div>
        <div class="fw-body">
          <!-- Score Rings -->
          <div class="score-rings">
            <div class="score-card">
              <div class="score-ring" :style="scoreRingStyle(validationResult.formatScore)">
                <span class="score-value">{{ validationResult.formatScore }}</span>
              </div>
              <span class="score-label">Format</span>
            </div>
            <div class="score-card">
              <div class="score-ring" :style="scoreRingStyle(validationResult.completenessScore)">
                <span class="score-value">{{ validationResult.completenessScore }}</span>
              </div>
              <span class="score-label">Completeness</span>
            </div>
            <div class="score-card">
              <div class="score-ring" :style="scoreRingStyle(validationResult.panelComplianceScore)">
                <span class="score-value">{{ validationResult.panelComplianceScore }}</span>
              </div>
              <span class="score-label">Panel Compliance</span>
            </div>
            <div class="score-card overall">
              <div class="score-ring large" :style="scoreRingStyle(validationResult.overallScore)">
                <span class="score-value">{{ validationResult.overallScore }}</span>
              </div>
              <span class="score-label">Overall</span>
            </div>
          </div>

          <!-- Issues List -->
          <div class="issues-section">
            <h3 class="issues-title">Issues Found</h3>
            <div class="issues-tabs">
              <button
                v-for="cat in ['All', 'Format', 'Content', 'Panel Requests']"
                :key="cat"
                class="issue-tab"
                :class="{ active: issueFilter === cat }"
                @click="issueFilter = cat"
              >
                {{ cat }}
                <span class="issue-count">{{ getIssueCategoryCount(cat) }}</span>
              </button>
            </div>
            <div class="issues-list">
              <div v-for="(issue, idx) in filteredIssues" :key="idx" class="issue-card" :class="issue.severity.toLowerCase()">
                <div class="issue-severity-badge" :class="issue.severity.toLowerCase()">{{ issue.severity }}</div>
                <div class="issue-details">
                  <div class="issue-message">{{ issue.message }}</div>
                  <div class="issue-meta">
                    <span class="issue-section" v-if="issue.section">
                      <span class="material-icons">folder</span> {{ getSectionTitle(issue.section) }}
                    </span>
                    <span class="issue-category">{{ issue.category }}</span>
                  </div>
                  <div class="issue-fix" v-if="issue.fix">
                    <span class="material-icons">lightbulb</span> {{ issue.fix }}
                  </div>
                </div>
              </div>
              <div v-if="filteredIssues.length === 0" class="empty-state">
                <span class="material-icons empty-icon">check_circle</span>
                <p>No issues found in this category.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <transition name="toast-fade">
      <div v-if="toast.visible" class="toast" :class="toast.type">
        <span class="material-icons">{{ toast.icon }}</span>
        {{ toast.message }}
      </div>
    </transition>

    <!-- Draggable AI Suggestion Reference Panel -->
    <transition name="slide-up">
      <div v-if="activeAiSuggestion" class="ai-suggestion-float" ref="aiFloat"
           :style="aiFloatStyle"
           @mousedown.self="startDragFloat">
        <div class="ai-float-header" @mousedown="startDragFloat" style="cursor: grab;">
          <span class="material-icons">auto_fix_high</span>
          <span>AI Suggestion Reference</span>
          <span class="ai-float-section">{{ getSectionTitle(activeAiSuggestion.section) }}</span>
          <button class="ai-float-close" @click="activeAiSuggestion = null" title="Close">
            <span class="material-icons">close</span>
          </button>
        </div>
        <div class="ai-float-body">
          <div class="ai-float-comment">
            <strong>Panel Comment:</strong> {{ activeAiSuggestion.originalComment }}
          </div>
          <div class="ai-float-suggestion">
            <strong>AI Suggestion:</strong>
            <p>{{ activeAiSuggestion.suggestion }}</p>
          </div>
        </div>
        <div class="ai-float-footer">
          <span class="ai-float-hint">
            <span class="material-icons">edit</span>
            Write your response in the editor above — use this suggestion as your guide
          </span>
          <button class="btn btn-sm btn-ghost" @click="copyToClipboard(activeAiSuggestion.suggestion)">
            <span class="material-icons">content_copy</span> Copy
          </button>
          <button class="btn btn-sm btn-accent" @click="activeAiSuggestion = null">
            <span class="material-icons">check</span> Done
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { database, ref } from '../services/firebase.js'
import { get, set, push, onValue } from 'firebase/database'
import { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, PageBreak } from 'docx'
import { saveAs } from 'file-saver'
import { ThesisSearchEngine } from '../services/vectorSearch.js'

const SECTION_MAP = {
  chapter1_background: { title: 'Background of the Study', chapter: 1 },
  chapter1_framework: { title: 'Theoretical/Conceptual Framework', chapter: 1 },
  chapter1_paradigm: { title: 'Paradigm of the Study', chapter: 1 },
  chapter1_statement: { title: 'Statement of the Problem and Hypotheses', chapter: 1 },
  chapter2_design: { title: 'Research Design and Methodology', chapter: 2 },
  chapter2_scope: { title: 'Scope of the Study', chapter: 2 },
  chapter2_population: { title: 'Population and Locale of the Study', chapter: 2 },
  chapter2_tools: { title: 'Data Gathering Tools', chapter: 2 },
  chapter2_procedure: { title: 'Data Gathering Procedure', chapter: 2 },
  chapter2_treatment: { title: 'Treatment of Data', chapter: 2 },
  chapter2_ethics: { title: 'Ethical Considerations', chapter: 2 },
  references: { title: 'References', chapter: 0 }
}

export default {
  name: 'ThesisValidator',

  data() {
    return {
      SECTION_MAP,
      sections: {},
      panelists: [],
      openAccordions: {},
      versionTab: 'current',
      versionList: [],
      selectedVersion: '',
      editMode: false,
      isDirty: false,
      saving: false,
      exporting: false,
      validating: false,
      activeSection: null,
      sectionRefs: {},

      currentVersionKey: null,
      newSummary: '',
      showCompare: false,
      compareLeft: null,
      compareRight: null,

      // Modals
      showPanelModal: false,
      showValidationModal: false,
      showTemplateViewer: false,
      showSaveModal: false,
      showCreateVersionModal: false,
      createVersionTitle: '',
      createVersionAuthor: 'Joy Nicole Codod Canutab',
      createVersionSummary: '',
      versionFileName: '',
      versionFileData: null,
      creatingVersion: false,
      saveTitle: '',
      saveAuthor: 'Joy Nicole Codod Canutab',
      appliedHighlight: null,
      activeAiSuggestion: null,
      aiFloatPos: { x: null, y: null },
      aiFloatDragging: false,
      aiFloatDragStart: { x: 0, y: 0, elX: 0, elY: 0 },

      // Tooltip
      tooltip: { visible: false, x: 0, y: 0, text: '', panelist: '' },

      // Validation
      validationResult: {
        formatScore: 0,
        completenessScore: 0,
        panelComplianceScore: 0,
        overallScore: 0,
        issues: []
      },
      issueFilter: 'All',

      // Highlight options
      highlightEnabled: false,
      diffScope: 'word',
      hlColorAdd: '#22C55E',
      hlColorDel: '#EF4444',
      hlUnderline: true,

      // Search
      searchQuery: '',
      searchMatches: [],
      searchMatchIndex: 0,
      searchPanel: 'both',
      searchEngine: new ThesisSearchEngine(),
      semanticResults: [],
      showSemanticResults: false,
      searchMode: 'exact', // 'exact' | 'semantic'

      // Panel request file drop
      dragOver: false,
      parsingFile: false,
      newPanelistName: '',
      newCommentText: '',

      // Rendered HTML cache (reactive - triggers v-html updates)
      renderedLeft: {},
      renderedRight: {},

      // Toast
      toast: { visible: false, message: '', type: 'info', icon: 'info' },

      // Firebase listeners
      unsubscribeSections: null,
      unsubscribePanel: null,
      unsubscribeVersions: null
    }
  },

  computed: {
    aiFloatStyle() {
      if (this.aiFloatPos.x !== null) {
        return {
          position: 'fixed',
          left: this.aiFloatPos.x + 'px',
          top: this.aiFloatPos.y + 'px',
          bottom: 'auto',
          transform: 'none',
        }
      }
      return {}
    },
    chapter1Sections() {
      const result = {}
      for (const [key, info] of Object.entries(SECTION_MAP)) {
        if (info.chapter === 1) result[key] = info
      }
      return result
    },
    chapter2Sections() {
      const result = {}
      for (const [key, info] of Object.entries(SECTION_MAP)) {
        if (info.chapter === 2) result[key] = info
      }
      return result
    },
    // COMPUTED HTML — Vue tracks all reactive deps automatically
    computedLeftHtml() {
      // Access reactive deps so Vue knows to recompute
      const _q = this.searchQuery
      const _p = this.searchPanel
      const _hl = this.highlightEnabled
      const _ds = this.diffScope
      const _ca = this.hlColorAdd
      const _cd = this.hlColorDel
      const _u = this.hlUnderline
      const _cl = this.compareLeft
      const _s = this.sections

      const result = {}
      for (const key of Object.keys(SECTION_MAP)) {
        if (this.compareLeft) {
          result[key] = this.renderDiffLeft(key)
        } else {
          // Show first version or current sections
          const firstKey = this.versionList.length ? this.versionList[this.versionList.length - 1].key : null
          const data = firstKey ? this.getVersionData(firstKey) : {}
          const text = data[key] || this.sections[key] || ''
          result[key] = text ? this.renderPlainWithSearch(text, 'left') : ''
        }
      }
      return result
    },
    computedRightHtml() {
      const _q = this.searchQuery
      const _p = this.searchPanel
      const _hl = this.highlightEnabled
      const _ds = this.diffScope
      const _ca = this.hlColorAdd
      const _cd = this.hlColorDel
      const _u = this.hlUnderline
      const _cl = this.compareLeft

      const result = {}
      for (const key of Object.keys(SECTION_MAP)) {
        const text = this.sections[key] || ''
        if (!text.trim()) {
          result[key] = ''
          continue
        }
        if (this.compareLeft) {
          result[key] = this.renderDiffRight(key)
        } else {
          result[key] = this.renderPlainWithSearch(text, 'right')
        }
      }
      return result
    },
    nextVersionNumber() {
      if (!this.versionList.length) return 1
      return Math.max(...this.versionList.map(v => v.versionNumber || 1)) + 1
    },
    filteredCompareSections() {
      if (this.activeSection) {
        return { [this.activeSection]: SECTION_MAP[this.activeSection] }
      }
      return SECTION_MAP
    },
    chapter2StartPage() {
      // Title(i) + TOC(ii) + Ch1 header(1) + 4 ch1 sections(2-5) = page 6
      return Object.keys(this.chapter1Sections).length + 2
    },
    referencesPageNumber() {
      return Object.keys(this.chapter1Sections).length + Object.keys(this.chapter2Sections).length + 3
    },
    pendingRequestCount() {
      let count = 0
      for (const p of this.panelists) {
        if (p.comments) {
          count += p.comments.filter(c => c.status === 'pending').length
        }
      }
      return count
    },
    filteredIssues() {
      if (this.issueFilter === 'All') return this.validationResult.issues
      return this.validationResult.issues.filter(i => i.category === this.issueFilter)
    }
  },

  methods: {
    // --- Firebase ---
    async initFirebase() {
      // Load thesis versions using get() for reliable initial load
      const versionsRef = ref(database, 'thesis_versions')
      try {
        const snapshot = await get(versionsRef)
        console.log('[ThesisValidator] Firebase get() exists:', snapshot.exists())
        if (snapshot.exists()) {
          const allVersions = snapshot.val()
          const entries = Object.entries(allVersions)
            .map(([key, val]) => ({ key, ...val }))
            .sort((a, b) => (b.versionNumber || 0) - (a.versionNumber || 0))

          console.log('[ThesisValidator] Found', entries.length, 'versions')

          // Build version list with data pre-populated (Vue 3 reactivity requires this)
          this.versionList = entries.map(v => {
            const vData = {}
            if (v.sections) {
              for (const sKey of Object.keys(SECTION_MAP)) {
                const sec = v.sections[sKey]
                vData[sKey] = (typeof sec === 'object' && sec !== null) ? (sec.content || '') : (typeof sec === 'string' ? sec : '')
              }
            }
            return {
              key: v.key,
              label: `v${v.versionNumber || 1} — ${v.author || 'Unknown'} (${new Date(v.date || 0).toLocaleDateString()})`,
              versionNumber: v.versionNumber || 1,
              author: v.author || 'Unknown',
              date: v.date,
              changesSummary: v.changesSummary || '',
              data: vData
            }
          })

          // Load latest version into sections
          const latest = entries[0]
          if (latest && latest.sections) {
            const loaded = {}
            for (const sKey of Object.keys(SECTION_MAP)) {
              const sec = latest.sections[sKey]
              if (typeof sec === 'object' && sec !== null) {
                loaded[sKey] = sec.content || ''
              } else if (typeof sec === 'string') {
                loaded[sKey] = sec
              } else {
                loaded[sKey] = ''
              }
            }
            this.sections = loaded
            this.currentVersionKey = latest.key

            // Count loaded words
            let totalWords = 0
            for (const sKey of Object.keys(SECTION_MAP)) {
              totalWords += (loaded[sKey] || '').split(/\s+/).filter(w => w).length
            }
            console.log('[ThesisValidator] Loaded', totalWords, 'words from version', latest.versionNumber)

            // Auto-select first version for compare
            if (this.versionList.length > 0 && !this.compareLeft) {
              this.compareLeft = this.versionList[this.versionList.length - 1].key
              this.compareRight = 'current'
            }

            // Render HTML for panels + build search index
            this.$nextTick(() => {
              this.updateRenderedHtml()
              this.searchEngine.buildIndex(this.sections, SECTION_MAP)
            })

            // Version data already pre-populated above
          }
        } else {
          console.log('[ThesisValidator] No thesis data in Firebase')
          const empty = {}
          for (const sKey of Object.keys(SECTION_MAP)) { empty[sKey] = '' }
          this.sections = empty
          this.versionList = []
        }
      } catch (err) {
        console.error('[ThesisValidator] Failed to load thesis:', err)
      }

      // Load panel requests — use onValue for real-time updates
      const panelRef = ref(database, 'thesis_panel_requests')
      this.unsubscribePanel = onValue(panelRef, (panelSnap) => {
        if (panelSnap.exists()) {
          const raw = panelSnap.val()
          let allPanelists = []
          for (const entry of Object.values(raw)) {
            if (entry.panelists) {
              const pList = Array.isArray(entry.panelists) ? entry.panelists : Object.values(entry.panelists)
              allPanelists = allPanelists.concat(pList)
            }
          }
          this.panelists = allPanelists
        } else {
          this.panelists = []
        }
      })
    },

    async loadVersion(versionKey) {
      const found = this.versionList.find(v => v.key === versionKey)
      if (found && found.data) {
        const loaded = {}
        for (const key of Object.keys(SECTION_MAP)) {
          loaded[key] = found.data[key] || ''
        }
        this.sections = loaded
        this.showToast('Version loaded', 'info', 'history')
      }
    },

    async saveVersion() {
      this.saving = true
      try {
        const nextVersion = this.nextVersionNumber
        const sectionsData = {}
        for (const key of Object.keys(SECTION_MAP)) {
          sectionsData[key] = {
            title: SECTION_MAP[key].title,
            content: this.sections[key] || ''
          }
        }
        const newRef = push(ref(database, 'thesis_versions'))
        await set(newRef, {
          versionNumber: nextVersion,
          date: new Date().toISOString(),
          author: this.saveAuthor || 'Editor',
          changesSummary: (this.saveTitle ? this.saveTitle + ' — ' : '') + (this.newSummary || 'Manual save'),
          sections: sectionsData
        })
        this.isDirty = false
        this.newSummary = ''
        this.saveTitle = ''
        this.showSaveModal = false

        // Reload from Firebase to sync version list and template viewer
        await this.initFirebase()
        this.showToast(`Version ${nextVersion} saved`, 'success', 'check_circle')
      } catch (err) {
        console.error('Save error:', err)
        this.showToast('Failed to save: ' + err.message, 'error', 'error')
      } finally {
        this.saving = false
      }
    },

    async saveCurrentVersion() {
      this.saving = true
      try {
        // Find the latest (current) version key — versionList is sorted descending, so [0] is latest
        const latestKey = this.versionList.length ? this.versionList[0].key : null
        if (!latestKey) {
          this.showToast('No existing version to overwrite — use "Save as New Version" instead', 'warning', 'warning')
          this.saving = false
          return
        }
        const sectionsData = {}
        for (const key of Object.keys(SECTION_MAP)) {
          sectionsData[key] = {
            title: SECTION_MAP[key].title,
            content: this.sections[key] || ''
          }
        }
        const latestData = this.versionList[0]
        await set(ref(database, `thesis_versions/${latestKey}`), {
          versionNumber: latestData.versionNumber,
          date: new Date().toISOString(),
          author: this.saveAuthor || latestData.author || 'Editor',
          changesSummary: (this.saveTitle || latestData.changesSummary || '') + ' (updated)',
          sections: sectionsData
        })
        this.isDirty = false
        this.showSaveModal = false

        // Reload from Firebase to sync everything
        await this.initFirebase()
        this.showToast(`v${latestData.versionNumber} saved successfully`, 'success', 'check_circle')
      } catch (err) {
        console.error('Save current error:', err)
        this.showToast('Failed to save: ' + err.message, 'error', 'error')
      } finally {
        this.saving = false
      }
    },

    // --- Create Version from File ---
    handleVersionFileDrop(e) {
      const file = e.dataTransfer.files[0]
      if (file) this.processVersionFile(file)
    },
    handleVersionFileSelect(e) {
      const file = e.target.files[0]
      if (file) this.processVersionFile(file)
    },
    async processVersionFile(file) {
      this.versionFileName = file.name
      if (file.name.endsWith('.docx')) {
        const mammoth = await import('mammoth')
        const arrayBuf = await file.arrayBuffer()
        const result = await mammoth.default.extractRawText({ arrayBuffer: arrayBuf })
        this.versionFileData = this.parseThesisText(result.value)
      } else if (file.name.endsWith('.pdf')) {
        this.showToast('PDF parsing — extracting text...', 'info', 'info')
        const arrayBuf = await file.arrayBuffer()
        const text = await this.extractPdfText(arrayBuf)
        this.versionFileData = this.parseThesisText(text)
      } else {
        this.showToast('Only .docx and .pdf files are supported', 'warning', 'warning')
      }
    },
    parseThesisText(text) {
      // Auto-detect sections from text
      const sections = {}
      const sectionPatterns = [
        { key: 'chapter1_background', patterns: ['background of the study'] },
        { key: 'chapter1_framework', patterns: ['theoretical', 'conceptual framework'] },
        { key: 'chapter1_paradigm', patterns: ['paradigm of the study'] },
        { key: 'chapter1_statement', patterns: ['statement of the problem'] },
        { key: 'chapter2_design', patterns: ['research design', 'design and methodology'] },
        { key: 'chapter2_scope', patterns: ['scope of the study'] },
        { key: 'chapter2_population', patterns: ['population and locale'] },
        { key: 'chapter2_tools', patterns: ['data gathering tool'] },
        { key: 'chapter2_procedure', patterns: ['data gathering procedure'] },
        { key: 'chapter2_treatment', patterns: ['treatment of data'] },
        { key: 'chapter2_ethics', patterns: ['ethical consideration'] },
        { key: 'references', patterns: ['references'] },
      ]

      const lines = text.split('\n')
      let currentSection = null

      for (const line of lines) {
        const lower = line.trim().toLowerCase()
        for (const sp of sectionPatterns) {
          if (sp.patterns.some(p => lower.startsWith(p) || lower.includes(p))) {
            currentSection = sp.key
            if (!sections[currentSection]) sections[currentSection] = ''
            break
          }
        }
        if (currentSection && line.trim()) {
          // Don't add the section header itself
          const isHeader = sectionPatterns.some(sp =>
            sp.patterns.some(p => line.trim().toLowerCase().startsWith(p))
          )
          if (!isHeader) {
            sections[currentSection] += (sections[currentSection] ? '\n' : '') + line.trim()
          }
        }
      }
      return sections
    },
    async extractPdfText(arrayBuffer) {
      // Simple PDF text extraction fallback
      try {
        const uint8 = new Uint8Array(arrayBuffer)
        const text = new TextDecoder('utf-8', { fatal: false }).decode(uint8)
        const extracted = text.match(/\(([^)]+)\)/g)?.map(m => m.slice(1, -1)).join(' ') || ''
        return extracted
      } catch {
        return ''
      }
    },
    async createVersionFromFile() {
      if (!this.versionFileData || !this.createVersionTitle.trim()) return
      this.creatingVersion = true
      try {
        const nextVersion = this.nextVersionNumber
        const sectionsData = {}
        for (const key of Object.keys(SECTION_MAP)) {
          sectionsData[key] = {
            title: SECTION_MAP[key].title,
            content: this.versionFileData[key] || this.sections[key] || ''
          }
        }
        const newRef = push(ref(database, 'thesis_versions'))
        await set(newRef, {
          versionNumber: nextVersion,
          date: new Date().toISOString(),
          author: this.createVersionAuthor || 'Editor',
          changesSummary: this.createVersionTitle + (this.createVersionSummary ? ' — ' + this.createVersionSummary : ''),
          sections: sectionsData
        })

        // Update current live sections with the uploaded content
        for (const key of Object.keys(SECTION_MAP)) {
          if (this.versionFileData[key]) {
            this.sections[key] = this.versionFileData[key]
          }
        }

        this.isDirty = false
        this.showCreateVersionModal = false
        this.createVersionTitle = ''
        this.createVersionSummary = ''
        this.versionFileName = ''
        this.versionFileData = null
        this.showToast(`Version ${nextVersion} created from file`, 'success', 'check_circle')
      } catch (err) {
        console.error('Create version error:', err)
        this.showToast('Failed: ' + err.message, 'error', 'error')
      } finally {
        this.creatingVersion = false
      }
    },

    markDirty() {
      this.isDirty = true
    },

    // --- Section helpers ---
    getParagraphs(key) {
      const text = this.sections[key] || ''
      if (!text.trim()) return []
      return text.split(/\n\n+/).filter(p => p.trim())
    },

    getWordCount(key) {
      const text = this.sections[key] || ''
      if (!text.trim()) return 0
      return text.trim().split(/\s+/).length
    },

    getTocPageNumber(key) {
      const keys = Object.keys(SECTION_MAP)
      const idx = keys.indexOf(key)
      const info = SECTION_MAP[key]
      if (info.chapter === 1) {
        // ch1 header on page 1, sections start on 2
        return idx + 2
      } else if (info.chapter === 2) {
        const ch1Count = Object.keys(this.chapter1Sections).length
        // ch2 header page + offset
        return ch1Count + 2 + (idx - ch1Count)
      } else {
        return this.referencesPageNumber
      }
    },

    getPageNumber(key) {
      return this.getTocPageNumber(key)
    },

    getSectionTitle(key) {
      return SECTION_MAP[key] ? SECTION_MAP[key].title : key
    },

    getSectionSnippet(key) {
      const text = this.sections[key] || ''
      return text.length > 300 ? text.substring(0, 300) + '...' : text
    },

    sectionHasPendingComments(sectionKey) {
      for (const p of this.panelists) {
        if (p.comments) {
          if (p.comments.some(c => c.section === sectionKey && c.status === 'pending')) return true
        }
      }
      return false
    },

    getCommentsForSection(sectionKey) {
      const comments = []
      for (const p of this.panelists) {
        if (p.comments) {
          for (const c of p.comments) {
            if (c.section === sectionKey) {
              comments.push({ ...c, panelist: p.name })
            }
          }
        }
      }
      return comments
    },

    isParagraphAffected(sectionKey, paraIndex) {
      // Mark first paragraph as affected if there are pending comments for this section
      if (paraIndex === 0) {
        return this.sectionHasPendingComments(sectionKey)
      }
      return false
    },

    // --- File Drop for Panel Requests ---
    async handleFileDrop(event) {
      this.dragOver = false
      const file = event.dataTransfer?.files?.[0]
      if (file) await this.parseUploadedFile(file)
    },
    async handleFileSelect(event) {
      const file = event.target?.files?.[0]
      if (file) await this.parseUploadedFile(file)
    },
    async parseUploadedFile(file) {
      this.parsingFile = true
      try {
        let text = ''
        if (file.name.endsWith('.docx')) {
          const mammoth = await import('mammoth')
          const arrayBuffer = await file.arrayBuffer()
          const result = await mammoth.default.extractRawText({ arrayBuffer })
          text = result.value
        } else if (file.name.endsWith('.txt')) {
          text = await file.text()
        } else if (file.name.endsWith('.pdf')) {
          this.showToast('PDF parsing — extracting text...', 'info', 'info')
          text = await file.text() // Basic text extraction
        }

        if (!text.trim()) {
          this.showToast('Could not extract text from file', 'error', 'error')
          return
        }

        // Parse text into panelist comments
        const parsed = this.parseCommentsFromText(text, file.name)
        if (parsed.panelists.length > 0) {
          // Save to Firebase
          const panelRef = push(ref(database, 'thesis_panel_requests'))
          await set(panelRef, {
            title: `Imported from ${file.name}`,
            date: new Date().toISOString(),
            source: file.name,
            status: 'pending',
            panelists: parsed.panelists
          })
          this.showToast(`Imported ${parsed.totalComments} comments from ${parsed.panelists.length} panelist(s)`, 'success', 'check_circle')
        } else {
          this.showToast('No panel comments detected in file', 'warning', 'warning')
        }
      } catch (err) {
        console.error('File parse error:', err)
        this.showToast('Failed to parse file: ' + err.message, 'error', 'error')
      } finally {
        this.parsingFile = false
      }
    },
    parseCommentsFromText(text, fileName) {
      const lines = text.split('\n').map(l => l.trim()).filter(l => l)
      const panelists = []
      let currentPanelist = null
      let totalComments = 0

      for (const line of lines) {
        // Detect panelist names (Dr., Prof., etc.)
        const nameMatch = line.match(/^(Dr\.|Prof\.|Mr\.|Ms\.|Mrs\.)\s*(.+?)$/i)
        if (nameMatch && line.length < 60) {
          currentPanelist = { name: line.replace(/[:\-]$/, '').trim(), comments: [] }
          panelists.push(currentPanelist)
          continue
        }

        // If we have a current panelist, add lines as comments
        if (currentPanelist && line.length > 10) {
          // Auto-detect target section
          const section = this.autoDetectSection(line)
          currentPanelist.comments.push({
            id: `imp-${Date.now()}-${totalComments}`,
            text: line,
            section: section,
            status: 'pending'
          })
          totalComments++
        } else if (!currentPanelist && line.length > 15) {
          // No panelist detected yet — create default
          if (!panelists.length) {
            currentPanelist = { name: `Panel (${fileName})`, comments: [] }
            panelists.push(currentPanelist)
          }
          const section = this.autoDetectSection(line)
          currentPanelist.comments.push({
            id: `imp-${Date.now()}-${totalComments}`,
            text: line,
            section: section,
            status: 'pending'
          })
          totalComments++
        }
      }

      return { panelists, totalComments }
    },
    autoDetectSection(text) {
      const lower = text.toLowerCase()
      if (/background|advantage|challenge|existing|app/i.test(lower)) return 'chapter1_background'
      if (/framework|theory|model|canvas/i.test(lower)) return 'chapter1_framework'
      if (/paradigm/i.test(lower)) return 'chapter1_paradigm'
      if (/statement|problem|hypothes|court|evidence/i.test(lower)) return 'chapter1_statement'
      if (/design|method|prototype|phase|developer|storage/i.test(lower)) return 'chapter2_design'
      if (/scope|limit|timeframe/i.test(lower)) return 'chapter2_scope'
      if (/population|locale|sample/i.test(lower)) return 'chapter2_population'
      if (/tool|instrument/i.test(lower)) return 'chapter2_tools'
      if (/procedure/i.test(lower)) return 'chapter2_procedure'
      if (/treatment|analysis/i.test(lower)) return 'chapter2_treatment'
      if (/ethic|security|reverse|feature/i.test(lower)) return 'chapter2_ethics'
      if (/reference|cite/i.test(lower)) return 'references'
      return 'chapter1_background'
    },
    async addManualComment() {
      if (!this.newCommentText.trim()) return
      const name = this.newPanelistName.trim() || 'Manual Entry'
      const section = this.autoDetectSection(this.newCommentText)

      // Find existing panelist or create new
      const panelRef = push(ref(database, 'thesis_panel_requests'))
      await set(panelRef, {
        title: `Manual entry — ${name}`,
        date: new Date().toISOString(),
        source: 'manual',
        status: 'pending',
        panelists: [{
          name: name,
          comments: [{
            id: `man-${Date.now()}`,
            text: this.newCommentText.trim(),
            section: section,
            status: 'pending'
          }]
        }]
      })
      this.showToast(`Comment added → ${SECTION_MAP[section]?.title || section}`, 'success', 'check_circle')
      this.newCommentText = ''
    },

    // --- Navigation ---
    scrollToSection(key) {
      this.activeSection = key
      const el = this.sectionRefs[key]
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },

    // --- Tooltip ---
    showCommentTooltip(event, sectionKey, paraIndex) {
      if (!this.isParagraphAffected(sectionKey, paraIndex)) return
      const comments = this.getCommentsForSection(sectionKey)
      if (!comments.length) return
      const first = comments[0]
      this.tooltip = {
        visible: true,
        x: event.clientX + 12,
        y: event.clientY - 40,
        text: first.text,
        panelist: first.panelist
      }
    },

    hideCommentTooltip() {
      this.tooltip.visible = false
    },

    // --- Panel Requests ---
    openPanelRequests() {
      this.showPanelModal = true
    },

    toggleAccordion(idx) {
      this.openAccordions[idx] = !this.openAccordions[idx]
    },

    generateSolution(comment) {
      // AI simulation - in production this would call an API
      comment.solution = `[AI Suggestion] Based on the panel comment regarding "${comment.text.substring(0, 60)}...", consider expanding this section with additional supporting evidence and clearer methodology description. Include specific data points and references to strengthen the argument.`
      this.showToast('Solution generated', 'success', 'auto_fix_high')
    },

    previewSolution(comment) {
      comment.showPreview = !comment.showPreview
    },

    applySolution(comment) {
      if (!comment.solution || !comment.section) {
        this.showToast('No solution to apply', 'warning', 'warning')
        return
      }
      // Save backup of the section content BEFORE user starts editing
      const key = comment.section
      comment.backupContent = this.sections[key] || ''
      comment.status = 'applied'
      comment.appliedText = comment.solution

      // Navigate to section with AI suggestion as reference — user writes manually
      this.viewAppliedInThesis(comment)
    },

    viewAppliedInThesis(comment) {
      // Close panel modal
      this.showPanelModal = false

      const key = comment.section

      // Enable edit mode and scroll to section
      this.editMode = true
      this.aiFloatPos = { x: null, y: null } // Reset to default bottom-center position
      this.activeAiSuggestion = {
        section: key,
        panelist: comment.panelistName || '',
        originalComment: comment.text,
        suggestion: comment.solution,
      }

      // Set the search to highlight the applied text (first 60 chars for precision)
      const snippet = (comment.appliedText || comment.solution || '').substring(0, 60).trim()
      this.appliedHighlight = { section: comment.section, text: comment.appliedText || comment.solution }

      // Scroll to the section
      this.$nextTick(() => {
        const sectionEl = document.querySelector(`[data-section="${comment.section}"]`)
        if (sectionEl) {
          sectionEl.scrollIntoView({ behavior: 'smooth', block: 'center' })

          // Highlight the applied text in the DOM
          this.$nextTick(() => {
            this.highlightAppliedText(comment.section, comment.appliedText || comment.solution)
          })
        }
      })
    },

    highlightAppliedText(sectionKey, appliedText) {
      // Find all text nodes in the right (current) panel for this section
      const container = document.querySelector(`[data-section="${sectionKey}"]`)
      if (!container) return

      // Remove old applied highlights
      container.querySelectorAll('.applied-highlight').forEach(el => {
        const parent = el.parentNode
        parent.replaceChild(document.createTextNode(el.textContent), el)
        parent.normalize()
      })

      // Walk text nodes and find the applied content
      const searchText = appliedText.trim().substring(0, 100) // Use first 100 chars for matching
      const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false)
      const matches = []

      while (walker.nextNode()) {
        const node = walker.currentNode
        const idx = node.textContent.toLowerCase().indexOf(searchText.substring(0, 40).toLowerCase())
        if (idx !== -1) {
          matches.push({ node, idx })
        }
      }

      if (matches.length > 0) {
        // Highlight the first match and everything after it in the section
        const { node, idx } = matches[0]
        const range = document.createRange()
        range.setStart(node, idx)
        range.setEnd(node, Math.min(idx + searchText.length, node.textContent.length))

        const highlight = document.createElement('span')
        highlight.className = 'applied-highlight'
        highlight.style.cssText = 'background: rgba(34, 197, 94, 0.2); border-left: 3px solid #22C55E; padding: 2px 4px; border-radius: 3px; animation: appliedPulse 2s ease-in-out 3;'
        range.surroundContents(highlight)

        // Scroll directly to the highlight
        highlight.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }

      // Auto-clear highlight after 10 seconds
      setTimeout(() => {
        this.appliedHighlight = null
        container.querySelectorAll('.applied-highlight').forEach(el => {
          const parent = el.parentNode
          parent.replaceChild(document.createTextNode(el.textContent), el)
          parent.normalize()
        })
      }, 10000)
    },

    revertSolution(comment) {
      if (!comment.backupContent && comment.backupContent !== '') {
        this.showToast('No backup available to revert', 'warning', 'warning')
        return
      }
      const key = comment.section
      this.sections[key] = comment.backupContent
      comment.status = 'pending'
      delete comment.backupContent
      delete comment.appliedText
      this.isDirty = true

      this.showToast(`Reverted "${this.getSectionTitle(key)}" to previous content`, 'info', 'undo')
    },

    async deleteComment(comment, panelist) {
      if (!confirm(`Delete this comment?\n\n"${comment.text.substring(0, 100)}..."`)) return

      // Remove from local panelist
      const idx = panelist.comments.indexOf(comment)
      if (idx !== -1) panelist.comments.splice(idx, 1)

      // Remove from Firebase — rebuild all panel requests
      try {
        const panelRef = ref(database, 'thesis_panel_requests')
        const snap = await get(panelRef)
        if (snap.exists()) {
          const all = snap.val()
          for (const [entryKey, entry] of Object.entries(all)) {
            if (!entry.panelists) continue
            const pList = Array.isArray(entry.panelists) ? entry.panelists : Object.values(entry.panelists)
            for (const p of pList) {
              if (p.name === panelist.name && p.comments) {
                const cList = Array.isArray(p.comments) ? p.comments : Object.values(p.comments)
                const filtered = cList.filter(c => c.id !== comment.id)
                if (filtered.length !== cList.length) {
                  p.comments = filtered
                  await set(ref(database, `thesis_panel_requests/${entryKey}/panelists`), pList)
                  break
                }
              }
            }
          }
        }
        this.showToast('Comment deleted', 'success', 'check_circle')
      } catch (err) {
        console.error('Delete error:', err)
        this.showToast('Failed to delete from Firebase', 'error', 'error')
      }
    },

    // --- Validation ---
    openValidation() {
      this.validating = true
      this.showValidationModal = true

      // Simulate analysis delay
      setTimeout(() => {
        this.runValidation()
        this.validating = false
      }, 800)
    },

    runValidation() {
      const issues = []
      let formatScore = 100
      let completenessScore = 100
      let panelComplianceScore = 100

      // --- Format checks ---
      for (const [key, info] of Object.entries(SECTION_MAP)) {
        const text = this.sections[key] || ''
        if (text.trim()) {
          // Check minimum length
          const words = text.trim().split(/\s+/).length
          if (words < 50 && key !== 'references') {
            issues.push({
              severity: 'Warning',
              category: 'Format',
              section: key,
              message: `"${info.title}" has only ${words} words. Academic sections typically require more detail.`,
              fix: 'Expand this section with additional supporting content, evidence, or analysis.'
            })
            formatScore -= 5
          }

          // Check for references format (APA)
          if (key === 'references') {
            const lines = text.split('\n').filter(l => l.trim())
            for (let i = 0; i < lines.length; i++) {
              const line = lines[i].trim()
              if (line && !line.match(/\(\d{4}\)/) && !line.match(/\d{4}\./)) {
                issues.push({
                  severity: 'Warning',
                  category: 'Format',
                  section: 'references',
                  message: `Reference entry ${i + 1} may not follow APA format (missing year in parentheses).`,
                  fix: 'Ensure all references include the publication year in parentheses, e.g., Author, A. (2024).'
                })
                formatScore -= 2
              }
            }
          }
        }
      }

      // Check title page presence (always present in our render)
      // Check page numbers (always present)

      // --- Completeness checks ---
      const requiredSections = Object.keys(SECTION_MAP)
      let emptySections = 0
      for (const key of requiredSections) {
        const text = this.sections[key] || ''
        if (!text.trim()) {
          emptySections++
          issues.push({
            severity: 'Critical',
            category: 'Content',
            section: key,
            message: `"${SECTION_MAP[key].title}" is empty. This is a required section.`,
            fix: 'Add content to this section. All thesis sections must have substantive content.'
          })
          completenessScore -= Math.floor(100 / requiredSections.length)
        }
      }

      // --- Panel compliance checks ---
      let totalComments = 0
      let resolvedComments = 0
      for (const p of this.panelists) {
        if (p.comments) {
          for (const c of p.comments) {
            totalComments++
            if (c.status === 'resolved') resolvedComments++
            if (c.status === 'pending') {
              issues.push({
                severity: 'Warning',
                category: 'Panel Requests',
                section: c.section,
                message: `Unresolved comment from ${p.name}: "${c.text.substring(0, 80)}${c.text.length > 80 ? '...' : ''}"`,
                fix: 'Address this panel comment and mark it as resolved.'
              })
            }
          }
        }
      }
      if (totalComments > 0) {
        panelComplianceScore = Math.round((resolvedComments / totalComments) * 100)
      }

      // Clamp scores
      formatScore = Math.max(0, Math.min(100, formatScore))
      completenessScore = Math.max(0, Math.min(100, completenessScore))
      panelComplianceScore = Math.max(0, Math.min(100, panelComplianceScore))
      const overallScore = Math.round(formatScore * 0.3 + completenessScore * 0.4 + panelComplianceScore * 0.3)

      this.validationResult = {
        formatScore,
        completenessScore,
        panelComplianceScore,
        overallScore,
        issues: issues.sort((a, b) => {
          const sev = { Critical: 0, Warning: 1, Info: 2 }
          return (sev[a.severity] || 2) - (sev[b.severity] || 2)
        })
      }
    },

    getIssueCategoryCount(cat) {
      if (cat === 'All') return this.validationResult.issues.length
      return this.validationResult.issues.filter(i => i.category === cat).length
    },

    scoreRingStyle(score) {
      const color = score >= 80 ? '#22C55E' : score >= 50 ? '#F59E0B' : '#EF4444'
      const deg = (score / 100) * 360
      return {
        background: `conic-gradient(${color} ${deg}deg, #2A3A4E ${deg}deg)`,
        color: color
      }
    },

    // --- Export ---
    async exportDocx() {
      this.exporting = true
      try {
        const children = []

        // Title
        children.push(new Paragraph({ spacing: { before: 4000 } }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'Bulacan State University', font: 'Times New Roman', size: 28, bold: true })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'College of Information and Communications Technology', font: 'Times New Roman', size: 24 })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({ spacing: { before: 1200 } }))
        children.push(new Paragraph({
          children: [new TextRun({
            text: 'BILLSENSE: Developing a Camera-Based System for Detecting Counterfeit and Genuine Philippine Currency',
            font: 'Times New Roman', size: 28, bold: true
          })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({ spacing: { before: 2000 } }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'A Thesis Presented to the Faculty of', font: 'Times New Roman', size: 24 })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'College of Information and Communications Technology', font: 'Times New Roman', size: 24 })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'Bulacan State University', font: 'Times New Roman', size: 24 })],
          alignment: AlignmentType.CENTER
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: String(new Date().getFullYear()), font: 'Times New Roman', size: 24 })],
          alignment: AlignmentType.CENTER,
          spacing: { before: 1600 }
        }))
        children.push(new Paragraph({ children: [new PageBreak()] }))

        // Chapter 1
        children.push(new Paragraph({
          children: [new TextRun({ text: 'CHAPTER I', font: 'Times New Roman', size: 28, bold: true })],
          alignment: AlignmentType.CENTER, spacing: { before: 600, after: 200 }
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'THE PROBLEM AND ITS BACKGROUND', font: 'Times New Roman', size: 24, bold: true })],
          alignment: AlignmentType.CENTER, spacing: { after: 400 }
        }))

        for (const [key, info] of Object.entries(this.chapter1Sections)) {
          children.push(new Paragraph({
            children: [new TextRun({ text: info.title, font: 'Times New Roman', size: 24, bold: true })],
            spacing: { before: 400, after: 200 }
          }))
          const paras = this.getParagraphs(key)
          for (const p of paras) {
            children.push(new Paragraph({
              children: [new TextRun({ text: p, font: 'Times New Roman', size: 24 })],
              spacing: { line: 480 },
              indent: { firstLine: 720 }
            }))
          }
        }

        children.push(new Paragraph({ children: [new PageBreak()] }))

        // Chapter 2
        children.push(new Paragraph({
          children: [new TextRun({ text: 'CHAPTER II', font: 'Times New Roman', size: 28, bold: true })],
          alignment: AlignmentType.CENTER, spacing: { before: 600, after: 200 }
        }))
        children.push(new Paragraph({
          children: [new TextRun({ text: 'RESEARCH METHODOLOGY', font: 'Times New Roman', size: 24, bold: true })],
          alignment: AlignmentType.CENTER, spacing: { after: 400 }
        }))

        for (const [key, info] of Object.entries(this.chapter2Sections)) {
          children.push(new Paragraph({
            children: [new TextRun({ text: info.title, font: 'Times New Roman', size: 24, bold: true })],
            spacing: { before: 400, after: 200 }
          }))
          const paras = this.getParagraphs(key)
          for (const p of paras) {
            children.push(new Paragraph({
              children: [new TextRun({ text: p, font: 'Times New Roman', size: 24 })],
              spacing: { line: 480 },
              indent: { firstLine: 720 }
            }))
          }
        }

        children.push(new Paragraph({ children: [new PageBreak()] }))

        // References
        children.push(new Paragraph({
          children: [new TextRun({ text: 'References', font: 'Times New Roman', size: 28, bold: true })],
          alignment: AlignmentType.CENTER, spacing: { before: 400, after: 400 }
        }))
        const refParas = this.getParagraphs('references')
        for (const p of refParas) {
          children.push(new Paragraph({
            children: [new TextRun({ text: p, font: 'Times New Roman', size: 24 })],
            spacing: { line: 480 },
            indent: { hanging: 720 }
          }))
        }

        const doc = new Document({
          sections: [{ properties: { page: { margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } }, children }]
        })

        const blob = await Packer.toBlob(doc)
        saveAs(blob, 'BillSense_Thesis.docx')
        this.showToast('DOCX exported successfully', 'success', 'download')
      } catch (err) {
        console.error('DOCX export error:', err)
        this.showToast('Export failed: ' + err.message, 'error', 'error')
      } finally {
        this.exporting = false
      }
    },

    async exportPdf() {
      this.exporting = true
      try {
        const html2pdf = (await import('html2pdf.js')).default
        const docArea = this.$refs.documentArea
        if (!docArea) throw new Error('Document area not found')

        const opt = {
          margin: 0,
          filename: 'BillSense_Thesis.pdf',
          image: { type: 'jpeg', quality: 0.98 },
          html2canvas: { scale: 2, useCORS: true, backgroundColor: '#ffffff' },
          jsPDF: { unit: 'in', format: 'letter', orientation: 'portrait' },
          pagebreak: { mode: ['css', 'legacy'], before: '.thesis-page' }
        }
        await html2pdf().set(opt).from(docArea).save()
        this.showToast('PDF exported successfully', 'success', 'download')
      } catch (err) {
        console.error('PDF export error:', err)
        this.showToast('PDF export failed: ' + err.message, 'error', 'error')
      } finally {
        this.exporting = false
      }
    },

    printThesis() {
      window.print()
    },

    // --- Compare ---
    getVersionData(versionKey) {
      if (versionKey === 'current') return { ...this.sections }
      const found = this.versionList.find(v => v.key === versionKey)
      return found ? found.data : {}
    },
    filteredCompareSections() {
      if (this.activeSection) {
        const key = this.activeSection
        return { [key]: SECTION_MAP[key] }
      }
      return SECTION_MAP
    },
    // Build inline style for highlights based on user settings
    hlStyle(type) {
      const color = type === 'add' ? this.hlColorAdd : this.hlColorDel
      const r = parseInt(color.slice(1,3),16), g = parseInt(color.slice(3,5),16), b = parseInt(color.slice(5,7),16)
      let s = `background:rgba(${r},${g},${b},0.25);color:inherit;padding:1px 3px;border-radius:2px;`
      if (type === 'del') s += 'text-decoration:line-through;'
      if (this.hlUnderline && type === 'add') s += `border-bottom:2px solid ${color};`
      if (this.hlUnderline && type === 'del') s += `border-bottom:2px solid ${color};`
      return s
    },
    hlBlockStyle(type) {
      const color = type === 'add' ? this.hlColorAdd : this.hlColorDel
      const r = parseInt(color.slice(1,3),16), g = parseInt(color.slice(3,5),16), b = parseInt(color.slice(5,7),16)
      return `background:rgba(${r},${g},${b},0.1);border-left:4px solid ${color};display:block;padding:8px 12px;border-radius:4px;color:inherit;`
    },
    hlSentenceStyle(type) {
      const color = type === 'add' ? this.hlColorAdd : this.hlColorDel
      const r = parseInt(color.slice(1,3),16), g = parseInt(color.slice(3,5),16), b = parseInt(color.slice(5,7),16)
      return `background:rgba(${r},${g},${b},0.12);border-left:3px solid ${color};padding:2px 6px;color:inherit;`
    },

    renderDiffLeft(sectionKey) {
      const leftData = this.getVersionData(this.compareLeft)
      const rightData = this.getVersionData(this.compareRight)
      const leftText = leftData[sectionKey] || ''
      const rightText = rightData[sectionKey] || ''
      if (!leftText && !rightText) return '<em style="color:var(--text-muted)">No content</em>'
      if (!leftText) return '<em style="color:var(--text-muted)">No content in this version</em>'
      // No highlights if disabled
      if (!this.highlightEnabled) return this.escapeHtml(leftText)

      if (this.diffScope === 'block') {
        const changed = leftText !== rightText
        if (changed) return `<mark style="${this.hlBlockStyle('del')}">${this.escapeHtml(leftText)}</mark>`
        return this.escapeHtml(leftText)
      }
      if (this.diffScope === 'sentence') {
        return this.renderSentenceDiff(leftText, rightText, 'left')
      }
      const diff = this.computeWordDiff(leftText, rightText)
      return diff.map(d => {
        if (d.type === 'removed') return `<mark style="${this.hlStyle('del')}">${this.escapeHtml(d.text)}</mark>`
        if (d.type === 'same') return this.escapeHtml(d.text)
        return ''
      }).join(' ')
    },
    renderDiffRight(sectionKey) {
      const leftData = this.getVersionData(this.compareLeft)
      const rightData = this.getVersionData(this.compareRight)
      const leftText = leftData[sectionKey] || ''
      const rightText = rightData[sectionKey] || ''
      if (!leftText && !rightText) return '<em style="color:var(--text-muted)">No content</em>'
      if (!rightText) return '<em style="color:var(--text-muted)">No content in this version</em>'
      if (!this.highlightEnabled) return this.escapeHtml(rightText)

      if (this.diffScope === 'block') {
        const changed = leftText !== rightText
        if (changed) return `<mark style="${this.hlBlockStyle('add')}">${this.escapeHtml(rightText)}</mark>`
        return this.escapeHtml(rightText)
      }
      if (this.diffScope === 'sentence') {
        return this.renderSentenceDiff(leftText, rightText, 'right')
      }
      const diff = this.computeWordDiff(leftText, rightText)
      return diff.map(d => {
        if (d.type === 'added') return `<mark style="${this.hlStyle('add')}">${this.escapeHtml(d.text)}</mark>`
        if (d.type === 'same') return this.escapeHtml(d.text)
        return ''
      }).join(' ')
    },
    renderSentenceDiff(leftText, rightText, side) {
      const splitS = (t) => t.split(/(?<=[.!?])\s+/).filter(s => s.trim())
      const leftS = splitS(leftText), rightS = splitS(rightText)
      const target = side === 'left' ? leftS : rightS
      const other = side === 'left' ? rightS : leftS
      return target.map(s => {
        if (other.some(os => os.trim() === s.trim())) return this.escapeHtml(s)
        const type = side === 'left' ? 'del' : 'add'
        return `<mark style="${this.hlSentenceStyle(type)}">${this.escapeHtml(s)}</mark>`
      }).join(' ')
    },
    // Highlight search matches in rendered HTML
    // Search highlighting is now DOM-based (see performSearch)
    computeWordDiff(oldText, newText) {
      const oldW = (oldText || '').split(/\s+/).filter(w => w)
      const newW = (newText || '').split(/\s+/).filter(w => w)
      if (!oldW.length) return newW.map(w => ({ type: 'added', text: w }))
      if (!newW.length) return oldW.map(w => ({ type: 'removed', text: w }))
      // For large texts, use line-based diff then word-diff changed lines
      if (oldW.length > 500 || newW.length > 500) {
        return this.fastDiff(oldW, newW)
      }
      const m = oldW.length, n = newW.length
      const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0))
      for (let i = 1; i <= m; i++)
        for (let j = 1; j <= n; j++)
          dp[i][j] = oldW[i-1] === newW[j-1] ? dp[i-1][j-1] + 1 : Math.max(dp[i-1][j], dp[i][j-1])
      const result = []
      let i = m, j = n
      while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && oldW[i-1] === newW[j-1]) { result.unshift({ type: 'same', text: oldW[i-1] }); i--; j-- }
        else if (j > 0 && (i === 0 || dp[i][j-1] >= dp[i-1][j])) { result.unshift({ type: 'added', text: newW[j-1] }); j-- }
        else { result.unshift({ type: 'removed', text: oldW[i-1] }); i-- }
      }
      return result
    },
    // Fast diff for large texts — compare sentence by sentence
    fastDiff(oldWords, newWords) {
      const oldStr = oldWords.join(' ')
      const newStr = newWords.join(' ')
      // If identical
      if (oldStr === newStr) return oldWords.map(w => ({ type: 'same', text: w }))
      // Split into sentences and compare
      const oldSentences = oldStr.split(/(?<=[.!?])\s+/)
      const newSentences = newStr.split(/(?<=[.!?])\s+/)
      const oldSet = new Set(oldSentences.map(s => s.trim()))
      const newSet = new Set(newSentences.map(s => s.trim()))
      const result = []
      // Mark old sentences
      for (const s of oldSentences) {
        const trimmed = s.trim()
        if (newSet.has(trimmed)) {
          trimmed.split(/\s+/).forEach(w => result.push({ type: 'same', text: w }))
        } else {
          trimmed.split(/\s+/).forEach(w => result.push({ type: 'removed', text: w }))
        }
        result.push({ type: 'same', text: '' }) // space between sentences
      }
      // Add new sentences not in old
      for (const s of newSentences) {
        if (!oldSet.has(s.trim())) {
          s.trim().split(/\s+/).forEach(w => result.push({ type: 'added', text: w }))
        }
      }
      return result.filter(d => d.text)
    },
    escapeHtml(text) {
      return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    },

    // Rebuild all rendered HTML — called by watchers
    updateRenderedHtml() {
      const left = {}
      const right = {}
      for (const key of Object.keys(SECTION_MAP)) {
        left[key] = this.buildLeftHtml(key)
        right[key] = this.buildRightHtml(key)
      }
      this.renderedLeft = left
      this.renderedRight = right
    },
    buildLeftHtml(key) {
      if (this.compareLeft) return this.renderDiffLeft(key)
      const firstKey = this.versionList.length ? this.versionList[this.versionList.length - 1].key : null
      const data = firstKey ? this.getVersionData(firstKey) : {}
      const text = data[key] || ''
      if (!text) return '<em style="color:var(--text-muted)">No content in this version</em>'
      return this.renderPlainWithSearch(text, 'left')
    },
    buildRightHtml(key) {
      if (this.compareLeft) return this.renderDiffRight(key)
      const text = this.sections[key] || ''
      if (!text.trim()) return '<em style="color:var(--text-muted)">No content yet</em>'
      return this.renderPlainWithSearch(text, 'right')
    },

    // Main render methods for left/right panels (kept for compare modal)
    getLeftHtml(key) {
      if (this.compareLeft) return this.renderDiffLeft(key)
      // No compare selected — show first version content with search
      const firstKey = this.versionList.length ? this.versionList[this.versionList.length - 1].key : null
      const data = firstKey ? this.getVersionData(firstKey) : {}
      const text = data[key] || ''
      if (!text) return '<em style="color:var(--text-muted)">No content in this version</em>'
      return this.renderPlainWithSearch(text, 'left')
    },
    getRightHtml(key) {
      if (this.compareLeft) return this.renderDiffRight(key)
      const text = this.sections[key] || ''
      if (!text.trim()) return '<em style="color:var(--text-muted)">No content yet</em>'
      return this.renderPlainWithSearch(text, 'right')
    },

    // Render plain text as formatted HTML
    renderPlainWithSearch(text, panel) {
      if (!text) return ''
      let html = this.escapeHtml(text)
      html = html.split(/\n\n+/).map(p => `<p style="text-indent:48px;margin:0;line-height:2;">${p}</p>`).join('')
      return html
    },

    // --- Search (DOM-based highlighting + semantic) ---
    performSearch() {
      this.clearHighlights()
      this.searchMatches = []
      this.searchMatchIndex = 0
      this.semanticResults = []

      const q = this.searchQuery.trim()
      if (!q || q.length < 2) return

      // Semantic search mode
      if (this.searchMode === 'semantic') {
        if (!this.searchEngine.ready) {
          this.searchEngine.buildIndex(this.sections, SECTION_MAP)
        }
        this.semanticResults = this.searchEngine.search(q, 15)
        return
      }

      // Determine which panels to search
      const panels = []
      if (this.searchPanel === 'both' || this.searchPanel === 'left') {
        const left = this.$refs.leftPanel
        if (left) panels.push(left)
      }
      if (this.searchPanel === 'both' || this.searchPanel === 'right') {
        const right = this.$refs.rightPanel
        if (right) panels.push(right)
      }

      // Walk DOM text nodes and wrap matches
      for (const panel of panels) {
        this.highlightInElement(panel, q)
      }

      // Collect all highlight spans
      this.searchMatches = Array.from(document.querySelectorAll('.search-hl'))
      if (this.searchMatches.length > 0) {
        this.searchMatchIndex = 0
        this.activateMatch(0)
      }
    },

    highlightInElement(element, query) {
      const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null)
      const textNodes = []
      while (walker.nextNode()) textNodes.push(walker.currentNode)

      const lowerQ = query.toLowerCase()
      for (const node of textNodes) {
        const text = node.textContent
        const lowerText = text.toLowerCase()
        if (!lowerText.includes(lowerQ)) continue

        // Split text node at match boundaries
        const frag = document.createDocumentFragment()
        let lastIdx = 0
        let idx = lowerText.indexOf(lowerQ)
        while (idx !== -1) {
          // Text before match
          if (idx > lastIdx) {
            frag.appendChild(document.createTextNode(text.substring(lastIdx, idx)))
          }
          // The match — wrap in highlight span
          const span = document.createElement('span')
          span.className = 'search-hl'
          span.textContent = text.substring(idx, idx + query.length)
          frag.appendChild(span)
          lastIdx = idx + query.length
          idx = lowerText.indexOf(lowerQ, lastIdx)
        }
        // Remaining text after last match
        if (lastIdx < text.length) {
          frag.appendChild(document.createTextNode(text.substring(lastIdx)))
        }
        node.parentNode.replaceChild(frag, node)
      }
    },

    clearHighlights() {
      const existing = document.querySelectorAll('.search-hl')
      for (const span of existing) {
        const parent = span.parentNode
        parent.replaceChild(document.createTextNode(span.textContent), span)
        parent.normalize() // Merge adjacent text nodes
      }
    },

    activateMatch(idx) {
      // Remove active from all
      document.querySelectorAll('.search-hl-active').forEach(el => el.classList.remove('search-hl-active'))
      const matches = document.querySelectorAll('.search-hl')
      if (idx >= 0 && idx < matches.length) {
        const target = matches[idx]
        target.classList.add('search-hl-active')
        // Scroll into view within its panel
        const panel = target.closest('.panel-content')
        if (panel) {
          const panelRect = panel.getBoundingClientRect()
          const targetRect = target.getBoundingClientRect()
          panel.scrollTop += targetRect.top - panelRect.top - 80
        }
      }
    },

    jumpToNextMatch() {
      const matches = document.querySelectorAll('.search-hl')
      if (!matches.length) return
      this.searchMatchIndex = (this.searchMatchIndex + 1) % matches.length
      this.activateMatch(this.searchMatchIndex)
    },
    jumpToPrevMatch() {
      const matches = document.querySelectorAll('.search-hl')
      if (!matches.length) return
      this.searchMatchIndex = (this.searchMatchIndex - 1 + matches.length) % matches.length
      this.activateMatch(this.searchMatchIndex)
    },
    clearSearch() {
      this.clearHighlights()
      this.searchQuery = ''
      this.searchMatches = []
      this.searchMatchIndex = 0
    },

    startDragFloat(e) {
      const el = this.$refs.aiFloat
      if (!el) return
      const rect = el.getBoundingClientRect()
      this.aiFloatDragging = true
      this.aiFloatDragStart = { x: e.clientX, y: e.clientY, elX: rect.left, elY: rect.top }
      document.addEventListener('mousemove', this.onDragFloat)
      document.addEventListener('mouseup', this.stopDragFloat)
      e.preventDefault()
    },
    onDragFloat(e) {
      if (!this.aiFloatDragging) return
      const dx = e.clientX - this.aiFloatDragStart.x
      const dy = e.clientY - this.aiFloatDragStart.y
      this.aiFloatPos = {
        x: Math.max(0, Math.min(window.innerWidth - 400, this.aiFloatDragStart.elX + dx)),
        y: Math.max(0, Math.min(window.innerHeight - 100, this.aiFloatDragStart.elY + dy)),
      }
    },
    stopDragFloat() {
      this.aiFloatDragging = false
      document.removeEventListener('mousemove', this.onDragFloat)
      document.removeEventListener('mouseup', this.stopDragFloat)
    },

    copyToClipboard(text) {
      navigator.clipboard.writeText(text).then(() => {
        this.showToast('Copied to clipboard', 'success', 'content_copy')
      }).catch(() => {
        // Fallback
        const ta = document.createElement('textarea')
        ta.value = text
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
        this.showToast('Copied to clipboard', 'success', 'content_copy')
      })
    },

    // --- Toast ---
    showToast(message, type = 'info', icon = 'info') {
      this.toast = { visible: true, message, type, icon }
      setTimeout(() => { this.toast.visible = false }, 3500)
    }
  },

  watch: {
    searchQuery() { this.updateRenderedHtml() },
    searchPanel() { this.updateRenderedHtml() },
    sections: { handler() { this.updateRenderedHtml() }, deep: true },
    compareLeft() { this.updateRenderedHtml() },
    compareRight() { this.updateRenderedHtml() },
    highlightEnabled() { this.updateRenderedHtml() },
    diffScope() { this.updateRenderedHtml() },
    hlColorAdd() { this.updateRenderedHtml() },
    hlColorDel() { this.updateRenderedHtml() },
    hlUnderline() { this.updateRenderedHtml() },
    activeSection() { this.updateRenderedHtml() }
  },

  mounted() {
    this.initFirebase()
  },

  beforeUnmount() {
    if (this.unsubscribeSections) this.unsubscribeSections()
    if (this.unsubscribePanel) this.unsubscribePanel()
    if (this.unsubscribeVersions) this.unsubscribeVersions()
  }
}
</script>

<style scoped>
/* ===== CSS Variables ===== */
.thesis-validator {
  --bg: #0F1923;
  --bg-card: #1A2332;
  --bg-elevated: #1E2D3D;
  --border: #2A3A4E;
  --accent: #4F86F7;
  --accent-hover: #3A6FE0;
  --text: #E2E8F0;
  --text-muted: #94A3B8;
  --success: #22C55E;
  --warning: #F59E0B;
  --danger: #EF4444;

  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* ===== Top Header ===== */
.top-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
  gap: 12px;
}
.header-left h1 {
  font-size: 1.4rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.header-icon {
  color: var(--accent);
  font-size: 1.6rem;
}
.subtitle {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: 4px 0 0;
  max-width: 600px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.export-group {
  display: flex;
  gap: 4px;
  padding-left: 8px;
  border-left: 1px solid var(--border);
  margin-left: 4px;
}

/* ===== Buttons ===== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.btn .material-icons {
  font-size: 18px;
}
.btn-ghost {
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border);
}
.btn-ghost:hover, .btn-ghost.active {
  background: var(--bg-elevated);
  color: var(--text);
  border-color: var(--accent);
}
.btn-primary {
  background: var(--accent);
  color: white;
}
.btn-primary:hover {
  background: var(--accent-hover);
}
.btn-primary:disabled, .btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-sm {
  padding: 5px 10px;
  font-size: 0.75rem;
}
.btn-accent {
  background: var(--accent);
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.btn-accent:hover {
  background: var(--accent-hover);
}
.btn-close {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
}
.btn-close:hover {
  background: var(--bg-elevated);
  color: var(--text);
}
.badge-count {
  background: var(--danger);
  color: white;
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 10px;
  margin-left: 2px;
}

/* ===== Spin ===== */
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ===== Version Bar ===== */
.version-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  gap: 12px;
}
.version-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.vtab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.2s;
}
.vtab .material-icons {
  font-size: 18px;
}
.vtab:hover, .vtab.active {
  background: var(--bg-elevated);
  color: var(--text);
  border-color: var(--accent);
}
.version-select {
  background: var(--bg-elevated);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 0.82rem;
  outline: none;
}
.version-select option {
  background: var(--bg-card);
}

/* ===== Edit Toggle ===== */
.edit-toggle {
  display: flex;
  align-items: center;
}
.toggle-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--text-muted);
  user-select: none;
}
.toggle-label input {
  display: none;
}
.toggle-track {
  width: 42px;
  height: 22px;
  background: var(--border);
  border-radius: 11px;
  position: relative;
  transition: background 0.2s;
}
.toggle-label input:checked + .toggle-track {
  background: var(--accent);
}
.toggle-thumb {
  width: 18px;
  height: 18px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform 0.2s;
}
.toggle-label input:checked + .toggle-track .toggle-thumb {
  transform: translateX(20px);
}

/* ===== Main Layout ===== */
.main-layout {
  display: flex;
  height: calc(100vh - 200px);
  overflow: hidden;
  gap: 0;
}

/* ===== Section Sidebar ===== */
.section-sidebar {
  width: 280px;
  min-width: 280px;
  background: var(--bg-card);
  border-right: 1px solid var(--border);
  overflow-y: auto;
  padding: 16px 0;
}
.sidebar-title {
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  padding: 0 16px 12px;
  margin: 0;
}
.section-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.section-item {
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}
.section-item:hover {
  background: var(--bg-elevated);
}
.section-item.active {
  background: var(--bg-elevated);
  border-left-color: var(--accent);
}
.section-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.section-chapter {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--accent);
  color: white;
  white-space: nowrap;
}
.ref-badge {
  background: var(--warning);
  color: #1a1a1a;
}
.section-name {
  font-size: 0.8rem;
  color: var(--text);
  line-height: 1.3;
}
.section-item-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 38px;
}
.word-badge {
  font-size: 0.68rem;
  color: var(--text-muted);
  background: rgba(255,255,255,0.05);
  padding: 1px 6px;
  border-radius: 4px;
}
.warning-badge {
  display: flex;
  align-items: center;
}
.warning-badge .material-icons {
  font-size: 16px;
  color: var(--warning);
}

/* ===== Document Area ===== */
.document-area {
  flex: 1;
  overflow-y: auto;
  padding: 32px 24px;
  background: #141C26;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ===== Thesis Document (Continuous Web View) ===== */
.thesis-document {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}
.thesis-page {
  width: 100%;
  background: var(--bg-card);
  color: var(--text);
  padding: 32px 40px;
  margin: 0 0 2px;
  font-family: 'Times New Roman', Times, serif;
  font-size: 12pt;
  line-height: 2;
  position: relative;
  border-left: 1px solid var(--border);
  border-right: 1px solid var(--border);
  box-sizing: border-box;
}
.thesis-page:first-child {
  border-top: 1px solid var(--border);
  border-radius: 12px 12px 0 0;
}
.thesis-page:last-child {
  border-bottom: 1px solid var(--border);
  border-radius: 0 0 12px 12px;
  margin-bottom: 40px;
}
.page-number {
  display: none; /* Hidden in web view — shown only in print/export */
}

/* Title Page */
.title-page-content {
  text-align: center;
  width: 100%;
  padding: 20px 0;
}
.title-page-content h2 {
  color: var(--accent);
}
.title-page-content p {
  color: var(--text);
}
.title-university {
  font-size: 14pt;
  font-weight: bold;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.title-college {
  font-size: 12pt;
  margin-bottom: 48px;
}
.title-main h2 {
  font-size: 16pt;
  font-weight: bold;
  line-height: 1.6;
  margin-bottom: 48px;
  text-transform: uppercase;
}
.title-label {
  font-size: 11pt;
  line-height: 1.8;
  margin-bottom: 24px;
}
.title-partial {
  font-size: 11pt;
  font-style: italic;
  line-height: 1.8;
  margin-bottom: 36px;
}
.title-authors {
  font-size: 12pt;
  line-height: 1.8;
  margin-bottom: 24px;
}
.title-date {
  font-size: 12pt;
  font-weight: bold;
}

/* TOC Page */
.toc-page {
  padding-top: 60px;
}
.toc-heading {
  text-align: center;
  font-size: 14pt;
  font-weight: bold;
  margin-bottom: 32px;
  letter-spacing: 0.05em;
}
.toc-entries {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.toc-entry {
  display: flex;
  align-items: baseline;
  cursor: pointer;
  font-size: 12pt;
  line-height: 1.8;
}
.toc-entry:hover .toc-title {
  color: #4F86F7;
}
.toc-title {
  white-space: nowrap;
  transition: color 0.15s;
}
.toc-dots {
  flex: 1;
  border-bottom: 1px dotted #999;
  margin: 0 8px;
  position: relative;
  top: -4px;
}
.toc-page-num {
  white-space: nowrap;
  min-width: 20px;
  text-align: right;
}

/* Chapter Header Page */
.chapter-header-page {
  display: flex;
  align-items: center;
  justify-content: center;
}
.chapter-header-content {
  text-align: center;
}
.chapter-label {
  font-size: 18pt;
  font-weight: bold;
  letter-spacing: 0.1em;
  margin-bottom: 16px;
}
.chapter-title-text {
  font-size: 14pt;
  font-weight: bold;
  letter-spacing: 0.05em;
}

/* Content Page */
.content-page {
  overflow-y: auto;
}
.doc-section {
  margin-bottom: 24px;
}
.section-heading {
  font-size: 13pt;
  font-weight: bold;
  margin-bottom: 16px;
  line-height: 2;
  color: var(--text);
}
.section-heading.centered {
  text-align: center;
}
.section-body {
  font-size: 12pt;
  line-height: 2;
}
.body-paragraph {
  text-indent: 48px;
  margin-bottom: 0;
  line-height: 2;
  transition: all 0.15s;
}
.reference-entry {
  text-indent: 0;
  padding-left: 48px;
  text-indent: -48px;
  margin-bottom: 8px;
}
.empty-section {
  color: var(--text-muted);
  font-style: italic;
  text-align: center;
  padding: 40px 0;
}

/* Panel affected highlight */
.panel-affected {
  border-left: 3px solid var(--warning);
  background: rgba(245, 158, 11, 0.06);
  padding-left: 12px;
  margin-left: -15px;
  cursor: pointer;
}
.panel-affected:hover {
  background: rgba(245, 158, 11, 0.12);
}

/* Edit Mode Textarea */
.section-edit {
  flex: 1;
  display: flex;
}
.section-textarea {
  width: 100%;
  height: 100%;
  min-height: 300px;
  border: 1px solid var(--accent);
  border-radius: 6px;
  padding: 16px;
  font-family: 'Times New Roman', Times, serif;
  font-size: 12pt;
  line-height: 2;
  color: #E2E8F0;
  resize: vertical;
  outline: none;
  background: #0D1520;
}
.section-textarea:focus {
  border-color: #4F86F7;
  box-shadow: 0 0 0 2px rgba(79, 134, 247, 0.2);
  background: #111927;
}

/* References Page */
.references-body {
  font-size: 12pt;
}

/* ===== Comment Tooltip ===== */
.comment-tooltip {
  position: fixed;
  z-index: 9999;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 16px;
  max-width: 340px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
  pointer-events: none;
}
.tooltip-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--warning);
  margin-bottom: 6px;
  font-family: 'Inter', sans-serif;
}
.tooltip-header .material-icons {
  font-size: 16px;
}
.tooltip-body {
  font-size: 0.8rem;
  color: var(--text);
  line-height: 1.5;
  font-family: 'Inter', sans-serif;
}
.tooltip-from {
  font-size: 0.7rem;
  color: var(--text-muted);
  margin-top: 6px;
  font-family: 'Inter', sans-serif;
}

/* ===== Modal Overlay ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ===== Floating Window ===== */
.floating-window {
  width: 90vw;
  max-width: 1200px;
  height: 85vh;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(0,0,0,0.5);
}
.fw-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-elevated);
}
.fw-header h2 {
  font-size: 1.15rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.fw-header .material-icons {
  color: var(--accent);
}
.fw-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* ===== Panel Requests Window ===== */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  display: block;
  opacity: 0.4;
}
.panelist-accordion {
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.accordion-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: var(--bg-elevated);
  cursor: pointer;
  transition: background 0.15s;
}
.accordion-header:hover {
  background: rgba(79, 134, 247, 0.08);
}
.accordion-arrow {
  font-size: 20px;
  color: var(--text-muted);
  transition: transform 0.2s;
}
.accordion-arrow.rotated {
  transform: rotate(90deg);
}
.panelist-name {
  font-weight: 600;
  font-size: 0.95rem;
}
.comment-count-badge {
  font-size: 0.72rem;
  color: var(--text-muted);
  background: rgba(255,255,255,0.06);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
}
.accordion-body {
  padding: 12px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.comment-card {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px;
}
.comment-text {
  font-size: 0.88rem;
  line-height: 1.6;
  margin-bottom: 10px;
}
.comment-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.comment-section-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--accent);
  cursor: pointer;
}
.comment-section-link:hover {
  text-decoration: underline;
}
.comment-section-link .material-icons {
  font-size: 14px;
}
.status-badge {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.03em;
}
.status-badge.pending {
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning);
}
.status-badge.in-progress {
  background: rgba(79, 134, 247, 0.15);
  color: var(--accent);
}
.status-badge.resolved {
  background: rgba(34, 197, 94, 0.15);
  color: var(--success);
}
.comment-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* Solution Preview */
.solution-preview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}
.preview-col {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}
.preview-col h5 {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--text-muted);
  margin: 0 0 8px;
}
.preview-col.suggested {
  border-color: var(--success);
}
.preview-col.suggested h5 {
  color: var(--success);
}
.preview-content {
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--text);
  max-height: 200px;
  overflow-y: auto;
}

/* ===== Validation Window ===== */
.score-rings {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 32px;
  margin-bottom: 32px;
  padding: 24px 0;
  flex-wrap: wrap;
}
.score-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.score-ring {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.score-ring.large {
  width: 130px;
  height: 130px;
}
.score-value {
  font-size: 1.5rem;
  font-weight: 700;
  background: var(--bg-card);
  width: 70%;
  height: 70%;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.score-ring.large .score-value {
  font-size: 2rem;
}
.score-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

/* Issues */
.issues-section {
  border-top: 1px solid var(--border);
  padding-top: 24px;
}
.issues-title {
  font-size: 1rem;
  font-weight: 700;
  margin: 0 0 16px;
}
.issues-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.issue-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.15s;
}
.issue-tab:hover, .issue-tab.active {
  background: var(--bg-elevated);
  color: var(--text);
  border-color: var(--accent);
}
.issue-count {
  font-size: 0.68rem;
  background: rgba(255,255,255,0.08);
  padding: 0 6px;
  border-radius: 8px;
}
.issues-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 400px;
  overflow-y: auto;
}
.issue-card {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 10px;
  align-items: flex-start;
}
.issue-card.critical {
  border-left: 3px solid var(--danger);
}
.issue-card.warning {
  border-left: 3px solid var(--warning);
}
.issue-card.info {
  border-left: 3px solid var(--accent);
}
.issue-severity-badge {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: 6px;
  white-space: nowrap;
  letter-spacing: 0.03em;
}
.issue-severity-badge.critical {
  background: rgba(239, 68, 68, 0.15);
  color: var(--danger);
}
.issue-severity-badge.warning {
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning);
}
.issue-severity-badge.info {
  background: rgba(79, 134, 247, 0.15);
  color: var(--accent);
}
.issue-details {
  flex: 1;
  min-width: 0;
}
.issue-message {
  font-size: 0.85rem;
  line-height: 1.5;
  margin-bottom: 6px;
}
.issue-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.issue-section {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.72rem;
  color: var(--text-muted);
}
.issue-section .material-icons {
  font-size: 14px;
}
.issue-category {
  font-size: 0.7rem;
  color: var(--text-muted);
  background: rgba(255,255,255,0.05);
  padding: 1px 6px;
  border-radius: 4px;
}
.issue-fix {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--success);
  line-height: 1.5;
  background: rgba(34, 197, 94, 0.06);
  padding: 6px 10px;
  border-radius: 6px;
}
.issue-fix .material-icons {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 1px;
}

/* ===== Toast ===== */
.toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  border-radius: 10px;
  font-size: 0.85rem;
  font-weight: 500;
  z-index: 9999;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  font-family: 'Inter', sans-serif;
}
.toast .material-icons {
  font-size: 20px;
}
.toast.success {
  background: var(--success);
  color: white;
}
.toast.error {
  background: var(--danger);
  color: white;
}
.toast.info {
  background: var(--accent);
  color: white;
}
.toast-fade-enter-active, .toast-fade-leave-active {
  transition: all 0.3s;
}
.toast-fade-enter-from, .toast-fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

/* ===== Print Styles ===== */
@media print {
  .top-header, .version-bar, .section-sidebar, .comment-tooltip, .modal-overlay, .toast {
    display: none !important;
  }
  .main-layout {
    display: block;
    height: auto;
  }
  .document-area {
    padding: 0;
    background: white;
    overflow: visible;
  }
  .thesis-page {
    box-shadow: none;
    margin: 0;
    page-break-after: always;
    max-width: none;
    border-radius: 0;
  }
}

/* ===== Scrollbar ===== */
.section-sidebar::-webkit-scrollbar,
.document-area::-webkit-scrollbar,
.fw-body::-webkit-scrollbar,
.issues-list::-webkit-scrollbar,
.preview-content::-webkit-scrollbar {
  width: 6px;
}
.section-sidebar::-webkit-scrollbar-track,
.document-area::-webkit-scrollbar-track,
.fw-body::-webkit-scrollbar-track,
.issues-list::-webkit-scrollbar-track,
.preview-content::-webkit-scrollbar-track {
  background: transparent;
}
.section-sidebar::-webkit-scrollbar-thumb,
.document-area::-webkit-scrollbar-thumb,
.fw-body::-webkit-scrollbar-thumb,
.issues-list::-webkit-scrollbar-thumb,
.preview-content::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 3px;
}
.section-sidebar::-webkit-scrollbar-thumb:hover,
.document-area::-webkit-scrollbar-thumb:hover,
.fw-body::-webkit-scrollbar-thumb:hover {
  background: var(--text-muted);
}

/* Compare Window */
.compare-window {
  width: 95vw !important;
  max-width: 1400px !important;
  max-height: 90vh !important;
}
.compare-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
  background: rgba(79, 134, 247, 0.05);
}
.compare-select-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.compare-select-group label {
  font-size: 0.8rem;
  color: var(--text-muted);
  white-space: nowrap;
}
.compare-select-group select, .compare-section-filter select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 6px 10px;
  color: var(--text);
  font-size: 0.8rem;
  min-width: 200px;
}
.compare-arrow {
  color: var(--accent);
  font-size: 24px;
}
.compare-section-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.compare-section-filter label {
  font-size: 0.8rem;
  color: var(--text-muted);
}
.compare-body {
  padding: 0 !important;
}
.compare-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 100%;
  min-height: 500px;
}
.compare-panel {
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}
.compare-panel:last-child {
  border-right: none;
}
.compare-panel-header {
  padding: 10px 16px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
  background: rgba(0,0,0,0.1);
}
.compare-panel-content {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
  font-family: 'Times New Roman', Times, serif;
  font-size: 11pt;
  line-height: 1.8;
  color: var(--text);
}
.compare-section-title {
  font-size: 12pt;
  font-weight: bold;
  margin: 20px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
  color: var(--accent);
}
.compare-section-title:first-child {
  margin-top: 0;
}
.compare-text {
  margin-bottom: 16px;
  word-wrap: break-word;
}
.empty-state-sm {
  text-align: center;
  color: var(--text-muted);
  padding: 60px 20px;
  font-style: italic;
}
.btn.active {
  background: rgba(79, 134, 247, 0.2);
  border-color: var(--accent);
  color: var(--accent);
}
.btn-accent {
  background: var(--accent);
  color: #fff;
  border: none;
}
.btn-accent:hover {
  background: #3A6FD8;
}

/* Main Compare Layout (inline, not modal) */
.compare-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
  min-height: 0;
}
.compare-controls-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-top: none;
  flex-wrap: wrap;
  width: 100%;
  box-sizing: border-box;
}
.compare-select-group {
  display: flex;
  align-items: center;
  gap: 6px;
}
.compare-select-group label, .compare-section-filter label {
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
}
.compare-select-group select, .compare-section-filter select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 6px 10px;
  color: var(--text);
  font-size: 0.8rem;
  min-width: 180px;
}
.compare-arrow-icon {
  color: var(--accent);
  font-size: 22px;
}
.compare-section-filter {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.compare-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  flex: 1;
  overflow: hidden;
  border: 1px solid var(--border);
  border-top: none;
  border-radius: 0 0 10px 10px;
  min-height: 0;
  height: calc(100vh - 320px);
}
.compare-panel-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.compare-panel-main.left {
  border-right: 2px solid var(--accent);
}
.panel-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: rgba(79, 134, 247, 0.05);
  border-bottom: 1px solid var(--border);
}
.panel-label .material-icons {
  font-size: 18px;
  color: var(--accent);
}
.edit-mode-btn {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 10px;
  color: var(--text-muted);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s;
}
.edit-mode-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.edit-mode-btn .material-icons {
  font-size: 16px;
  color: inherit;
}
.save-inline-btn {
  background: rgba(34, 197, 94, 0.15) !important;
  color: #22C55E !important;
  border: 1px solid rgba(34, 197, 94, 0.3) !important;
  animation: pulse-save 1.5s infinite;
}
.save-inline-btn:hover {
  background: rgba(34, 197, 94, 0.25) !important;
}
@keyframes pulse-save {
  0%, 100% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(34, 197, 94, 0); }
}
.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  background: var(--bg-card);
  font-family: 'Times New Roman', Times, serif;
  font-size: 11pt;
  line-height: 1.8;
}
.compare-section {
  margin-bottom: 20px;
}
.compare-section-title {
  font-size: 11pt;
  font-weight: bold;
  margin: 16px 0 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border);
  color: var(--accent);
}
.compare-section-title:first-child {
  margin-top: 0;
}
.compare-text {
  word-wrap: break-word;
  color: var(--text);
}
.empty-compare {
  color: var(--text-muted);
  text-align: center;
  padding: 20px;
}

/* Search Bar */
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 10px 10px 0 0;
  border-bottom: none;
  width: 100%;
  box-sizing: border-box;
}
.search-icon {
  color: var(--text-muted);
  font-size: 20px;
  flex-shrink: 0;
}
.search-input {
  flex: 1;
  min-width: 0;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 7px 12px;
  color: var(--text);
  font-size: 0.85rem;
  outline: none;
}
.search-input:focus {
  border-color: var(--accent);
}
.search-input::placeholder {
  color: var(--text-muted);
  opacity: 0.6;
}
.search-results-count {
  font-size: 0.75rem;
  color: var(--text-muted);
  white-space: nowrap;
  padding: 0 4px;
  flex-shrink: 0;
}
.search-nav-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 3px;
  cursor: pointer;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  transition: all 0.15s;
  flex-shrink: 0;
}
.search-nav-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.search-nav-btn .material-icons {
  font-size: 18px;
}
.search-panel-select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 5px 8px;
  color: var(--text);
  font-size: 0.75rem;
  outline: none;
  flex-shrink: 0;
}
.search-mode-toggle {
  display: flex;
  gap: 2px;
  background: var(--bg);
  border-radius: 6px;
  padding: 2px;
  border: 1px solid var(--border);
  flex-shrink: 0;
}
.smode-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  cursor: pointer;
  background: none;
  color: var(--text-muted);
  transition: all 0.15s;
}
.smode-btn.active {
  background: var(--accent);
  color: #fff;
}

/* Semantic Search Results */
.semantic-results {
  max-height: 300px;
  overflow-y: auto;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-top: none;
  padding: 8px;
}
.semantic-result {
  padding: 10px 14px;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.semantic-result:hover {
  border-color: var(--accent);
  background: rgba(79, 134, 247, 0.05);
}
.sr-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.sr-section {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--accent);
}
.sr-score {
  font-size: 0.7rem;
  color: var(--success);
  background: rgba(34, 197, 94, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
}
.sr-text {
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.5;
  max-height: 60px;
  overflow: hidden;
}
:deep(.search-hl-exact) {
  background: rgba(59, 130, 246, 0.3);
  color: #93c5fd;
  padding: 1px 2px;
  border-radius: 2px;
  font-weight: bold;
}
:deep(.search-hl-term) {
  background: rgba(245, 158, 11, 0.2);
  color: #fbbf24;
  padding: 0 1px;
  border-radius: 2px;
}

/* File Drop Zone */
.file-drop-zone {
  border: 2px dashed var(--border);
  border-radius: 10px;
  padding: 20px;
  text-align: center;
  margin-bottom: 12px;
  transition: all 0.2s;
  cursor: pointer;
}
.file-drop-zone .material-icons {
  font-size: 32px;
  color: var(--text-muted);
  display: block;
  margin-bottom: 6px;
}
.file-drop-zone p {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 4px 0;
}
.file-drop-zone.drag-over {
  border-color: var(--accent);
  background: rgba(79, 134, 247, 0.08);
}
.file-drop-zone.drag-over .material-icons {
  color: var(--accent);
}
.file-drop-zone.parsing {
  border-color: var(--warning);
  background: rgba(245, 158, 11, 0.05);
}
.drop-hint {
  font-size: 0.75rem !important;
}
.file-browse-label {
  color: var(--accent);
  cursor: pointer;
  text-decoration: underline;
}

/* Manual Input Bar */
.manual-input-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 0 4px;
}
.manual-input {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 8px 12px;
  color: var(--text);
  font-size: 0.8rem;
  outline: none;
}
.manual-input:focus {
  border-color: var(--accent);
}
.name-input {
  width: 180px;
  flex-shrink: 0;
}
.comment-input {
  flex: 1;
}
.btn-sm {
  padding: 6px 14px;
  font-size: 0.8rem;
}
.btn-view-thesis {
  background: rgba(59, 130, 246, 0.15);
  color: #3B82F6;
  border: 1px solid rgba(59, 130, 246, 0.3);
}
.btn-view-thesis:hover {
  background: rgba(59, 130, 246, 0.3);
}
.applied-highlight {
  background: rgba(34, 197, 94, 0.2);
  border-left: 3px solid #22C55E;
  padding: 2px 6px;
  border-radius: 3px;
}
@keyframes appliedPulse {
  0%, 100% { background: rgba(34, 197, 94, 0.2); }
  50% { background: rgba(34, 197, 94, 0.45); }
}

/* Floating AI Suggestion Reference Panel */
.ai-suggestion-float {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 900px;
  background: #1a2332;
  border: 1px solid rgba(59, 130, 246, 0.4);
  border-bottom: none;
  border-radius: 12px 12px 0 0;
  box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.5);
  z-index: 9999;
  overflow: hidden;
}
.ai-float-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(59, 130, 246, 0.1);
  border-bottom: 1px solid rgba(59, 130, 246, 0.2);
  font-weight: 600;
  color: #93C5FD;
}
.ai-float-header .material-icons {
  font-size: 18px;
  color: #60A5FA;
}
.ai-float-section {
  margin-left: auto;
  font-size: 0.8rem;
  background: rgba(59, 130, 246, 0.15);
  padding: 3px 10px;
  border-radius: 12px;
  color: #93C5FD;
}
.ai-float-close {
  background: none;
  border: none;
  color: #94A3B8;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  margin-left: 8px;
}
.ai-float-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #E2E8F0;
}
.ai-float-body {
  padding: 16px;
  max-height: 250px;
  overflow-y: auto;
}
.ai-float-comment {
  background: rgba(245, 158, 11, 0.08);
  border-left: 3px solid #F59E0B;
  padding: 10px 14px;
  border-radius: 0 8px 8px 0;
  margin-bottom: 12px;
  font-size: 0.85rem;
  color: #CBD5E1;
}
.ai-float-comment strong {
  color: #F59E0B;
  display: block;
  margin-bottom: 4px;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ai-float-suggestion {
  background: rgba(34, 197, 94, 0.08);
  border-left: 3px solid #22C55E;
  padding: 10px 14px;
  border-radius: 0 8px 8px 0;
  font-size: 0.85rem;
  color: #E2E8F0;
  line-height: 1.6;
}
.ai-float-suggestion strong {
  color: #22C55E;
  display: block;
  margin-bottom: 4px;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ai-float-suggestion p {
  margin: 0;
}
.ai-float-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(0, 0, 0, 0.15);
}
.ai-float-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: #64748B;
  flex: 1;
}
.ai-float-hint .material-icons {
  font-size: 16px;
  color: #3B82F6;
}

/* Slide-up transition */
.slide-up-enter-active, .slide-up-leave-active {
  transition: transform 0.35s ease, opacity 0.25s ease;
}
.slide-up-enter-from, .slide-up-leave-to {
  transform: translateX(-50%) translateY(100%);
  opacity: 0;
}
.slide-up-enter-to, .slide-up-leave-from {
  transform: translateX(-50%) translateY(0);
  opacity: 1;
}
.btn-outline-save {
  background: transparent;
  color: #22C55E;
  border: 1px solid rgba(34, 197, 94, 0.4);
}
.btn-outline-save:hover {
  background: rgba(34, 197, 94, 0.1);
}
.btn-warning {
  background: rgba(245, 158, 11, 0.15);
  color: #F59E0B;
  border: 1px solid rgba(245, 158, 11, 0.3);
}
.btn-warning:hover {
  background: rgba(245, 158, 11, 0.25);
}
.btn-danger {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}
.btn-danger:hover {
  background: rgba(239, 68, 68, 0.25);
}
:deep(.status-badge.applied) {
  background: rgba(34, 197, 94, 0.15);
  color: #22C55E;
}
/* Search highlight — injected directly into DOM (use :deep for scoped) */
:deep(.search-hl) {
  background: rgba(59, 130, 246, 0.35) !important;
  color: #93c5fd !important;
  border-bottom: 2px solid #3B82F6 !important;
  padding: 1px 2px !important;
  border-radius: 2px !important;
  font-weight: bold !important;
}
:deep(.search-hl-active) {
  background: #3B82F6 !important;
  color: #fff !important;
  border-bottom: 3px solid #60a5fa !important;
  outline: 2px solid #93c5fd !important;
  outline-offset: 1px !important;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.6) !important;
}

/* Template Viewer */
.template-window {
  width: 95vw !important;
  max-width: 900px !important;
  max-height: 92vh !important;
}
.template-body {
  padding: 0 !important;
  background: #e8e8e8;
}
.tpl-page {
  width: 100%;
  max-width: 816px; /* 8.5in at 96dpi */
  margin: 20px auto;
  padding: 72px 72px;
  background: #fff;
  color: #1a1a1a;
  font-family: 'Times New Roman', Times, serif;
  font-size: 12pt;
  line-height: 2;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  position: relative;
  min-height: 500px;
}
.tpl-page.title-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 1056px; /* 11in at 96dpi */
}
.tpl-title-content {
  text-align: center;
  font-weight: bold;
  font-size: 14pt;
  line-height: 1.8;
}
.tpl-page-num {
  position: absolute;
  bottom: 36px;
  right: 72px;
  font-size: 11pt;
  color: #666;
}
.tpl-chapter {
  text-align: center;
  font-size: 14pt;
  font-weight: bold;
  margin-top: 60px;
  margin-bottom: 4px;
}
.tpl-chapter-title {
  text-align: center;
  font-size: 13pt;
  font-weight: bold;
  margin-bottom: 30px;
}
.tpl-section-heading {
  font-size: 12pt;
  font-weight: bold;
  margin: 24px 0 8px;
}
.tpl-paragraph {
  text-indent: 48px;
  margin: 0;
}
.tpl-reference {
  text-indent: -48px;
  padding-left: 48px;
  margin-bottom: 6px;
}
.tpl-empty {
  color: #999;
  text-align: center;
  padding: 20px 0;
}

/* Highlight Options */
.highlight-options {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.hl-toggle {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  transition: all 0.15s;
}
.hl-toggle.on {
  background: rgba(79,134,247,0.15);
  border-color: var(--accent);
  color: var(--accent);
}
.hl-toggle .material-icons { font-size: 18px; }
.color-pick {
  display: flex;
  align-items: center;
  gap: 4px;
}
.color-pick label {
  font-size: 0.7rem;
  color: var(--text-muted);
}
.color-swatch {
  width: 24px;
  height: 24px;
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0;
  cursor: pointer;
  background: none;
}
.underline-toggle {
  display: flex;
  align-items: center;
}
.toggle-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.7rem;
  color: var(--text-muted);
  cursor: pointer;
}
.toggle-label input { accent-color: var(--accent); }
.scope-btns {
  display: flex;
  gap: 2px;
  background: var(--bg);
  border-radius: 6px;
  padding: 2px;
  border: 1px solid var(--border);
}
.scope-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  background: none;
  border: none;
  padding: 4px 10px;
  border-radius: 4px;
  color: var(--text-muted);
  font-size: 0.7rem;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.scope-btn .material-icons {
  font-size: 14px;
}
.scope-btn.active {
  background: var(--accent);
  color: #fff;
}
.scope-btn:hover:not(.active) {
  background: rgba(79, 134, 247, 0.1);
  color: var(--text);
}

/* Diff marks use inline styles now — controlled by user color picker */

/* Save Version Modal */
.save-modal {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  width: 90vw;
  max-width: 500px;
  overflow: hidden;
}
.save-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.save-modal-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1rem;
  color: var(--text);
  margin: 0;
}
.save-modal-header h3 .material-icons {
  color: var(--accent);
}
.save-modal-body {
  padding: 20px;
}
.save-field {
  margin-bottom: 16px;
}
.save-field label {
  display: block;
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-bottom: 6px;
  font-weight: 600;
}
.save-input {
  width: 100%;
  background: #0D1520;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  color: #E2E8F0;
  font-size: 0.9rem;
  outline: none;
  box-sizing: border-box;
}
.save-input:focus {
  border-color: var(--accent);
}
.save-input::placeholder {
  color: var(--text-muted);
  opacity: 0.5;
}
.save-textarea {
  resize: vertical;
  min-height: 70px;
  font-family: inherit;
}
.save-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8rem;
  color: var(--text-muted);
  padding: 10px 14px;
  background: rgba(79, 134, 247, 0.08);
  border-radius: 8px;
}
.save-info .material-icons {
  font-size: 18px;
  color: var(--accent);
}
.save-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--border);
}
</style>
