<template>
  <div class="ml-models">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">model_training</span> ML &amp; AI Models</h1>
        <p class="subtitle">Monitor AI agents, test Billy chatbot, manage knowledge base, and view analytics</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost" @click="refreshAll" :disabled="refreshing">
          <span class="material-icons" :class="{ spin: refreshing }">sync</span> Refresh All
        </button>
      </div>
    </div>

    <!-- Tab Navigation -->
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        <span class="material-icons">{{ tab.icon }}</span>
        {{ tab.label }}
      </button>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 0: ML Configuration                                       -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'ml-config'" class="tab-content">
      <div v-if="mlConfigLoading" class="loading-state">
        <span class="material-icons spin">sync</span> Loading ML configuration...
      </div>
      <div v-else class="ml-config-layout">
        <!-- Mode Switch Card -->
        <div class="config-card mode-switch-card">
          <div class="config-card-header">
            <span class="material-icons">swap_horiz</span>
            <h3>Inference Mode</h3>
          </div>
          <p class="config-card-desc">Choose how the Android app processes banknote scans. This setting is read by the app on startup.</p>
          <div class="mode-toggle">
            <button
              class="mode-btn"
              :class="{ active: mlConfig.scan_mode === 'cloud' }"
              @click="setMlMode('cloud')"
            >
              <span class="material-icons">cloud</span>
              <span class="mode-label">Cloud API</span>
              <span class="mode-sub">FastAPI on Cloud Run</span>
            </button>
            <button
              class="mode-btn"
              :class="{ active: mlConfig.scan_mode === 'on_device' }"
              @click="setMlMode('on_device')"
            >
              <span class="material-icons">phone_android</span>
              <span class="mode-label">On-Device (TFLite)</span>
              <span class="mode-sub">Local inference</span>
            </button>
          </div>
          <div class="mode-status">
            <span class="material-icons" :style="{ color: mlConfig.scan_mode === 'cloud' ? '#4F86F7' : '#22C55E' }">
              {{ mlConfig.scan_mode === 'cloud' ? 'cloud_done' : 'offline_bolt' }}
            </span>
            <span>Current mode: <strong>{{ mlConfig.scan_mode === 'cloud' ? 'Cloud API' : 'On-Device TFLite' }}</strong></span>
            <span v-if="mlConfig.updated_at" class="mode-updated">Updated {{ formatTime(mlConfig.updated_at) }} by {{ mlConfig.updated_by || 'admin' }}</span>
          </div>
        </div>

        <!-- Performance Comparison Card -->
        <div class="config-card comparison-card">
          <div class="config-card-header">
            <span class="material-icons">compare</span>
            <h3>Performance Comparison</h3>
          </div>
          <div class="comparison-table">
            <table>
              <thead>
                <tr>
                  <th>Aspect</th>
                  <th><span class="material-icons">cloud</span> Cloud API</th>
                  <th><span class="material-icons">phone_android</span> On-Device</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Latency</td>
                  <td>2-8s (network dependent)</td>
                  <td class="highlight">100-500ms</td>
                </tr>
                <tr>
                  <td>Offline Support</td>
                  <td>No - requires internet</td>
                  <td class="highlight">Yes - fully offline</td>
                </tr>
                <tr>
                  <td>Accuracy</td>
                  <td class="highlight">High (GPU inference)</td>
                  <td>Good (quantized models)</td>
                </tr>
                <tr>
                  <td>Storage Cost</td>
                  <td class="highlight">None</td>
                  <td>22-165MB (model dependent)</td>
                </tr>
                <tr>
                  <td>Battery Impact</td>
                  <td class="highlight">Low (server-side)</td>
                  <td>Medium (CPU/GPU on device)</td>
                </tr>
                <tr>
                  <td>Privacy</td>
                  <td>Images sent to server</td>
                  <td class="highlight">All processing local</td>
                </tr>
                <tr>
                  <td>Cold Start</td>
                  <td>1-2min (Cloud Run wake)</td>
                  <td class="highlight">Instant (model cached)</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Model Registry Table -->
        <div class="config-card models-card">
          <div class="config-card-header">
            <span class="material-icons">inventory_2</span>
            <h3>Model Registry</h3>
            <span class="total-size-badge">
              Active: {{ activeModelsTotalSize.toFixed(1) }} MB
            </span>
          </div>
          <p class="config-card-desc">Select which TFLite models to deploy on-device. Recommended combo: Counterfeit + Security Quantized (22.6 MB total).</p>
          <table class="registry-table">
            <thead>
              <tr>
                <th>Active</th>
                <th>Model</th>
                <th>File</th>
                <th>Size</th>
                <th>Format</th>
                <th>Classes</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="model in modelRegistry" :key="model.key" :class="{ 'recommended-row': model.recommended }">
                <td>
                  <label class="toggle-switch">
                    <input
                      type="checkbox"
                      :checked="isModelActive(model.key)"
                      @change="toggleModel(model.key, $event.target.checked, model)"
                      :disabled="mlConfigSaving"
                    />
                    <span class="toggle-slider"></span>
                  </label>
                </td>
                <td>
                  <div class="model-name">
                    {{ model.name }}
                    <span v-if="model.recommended" class="recommended-badge">Recommended</span>
                  </div>
                  <div class="model-desc">{{ model.description }}</div>
                </td>
                <td class="mono">{{ model.file }}</td>
                <td>{{ model.size_mb }} MB</td>
                <td><span class="format-badge" :class="model.format">{{ model.format }}</span></td>
                <td>{{ model.classes }}</td>
                <td>
                  <span class="status-pill available">
                    <span class="material-icons">check_circle</span> In Storage
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Save Button -->
        <div class="config-actions">
          <button class="btn btn-primary" @click="saveMlConfig" :disabled="mlConfigSaving">
            <span class="material-icons" :class="{ spin: mlConfigSaving }">{{ mlConfigSaving ? 'sync' : 'save' }}</span>
            {{ mlConfigSaving ? 'Saving...' : 'Save Configuration' }}
          </button>
          <span v-if="mlConfig.updated_at" class="save-hint">
            Firebase RTDB path: <code>ml_config/</code>
          </span>
        </div>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 1: AI Agent Dashboard                                     -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'agents'" class="tab-content">
      <div class="agents-grid">
        <div
          v-for="agent in agents"
          :key="agent.id"
          class="agent-card"
          :class="'border-' + agent.statusColor"
        >
          <div class="agent-header">
            <div class="agent-icon" :style="{ background: agent.iconBg }">
              <span class="material-icons">{{ agent.icon }}</span>
            </div>
            <span class="agent-status-badge" :class="agent.statusColor">
              <span class="status-dot"></span>
              {{ agent.status }}
            </span>
          </div>
          <h3 class="agent-name">{{ agent.name }}</h3>
          <p class="agent-desc">{{ agent.description }}</p>
          <div class="agent-metrics">
            <div v-for="metric in agent.metrics" :key="metric.label" class="metric-item">
              <span class="metric-value">{{ metric.value }}</span>
              <span class="metric-label">{{ metric.label }}</span>
            </div>
          </div>
          <div class="agent-footer">
            <span class="last-checked">Checked: {{ agent.lastChecked || 'Never' }}</span>
            <button class="btn-sm" @click="pingAgent(agent)" :disabled="agent.pinging">
              <span class="material-icons" :class="{ spin: agent.pinging }">{{ agent.pinging ? 'sync' : 'play_arrow' }}</span>
              Ping
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 2: Billy AI Test Console                                  -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'console'" class="tab-content">
      <div class="console-layout">
        <div class="chat-container">
          <div class="chat-header">
            <span class="material-icons">smart_toy</span>
            <span>Billy AI Test Console</span>
            <button class="btn-sm ml-auto" @click="clearChat">
              <span class="material-icons">delete_sweep</span> Clear
            </button>
          </div>
          <div class="chat-messages" ref="chatMessages">
            <div v-if="chatHistory.length === 0" class="chat-empty">
              <span class="material-icons">chat_bubble_outline</span>
              <p>Send a message to test Billy's responses</p>
              <div class="quick-prompts">
                <button v-for="prompt in quickPrompts" :key="prompt" class="quick-prompt-btn" @click="sendMessage(prompt)">
                  {{ prompt }}
                </button>
              </div>
            </div>
            <div
              v-for="(msg, idx) in chatHistory"
              :key="idx"
              class="chat-msg"
              :class="msg.role"
            >
              <div class="msg-bubble">
                <div class="msg-text" v-html="formatMarkdown(msg.content)"></div>
                <div class="msg-meta" v-if="msg.role === 'assistant'">
                  <span class="response-path" :class="msg.path">{{ msg.path }}</span>
                  <span class="response-time">{{ msg.responseTime }}ms</span>
                </div>
              </div>
            </div>
            <div v-if="billyTyping" class="chat-msg assistant">
              <div class="msg-bubble typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>
          <div class="chat-input-bar">
            <input
              v-model="chatInput"
              @keydown.enter="sendMessage(chatInput)"
              placeholder="Type a message to test Billy..."
              :disabled="billyTyping"
            />
            <button class="send-btn" @click="sendMessage(chatInput)" :disabled="!chatInput.trim() || billyTyping">
              <span class="material-icons">send</span>
            </button>
          </div>
        </div>
        <div class="console-sidebar">
          <h4>Response Routing</h4>
          <div class="route-legend">
            <div class="route-item"><span class="response-path Local-KB">Local KB</span> Instant match from knowledge base</div>
            <div class="route-item"><span class="response-path Firebase-KB">Firebase KB</span> Match from Firebase custom entries</div>
            <div class="route-item"><span class="response-path API">API</span> Sent to Billy API endpoint</div>
            <div class="route-item"><span class="response-path Fallback">Fallback</span> No match, generic response</div>
          </div>
          <h4 style="margin-top:16px">Session Stats</h4>
          <div class="session-stats">
            <div class="ss-row"><span>Messages Sent</span><span>{{ chatHistory.filter(m => m.role === 'user').length }}</span></div>
            <div class="ss-row"><span>Local KB Hits</span><span>{{ chatHistory.filter(m => m.path === 'Local-KB').length }}</span></div>
            <div class="ss-row"><span>Firebase KB Hits</span><span>{{ chatHistory.filter(m => m.path === 'Firebase-KB').length }}</span></div>
            <div class="ss-row"><span>API Calls</span><span>{{ chatHistory.filter(m => m.path === 'API').length }}</span></div>
            <div class="ss-row"><span>Fallbacks</span><span>{{ chatHistory.filter(m => m.path === 'Fallback').length }}</span></div>
            <div class="ss-row"><span>Avg Response</span><span>{{ avgResponseTime }}ms</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 3: Billy Training Panel                                   -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'training'" class="tab-content">
      <div class="training-header">
        <h3>Knowledge Base Entries</h3>
        <div class="training-actions">
          <button class="btn btn-primary" @click="openEntryModal(null)">
            <span class="material-icons">add</span> Add Entry
          </button>
          <button class="btn btn-ghost" @click="exportKB">
            <span class="material-icons">download</span> Export JSON
          </button>
          <label class="btn btn-ghost import-btn">
            <span class="material-icons">upload</span> Import JSON
            <input type="file" accept=".json" @change="importKB" style="display:none" />
          </label>
        </div>
      </div>

      <div v-if="kbLoading" class="loading-state">
        <span class="material-icons spin">sync</span> Loading knowledge base...
      </div>

      <div v-else-if="knowledgeBase.length === 0" class="empty-state">
        <span class="material-icons">school</span>
        <p>No custom knowledge base entries yet</p>
        <p class="subtitle">Add entries to extend Billy's knowledge beyond the built-in responses</p>
      </div>

      <table v-else class="kb-table">
        <thead>
          <tr>
            <th>Topic</th>
            <th>Trigger Words</th>
            <th>Response Preview</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in knowledgeBase" :key="entry.id">
            <td class="topic-cell">{{ entry.topic }}</td>
            <td class="triggers-cell">
              <span v-for="t in entry.triggers.slice(0, 5)" :key="t" class="trigger-tag">{{ t }}</span>
              <span v-if="entry.triggers.length > 5" class="trigger-more">+{{ entry.triggers.length - 5 }}</span>
            </td>
            <td class="preview-cell">{{ truncate(entry.response, 120) }}</td>
            <td class="actions-cell">
              <button class="icon-btn" @click="openEntryModal(entry)" title="Edit">
                <span class="material-icons">edit</span>
              </button>
              <button class="icon-btn danger" @click="deleteEntry(entry)" title="Delete">
                <span class="material-icons">delete</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- Add/Edit Modal -->
      <div v-if="showEntryModal" class="modal-overlay" @click.self="showEntryModal = false">
        <div class="modal-content">
          <div class="modal-header">
            <h3>{{ editingEntry ? 'Edit' : 'Add' }} Knowledge Base Entry</h3>
            <button class="icon-btn" @click="showEntryModal = false">
              <span class="material-icons">close</span>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>Topic Name</label>
              <input v-model="entryForm.topic" placeholder="e.g. BSP Contact Info" />
            </div>
            <div class="form-group">
              <label>Trigger Words (comma-separated)</label>
              <input v-model="entryForm.triggersRaw" placeholder="e.g. bsp, contact, phone, office" />
            </div>
            <div class="form-group">
              <label>Response Text</label>
              <textarea v-model="entryForm.response" rows="8" placeholder="Billy's response when these triggers match..."></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-ghost" @click="showEntryModal = false">Cancel</button>
            <button class="btn btn-primary" @click="saveEntry" :disabled="!entryForm.topic || !entryForm.triggersRaw || !entryForm.response">
              {{ editingEntry ? 'Update' : 'Create' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 4: Response Analytics                                     -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'analytics'" class="tab-content">
      <div class="analytics-grid">
        <!-- Pie Chart: Response Distribution -->
        <div class="analytics-card wide">
          <h3><span class="material-icons">pie_chart</span> Response Distribution</h3>
          <div class="pie-chart-container">
            <div class="pie-chart">
              <svg viewBox="0 0 200 200">
                <circle
                  v-for="(slice, idx) in pieSlices"
                  :key="idx"
                  cx="100" cy="100" r="80"
                  fill="transparent"
                  :stroke="slice.color"
                  stroke-width="40"
                  :stroke-dasharray="slice.dashArray"
                  :stroke-dashoffset="slice.dashOffset"
                  :style="{ transform: 'rotate(-90deg)', transformOrigin: '100px 100px' }"
                />
                <text v-if="totalAnalytics === 0" x="100" y="105" text-anchor="middle" fill="var(--text-muted)" font-size="14">No data</text>
              </svg>
            </div>
            <div class="pie-legend">
              <div class="legend-item" v-for="(slice, idx) in pieSlices" :key="idx">
                <span class="legend-dot" :style="{ background: slice.color }"></span>
                <span class="legend-label">{{ slice.label }}</span>
                <span class="legend-value">{{ slice.count }} ({{ slice.percent }}%)</span>
              </div>
              <div class="legend-total">Total: {{ totalAnalytics }} responses</div>
            </div>
          </div>
        </div>

        <!-- Most Asked Topics -->
        <div class="analytics-card">
          <h3><span class="material-icons">trending_up</span> Most Asked Topics</h3>
          <div v-if="topTopics.length === 0" class="empty-mini">No conversation data yet</div>
          <div v-else class="topics-list">
            <div v-for="(topic, idx) in topTopics" :key="idx" class="topic-row">
              <span class="topic-rank">#{{ idx + 1 }}</span>
              <span class="topic-name">{{ topic.name }}</span>
              <span class="topic-count">{{ topic.count }}</span>
              <div class="topic-bar">
                <div class="topic-bar-fill" :style="{ width: topic.percent + '%' }"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- Unanswered Queries -->
        <div class="analytics-card">
          <h3><span class="material-icons">help_outline</span> Unanswered Queries (Fallbacks)</h3>
          <div v-if="unansweredQueries.length === 0" class="empty-mini">No fallback queries recorded</div>
          <div v-else class="unanswered-list">
            <div v-for="(q, idx) in unansweredQueries.slice(0, 20)" :key="idx" class="unanswered-row">
              <span class="material-icons">chat_bubble_outline</span>
              <span class="unanswered-text">{{ q.message }}</span>
              <span class="unanswered-time">{{ formatTime(q.timestamp) }}</span>
            </div>
          </div>
        </div>

        <!-- Recent Conversations -->
        <div class="analytics-card wide">
          <h3><span class="material-icons">forum</span> Recent Conversations</h3>
          <div v-if="recentConversations.length === 0" class="empty-mini">No conversations recorded yet</div>
          <div v-else class="conversations-list">
            <div v-for="(conv, idx) in recentConversations.slice(0, 15)" :key="idx" class="conv-row">
              <div class="conv-user">
                <span class="material-icons">person</span>
                {{ conv.userName || 'Anonymous' }}
              </div>
              <div class="conv-message">{{ truncate(conv.lastMessage, 80) }}</div>
              <span class="response-path" :class="conv.responsePath || 'Fallback'">{{ conv.responsePath || 'Unknown' }}</span>
              <span class="conv-time">{{ formatTime(conv.timestamp) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================================ -->
    <!-- TAB 5: Scan Testing                                           -->
    <!-- ============================================================ -->
    <div v-if="activeTab === 'scan-testing'" class="tab-content">
      <!-- Sub-tab navigation -->
      <div class="scan-sub-tabs">
        <button class="scan-sub-tab" :class="{ active: scanSubTab === 'upload' }" @click="scanSubTab = 'upload'">
          <span class="material-icons">upload_file</span> Image Upload
        </button>
        <button class="scan-sub-tab" :class="{ active: scanSubTab === 'camera' }" @click="scanSubTab = 'camera'">
          <span class="material-icons">photo_camera</span> Camera Scanner
        </button>
        <button class="scan-sub-tab" :class="{ active: scanSubTab === 'websocket' }" @click="scanSubTab = 'websocket'">
          <span class="material-icons">stream</span> WebSocket Live
        </button>
        <button class="scan-sub-tab" :class="{ active: scanSubTab === 'response' }" @click="scanSubTab = 'response'">
          <span class="material-icons">data_object</span> API Response
        </button>
        <button class="scan-sub-tab" :class="{ active: scanSubTab === 'history' }" @click="scanSubTab = 'history'">
          <span class="material-icons">history</span> Scan History
        </button>
      </div>

      <!-- ===== Image Upload Scanner ===== -->
      <div v-if="scanSubTab === 'upload'" class="scan-panel">
        <div class="scan-upload-area"
          :class="{ 'drag-over': scanDragOver }"
          @dragover.prevent="scanDragOver = true"
          @dragleave="scanDragOver = false"
          @drop.prevent="handleScanDrop"
        >
          <div v-if="!scanUploadPreview" class="upload-placeholder">
            <span class="material-icons upload-icon">cloud_upload</span>
            <p>Drag & drop an image here, or click to select</p>
            <label class="btn btn-primary upload-btn">
              <span class="material-icons">folder_open</span> Choose File
              <input type="file" accept="image/*" @change="handleScanFileSelect" style="display:none" />
            </label>
          </div>
          <div v-else class="upload-preview-container">
            <img :src="scanUploadPreview" alt="Preview" class="scan-preview-img" />
            <button class="btn btn-ghost clear-preview-btn" @click="clearScanUpload">
              <span class="material-icons">close</span> Clear
            </button>
          </div>
        </div>
        <div class="scan-actions">
          <button class="btn btn-primary" @click="submitScanUpload" :disabled="!scanUploadPreview || scanUploading">
            <span class="material-icons" :class="{ spin: scanUploading }">{{ scanUploading ? 'sync' : 'search' }}</span>
            {{ scanUploading ? 'Scanning...' : 'Scan Image' }}
          </button>
        </div>
        <div v-if="scanUploadError" class="scan-error">
          <span class="material-icons">error</span> {{ scanUploadError }}
        </div>
        <div v-if="scanUploadResult" class="scan-result-card">
          <h4><span class="material-icons">verified</span> Scan Result</h4>
          <div class="result-grid">
            <div class="result-item">
              <span class="result-label">Status</span>
              <span class="result-value status-badge" :class="scanUploadResult.status === 'GENUINE' ? 'genuine' : 'counterfeit'">
                {{ scanUploadResult.status || 'N/A' }}
              </span>
            </div>
            <div class="result-item">
              <span class="result-label">Denomination</span>
              <span class="result-value">{{ scanUploadResult.denomination || 'N/A' }}</span>
            </div>
            <div class="result-item">
              <span class="result-label">Confidence</span>
              <span class="result-value">{{ scanUploadResult.confidence ? (scanUploadResult.confidence * 100).toFixed(1) + '%' : 'N/A' }}</span>
            </div>
            <div class="result-item">
              <span class="result-label">Response Time</span>
              <span class="result-value">{{ scanResponseTime }}ms</span>
            </div>
          </div>
          <div v-if="scanUploadResult.features && scanUploadResult.features.length" class="features-section">
            <h5>Detected Features</h5>
            <div class="features-list">
              <span v-for="f in scanUploadResult.features" :key="f" class="feature-tag">{{ f }}</span>
            </div>
          </div>
          <div v-if="scanUploadResult.annotated_image" class="annotated-section">
            <h5>Annotated Image</h5>
            <img :src="'data:image/jpeg;base64,' + scanUploadResult.annotated_image" alt="Annotated" class="annotated-img" />
          </div>
        </div>
      </div>

      <!-- ===== Camera Scanner ===== -->
      <div v-if="scanSubTab === 'camera'" class="scan-panel">
        <div class="camera-container">
          <video v-show="cameraActive && !cameraCaptured" ref="cameraVideo" autoplay playsinline class="camera-video"></video>
          <canvas v-show="cameraCaptured" ref="cameraCanvas" class="camera-canvas"></canvas>
          <div v-if="!cameraActive && !cameraCaptured" class="camera-placeholder">
            <span class="material-icons">videocam_off</span>
            <p>Camera is not active</p>
          </div>
        </div>
        <div class="scan-actions">
          <button v-if="!cameraActive && !cameraCaptured" class="btn btn-primary" @click="startCamera">
            <span class="material-icons">videocam</span> Start Camera
          </button>
          <button v-if="cameraActive && !cameraCaptured" class="btn btn-primary" @click="captureCamera">
            <span class="material-icons">camera</span> Capture
          </button>
          <button v-if="cameraActive" class="btn btn-ghost" @click="stopCamera">
            <span class="material-icons">stop</span> Stop Camera
          </button>
          <button v-if="cameraCaptured" class="btn btn-primary" @click="submitCameraScan" :disabled="cameraScanning">
            <span class="material-icons" :class="{ spin: cameraScanning }">{{ cameraScanning ? 'sync' : 'search' }}</span>
            {{ cameraScanning ? 'Scanning...' : 'Scan Capture' }}
          </button>
          <button v-if="cameraCaptured" class="btn btn-ghost" @click="retakeCamera">
            <span class="material-icons">replay</span> Retake
          </button>
        </div>
        <div v-if="cameraScanError" class="scan-error">
          <span class="material-icons">error</span> {{ cameraScanError }}
        </div>
        <div v-if="cameraScanResult" class="scan-result-card">
          <h4><span class="material-icons">verified</span> Camera Scan Result</h4>
          <div class="result-grid">
            <div class="result-item">
              <span class="result-label">Status</span>
              <span class="result-value status-badge" :class="cameraScanResult.status === 'GENUINE' ? 'genuine' : 'counterfeit'">
                {{ cameraScanResult.status || 'N/A' }}
              </span>
            </div>
            <div class="result-item">
              <span class="result-label">Denomination</span>
              <span class="result-value">{{ cameraScanResult.denomination || 'N/A' }}</span>
            </div>
            <div class="result-item">
              <span class="result-label">Confidence</span>
              <span class="result-value">{{ cameraScanResult.confidence ? (cameraScanResult.confidence * 100).toFixed(1) + '%' : 'N/A' }}</span>
            </div>
          </div>
          <div v-if="cameraScanResult.features && cameraScanResult.features.length" class="features-section">
            <h5>Detected Features</h5>
            <div class="features-list">
              <span v-for="f in cameraScanResult.features" :key="f" class="feature-tag">{{ f }}</span>
            </div>
          </div>
          <div v-if="cameraScanResult.annotated_image" class="annotated-section">
            <h5>Annotated Image</h5>
            <img :src="'data:image/jpeg;base64,' + cameraScanResult.annotated_image" alt="Annotated" class="annotated-img" />
          </div>
        </div>
      </div>

      <!-- ===== WebSocket Live Scanner ===== -->
      <div v-if="scanSubTab === 'websocket'" class="scan-panel">
        <div class="ws-status-bar">
          <div class="ws-indicator" :class="wsConnected ? 'connected' : wsConnecting ? 'connecting' : 'disconnected'">
            <span class="status-dot"></span>
            {{ wsConnected ? 'Connected' : wsConnecting ? 'Connecting...' : 'Disconnected' }}
          </div>
          <div class="ws-actions">
            <button v-if="!wsConnected && !wsConnecting" class="btn btn-primary" @click="wsConnect">
              <span class="material-icons">power</span> Connect
            </button>
            <button v-if="wsConnected && !wsStreaming" class="btn btn-primary" @click="wsStartStreaming">
              <span class="material-icons">play_arrow</span> Start Streaming
            </button>
            <button v-if="wsStreaming" class="btn btn-ghost" @click="wsStopStreaming" style="border-color:#EF4444;color:#EF4444">
              <span class="material-icons">stop</span> Stop Streaming
            </button>
            <button v-if="wsConnected" class="btn btn-ghost" @click="wsDisconnect" style="border-color:#EF4444;color:#EF4444">
              <span class="material-icons">power_off</span> Disconnect
            </button>
          </div>
        </div>
        <div class="ws-layout">
          <div class="ws-camera-section">
            <video v-show="wsCameraActive" ref="wsVideo" autoplay playsinline class="camera-video"></video>
            <canvas ref="wsCanvas" style="display:none"></canvas>
            <div v-if="!wsCameraActive" class="camera-placeholder">
              <span class="material-icons">videocam_off</span>
              <p>{{ wsConnected ? 'Click "Start Streaming" to begin' : 'Connect to WebSocket first' }}</p>
            </div>
          </div>
          <div class="ws-results-section">
            <h4>Live Results <span class="result-count">({{ wsResults.length }})</span></h4>
            <div class="ws-results-list">
              <div v-if="wsResults.length === 0" class="empty-mini">No results yet. Start streaming to see live detections.</div>
              <div v-for="(r, idx) in wsResults" :key="idx" class="ws-result-row">
                <span class="ws-result-status" :class="r.status === 'GENUINE' ? 'genuine' : 'counterfeit'">{{ r.status }}</span>
                <span class="ws-result-denom">{{ r.denomination || '?' }}</span>
                <span class="ws-result-conf">{{ r.confidence ? (r.confidence * 100).toFixed(1) + '%' : '-' }}</span>
                <span class="ws-result-time">{{ r.timestamp }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== API Response Viewer ===== -->
      <div v-if="scanSubTab === 'response'" class="scan-panel">
        <div v-if="!scanRawResponse" class="empty-state">
          <span class="material-icons">data_object</span>
          <p>No API response yet</p>
          <p class="subtitle">Perform a scan from the Image Upload or Camera tab to see the raw response</p>
        </div>
        <div v-else class="response-viewer">
          <div class="response-meta">
            <span class="meta-item"><strong>Status Code:</strong> {{ scanStatusCode }}</span>
            <span class="meta-item"><strong>Response Time:</strong> {{ scanResponseTime }}ms</span>
          </div>
          <pre class="json-viewer">{{ JSON.stringify(scanRawResponse, null, 2) }}</pre>
        </div>
      </div>

      <!-- ===== Scan History ===== -->
      <div v-if="scanSubTab === 'history'" class="scan-panel">
        <div class="scan-history-header">
          <h3>Recent Test Scans</h3>
          <button v-if="scanHistory.length > 0" class="btn btn-ghost" @click="clearScanHistory">
            <span class="material-icons">delete_sweep</span> Clear History
          </button>
        </div>
        <div v-if="scanHistory.length === 0" class="empty-state">
          <span class="material-icons">history</span>
          <p>No scan history yet</p>
          <p class="subtitle">Test scans will be recorded here for reference</p>
        </div>
        <table v-else class="kb-table">
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Scan Type</th>
              <th>Result</th>
              <th>Denomination</th>
              <th>Confidence</th>
              <th>Response Time</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(h, idx) in scanHistory" :key="idx">
              <td>{{ h.timestamp }}</td>
              <td><span class="trigger-tag">{{ h.scanType }}</span></td>
              <td>
                <span class="status-badge" :class="h.result === 'GENUINE' ? 'genuine' : h.result === 'COUNTERFEIT' ? 'counterfeit' : ''">
                  {{ h.result || 'N/A' }}
                </span>
              </td>
              <td>{{ h.denomination || 'N/A' }}</td>
              <td>{{ h.confidence || 'N/A' }}</td>
              <td>{{ h.responseTime }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref } from '../services/firebase.js'
import { get, set, push, remove, onValue, off } from 'firebase/database'

// ============================================================
// BILLY LOCAL KNOWLEDGE BASE (mirrors BillyAIService.java)
// ============================================================
const LOCAL_KNOWLEDGE_BASE = {
  greetings: {
    topic: 'Greetings',
    patterns: ['hello', 'hi', 'hey', 'good morning', 'good afternoon', 'kumusta', 'musta'],
    response: '\uD83D\uDC4B Hi there! I\'m Billy, your BillSense AI assistant.\n\n\uD83D\uDE0A How can I help you today? I can guide you on:\n\n\u2022 Bill authentication & security features\n\u2022 Using the BillSense app (Scan, Compare, Report)\n\u2022 Counterfeit prevention tips\n\u2022 Philippine currency knowledge\n\nJust ask anything! \uD83E\uDDD0'
  },
  scan: {
    topic: 'Scan Guide',
    patterns: ['how to scan', 'scan bill', 'camera scan', 'scan feature'],
    response: '\uD83D\uDCF1 Here\'s how to use Scan Bill:\n\n1\uFE0F\u20E3 Tap Scan Bill on the home screen\n2\uFE0F\u20E3 Choose your scan type:\n   \u2022 Standard \u2014 quick single-frame scan\n   \u2022 Multi-Angle \u2014 captures multiple views\n   \u2022 Video \u2014 continuous detection\n3\uFE0F\u20E3 Hold your camera steady over the banknote\n4\uFE0F\u20E3 Wait for the AI to analyze \u2014 results appear in seconds!\n\n\uD83D\uDCCC Tip: Good lighting makes a big difference. Natural daylight works best!\n\nWould you like to know about each scan mode in detail?'
  },
  watermark: {
    topic: 'Watermark Check',
    patterns: ['watermark', 'hold to light', 'shadow portrait'],
    response: '\uD83D\uDD0D Watermark Check:\n\nHold the bill up to a light source and look for:\n\n\u2705 A shadow portrait matching the printed portrait\n\u2705 The denomination number\n\u2705 Clear, sharp edges (fakes are often blurry)\n\nEach denomination has a unique watermark:\n\u2022 \u20B120: Manuel Quezon\n\u2022 \u20B150: Sergio Osme\u00F1a\n\u2022 \u20B1100: Manuel Roxas\n\u2022 \u20B1200: Diosdado Macapagal\n\u2022 \u20B1500: Benigno Aquino Jr.\n\u2022 \u20B11000: Heroes trio\n\n\uD83D\uDCCC Use the Step-by-Step Detection Guide for photo guidance!'
  },
  serial: {
    topic: 'Serial Number',
    patterns: ['serial', 'serial number', 'number check'],
    response: '\uD83D\uDD22 Serial Number Verification:\n\n\u2705 Both serial numbers (top-right and bottom-left) must match exactly\n\u2705 Look for consistent font, spacing, and alignment\n\u2705 Genuine serials are crisp and evenly printed\n\u2705 Under UV light, serial numbers should fluoresce\n\n\uD83D\uDCCC BillSense\'s Scan Bill feature automatically checks serial number consistency!'
  },
  uv: {
    topic: 'UV Features',
    patterns: ['uv', 'ultraviolet', 'black light', 'glow', 'fluorescent'],
    response: '\uD83D\uDCA1 UV (Ultraviolet) Features:\n\nUnder UV light, genuine Philippine bills reveal:\n\n\u2705 Security fibers that glow in different colors\n\u2705 Serial numbers that fluoresce\n\u2705 UV thread becomes clearly visible\n\u2705 Concealed value appears in hidden locations\n\n\uD83D\uDCCC BillSense\'s UV Scan mode detects these features automatically!\n\nWould you like to know which UV features are on a specific denomination?'
  },
  compare: {
    topic: 'Compare Feature',
    patterns: ['compare', 'comparison', 'side by side', 'genuine vs fake'],
    response: '\uD83D\uDCCA Compare Bill Feature:\n\n1\uFE0F\u20E3 Open Compare Bill from the home screen\n2\uFE0F\u20E3 Upload or capture a photo of your suspected note\n3\uFE0F\u20E3 View it side-by-side with a genuine reference\n4\uFE0F\u20E3 Check key security features point by point\n\n\uD83D\uDCCC Great for learning what genuine bills look like vs. counterfeits!\n\nWould you like tips on specific features to compare?'
  },
  report: {
    topic: 'Report / Evidence',
    patterns: ['report', 'evidence', 'found fake', 'counterfeit found', 'submit case'],
    response: '\uD83D\uDEA8 Reporting Suspected Counterfeits:\n\n1\uFE0F\u20E3 Go to Evidence Submission in the app\n2\uFE0F\u20E3 Take clear photos of the front and back\n3\uFE0F\u20E3 Add the location where you received the bill\n4\uFE0F\u20E3 Submit your case \u2014 it helps the community!\n\n\uD83D\uDCCC View reported cases on the Cases Map to see counterfeit hotspots.\n\n\u26A0\uFE0F Important: Do not circulate suspected counterfeit bills. Contact your nearest BSP office or police station.'
  },
  feelLookTilt: {
    topic: 'Feel-Look-Tilt Method',
    patterns: ['feel look tilt', 'how to check', 'is this real', 'is this fake', 'genuine', 'authenticate', 'verify bill', 'check bill'],
    response: '\uD83E\uDDD0 Use the Feel, Look, Tilt method:\n\n1\uFE0F\u20E3 FEEL \u2014 Genuine bills have a distinct cotton-linen texture with raised print on key areas (denomination, portraits)\n\n2\uFE0F\u20E3 LOOK \u2014 Hold up to light:\n   \u2022 Watermark portrait should be clear\n   \u2022 Security thread should be embedded (not printed on)\n   \u2022 See-through register should align perfectly\n\n3\uFE0F\u20E3 TILT \u2014 Observe:\n   \u2022 Color-shifting ink (\u20B1500/\u20B11000)\n   \u2022 Holographic elements\n   \u2022 Optically variable devices\n\n\uD83D\uDCCC For AI-powered verification, use Scan Bill in BillSense!\n\nWhich denomination would you like to check?'
  },
  law: {
    topic: 'Law / Legal',
    patterns: ['law', 'penalty', 'punishment', 'illegal', 'crime', 'prison', 'legal'],
    response: '\u2696\uFE0F Anti-Counterfeiting Laws (Philippines):\n\nUnder Republic Act No. 10951:\n\n\uD83D\uDCCC Counterfeiting Philippine currency is a serious criminal offense\n\uD83D\uDCCC Penalties include imprisonment and fines\n\uD83D\uDCCC Even possessing or passing counterfeit bills knowingly is punishable\n\n\u26A0\uFE0F This is educational information only \u2014 for specific legal questions, please consult the BSP or a legal professional.\n\nWould you like to know how to protect yourself from receiving counterfeit bills?'
  },
  help: {
    topic: 'Help / Features',
    patterns: ['help', 'what can you do', 'features', 'menu', 'options', 'guide me'],
    response: '\uD83D\uDE0A Here\'s what I can help you with:\n\n\uD83D\uDD0D Bill Verification \u2014 How to check if a bill is genuine\n\uD83D\uDCF1 App Features \u2014 Scan Bill, Compare Bill, Step-by-Step Guide\n\uD83D\uDCA1 Security Features \u2014 Watermarks, UV, serial numbers, OVD\n\uD83D\uDCCA Tutorials \u2014 Step-by-step guides for each feature\n\uD83D\uDEA8 Reporting \u2014 How to submit evidence of counterfeits\n\u2696\uFE0F Laws \u2014 Anti-counterfeiting regulations\n\uD83D\uDCDA Education \u2014 Currency history and design\n\nJust ask about any of these topics! What would you like to know?'
  },
  denomination1000: {
    topic: 'PHP 1000 Note',
    patterns: ['1000', 'one thousand'],
    response: '\uD83D\uDCB5 \u20B11000 Note Security Features:\n\n\u2705 Watermark: Three heroes (Abad Santos, Lim, Escoda)\n\u2705 Wide holographic security thread with color shift\n\u2705 Color-shifting ink on denomination number\n\u2705 Microprinting around portrait area\n\u2705 Concealed value visible under UV\n\u2705 Serial number fluorescence under UV\n\n\uD83D\uDCCC The \u20B11000 has the most security features. Use Scan Bill for AI-powered verification!'
  },
  denomination500: {
    topic: 'PHP 500 Note',
    patterns: ['500', 'five hundred'],
    response: '\uD83D\uDCB5 \u20B1500 Note Security Features:\n\n\u2705 Watermark: Benigno Aquino Jr.\n\u2705 Holographic security strip\n\u2705 Color-shifting ink (gold to green)\n\u2705 Microprinting details\n\u2705 UV-reactive security fibers\n\u2705 Embossed denomination for the visually impaired\n\n\uD83D\uDCCC Tip: The color-shifting ink is one of the hardest features to counterfeit!'
  },
  thankYou: {
    topic: 'Thank You',
    patterns: ['thank', 'thanks', 'salamat', 'thank you'],
    response: 'You\'re welcome! \uD83D\uDE0A Happy to help!\n\nRemember, you can always come back if you have more questions about bill verification or the BillSense app.\n\nStay safe and always check your bills! \u2705'
  }
}

export default {
  name: 'MLModels',
  data() {
    return {
      activeTab: 'ml-config',
      refreshing: false,
      tabs: [
        { id: 'ml-config', label: 'ML Configuration', icon: 'settings_suggest' },
        { id: 'agents', label: 'AI Agents', icon: 'hub' },
        { id: 'console', label: 'Billy Test Console', icon: 'smart_toy' },
        { id: 'training', label: 'Training Panel', icon: 'school' },
        { id: 'analytics', label: 'Analytics', icon: 'analytics' },
        { id: 'scan-testing', label: 'Scan Testing', icon: 'document_scanner' }
      ],

      // --- ML Configuration ---
      mlConfig: {
        scan_mode: 'cloud',
        active_models: {
          counterfeit: { enabled: true, file: 'counterfeit_best_float32.tflite', size_mb: 11.7 },
          security: { enabled: true, file: 'security_best_int8.tflite', size_mb: 10.9 },
          simple: { enabled: false, file: 'simple_model_float16.tflite', size_mb: 50.5 },
          uv: { enabled: false, file: 'uv_model_float16.tflite', size_mb: 50.6 }
        },
        updated_at: null,
        updated_by: null
      },
      mlConfigLoading: true,
      mlConfigSaving: false,
      modelRegistry: [
        { key: 'counterfeit', name: 'Counterfeit Detection', file: 'counterfeit_best_float32.tflite', size_mb: 11.7, format: 'float32', classes: 7, description: 'Classifies Philippine banknotes as real or counterfeit', recommended: true },
        { key: 'security', name: 'Security Features (Quantized)', file: 'security_best_int8.tflite', size_mb: 10.9, format: 'int8', classes: 6, description: 'Detects UV security features - quantized for speed', recommended: true },
        { key: 'security_full', name: 'Security Features (Full)', file: 'security_best_float32.tflite', size_mb: 42.7, format: 'float32', classes: 6, description: 'Detects UV security features - full precision' },
        { key: 'simple', name: 'Real vs Fake (Simple)', file: 'simple_model_float16.tflite', size_mb: 50.5, format: 'float16', classes: 2, description: 'Binary real/fake classification model' },
        { key: 'uv', name: 'UV Features', file: 'uv_model_float16.tflite', size_mb: 50.6, format: 'float16', classes: 6, description: 'UV-specific feature detection model' }
      ],

      // --- Agents ---
      agents: [
        {
          id: 'billy',
          name: 'Billy AI Assistant',
          description: 'Mobile chatbot for currency authentication guidance',
          icon: 'smart_toy',
          iconBg: 'linear-gradient(135deg, #4F86F7, #6C63FF)',
          status: 'Checking...',
          statusColor: 'warning',
          pinging: false,
          lastChecked: null,
          metrics: [
            { label: 'Responses', value: '...' },
            { label: 'Avg Time', value: '...' },
            { label: 'KB Entries', value: Object.keys(LOCAL_KNOWLEDGE_BASE).length }
          ]
        },
        {
          id: 'thesis',
          name: 'Thesis Validator Agent',
          description: 'Pipeline-based thesis content validation and review',
          icon: 'school',
          iconBg: 'linear-gradient(135deg, #22C55E, #16A34A)',
          status: 'Checking...',
          statusColor: 'warning',
          pinging: false,
          lastChecked: null,
          metrics: [
            { label: 'Validations', value: '...' },
            { label: 'Status', value: '...' }
          ]
        },
        {
          id: 'reports',
          name: 'Session Report Generator',
          description: 'Generates session reports from development activity',
          icon: 'assessment',
          iconBg: 'linear-gradient(135deg, #F59E0B, #D97706)',
          status: 'Checking...',
          statusColor: 'warning',
          pinging: false,
          lastChecked: null,
          metrics: [
            { label: 'Reports', value: '...' },
            { label: 'Status', value: '...' }
          ]
        },
        {
          id: 'vector',
          name: 'Vector Search Engine',
          description: 'TF-IDF + cosine similarity search for thesis documents',
          icon: 'travel_explore',
          iconBg: 'linear-gradient(135deg, #A855F7, #7C3AED)',
          status: 'Checking...',
          statusColor: 'warning',
          pinging: false,
          lastChecked: null,
          metrics: [
            { label: 'Indexed Chunks', value: '...' },
            { label: 'Status', value: '...' }
          ]
        },
        {
          id: 'ml-api',
          name: 'BillSense ML Detection API',
          description: 'YOLOv8 currency detection on Google Cloud Run',
          icon: 'model_training',
          iconBg: 'linear-gradient(135deg, #EF4444, #DC2626)',
          status: 'Checking...',
          statusColor: 'warning',
          pinging: false,
          lastChecked: null,
          metrics: [
            { label: 'Endpoint', value: 'Cloud Run' },
            { label: 'Latency', value: '...' }
          ]
        }
      ],

      // --- Console ---
      chatHistory: [],
      chatInput: '',
      billyTyping: false,
      firebaseKBEntries: {},
      quickPrompts: ['Hello', 'How to scan a bill?', 'What is a watermark?', 'PHP 1000 features', 'Help'],

      // --- Training ---
      knowledgeBase: [],
      kbLoading: true,
      showEntryModal: false,
      editingEntry: null,
      entryForm: { topic: '', triggersRaw: '', response: '' },

      // --- Analytics ---
      analyticsData: { localKB: 0, firebaseKB: 0, api: 0, fallback: 0 },
      recentConversations: [],
      unansweredQueries: [],
      topTopics: [],

      // --- Scan Testing ---
      scanSubTab: 'upload',
      // Upload scanner
      scanUploading: false,
      scanUploadResult: null,
      scanUploadError: null,
      scanDragOver: false,
      scanUploadPreview: null,
      // Camera scanner
      cameraActive: false,
      cameraStream: null,
      cameraCaptured: false,
      cameraScanResult: null,
      cameraScanError: null,
      cameraScanning: false,
      // WebSocket scanner
      wsConnected: false,
      wsConnecting: false,
      wsSocket: null,
      wsStreaming: false,
      wsCameraStream: null,
      wsCameraActive: false,
      wsResults: [],
      wsFrameInterval: null,
      // API response viewer
      scanRawResponse: null,
      scanResponseTime: null,
      scanStatusCode: null,
      // Scan history
      scanHistory: JSON.parse(localStorage.getItem('billsense_scan_history') || '[]'),

      // Listeners
      _kbListener: null,
      _chatsListener: null,
      _analyticsListener: null
    }
  },

  computed: {
    activeModelsTotalSize() {
      let total = 0
      for (const [key, model] of Object.entries(this.mlConfig.active_models)) {
        if (model.enabled) total += model.size_mb
      }
      return total
    },
    avgResponseTime() {
      const times = this.chatHistory.filter(m => m.role === 'assistant' && m.responseTime).map(m => m.responseTime)
      if (times.length === 0) return 0
      return Math.round(times.reduce((a, b) => a + b, 0) / times.length)
    },

    totalAnalytics() {
      const d = this.analyticsData
      return d.localKB + d.firebaseKB + d.api + d.fallback
    },

    pieSlices() {
      const d = this.analyticsData
      const total = this.totalAnalytics
      if (total === 0) return []

      const items = [
        { label: 'Local KB', count: d.localKB, color: '#22C55E' },
        { label: 'Firebase KB', count: d.firebaseKB, color: '#4F86F7' },
        { label: 'API', count: d.api, color: '#F59E0B' },
        { label: 'Fallback', count: d.fallback, color: '#EF4444' }
      ].filter(i => i.count > 0)

      const circumference = 2 * Math.PI * 80
      let offset = 0
      return items.map(item => {
        const percent = Math.round((item.count / total) * 100)
        const dash = (item.count / total) * circumference
        const slice = {
          ...item,
          percent,
          dashArray: `${dash} ${circumference - dash}`,
          dashOffset: -offset
        }
        offset += dash
        return slice
      })
    }
  },

  mounted() {
    this.loadMlConfig()
    this.initFirebaseListeners()
    this.checkAllAgents()
  },

  beforeUnmount() {
    if (this._kbListener) off(ref(database, 'billy_knowledge_base'), 'value', this._kbListener)
    if (this._chatsListener) off(ref(database, 'Billy Chats'), 'value', this._chatsListener)
    if (this._analyticsListener) off(ref(database, 'billy_analytics'), 'value', this._analyticsListener)
    // Cleanup scan testing resources
    this.stopCamera()
    this.wsDisconnect()
  },

  methods: {
    // ============================================================
    // ML CONFIGURATION
    // ============================================================
    async loadMlConfig() {
      this.mlConfigLoading = true
      try {
        const snapshot = await get(ref(database, 'ml_config'))
        if (snapshot.exists()) {
          const data = snapshot.val()
          this.mlConfig.scan_mode = data.scan_mode || 'cloud'
          if (data.active_models) {
            this.mlConfig.active_models = { ...this.mlConfig.active_models, ...data.active_models }
          }
          this.mlConfig.updated_at = data.updated_at || null
          this.mlConfig.updated_by = data.updated_by || null
        }
      } catch (e) {
        console.error('Failed to load ML config:', e)
      }
      this.mlConfigLoading = false
    },

    setMlMode(mode) {
      this.mlConfig.scan_mode = mode
    },

    isModelActive(key) {
      return this.mlConfig.active_models[key]?.enabled === true
    },

    toggleModel(key, checked, model) {
      if (this.mlConfig.active_models[key]) {
        this.mlConfig.active_models[key].enabled = checked
      } else {
        this.mlConfig.active_models[key] = {
          enabled: checked,
          file: model.file,
          size_mb: model.size_mb
        }
      }
    },

    async saveMlConfig() {
      this.mlConfigSaving = true
      try {
        const configData = {
          scan_mode: this.mlConfig.scan_mode,
          active_models: { ...this.mlConfig.active_models },
          updated_at: new Date().toISOString(),
          updated_by: 'admin'
        }
        await set(ref(database, 'ml_config'), configData)
        this.mlConfig.updated_at = configData.updated_at
        this.mlConfig.updated_by = configData.updated_by
        alert('ML configuration saved successfully!')
      } catch (e) {
        console.error('Failed to save ML config:', e)
        alert('Failed to save: ' + e.message)
      }
      this.mlConfigSaving = false
    },

    // ============================================================
    // FIREBASE LISTENERS
    // ============================================================
    initFirebaseListeners() {
      // Knowledge Base listener
      const kbRef = ref(database, 'billy_knowledge_base')
      this._kbListener = onValue(kbRef, (snapshot) => {
        this.kbLoading = false
        if (!snapshot.exists()) {
          this.knowledgeBase = []
          this.firebaseKBEntries = {}
          return
        }
        const data = snapshot.val()
        this.firebaseKBEntries = data
        this.knowledgeBase = Object.keys(data).map(key => ({
          id: key,
          topic: data[key].topic || 'Untitled',
          triggers: (data[key].triggers || '').split(',').map(t => t.trim()).filter(Boolean),
          response: data[key].response || ''
        }))
      }, (error) => {
        console.error('KB listener error:', error)
        this.kbLoading = false
      })

      // Billy Chats listener
      const chatsRef = ref(database, 'Billy Chats')
      this._chatsListener = onValue(chatsRef, (snapshot) => {
        if (!snapshot.exists()) {
          this.recentConversations = []
          this.unansweredQueries = []
          this.topTopics = []
          return
        }
        const data = snapshot.val()
        this.processConversations(data)
      }, (error) => {
        console.error('Chats listener error:', error)
      })

      // Analytics listener
      const analyticsRef = ref(database, 'billy_analytics')
      this._analyticsListener = onValue(analyticsRef, (snapshot) => {
        if (!snapshot.exists()) return
        const data = snapshot.val()
        this.analyticsData = {
          localKB: data.localKB || 0,
          firebaseKB: data.firebaseKB || 0,
          api: data.api || 0,
          fallback: data.fallback || 0
        }
      }, (error) => {
        console.error('Analytics listener error:', error)
      })
    },

    processConversations(data) {
      const conversations = []
      const topicCounts = {}
      const fallbacks = []

      // data is keyed by userId -> messages
      for (const userId of Object.keys(data)) {
        const userChats = data[userId]
        if (typeof userChats !== 'object') continue

        for (const chatId of Object.keys(userChats)) {
          const chat = userChats[chatId]
          if (!chat) continue

          const entry = {
            userName: chat.userName || userId,
            lastMessage: chat.userMessage || chat.message || '',
            responsePath: chat.responsePath || 'Unknown',
            timestamp: chat.timestamp || 0
          }
          conversations.push(entry)

          // Track topics
          const topic = chat.matchedTopic || chat.responsePath || 'Unknown'
          topicCounts[topic] = (topicCounts[topic] || 0) + 1

          // Track fallbacks
          if (chat.responsePath === 'Fallback' || chat.responsePath === 'fallback') {
            fallbacks.push({
              message: chat.userMessage || chat.message || '',
              timestamp: chat.timestamp || 0
            })
          }
        }
      }

      // Sort by timestamp desc
      conversations.sort((a, b) => b.timestamp - a.timestamp)
      this.recentConversations = conversations

      // Top topics
      const maxCount = Math.max(...Object.values(topicCounts), 1)
      this.topTopics = Object.entries(topicCounts)
        .map(([name, count]) => ({ name, count, percent: Math.round((count / maxCount) * 100) }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10)

      // Unanswered
      fallbacks.sort((a, b) => b.timestamp - a.timestamp)
      this.unansweredQueries = fallbacks
    },

    // ============================================================
    // AGENT STATUS CHECKS
    // ============================================================
    async checkAllAgents() {
      await Promise.all(this.agents.map(a => this.pingAgent(a)))
    },

    async pingAgent(agent) {
      agent.pinging = true
      const now = new Date().toLocaleTimeString()

      try {
        switch (agent.id) {
          case 'billy':
            await this.checkBilly(agent)
            break
          case 'thesis':
            await this.checkThesis(agent)
            break
          case 'reports':
            await this.checkReports(agent)
            break
          case 'vector':
            await this.checkVector(agent)
            break
          case 'ml-api':
            await this.checkMLApi(agent)
            break
        }
      } catch (err) {
        agent.status = 'Error'
        agent.statusColor = 'danger'
        console.error(`Agent ${agent.id} check failed:`, err)
      }

      agent.lastChecked = now
      agent.pinging = false
    },

    async checkBilly(agent) {
      // Check if Billy chat data exists in Firebase
      try {
        const snapshot = await get(ref(database, 'Billy Chats'))
        const chatCount = snapshot.exists() ? Object.keys(snapshot.val()).length : 0

        // Test local KB
        const testResult = this.getLocalKBResponse('hello')
        const kbWorking = testResult !== null

        agent.status = kbWorking ? 'Online' : 'Degraded'
        agent.statusColor = kbWorking ? 'success' : 'warning'
        agent.metrics = [
          { label: 'Chat Users', value: chatCount },
          { label: 'Local KB', value: kbWorking ? 'Active' : 'Down' },
          { label: 'KB Topics', value: Object.keys(LOCAL_KNOWLEDGE_BASE).length + this.knowledgeBase.length }
        ]
      } catch (err) {
        agent.status = 'Error'
        agent.statusColor = 'danger'
        agent.metrics[0].value = 'Error'
      }
    },

    async checkThesis(agent) {
      try {
        const snapshot = await get(ref(database, 'thesis_validations'))
        const count = snapshot.exists() ? Object.keys(snapshot.val()).length : 0
        agent.status = 'Online'
        agent.statusColor = 'success'
        agent.metrics = [
          { label: 'Validations', value: count },
          { label: 'Status', value: 'Ready' }
        ]
      } catch {
        agent.status = 'Offline'
        agent.statusColor = 'danger'
        agent.metrics = [
          { label: 'Validations', value: 'N/A' },
          { label: 'Status', value: 'Error' }
        ]
      }
    },

    async checkReports(agent) {
      try {
        const snapshot = await get(ref(database, 'session_reports'))
        const count = snapshot.exists() ? Object.keys(snapshot.val()).length : 0
        agent.status = 'Online'
        agent.statusColor = 'success'
        agent.metrics = [
          { label: 'Reports', value: count },
          { label: 'Status', value: 'Ready' }
        ]
      } catch {
        agent.status = 'Offline'
        agent.statusColor = 'danger'
        agent.metrics = [
          { label: 'Reports', value: 'N/A' },
          { label: 'Status', value: 'Error' }
        ]
      }
    },

    async checkVector(agent) {
      try {
        const snapshot = await get(ref(database, 'thesis_sections'))
        const sectionCount = snapshot.exists() ? Object.keys(snapshot.val()).length : 0
        // Estimate chunks (roughly 3-5 chunks per section)
        const estimatedChunks = sectionCount * 4
        agent.status = sectionCount > 0 ? 'Online' : 'No Data'
        agent.statusColor = sectionCount > 0 ? 'success' : 'warning'
        agent.metrics = [
          { label: 'Indexed Chunks', value: estimatedChunks > 0 ? `~${estimatedChunks}` : '0' },
          { label: 'Status', value: sectionCount > 0 ? 'Indexed' : 'Empty' }
        ]
      } catch {
        agent.status = 'Error'
        agent.statusColor = 'danger'
        agent.metrics = [
          { label: 'Indexed Chunks', value: 'N/A' },
          { label: 'Status', value: 'Error' }
        ]
      }
    },

    async checkMLApi(agent) {
      const start = performance.now()
      try {
        const controller = new AbortController()
        const timeout = setTimeout(() => controller.abort(), 10000)
        const res = await fetch('https://billsense-api-340624938055.asia-southeast2.run.app/api/health', {
          signal: controller.signal
        })
        clearTimeout(timeout)
        const latency = Math.round(performance.now() - start)

        if (res.ok) {
          agent.status = 'Online'
          agent.statusColor = 'success'
        } else {
          agent.status = `HTTP ${res.status}`
          agent.statusColor = 'warning'
        }
        agent.metrics = [
          { label: 'Endpoint', value: 'Cloud Run' },
          { label: 'Latency', value: `${latency}ms` }
        ]
      } catch (err) {
        const latency = Math.round(performance.now() - start)
        agent.status = err.name === 'AbortError' ? 'Timeout' : 'Offline'
        agent.statusColor = 'danger'
        agent.metrics = [
          { label: 'Endpoint', value: 'Cloud Run' },
          { label: 'Latency', value: latency > 9000 ? 'Timeout' : `${latency}ms` }
        ]
      }
    },

    // ============================================================
    // BILLY TEST CONSOLE
    // ============================================================
    async sendMessage(text) {
      if (!text || !text.trim()) return
      const userMsg = text.trim()
      this.chatInput = ''

      this.chatHistory.push({ role: 'user', content: userMsg })
      this.$nextTick(() => this.scrollChat())

      this.billyTyping = true
      const start = performance.now()

      // Step 1: Try local knowledge base
      const localResponse = this.getLocalKBResponse(userMsg)
      if (localResponse) {
        await this.simulateDelay(200)
        this.billyTyping = false
        this.chatHistory.push({
          role: 'assistant',
          content: localResponse,
          path: 'Local-KB',
          responseTime: Math.round(performance.now() - start)
        })
        this.recordAnalytics('localKB')
        this.$nextTick(() => this.scrollChat())
        return
      }

      // Step 2: Try Firebase custom KB
      const fbResponse = this.getFirebaseKBResponse(userMsg)
      if (fbResponse) {
        await this.simulateDelay(300)
        this.billyTyping = false
        this.chatHistory.push({
          role: 'assistant',
          content: fbResponse,
          path: 'Firebase-KB',
          responseTime: Math.round(performance.now() - start)
        })
        this.recordAnalytics('firebaseKB')
        this.$nextTick(() => this.scrollChat())
        return
      }

      // Step 3: Try API
      try {
        const apiResponse = await this.callBillyAPI(userMsg)
        this.billyTyping = false
        this.chatHistory.push({
          role: 'assistant',
          content: apiResponse,
          path: 'API',
          responseTime: Math.round(performance.now() - start)
        })
        this.recordAnalytics('api')
      } catch {
        // Step 4: Fallback
        this.billyTyping = false
        this.chatHistory.push({
          role: 'assistant',
          content: this.getFallbackResponse(userMsg),
          path: 'Fallback',
          responseTime: Math.round(performance.now() - start)
        })
        this.recordAnalytics('fallback')
      }

      this.$nextTick(() => this.scrollChat())
    },

    getLocalKBResponse(message) {
      const lower = message.toLowerCase().trim()
      for (const entry of Object.values(LOCAL_KNOWLEDGE_BASE)) {
        for (const pattern of entry.patterns) {
          if (lower.includes(pattern)) return entry.response
        }
      }
      return null
    },

    getFirebaseKBResponse(message) {
      const lower = message.toLowerCase().trim()
      for (const entry of this.knowledgeBase) {
        for (const trigger of entry.triggers) {
          if (lower.includes(trigger.toLowerCase())) return entry.response
        }
      }
      return null
    },

    async callBillyAPI(message) {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 15000)

      const res = await fetch('https://billsense-api-340624938055.asia-southeast2.run.app/api/billy-chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, agent: 'billy' }),
        signal: controller.signal
      })
      clearTimeout(timeout)

      if (!res.ok) throw new Error(`API returned ${res.status}`)
      const data = await res.json()
      return data.response || 'I received your message but couldn\'t generate a response.'
    },

    getFallbackResponse(message) {
      const lower = message.toLowerCase()
      if (lower.includes('bill') || lower.includes('money') || lower.includes('peso') || lower.includes('currency')) {
        return '\uD83D\uDE0A That\'s a great question about currency!\n\nI can help you with:\n\u2022 Checking bill authenticity (try asking "How to check if my bill is real?")\n\u2022 Security features (ask about "watermark", "UV", or "serial number")\n\u2022 Using the Scan Bill feature\n\n\uD83D\uDCCC Could you be more specific so I can give you the best answer?'
      }
      return '\uD83E\uDDD0 I appreciate your question!\n\nI specialize in Philippine currency verification and the BillSense app. Here are some things I can help with:\n\n\u2022 \uD83D\uDD0D "How to check if a bill is genuine?"\n\u2022 \uD83D\uDCF1 "How to use Scan Bill?"\n\u2022 \uD83D\uDCA1 "What are the security features of \u20B11000?"\n\u2022 \uD83D\uDCCA "How to compare bills?"\n\nTry asking one of these! \uD83D\uDE0A'
    },

    async recordAnalytics(path) {
      try {
        const analyticsRef = ref(database, `billy_analytics/${path}`)
        const snapshot = await get(analyticsRef)
        const current = snapshot.exists() ? snapshot.val() : 0
        await set(analyticsRef, current + 1)
      } catch (err) {
        console.error('Failed to record analytics:', err)
      }
    },

    simulateDelay(ms) {
      return new Promise(resolve => setTimeout(resolve, ms))
    },

    scrollChat() {
      const el = this.$refs.chatMessages
      if (el) el.scrollTop = el.scrollHeight
    },

    clearChat() {
      this.chatHistory = []
    },

    formatMarkdown(text) {
      if (!text) return ''
      return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\n/g, '<br>')
    },

    // ============================================================
    // TRAINING PANEL
    // ============================================================
    openEntryModal(entry) {
      if (entry) {
        this.editingEntry = entry
        this.entryForm = {
          topic: entry.topic,
          triggersRaw: entry.triggers.join(', '),
          response: entry.response
        }
      } else {
        this.editingEntry = null
        this.entryForm = { topic: '', triggersRaw: '', response: '' }
      }
      this.showEntryModal = true
    },

    async saveEntry() {
      const payload = {
        topic: this.entryForm.topic.trim(),
        triggers: this.entryForm.triggersRaw.trim(),
        response: this.entryForm.response.trim(),
        updatedAt: Date.now()
      }

      try {
        if (this.editingEntry) {
          await set(ref(database, `billy_knowledge_base/${this.editingEntry.id}`), payload)
        } else {
          await push(ref(database, 'billy_knowledge_base'), payload)
        }
        this.showEntryModal = false
      } catch (err) {
        console.error('Failed to save entry:', err)
        alert('Failed to save entry: ' + err.message)
      }
    },

    async deleteEntry(entry) {
      if (!confirm(`Delete "${entry.topic}"? This cannot be undone.`)) return
      try {
        await remove(ref(database, `billy_knowledge_base/${entry.id}`))
      } catch (err) {
        console.error('Failed to delete entry:', err)
        alert('Failed to delete entry: ' + err.message)
      }
    },

    exportKB() {
      const data = this.knowledgeBase.map(e => ({
        topic: e.topic,
        triggers: e.triggers.join(', '),
        response: e.response
      }))
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `billy-kb-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
    },

    async importKB(event) {
      const file = event.target.files?.[0]
      if (!file) return

      try {
        const text = await file.text()
        const entries = JSON.parse(text)

        if (!Array.isArray(entries)) throw new Error('Expected JSON array')

        for (const entry of entries) {
          if (!entry.topic || !entry.triggers || !entry.response) continue
          await push(ref(database, 'billy_knowledge_base'), {
            topic: entry.topic,
            triggers: typeof entry.triggers === 'string' ? entry.triggers : entry.triggers.join(', '),
            response: entry.response,
            updatedAt: Date.now()
          })
        }

        alert(`Imported ${entries.length} entries successfully`)
      } catch (err) {
        console.error('Import failed:', err)
        alert('Import failed: ' + err.message)
      }

      event.target.value = ''
    },

    // ============================================================
    // SCAN TESTING
    // ============================================================
    handleScanDrop(e) {
      this.scanDragOver = false
      const file = e.dataTransfer?.files?.[0]
      if (file && file.type.startsWith('image/')) {
        this._scanFile = file
        this.scanUploadPreview = URL.createObjectURL(file)
        this.scanUploadResult = null
        this.scanUploadError = null
      }
    },

    handleScanFileSelect(e) {
      const file = e.target.files?.[0]
      if (file) {
        this._scanFile = file
        this.scanUploadPreview = URL.createObjectURL(file)
        this.scanUploadResult = null
        this.scanUploadError = null
      }
    },

    clearScanUpload() {
      this._scanFile = null
      this.scanUploadPreview = null
      this.scanUploadResult = null
      this.scanUploadError = null
    },

    async submitScanUpload() {
      if (!this._scanFile) return
      this.scanUploading = true
      this.scanUploadError = null
      this.scanUploadResult = null
      const start = performance.now()

      try {
        const formData = new FormData()
        formData.append('file', this._scanFile)

        const res = await fetch('https://billsense-api-340624938055.asia-southeast2.run.app/api/standard-scan', {
          method: 'POST',
          body: formData
        })

        this.scanResponseTime = Math.round(performance.now() - start)
        this.scanStatusCode = res.status
        const data = await res.json()
        this.scanRawResponse = data

        if (!res.ok) {
          this.scanUploadError = `API returned ${res.status}: ${data.detail || data.message || 'Unknown error'}`
        } else {
          this.scanUploadResult = data
          this.addScanHistory('Upload', data, this.scanResponseTime)
        }
      } catch (err) {
        this.scanResponseTime = Math.round(performance.now() - start)
        this.scanUploadError = 'Request failed: ' + err.message
        this.scanStatusCode = 0
        this.scanRawResponse = { error: err.message }
      }

      this.scanUploading = false
    },

    // --- Camera Scanner ---
    async startCamera() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } } })
        this.cameraStream = stream
        this.cameraActive = true
        this.cameraCaptured = false
        this.cameraScanResult = null
        this.cameraScanError = null
        this.$nextTick(() => {
          if (this.$refs.cameraVideo) {
            this.$refs.cameraVideo.srcObject = stream
          }
        })
      } catch (err) {
        this.cameraScanError = 'Camera access denied: ' + err.message
      }
    },

    stopCamera() {
      if (this.cameraStream) {
        this.cameraStream.getTracks().forEach(t => t.stop())
        this.cameraStream = null
      }
      this.cameraActive = false
      this.cameraCaptured = false
    },

    captureCamera() {
      const video = this.$refs.cameraVideo
      const canvas = this.$refs.cameraCanvas
      if (!video || !canvas) return

      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      const ctx = canvas.getContext('2d')
      ctx.drawImage(video, 0, 0)
      this.cameraCaptured = true
    },

    retakeCamera() {
      this.cameraCaptured = false
      this.cameraScanResult = null
      this.cameraScanError = null
    },

    async submitCameraScan() {
      const canvas = this.$refs.cameraCanvas
      if (!canvas) return
      this.cameraScanning = true
      this.cameraScanError = null
      this.cameraScanResult = null
      const start = performance.now()

      try {
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.8))
        const formData = new FormData()
        formData.append('file', blob, 'capture.jpg')

        const res = await fetch('https://billsense-api-340624938055.asia-southeast2.run.app/api/standard-scan', {
          method: 'POST',
          body: formData
        })

        this.scanResponseTime = Math.round(performance.now() - start)
        this.scanStatusCode = res.status
        const data = await res.json()
        this.scanRawResponse = data

        if (!res.ok) {
          this.cameraScanError = `API returned ${res.status}: ${data.detail || data.message || 'Unknown error'}`
        } else {
          this.cameraScanResult = data
          this.addScanHistory('Camera', data, this.scanResponseTime)
        }
      } catch (err) {
        this.scanResponseTime = Math.round(performance.now() - start)
        this.cameraScanError = 'Request failed: ' + err.message
        this.scanStatusCode = 0
        this.scanRawResponse = { error: err.message }
      }

      this.cameraScanning = false
    },

    // --- WebSocket Live Scanner ---
    wsConnect() {
      if (this.wsSocket) return
      this.wsConnecting = true
      this.wsResults = []

      try {
        const ws = new WebSocket('wss://billsense-api-340624938055.asia-southeast2.run.app/ws/standard-scan')

        ws.onopen = () => {
          this.wsConnected = true
          this.wsConnecting = false
          this.wsSocket = ws
        }

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)
            this.wsResults.unshift({
              status: data.status || 'UNKNOWN',
              denomination: data.denomination || '?',
              confidence: data.confidence,
              features: data.features || [],
              timestamp: new Date().toLocaleTimeString()
            })
            // Keep only last 50 results
            if (this.wsResults.length > 50) this.wsResults.pop()
            this.scanRawResponse = data
          } catch (e) {
            console.error('WS parse error:', e)
          }
        }

        ws.onclose = () => {
          this.wsConnected = false
          this.wsConnecting = false
          this.wsSocket = null
          this.wsStopStreaming()
        }

        ws.onerror = (err) => {
          console.error('WebSocket error:', err)
          this.wsConnected = false
          this.wsConnecting = false
          this.wsSocket = null
        }
      } catch (err) {
        this.wsConnecting = false
        console.error('WS connect failed:', err)
      }
    },

    wsDisconnect() {
      this.wsStopStreaming()
      if (this.wsSocket) {
        this.wsSocket.close()
        this.wsSocket = null
      }
      this.wsConnected = false
    },

    async wsStartStreaming() {
      if (!this.wsSocket || this.wsStreaming) return

      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } } })
        this.wsCameraStream = stream
        this.wsCameraActive = true
        this.$nextTick(() => {
          if (this.$refs.wsVideo) {
            this.$refs.wsVideo.srcObject = stream
          }
        })

        this.wsStreaming = true
        // Send frames every 500ms
        this.wsFrameInterval = setInterval(() => {
          this.wsSendFrame()
        }, 500)
      } catch (err) {
        console.error('Camera access for WS failed:', err)
      }
    },

    wsSendFrame() {
      if (!this.wsSocket || this.wsSocket.readyState !== WebSocket.OPEN) return
      const video = this.$refs.wsVideo
      const canvas = this.$refs.wsCanvas
      if (!video || !canvas) return

      canvas.width = video.videoWidth || 640
      canvas.height = video.videoHeight || 480
      const ctx = canvas.getContext('2d')
      ctx.drawImage(video, 0, 0)

      const dataUrl = canvas.toDataURL('image/jpeg', 0.8)
      const base64 = dataUrl.split(',')[1]
      if (base64) {
        this.wsSocket.send(JSON.stringify({ image: base64 }))
      }
    },

    wsStopStreaming() {
      if (this.wsFrameInterval) {
        clearInterval(this.wsFrameInterval)
        this.wsFrameInterval = null
      }
      if (this.wsCameraStream) {
        this.wsCameraStream.getTracks().forEach(t => t.stop())
        this.wsCameraStream = null
      }
      this.wsCameraActive = false
      this.wsStreaming = false
    },

    // --- Scan History ---
    addScanHistory(scanType, data, responseTime) {
      const entry = {
        timestamp: new Date().toLocaleString(),
        scanType,
        result: data.status || 'N/A',
        denomination: data.denomination || 'N/A',
        confidence: data.confidence ? (data.confidence * 100).toFixed(1) + '%' : 'N/A',
        responseTime: responseTime + 'ms'
      }
      this.scanHistory.unshift(entry)
      if (this.scanHistory.length > 100) this.scanHistory.pop()
      localStorage.setItem('billsense_scan_history', JSON.stringify(this.scanHistory))
    },

    clearScanHistory() {
      if (!confirm('Clear all scan history?')) return
      this.scanHistory = []
      localStorage.removeItem('billsense_scan_history')
    },

    // ============================================================
    // UTILITIES
    // ============================================================
    async refreshAll() {
      this.refreshing = true
      await this.checkAllAgents()
      this.refreshing = false
    },

    truncate(text, max) {
      if (!text) return ''
      return text.length > max ? text.substring(0, max) + '...' : text
    },

    formatTime(timestamp) {
      if (!timestamp) return ''
      const d = new Date(timestamp)
      if (isNaN(d.getTime())) return ''
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return 'Just now'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`
      return d.toLocaleDateString()
    }
  }
}
</script>

<style scoped>
/* ============================================================
   ML Models Page — Dark Theme Styles
   ============================================================ */

.ml-models {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left h1 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--text);
  margin: 0;
}

.header-icon {
  font-size: 28px;
  color: var(--accent);
}

.subtitle {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-top: 4px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* --- Buttons --- */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--accent);
  color: #fff;
}

.btn-primary:hover {
  filter: brightness(1.15);
}

.btn-ghost {
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border);
}

.btn-ghost:hover {
  background: var(--border);
  color: var(--text);
}

.btn-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 0.78rem;
  cursor: pointer;
  background: transparent;
  color: var(--text-muted);
  border: 1px solid var(--border);
  transition: all 0.2s;
}

.btn-sm:hover {
  background: var(--border);
  color: var(--text);
}

.btn-sm .material-icons {
  font-size: 16px;
}

.icon-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: var(--border);
  color: var(--text);
}

.icon-btn.danger:hover {
  background: rgba(239, 68, 68, 0.15);
  color: var(--danger);
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.ml-auto {
  margin-left: auto;
}

/* --- Tab Bar --- */
.tab-bar {
  display: flex;
  gap: 4px;
  margin-bottom: 24px;
  padding: 4px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid var(--border);
  overflow-x: auto;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  background: transparent;
  border: none;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.tab-btn:hover {
  background: var(--border);
  color: var(--text);
}

.tab-btn.active {
  background: var(--accent);
  color: #fff;
}

.tab-btn .material-icons {
  font-size: 18px;
}

/* ============================================================
   TAB 1: Agents Grid
   ============================================================ */
.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.agent-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
  transition: all 0.2s;
}

.agent-card:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.agent-card.border-success { border-left: 3px solid var(--success); }
.agent-card.border-warning { border-left: 3px solid var(--warning); }
.agent-card.border-danger { border-left: 3px solid var(--danger); }

.agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.agent-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.agent-icon .material-icons {
  font-size: 22px;
}

.agent-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.agent-status-badge.success { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.agent-status-badge.warning { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.agent-status-badge.danger { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.agent-name {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 4px;
}

.agent-desc {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 0 0 16px;
}

.agent-metrics {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.metric-item {
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text);
}

.metric-label {
  font-size: 0.72rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.agent-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.last-checked {
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* ============================================================
   TAB 2: Chat Console
   ============================================================ */
.console-layout {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 16px;
  height: calc(100vh - 260px);
  min-height: 500px;
}

.chat-container {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  color: var(--text);
}

.chat-header .material-icons {
  color: var(--accent);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  text-align: center;
  gap: 12px;
}

.chat-empty .material-icons {
  font-size: 48px;
  opacity: 0.3;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  justify-content: center;
}

.quick-prompt-btn {
  padding: 6px 14px;
  border-radius: 20px;
  background: var(--border);
  color: var(--text);
  border: none;
  cursor: pointer;
  font-size: 0.82rem;
  transition: all 0.2s;
}

.quick-prompt-btn:hover {
  background: var(--accent);
  color: #fff;
}

.chat-msg {
  display: flex;
}

.chat-msg.user {
  justify-content: flex-end;
}

.chat-msg.assistant {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 0.88rem;
  line-height: 1.5;
  word-break: break-word;
}

.chat-msg.user .msg-bubble {
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.chat-msg.assistant .msg-bubble {
  background: var(--bg);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 0.72rem;
}

.response-path {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.response-path.Local-KB { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.response-path.Firebase-KB { background: rgba(79, 134, 247, 0.15); color: #4F86F7; }
.response-path.API { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.response-path.Fallback { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.response-time {
  color: var(--text-muted);
}

/* Typing indicator */
.msg-bubble.typing {
  display: flex;
  gap: 4px;
  padding: 14px 18px;
}

.msg-bubble.typing .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typingBounce 1.4s ease-in-out infinite;
}

.msg-bubble.typing .dot:nth-child(2) { animation-delay: 0.2s; }
.msg-bubble.typing .dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

.chat-input-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}

.chat-input-bar input {
  flex: 1;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--input-bg);
  color: var(--text);
  font-size: 0.88rem;
  outline: none;
  transition: border-color 0.2s;
}

.chat-input-bar input:focus {
  border-color: var(--accent);
}

.send-btn {
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: all 0.2s;
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.15);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* Console sidebar */
.console-sidebar {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  overflow-y: auto;
}

.console-sidebar h4 {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.route-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.route-item {
  font-size: 0.78rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.route-item .response-path {
  margin-right: 6px;
}

.session-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ss-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.ss-row span:last-child {
  font-weight: 700;
  color: var(--text);
}

/* ============================================================
   TAB 3: Training Panel
   ============================================================ */
.training-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.training-header h3 {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text);
  margin: 0;
}

.training-actions {
  display: flex;
  gap: 8px;
}

.import-btn {
  cursor: pointer;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-muted);
  text-align: center;
}

.loading-state .material-icons,
.empty-state .material-icons {
  font-size: 48px;
  opacity: 0.3;
  margin-bottom: 12px;
}

.kb-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}

.kb-table thead {
  background: var(--bg);
}

.kb-table th {
  padding: 12px 16px;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

.kb-table td {
  padding: 12px 16px;
  font-size: 0.85rem;
  color: var(--text);
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

.kb-table tr:last-child td {
  border-bottom: none;
}

.topic-cell {
  font-weight: 600;
  white-space: nowrap;
}

.triggers-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.trigger-tag {
  display: inline-block;
  padding: 2px 8px;
  background: var(--border);
  border-radius: 4px;
  font-size: 0.75rem;
  color: var(--text);
}

.trigger-more {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.preview-cell {
  color: var(--text-muted);
  max-width: 300px;
}

.actions-cell {
  display: flex;
  gap: 4px;
  white-space: nowrap;
}

/* --- Modal --- */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--text);
}

.modal-body {
  padding: 20px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--input-bg);
  color: var(--text);
  font-size: 0.88rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  border-color: var(--accent);
}

/* ============================================================
   TAB 4: Analytics
   ============================================================ */
.analytics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.analytics-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 20px;
}

.analytics-card.wide {
  grid-column: 1 / -1;
}

.analytics-card h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin: 0 0 16px;
}

.analytics-card h3 .material-icons {
  font-size: 20px;
  color: var(--accent);
}

.empty-mini {
  text-align: center;
  color: var(--text-muted);
  font-size: 0.85rem;
  padding: 30px 0;
}

/* Pie chart */
.pie-chart-container {
  display: flex;
  align-items: center;
  gap: 40px;
  flex-wrap: wrap;
}

.pie-chart {
  width: 200px;
  height: 200px;
  flex-shrink: 0;
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--text);
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  flex-shrink: 0;
}

.legend-label {
  min-width: 80px;
}

.legend-value {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.legend-total {
  margin-top: 4px;
  font-size: 0.82rem;
  color: var(--text-muted);
  font-weight: 600;
}

/* Topics list */
.topics-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.topic-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topic-rank {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  width: 28px;
}

.topic-name {
  font-size: 0.85rem;
  color: var(--text);
  min-width: 80px;
  flex-shrink: 0;
}

.topic-count {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--accent);
  width: 30px;
  text-align: right;
}

.topic-bar {
  flex: 1;
  height: 6px;
  background: var(--border);
  border-radius: 3px;
  overflow: hidden;
}

.topic-bar-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 3px;
  transition: width 0.5s ease;
}

/* Unanswered queries */
.unanswered-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.unanswered-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg);
  border-radius: 8px;
  font-size: 0.82rem;
}

.unanswered-row .material-icons {
  font-size: 16px;
  color: var(--danger);
  flex-shrink: 0;
}

.unanswered-text {
  flex: 1;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unanswered-time {
  font-size: 0.72rem;
  color: var(--text-muted);
  white-space: nowrap;
}

/* Conversations */
.conversations-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.conv-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--bg);
  border-radius: 8px;
  font-size: 0.82rem;
}

.conv-user {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text);
  font-weight: 600;
  min-width: 100px;
  flex-shrink: 0;
}

.conv-user .material-icons {
  font-size: 16px;
  color: var(--text-muted);
}

.conv-message {
  flex: 1;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 0.72rem;
  color: var(--text-muted);
  white-space: nowrap;
}

/* ============================================================
   Scan Testing Tab
   ============================================================ */
.scan-sub-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--bg-deeper, #0d1117);
  border-radius: 10px;
  margin-bottom: 20px;
  overflow-x: auto;
}

.scan-sub-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  background: transparent;
  color: var(--text-muted);
  border: none;
  white-space: nowrap;
  transition: all 0.2s;
}

.scan-sub-tab:hover {
  background: var(--border);
  color: var(--text);
}

.scan-sub-tab.active {
  background: var(--accent);
  color: #fff;
}

.scan-sub-tab .material-icons {
  font-size: 18px;
}

.scan-panel {
  animation: fadeIn 0.2s;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Upload area */
.scan-upload-area {
  border: 2px dashed var(--border);
  border-radius: 12px;
  padding: 40px;
  text-align: center;
  transition: all 0.2s;
  background: var(--bg-deeper, #0d1117);
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scan-upload-area.drag-over {
  border-color: var(--accent);
  background: rgba(79, 134, 247, 0.05);
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
}

.upload-icon {
  font-size: 48px;
  opacity: 0.5;
}

.upload-btn {
  margin-top: 8px;
}

.upload-preview-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.scan-preview-img {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  object-fit: contain;
}

.clear-preview-btn {
  position: absolute;
  top: 8px;
  right: 8px;
}

.scan-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.scan-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  color: #EF4444;
  margin-top: 16px;
  font-size: 0.85rem;
}

/* Scan result card */
.scan-result-card {
  margin-top: 20px;
  padding: 20px;
  background: var(--bg-deeper, #0d1117);
  border-radius: 12px;
  border: 1px solid var(--border);
}

.scan-result-card h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px 0;
  font-size: 1rem;
  color: var(--text);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.result-value {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text);
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 700;
  width: fit-content;
}

.status-badge.genuine {
  background: rgba(34, 197, 94, 0.15);
  color: #22C55E;
}

.status-badge.counterfeit {
  background: rgba(239, 68, 68, 0.15);
  color: #EF4444;
}

.features-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.features-section h5 {
  margin: 0 0 10px 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.features-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feature-tag {
  padding: 4px 10px;
  background: rgba(79, 134, 247, 0.12);
  color: #4F86F7;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 600;
}

.annotated-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.annotated-section h5 {
  margin: 0 0 10px 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.annotated-img {
  max-width: 100%;
  border-radius: 8px;
}

/* Camera */
.camera-container {
  border-radius: 12px;
  overflow: hidden;
  background: #000;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.camera-video,
.camera-canvas {
  width: 100%;
  max-height: 480px;
  border-radius: 12px;
  object-fit: contain;
}

.camera-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  padding: 40px;
}

.camera-placeholder .material-icons {
  font-size: 48px;
  opacity: 0.4;
}

/* WebSocket */
.ws-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-deeper, #0d1117);
  border-radius: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.ws-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 0.85rem;
}

.ws-indicator .status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.ws-indicator.connected .status-dot { background: #22C55E; box-shadow: 0 0 6px #22C55E; }
.ws-indicator.connecting .status-dot { background: #F59E0B; animation: pulse 1s infinite; }
.ws-indicator.disconnected .status-dot { background: #6B7280; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.ws-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ws-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.ws-camera-section {
  border-radius: 12px;
  overflow: hidden;
  background: #000;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ws-results-section {
  background: var(--bg-deeper, #0d1117);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--border);
  max-height: 480px;
  overflow-y: auto;
}

.ws-results-section h4 {
  margin: 0 0 12px 0;
  font-size: 0.9rem;
  color: var(--text);
}

.result-count {
  color: var(--text-muted);
  font-weight: 400;
  font-size: 0.8rem;
}

.ws-results-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ws-result-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-card, #161b22);
  border-radius: 8px;
  font-size: 0.82rem;
}

.ws-result-status {
  font-weight: 700;
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 4px;
}

.ws-result-status.genuine { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.ws-result-status.counterfeit { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.ws-result-denom { color: var(--text); font-weight: 600; }
.ws-result-conf { color: var(--text-muted); }
.ws-result-time { margin-left: auto; color: var(--text-muted); font-size: 0.72rem; }

/* Response viewer */
.response-viewer {
  background: var(--bg-deeper, #0d1117);
  border-radius: 12px;
  border: 1px solid var(--border);
  overflow: hidden;
}

.response-meta {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 0.85rem;
  color: var(--text-muted);
  flex-wrap: wrap;
}

.meta-item strong {
  color: var(--text);
}

.json-viewer {
  padding: 16px;
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #e6edf3;
  overflow-x: auto;
  max-height: 600px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Scan history header */
.scan-history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.scan-history-header h3 {
  margin: 0;
  font-size: 1rem;
  color: var(--text);
}

/* ============================================================
   Responsive
   ============================================================ */
@media (max-width: 900px) {
  .console-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .console-sidebar {
    order: -1;
  }

  .analytics-grid {
    grid-template-columns: 1fr;
  }

  .agents-grid {
    grid-template-columns: 1fr;
  }

  .pie-chart-container {
    flex-direction: column;
    align-items: flex-start;
  }

  .ws-layout {
    grid-template-columns: 1fr;
  }

  .scan-sub-tabs {
    flex-wrap: wrap;
  }
}

/* ============================================================
   ML Configuration Tab Styles
   ============================================================ */

.ml-config-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-card {
  background: var(--bg-card, #1a1a2e);
  border: 1px solid var(--border, #2a2a4a);
  border-radius: 12px;
  padding: 24px;
}

.config-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.config-card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: var(--text, #e0e0e0);
}

.config-card-header .material-icons {
  color: var(--accent, #4F86F7);
  font-size: 22px;
}

.config-card-desc {
  color: var(--text-muted, #8888aa);
  font-size: 0.875rem;
  margin: 0 0 16px 0;
}

/* Mode Toggle */
.mode-toggle {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.mode-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px;
  background: var(--bg-secondary, #12122a);
  border: 2px solid var(--border, #2a2a4a);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-muted, #8888aa);
}

.mode-btn:hover {
  border-color: var(--accent, #4F86F7);
  background: rgba(79, 134, 247, 0.05);
}

.mode-btn.active {
  border-color: var(--accent, #4F86F7);
  background: rgba(79, 134, 247, 0.1);
  color: var(--text, #e0e0e0);
}

.mode-btn .material-icons {
  font-size: 32px;
}

.mode-btn.active .material-icons {
  color: var(--accent, #4F86F7);
}

.mode-label {
  font-size: 1rem;
  font-weight: 600;
}

.mode-sub {
  font-size: 0.75rem;
  opacity: 0.7;
}

.mode-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: var(--bg-secondary, #12122a);
  border-radius: 8px;
  font-size: 0.875rem;
  color: var(--text-muted, #8888aa);
}

.mode-updated {
  margin-left: auto;
  font-size: 0.75rem;
  opacity: 0.6;
}

/* Comparison Table */
.comparison-table table {
  width: 100%;
  border-collapse: collapse;
}

.comparison-table th,
.comparison-table td {
  padding: 10px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border, #2a2a4a);
  font-size: 0.875rem;
  color: var(--text-muted, #8888aa);
}

.comparison-table th {
  color: var(--text, #e0e0e0);
  font-weight: 600;
  background: var(--bg-secondary, #12122a);
}

.comparison-table th .material-icons {
  font-size: 16px;
  vertical-align: middle;
  margin-right: 4px;
}

.comparison-table td.highlight {
  color: #22C55E;
  font-weight: 500;
}

/* Model Registry Table */
.total-size-badge {
  margin-left: auto;
  background: rgba(79, 134, 247, 0.15);
  color: #4F86F7;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
}

.registry-table {
  width: 100%;
  border-collapse: collapse;
}

.registry-table th,
.registry-table td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border, #2a2a4a);
  font-size: 0.875rem;
  color: var(--text-muted, #8888aa);
}

.registry-table th {
  color: var(--text, #e0e0e0);
  font-weight: 600;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.registry-table tr.recommended-row {
  background: rgba(34, 197, 94, 0.04);
}

.model-name {
  color: var(--text, #e0e0e0);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-desc {
  font-size: 0.75rem;
  opacity: 0.6;
  margin-top: 2px;
}

.recommended-badge {
  background: rgba(34, 197, 94, 0.15);
  color: #22C55E;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
}

.mono {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
}

.format-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
}

.format-badge.float32 { background: rgba(79, 134, 247, 0.15); color: #4F86F7; }
.format-badge.float16 { background: rgba(168, 85, 247, 0.15); color: #A855F7; }
.format-badge.int8 { background: rgba(34, 197, 94, 0.15); color: #22C55E; }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-pill.available {
  background: rgba(34, 197, 94, 0.12);
  color: #22C55E;
}

.status-pill .material-icons {
  font-size: 14px;
}

/* Toggle Switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 22px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background: var(--border, #2a2a4a);
  border-radius: 22px;
  transition: 0.3s;
}

.toggle-slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 3px;
  bottom: 3px;
  background: white;
  border-radius: 50%;
  transition: 0.3s;
}

.toggle-switch input:checked + .toggle-slider {
  background: #4F86F7;
}

.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(18px);
}

/* Config Actions */
.config-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.save-hint {
  font-size: 0.8rem;
  color: var(--text-muted, #8888aa);
}

.save-hint code {
  background: var(--bg-secondary, #12122a);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.75rem;
}
</style>
