<template>
  <!-- Login page — no sidebar -->
  <div v-if="isLoginPage" id="login-wrapper">
    <router-view />
  </div>

  <!-- Main layout with sidebar -->
  <div v-else id="main-wrapper">
    <!-- Mobile menu button -->
    <button class="mobile-menu-btn" @click="mobileMenuOpen = !mobileMenuOpen">
      <span class="material-icons">{{ mobileMenuOpen ? 'close' : 'menu' }}</span>
    </button>

    <!-- Mobile overlay -->
    <div class="sidebar-overlay" :class="{ active: mobileMenuOpen }" @click="mobileMenuOpen = false"></div>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed, 'mobile-open': mobileMenuOpen }">
      <div class="sidebar-brand">
        <img src="/billsense-logo.png" alt="BillSense" />
        <h2>BillSense</h2>
      </div>

      <nav class="sidebar-nav">
        <ul>
          <!-- Dashboard -->
          <li>
            <router-link to="/" exact-active-class="active" @click="mobileMenuOpen = false">
              <span class="material-icons">dashboard</span>
              <span class="nav-text">Dashboard</span>
            </router-link>
          </li>

          <!-- Scanning submenu -->
          <li class="nav-group">
            <button class="nav-group-toggle" :class="{ open: openMenus.scanning }" @click="toggleMenu('scanning')">
              <span class="material-icons">document_scanner</span>
              <span class="nav-text">Scanning</span>
              <span class="material-icons nav-arrow nav-text">{{ openMenus.scanning ? 'expand_less' : 'expand_more' }}</span>
            </button>
            <ul class="nav-submenu" v-show="openMenus.scanning">
              <li>
                <router-link to="/scan-results" @click="mobileMenuOpen = false">
                  <span class="nav-text">Scan Results</span>
                </router-link>
              </li>
              <li>
                <router-link to="/standard-scans" @click="mobileMenuOpen = false">
                  <span class="nav-text">Standard Scans</span>
                </router-link>
              </li>
              <li>
                <router-link to="/multi-scans" @click="mobileMenuOpen = false">
                  <span class="nav-text">Multi Scans</span>
                </router-link>
              </li>
              <li>
                <router-link to="/video-scans" @click="mobileMenuOpen = false">
                  <span class="nav-text">Video Scans</span>
                </router-link>
              </li>
              <li>
                <router-link to="/detections" @click="mobileMenuOpen = false">
                  <span class="nav-text">Detections</span>
                </router-link>
              </li>
            </ul>
          </li>

          <!-- Users -->
          <li>
            <router-link to="/users" @click="mobileMenuOpen = false">
              <span class="material-icons">people</span>
              <span class="nav-text">Users</span>
            </router-link>
          </li>

          <!-- Community submenu -->
          <li class="nav-group">
            <button class="nav-group-toggle" :class="{ open: openMenus.community }" @click="toggleMenu('community')">
              <span class="material-icons">forum</span>
              <span class="nav-text">Community</span>
              <span class="material-icons nav-arrow nav-text">{{ openMenus.community ? 'expand_less' : 'expand_more' }}</span>
            </button>
            <ul class="nav-submenu" v-show="openMenus.community">
              <li>
                <router-link to="/voting-posts" @click="mobileMenuOpen = false">
                  <span class="nav-text">Voting Posts</span>
                </router-link>
              </li>
              <li>
                <router-link to="/cases-evidence" @click="mobileMenuOpen = false">
                  <span class="nav-text">Cases &amp; Evidence</span>
                </router-link>
              </li>
            </ul>
          </li>

          <!-- Content submenu -->
          <li class="nav-group">
            <button class="nav-group-toggle" :class="{ open: openMenus.content }" @click="toggleMenu('content')">
              <span class="material-icons">article</span>
              <span class="nav-text">Content</span>
              <span class="material-icons nav-arrow nav-text">{{ openMenus.content ? 'expand_less' : 'expand_more' }}</span>
            </button>
            <ul class="nav-submenu" v-show="openMenus.content">
              <li>
                <router-link to="/announcements" @click="mobileMenuOpen = false">
                  <span class="nav-text">Announcements</span>
                </router-link>
              </li>
              <li>
                <router-link to="/educational-content" @click="mobileMenuOpen = false">
                  <span class="nav-text">Educational Content</span>
                </router-link>
              </li>
              <li>
                <router-link to="/notifications" @click="mobileMenuOpen = false">
                  <span class="nav-text">Notifications</span>
                </router-link>
              </li>
            </ul>
          </li>

          <!-- Support submenu -->
          <li class="nav-group">
            <button class="nav-group-toggle" :class="{ open: openMenus.support }" @click="toggleMenu('support')">
              <span class="material-icons">support_agent</span>
              <span class="nav-text">Support</span>
              <span class="material-icons nav-arrow nav-text">{{ openMenus.support ? 'expand_less' : 'expand_more' }}</span>
            </button>
            <ul class="nav-submenu" v-show="openMenus.support">
              <li>
                <router-link to="/support-tickets" @click="mobileMenuOpen = false">
                  <span class="nav-text">Active Tickets</span>
                </router-link>
              </li>
              <li>
                <router-link to="/support-tickets?filter=resolved" @click="mobileMenuOpen = false">
                  <span class="nav-text">Ticket History</span>
                </router-link>
              </li>
            </ul>
          </li>

          <!-- AI & ML submenu -->
          <li class="nav-group">
            <button class="nav-group-toggle" :class="{ open: openMenus.aiml }" @click="toggleMenu('aiml')">
              <span class="material-icons">psychology</span>
              <span class="nav-text">AI &amp; ML</span>
              <span class="material-icons nav-arrow nav-text">{{ openMenus.aiml ? 'expand_less' : 'expand_more' }}</span>
            </button>
            <ul class="nav-submenu" v-show="openMenus.aiml">
              <li>
                <router-link to="/ml-models" @click="mobileMenuOpen = false">
                  <span class="nav-text">ML &amp; AI Models</span>
                </router-link>
              </li>
              <li>
                <router-link to="/agents" @click="mobileMenuOpen = false">
                  <span class="nav-text">Agents</span>
                </router-link>
              </li>
            </ul>
          </li>

          <!-- Thesis -->
          <li>
            <router-link to="/thesis" @click="mobileMenuOpen = false">
              <span class="material-icons">school</span>
              <span class="nav-text">Thesis</span>
            </router-link>
          </li>

          <!-- Session Reports -->
          <li>
            <router-link to="/session-reports" @click="mobileMenuOpen = false">
              <span class="material-icons">assessment</span>
              <span class="nav-text">Session Reports</span>
            </router-link>
          </li>

          <!-- APK Management -->
          <li>
            <router-link to="/apk-management" @click="mobileMenuOpen = false">
              <span class="material-icons">android</span>
              <span class="nav-text">APK Management</span>
            </router-link>
          </li>

          <!-- App Testing -->
          <li>
            <router-link to="/app-testing" @click="mobileMenuOpen = false">
              <span class="material-icons">bug_report</span>
              <span class="nav-text">App Testing</span>
            </router-link>
          </li>

          <!-- Connection Health -->
          <li>
            <router-link to="/connection-health" @click="mobileMenuOpen = false">
              <span class="material-icons">settings_ethernet</span>
              <span class="nav-text">Connection Health</span>
            </router-link>
          </li>

          <!-- GitNexus -->
          <li>
            <router-link to="/gitnexus" @click="mobileMenuOpen = false">
              <span class="material-icons">account_tree</span>
              <span class="nav-text">GitNexus</span>
            </router-link>
          </li>

          <!-- Theme Toggle -->
          <li>
            <a href="#" @click.prevent="toggleTheme" class="theme-toggle-link">
              <span class="material-icons">{{ isDarkMode ? 'light_mode' : 'dark_mode' }}</span>
              <span class="nav-text">{{ isDarkMode ? 'Light Mode' : 'Dark Mode' }}</span>
            </a>
          </li>
        </ul>
      </nav>

      <!-- Logout button at bottom -->
      <div class="sidebar-bottom">
        <button class="logout-btn" @click="handleLogout">
          <span class="material-icons">logout</span>
          <span class="nav-text">Logout</span>
        </button>
      </div>

      <div class="sidebar-toggle">
        <button @click="sidebarCollapsed = !sidebarCollapsed">
          <span class="material-icons">{{ sidebarCollapsed ? 'chevron_right' : 'chevron_left' }}</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      sidebarCollapsed: false,
      mobileMenuOpen: false,
      isDarkMode: true,
      openMenus: {
        scanning: false,
        community: false,
        content: false,
        support: false,
        aiml: false
      }
    }
  },
  computed: {
    isLoginPage() {
      return this.$route.path === '/login'
    }
  },
  watch: {
    '$route.path'(newPath) {
      this.autoOpenMenuForRoute(newPath)
    }
  },
  mounted() {
    const saved = localStorage.getItem('billsense_theme')
    this.isDarkMode = saved ? saved === 'dark' : true
    this.applyTheme()
    this.autoOpenMenuForRoute(this.$route.path)
  },
  methods: {
    toggleMenu(menu) {
      this.openMenus[menu] = !this.openMenus[menu]
    },
    autoOpenMenuForRoute(path) {
      const scanRoutes = ['/scan-results', '/standard-scans', '/multi-scans', '/video-scans', '/detections']
      const communityRoutes = ['/voting-posts', '/cases-evidence']
      const contentRoutes = ['/announcements', '/educational-content', '/notifications']
      const supportRoutes = ['/support-tickets']
      const aimlRoutes = ['/ml-models', '/agents']

      if (scanRoutes.some(r => path.startsWith(r))) this.openMenus.scanning = true
      if (communityRoutes.some(r => path.startsWith(r))) this.openMenus.community = true
      if (contentRoutes.some(r => path.startsWith(r))) this.openMenus.content = true
      if (supportRoutes.some(r => path.startsWith(r))) this.openMenus.support = true
      if (aimlRoutes.some(r => path.startsWith(r))) this.openMenus.aiml = true
    },
    toggleTheme() {
      this.isDarkMode = !this.isDarkMode
      localStorage.setItem('billsense_theme', this.isDarkMode ? 'dark' : 'light')
      this.applyTheme()
    },
    applyTheme() {
      const root = document.documentElement
      if (this.isDarkMode) {
        root.setAttribute('data-theme', 'dark')
        root.style.setProperty('--bg', '#0F1923')
        root.style.setProperty('--bg-card', '#1A2332')
        root.style.setProperty('--bg-sidebar', '#0D1520')
        root.style.setProperty('--border', '#2A3A4E')
        root.style.setProperty('--text', '#E2E8F0')
        root.style.setProperty('--text-muted', '#94A3B8')
        root.style.setProperty('--accent', '#4F86F7')
        root.style.setProperty('--success', '#22C55E')
        root.style.setProperty('--warning', '#F59E0B')
        root.style.setProperty('--danger', '#EF4444')
        root.style.setProperty('--input-bg', '#0F1923')
      } else {
        root.setAttribute('data-theme', 'light')
        root.style.setProperty('--bg', '#F1F5F9')
        root.style.setProperty('--bg-card', '#FFFFFF')
        root.style.setProperty('--bg-sidebar', '#1E293B')
        root.style.setProperty('--border', '#E2E8F0')
        root.style.setProperty('--text', '#1E293B')
        root.style.setProperty('--text-muted', '#64748B')
        root.style.setProperty('--accent', '#3B82F6')
        root.style.setProperty('--success', '#16A34A')
        root.style.setProperty('--warning', '#D97706')
        root.style.setProperty('--danger', '#DC2626')
        root.style.setProperty('--input-bg', '#F8FAFC')
      }
    },
    handleLogout() {
      sessionStorage.removeItem('billsense_admin_auth')
      localStorage.removeItem('billsense_admin_auth')
      this.$router.push('/login')
    }
  }
}
</script>

<style>
/* Submenu styles */
.nav-group { position: relative; }

.nav-group-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  width: 100%;
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  text-align: left;
}

.nav-group-toggle:hover {
  background: var(--primary-light);
  color: #fff;
}

.nav-group-toggle.open {
  color: #fff;
}

.nav-group-toggle .material-icons {
  font-size: 20px;
}

.nav-arrow {
  margin-left: auto;
  font-size: 18px !important;
}

.nav-submenu {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-submenu li a {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 16px 9px 48px;
  color: var(--text-muted);
  text-decoration: none;
  border-radius: 8px;
  transition: all 0.2s;
  font-size: 13px;
  font-weight: 500;
}

.nav-submenu li a:hover,
.nav-submenu li a.router-link-active {
  background: var(--primary-light);
  color: #fff;
}

.nav-submenu li a.router-link-active {
  background: var(--accent);
  color: #fff;
}

/* Hide submenu text when sidebar collapsed */
.collapsed .nav-group-toggle .nav-text,
.collapsed .nav-submenu {
  display: none;
}
</style>
