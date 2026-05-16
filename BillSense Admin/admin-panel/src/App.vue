<template>
  <div id="main-wrapper">
    <!-- Mobile menu button -->
    <button v-if="chrome" class="mobile-menu-btn" @click="mobileMenuOpen = !mobileMenuOpen">
      <span class="material-icons">{{ mobileMenuOpen ? 'close' : 'menu' }}</span>
    </button>

    <!-- Mobile overlay -->
    <div v-if="chrome" class="sidebar-overlay" :class="{ active: mobileMenuOpen }" @click="mobileMenuOpen = false"></div>

    <!-- Sidebar -->
    <aside v-if="chrome" class="sidebar" :class="{ collapsed: sidebarCollapsed, 'mobile-open': mobileMenuOpen }">
      <div class="sidebar-brand">
        <img src="/billsense-logo.png" alt="BillSense" />
        <h2>BillSense</h2>
      </div>

      <nav class="sidebar-nav">
        <ul>
          <li>
            <router-link to="/" exact-active-class="active" @click="mobileMenuOpen = false">
              <span class="material-icons">dashboard</span>
              <span class="nav-text">Dashboard</span>
            </router-link>
          </li>
          <li>
            <router-link to="/billy" @click="mobileMenuOpen = false">
              <span class="material-icons">smart_toy</span>
              <span class="nav-text">Billy AI</span>
            </router-link>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">qr_code_scanner</span>
              <span class="nav-text">Scan Reports</span>
            </a>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">people</span>
              <span class="nav-text">Users</span>
            </a>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">model_training</span>
              <span class="nav-text">ML Models</span>
            </a>
          </li>
          <li>
            <router-link to="/apk-management" @click="mobileMenuOpen = false">
              <span class="material-icons">android</span>
              <span class="nav-text">APK Management</span>
            </router-link>
          </li>
          <li>
            <router-link to="/app-testing" @click="mobileMenuOpen = false">
              <span class="material-icons">bug_report</span>
              <span class="nav-text">App Testing</span>
            </router-link>
          </li>
          <li>
            <router-link to="/connection-health" @click="mobileMenuOpen = false">
              <span class="material-icons">settings_ethernet</span>
              <span class="nav-text">Connection Health</span>
            </router-link>
          </li>
          <li>
            <router-link to="/gitnexus" @click="mobileMenuOpen = false">
              <span class="material-icons">account_tree</span>
              <span class="nav-text">GitNexus</span>
            </router-link>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">gavel</span>
              <span class="nav-text">Cases</span>
            </a>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">how_to_vote</span>
              <span class="nav-text">Voting Posts</span>
            </a>
          </li>
          <li>
            <a href="#" @click="mobileMenuOpen = false">
              <span class="material-icons">settings</span>
              <span class="nav-text">Settings</span>
            </a>
          </li>
          <li>
            <a href="#" class="logout-link" @click.prevent="onLogout">
              <span class="material-icons">logout</span>
              <span class="nav-text">Sign out</span>
            </a>
          </li>
        </ul>
      </nav>

      <div class="sidebar-toggle">
        <button @click="sidebarCollapsed = !sidebarCollapsed">
          <span class="material-icons">{{ sidebarCollapsed ? 'chevron_right' : 'chevron_left' }}</span>
        </button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content" :class="{ 'no-chrome': !chrome }">
      <router-view />
    </main>
  </div>
</template>

<script>
import { logout } from './services/auth.js'

export default {
  name: 'App',
  data() {
    return {
      sidebarCollapsed: false,
      mobileMenuOpen: false
    }
  },
  computed: {
    chrome() {
      // No sidebar / mobile button on public pages (login).
      return !this.$route.meta.public
    }
  },
  methods: {
    onLogout() {
      logout()
      this.mobileMenuOpen = false
      this.$router.replace('/login')
    }
  }
}
</script>
