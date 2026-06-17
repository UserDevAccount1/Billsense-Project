<template>
  <div class="users-page">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <h1><span class="material-icons header-icon">people</span> Users</h1>
        <p class="subtitle">Manage registered BillSense users</p>
      </div>
      <div class="header-actions">
        <div class="view-toggle">
          <button :class="['toggle-btn', { active: viewMode === 'list' }]" @click="viewMode = 'list'" title="List View">
            <span class="material-icons">view_list</span>
          </button>
          <button :class="['toggle-btn', { active: viewMode === 'grid' }]" @click="viewMode = 'grid'" title="Grid View">
            <span class="material-icons">grid_view</span>
          </button>
        </div>
        <button class="btn btn-ghost" @click="refreshData" :disabled="loading">
          <span class="material-icons" :class="{ spin: loading }">sync</span> Refresh
        </button>
      </div>
    </div>

    <!-- Stats Bar -->
    <div class="stats-bar">
      <div class="stat-card">
        <span class="material-icons stat-icon">group</span>
        <div class="stat-info">
          <span class="stat-value">{{ allUsers.length }}</span>
          <span class="stat-label">Total Users</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--success)">verified_user</span>
        <div class="stat-info">
          <span class="stat-value">{{ verifiedCount }}</span>
          <span class="stat-label">Verified</span>
        </div>
      </div>
      <div class="stat-card">
        <span class="material-icons stat-icon" style="color: var(--warning)">hourglass_empty</span>
        <div class="stat-info">
          <span class="stat-value">{{ pendingCount }}</span>
          <span class="stat-label">Pending</span>
        </div>
      </div>
    </div>

    <!-- Search Bar -->
    <div class="filter-bar">
      <div class="filter-group search-group">
        <div class="search-input-wrapper">
          <span class="material-icons search-icon">search</span>
          <input v-model="searchQuery" placeholder="Search by name or email..." class="filter-input" />
        </div>
      </div>
      <div class="filter-group">
        <select v-model="filterStatus" class="filter-select">
          <option value="all">All Status</option>
          <option value="verified">Verified</option>
          <option value="pending">Pending</option>
          <option value="blocked">Blocked</option>
        </select>
      </div>
    </div>

    <!-- List View -->
    <div v-if="viewMode === 'list'" class="reports-table-container">
      <table class="reports-table">
        <thead>
          <tr>
            <th>Avatar</th>
            <th>Name</th>
            <th>Email</th>
            <th>Phone</th>
            <th>Status</th>
            <th>ID Image</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="empty-state">
              <span class="material-icons spin">sync</span> Loading users...
            </td>
          </tr>
          <tr v-else-if="filteredUsers.length === 0">
            <td colspan="7" class="empty-state">
              <span class="material-icons">person_off</span>
              <p>No users found.</p>
            </td>
          </tr>
          <tr v-for="user in filteredUsers" :key="user._uid" @click="openDetail(user)" class="report-row">
            <td>
              <img v-if="user.image" :src="user.image" class="user-avatar" alt="Avatar" />
              <div v-else class="avatar-placeholder">
                <span class="material-icons">person</span>
              </div>
            </td>
            <td>
              <span class="user-name-cell">{{ user.name || user.fullName || 'Unknown' }}</span>
            </td>
            <td>
              <span class="user-email-cell">{{ user.email || '-' }}</span>
            </td>
            <td>{{ user.phone || '-' }}</td>
            <td>
              <span :class="['status-badge', getStatusClass(user.status)]">
                {{ user.status || 'pending' }}
              </span>
            </td>
            <td @click.stop>
              <img
                v-if="user.downloadIdUrl"
                :src="user.downloadIdUrl"
                class="id-thumbnail"
                @click="openImagePreview(user.downloadIdUrl)"
                alt="ID"
              />
              <span v-else class="no-image">-</span>
            </td>
            <td class="actions-cell" @click.stop>
              <button class="icon-btn" @click="openDetail(user)" title="View Details">
                <span class="material-icons">visibility</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Grid View -->
    <div v-else class="users-grid">
      <div v-if="loading" class="empty-state" style="grid-column: 1/-1;">
        <span class="material-icons spin">sync</span> Loading users...
      </div>
      <div v-else-if="filteredUsers.length === 0" class="empty-state" style="grid-column: 1/-1;">
        <span class="material-icons">person_off</span>
        <p>No users found.</p>
      </div>
      <div v-for="user in filteredUsers" :key="user._uid" class="user-card" @click="openDetail(user)">
        <div class="card-avatar">
          <img v-if="user.image" :src="user.image" class="card-avatar-img" alt="Avatar" />
          <div v-else class="card-avatar-placeholder">
            <span class="material-icons">person</span>
          </div>
          <span :class="['status-dot', getStatusClass(user.status)]"></span>
        </div>
        <div class="card-info">
          <h3 class="card-name">{{ user.name || user.fullName || 'Unknown' }}</h3>
          <p class="card-email">{{ user.email || '-' }}</p>
          <p class="card-phone" v-if="user.phone">{{ user.phone }}</p>
          <span :class="['status-badge', getStatusClass(user.status)]">
            {{ user.status || 'pending' }}
          </span>
        </div>
      </div>
    </div>

    <!-- Detail Modal -->
    <div v-if="selectedUser" class="modal-overlay" @click.self="selectedUser = null">
      <div class="preview-panel">
        <div class="preview-header">
          <h2>
            <span class="material-icons">person</span>
            User Details
          </h2>
          <div class="preview-actions">
            <button class="icon-btn" @click="selectedUser = null">
              <span class="material-icons">close</span>
            </button>
          </div>
        </div>
        <div class="preview-body">
          <div class="user-detail-layout">
            <!-- Profile Section -->
            <div class="user-profile-section">
              <div class="profile-avatar-large">
                <img v-if="selectedUser.image" :src="selectedUser.image" class="profile-img-large" alt="Profile" />
                <div v-else class="profile-placeholder-large">
                  <span class="material-icons">person</span>
                </div>
              </div>
              <h3 class="profile-name">{{ selectedUser.name || selectedUser.fullName || 'Unknown' }}</h3>
              <span :class="['status-badge', getStatusClass(selectedUser.status)]">
                {{ selectedUser.status || 'pending' }}
              </span>
            </div>

            <!-- Info Section -->
            <div class="user-info-section">
              <h4>Account Information</h4>
              <div class="detail-row">
                <span class="detail-label">
                  <span class="material-icons" style="font-size:16px">badge</span> User ID
                </span>
                <span class="detail-value"><code class="uid-code">{{ selectedUser._uid }}</code></span>
              </div>
              <div class="detail-row">
                <span class="detail-label">
                  <span class="material-icons" style="font-size:16px">email</span> Email
                </span>
                <span class="detail-value">{{ selectedUser.email || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">
                  <span class="material-icons" style="font-size:16px">phone</span> Phone
                </span>
                <span class="detail-value">{{ selectedUser.phone || '-' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">
                  <span class="material-icons" style="font-size:16px">verified</span> Status
                </span>
                <span class="detail-value">
                  <span :class="['status-badge', getStatusClass(selectedUser.status)]">
                    {{ selectedUser.status || 'pending' }}
                  </span>
                </span>
              </div>
              <div class="detail-row" v-if="selectedUser.id">
                <span class="detail-label">
                  <span class="material-icons" style="font-size:16px">fingerprint</span> Internal ID
                </span>
                <span class="detail-value"><code class="uid-code">{{ selectedUser.id }}</code></span>
              </div>

              <!-- ID Image -->
              <div v-if="selectedUser.downloadIdUrl" class="id-image-section">
                <h4>Identification Document</h4>
                <img
                  :src="selectedUser.downloadIdUrl"
                  class="id-image-large"
                  alt="ID Document"
                  @click="openImagePreview(selectedUser.downloadIdUrl)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Image Preview Modal -->
    <div v-if="previewImage" class="modal-overlay" @click.self="previewImage = null">
      <div class="image-preview-container">
        <button class="icon-btn close-preview" @click="previewImage = null">
          <span class="material-icons">close</span>
        </button>
        <img :src="previewImage" class="preview-full-image" alt="Full preview" />
      </div>
    </div>
  </div>
</template>

<script>
import { database, ref, get } from '../services/firebase.js'

export default {
  name: 'Users',
  data() {
    return {
      allUsers: [],
      loading: true,
      searchQuery: '',
      filterStatus: 'all',
      viewMode: 'list',
      selectedUser: null,
      previewImage: null
    }
  },
  computed: {
    verifiedCount() {
      return this.allUsers.filter(u => (u.status || '').toLowerCase() === 'verified').length
    },
    pendingCount() {
      return this.allUsers.filter(u => !u.status || u.status.toLowerCase() === 'pending').length
    },
    filteredUsers() {
      let users = [...this.allUsers]

      if (this.filterStatus !== 'all') {
        users = users.filter(u => {
          const s = (u.status || 'pending').toLowerCase()
          return s === this.filterStatus
        })
      }

      if (this.searchQuery.trim()) {
        const q = this.searchQuery.toLowerCase()
        users = users.filter(u =>
          (u.name || '').toLowerCase().includes(q) ||
          (u.fullName || '').toLowerCase().includes(q) ||
          (u.email || '').toLowerCase().includes(q)
        )
      }

      // Sort by name
      users.sort((a, b) => (a.name || a.fullName || '').localeCompare(b.name || b.fullName || ''))
      return users
    }
  },
  mounted() {
    this.loadUsers()
  },
  methods: {
    async loadUsers() {
      this.loading = true
      try {
        const snap = await get(ref(database, 'Users'))
        if (snap.exists()) {
          const data = snap.val()
          this.allUsers = Object.entries(data).map(([uid, user]) => ({
            ...user,
            _uid: uid
          }))
        } else {
          this.allUsers = []
        }
      } catch (err) {
        console.error('Failed to load users:', err)
      } finally {
        this.loading = false
      }
    },
    getStatusClass(status) {
      const s = (status || 'pending').toLowerCase()
      if (s === 'verified') return 'verified'
      if (s === 'blocked') return 'blocked'
      return 'pending'
    },
    openDetail(user) {
      this.selectedUser = user
    },
    openImagePreview(url) {
      this.previewImage = url
    },
    async refreshData() {
      await this.loadUsers()
    }
  }
}
</script>

<style scoped>
.users-page { padding: 0; }

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px; flex-wrap: wrap; gap: 16px;
}
.header-left h1 {
  display: flex; align-items: center; gap: 10px;
  font-size: 1.5rem; color: var(--text, #E2E8F0); margin: 0;
}
.header-icon { color: var(--accent, #4F86F7); font-size: 28px; }
.subtitle { color: var(--text-muted, #94A3B8); margin: 4px 0 0; font-size: 0.875rem; }

.header-actions { display: flex; gap: 8px; align-items: center; }

.view-toggle {
  display: flex; border: 1px solid var(--border, #2A3A4E); border-radius: 8px; overflow: hidden;
}
.toggle-btn {
  background: transparent; border: none; color: var(--text-muted, #94A3B8);
  padding: 8px 12px; cursor: pointer; transition: all 0.2s;
}
.toggle-btn:hover { color: var(--text, #E2E8F0); }
.toggle-btn.active {
  background: var(--accent, #4F86F7); color: #fff;
}
.toggle-btn .material-icons { font-size: 20px; }

.btn {
  display: inline-flex; align-items: center; gap: 6px; padding: 10px 20px;
  border: none; border-radius: 8px; cursor: pointer; font-size: 0.875rem;
  font-weight: 600; transition: all 0.2s;
}
.btn-ghost { background: transparent; color: var(--text-muted, #94A3B8); border: 1px solid var(--border, #2A3A4E); }
.btn-ghost:hover { border-color: var(--accent, #4F86F7); color: var(--text, #E2E8F0); }

.stats-bar {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px; margin-bottom: 24px;
}
.stat-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 12px; padding: 16px 20px;
}
.stat-icon { font-size: 32px; color: var(--accent, #4F86F7); }
.stat-value { font-size: 1.5rem; font-weight: 700; color: var(--text, #E2E8F0); display: block; }
.stat-label { font-size: 0.75rem; color: var(--text-muted, #94A3B8); }

/* Filter Bar */
.filter-bar {
  display: flex; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; align-items: center;
}
.filter-group { display: flex; flex-direction: column; gap: 4px; }
.search-group { flex: 1; min-width: 250px; }
.search-input-wrapper { position: relative; }
.search-icon { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--text-muted, #94A3B8); font-size: 20px; }
.filter-input {
  width: 100%; background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 8px; padding: 10px 14px 10px 40px; color: var(--text, #E2E8F0);
  font-size: 0.875rem;
}
.filter-input:focus { border-color: var(--accent, #4F86F7); outline: none; }
.filter-select {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 8px; padding: 10px 14px; color: var(--text, #E2E8F0);
  font-size: 0.875rem; min-width: 150px; cursor: pointer;
}
.filter-select:focus { border-color: var(--accent, #4F86F7); outline: none; }

/* Table */
.reports-table-container {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 12px; overflow: hidden;
}
.reports-table { width: 100%; border-collapse: collapse; }
.reports-table thead { background: rgba(79, 134, 247, 0.08); }
.reports-table th {
  padding: 12px 16px; text-align: left; font-size: 0.75rem;
  font-weight: 600; color: var(--text-muted, #94A3B8); text-transform: uppercase;
  letter-spacing: 0.5px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.reports-table td {
  padding: 12px 16px; font-size: 0.875rem; color: var(--text, #E2E8F0);
  border-bottom: 1px solid var(--border, #2A3A4E);
}
.report-row { cursor: pointer; transition: background 0.2s; }
.report-row:hover { background: rgba(79, 134, 247, 0.05); }

.user-avatar {
  width: 40px; height: 40px; border-radius: 50%; object-fit: cover;
  border: 2px solid var(--border, #2A3A4E);
}
.avatar-placeholder {
  width: 40px; height: 40px; border-radius: 50%;
  background: rgba(79, 134, 247, 0.15); display: flex; align-items: center; justify-content: center;
  border: 2px solid var(--border, #2A3A4E);
}
.avatar-placeholder .material-icons { color: var(--accent, #4F86F7); font-size: 22px; }

.user-name-cell { font-weight: 600; }
.user-email-cell { color: var(--text-muted, #94A3B8); font-size: 0.85rem; }

.status-badge {
  display: inline-flex; padding: 4px 10px; border-radius: 12px;
  font-size: 0.75rem; font-weight: 600; text-transform: capitalize;
}
.status-badge.verified { background: rgba(34, 197, 94, 0.15); color: #22C55E; }
.status-badge.pending { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
.status-badge.blocked { background: rgba(239, 68, 68, 0.15); color: #EF4444; }

.id-thumbnail {
  width: 48px; height: 36px; object-fit: cover; border-radius: 6px;
  border: 1px solid var(--border, #2A3A4E); cursor: pointer;
  transition: transform 0.2s;
}
.id-thumbnail:hover { transform: scale(1.1); }
.no-image { color: var(--text-muted, #94A3B8); font-size: 0.8rem; }

.icon-btn {
  background: none; border: none; color: var(--text-muted, #94A3B8);
  cursor: pointer; padding: 6px; border-radius: 6px; transition: all 0.2s;
}
.icon-btn:hover { background: rgba(79, 134, 247, 0.1); color: var(--accent, #4F86F7); }
.icon-btn .material-icons { font-size: 20px; }

.actions-cell { display: flex; gap: 4px; }

.empty-state {
  text-align: center; padding: 48px 16px !important; color: var(--text-muted, #94A3B8);
}
.empty-state .material-icons { font-size: 48px; display: block; margin-bottom: 12px; }

/* Grid View */
.users-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.user-card {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 12px; padding: 20px; cursor: pointer; transition: all 0.2s;
  display: flex; flex-direction: column; align-items: center; text-align: center;
}
.user-card:hover {
  border-color: var(--accent, #4F86F7);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(79, 134, 247, 0.1);
}
.card-avatar { position: relative; margin-bottom: 12px; }
.card-avatar-img {
  width: 64px; height: 64px; border-radius: 50%; object-fit: cover;
  border: 3px solid var(--border, #2A3A4E);
}
.card-avatar-placeholder {
  width: 64px; height: 64px; border-radius: 50%;
  background: rgba(79, 134, 247, 0.15); display: flex; align-items: center; justify-content: center;
  border: 3px solid var(--border, #2A3A4E);
}
.card-avatar-placeholder .material-icons { color: var(--accent, #4F86F7); font-size: 32px; }
.status-dot {
  position: absolute; bottom: 2px; right: 2px;
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid var(--bg-card, #1A2332);
}
.status-dot.verified { background: #22C55E; }
.status-dot.pending { background: #F59E0B; }
.status-dot.blocked { background: #EF4444; }

.card-info { width: 100%; }
.card-name { color: var(--text, #E2E8F0); font-size: 1rem; margin: 0 0 4px; }
.card-email { color: var(--text-muted, #94A3B8); font-size: 0.8rem; margin: 0 0 4px; word-break: break-all; }
.card-phone { color: var(--text-muted, #94A3B8); font-size: 0.8rem; margin: 0 0 8px; }

/* Detail Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.6);
  display: flex; justify-content: center; align-items: center;
  z-index: 1000; backdrop-filter: blur(4px);
}
.preview-panel {
  background: var(--bg-card, #1A2332); border: 1px solid var(--border, #2A3A4E);
  border-radius: 16px; width: 90vw; max-width: 700px; max-height: 85vh;
  display: flex; flex-direction: column; overflow: hidden;
}
.preview-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 24px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.preview-header h2 {
  display: flex; align-items: center; gap: 8px;
  font-size: 1.1rem; color: var(--text, #E2E8F0); margin: 0;
}
.preview-actions { display: flex; gap: 8px; align-items: center; }
.preview-body { overflow-y: auto; padding: 24px; flex: 1; }

.user-detail-layout { display: flex; flex-direction: column; gap: 24px; }

.user-profile-section {
  display: flex; flex-direction: column; align-items: center;
  padding-bottom: 20px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.profile-avatar-large { margin-bottom: 12px; }
.profile-img-large {
  width: 96px; height: 96px; border-radius: 50%; object-fit: cover;
  border: 3px solid var(--accent, #4F86F7);
}
.profile-placeholder-large {
  width: 96px; height: 96px; border-radius: 50%;
  background: rgba(79, 134, 247, 0.15); display: flex; align-items: center; justify-content: center;
  border: 3px solid var(--accent, #4F86F7);
}
.profile-placeholder-large .material-icons { color: var(--accent, #4F86F7); font-size: 48px; }
.profile-name { color: var(--text, #E2E8F0); font-size: 1.25rem; margin: 0 0 8px; }

.user-info-section h4 {
  color: var(--accent, #4F86F7); margin: 0 0 16px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border, #2A3A4E);
}

.detail-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 0; border-bottom: 1px solid rgba(42, 58, 78, 0.5);
}
.detail-label {
  color: var(--text-muted, #94A3B8); font-size: 0.85rem;
  display: flex; align-items: center; gap: 6px;
}
.detail-value { color: var(--text, #E2E8F0); font-size: 0.85rem; font-weight: 500; }

.uid-code {
  background: rgba(79, 134, 247, 0.15); color: var(--accent, #4F86F7);
  padding: 2px 8px; border-radius: 4px; font-size: 0.75rem;
}

.id-image-section { margin-top: 20px; }
.id-image-section h4 {
  color: var(--accent, #4F86F7); margin: 0 0 12px;
  padding-bottom: 8px; border-bottom: 1px solid var(--border, #2A3A4E);
}
.id-image-large {
  width: 100%; max-height: 300px; object-fit: contain; border-radius: 10px;
  border: 1px solid var(--border, #2A3A4E); cursor: pointer;
}

/* Image Preview */
.image-preview-container {
  position: relative; max-width: 90vw; max-height: 90vh;
}
.close-preview {
  position: absolute; top: 8px; right: 8px; z-index: 10;
  background: rgba(0,0,0,0.6) !important; border-radius: 50%;
}
.close-preview .material-icons { color: #fff; }
.preview-full-image {
  max-width: 90vw; max-height: 85vh; border-radius: 12px;
  border: 1px solid var(--border, #2A3A4E);
}

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }

@media (max-width: 768px) {
  .page-header { flex-direction: column; align-items: flex-start; }
  .stats-bar { grid-template-columns: 1fr 1fr; }
  .filter-bar { flex-direction: column; }
  .users-grid { grid-template-columns: 1fr 1fr; }
  .reports-table th:nth-child(n+4), .reports-table td:nth-child(n+4) { display: none; }
  .preview-panel { width: 95vw; max-height: 90vh; }
}

@media (max-width: 480px) {
  .users-grid { grid-template-columns: 1fr; }
}
</style>
