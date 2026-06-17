<template>
  <div class="login-wrapper">
    <!-- Animated background -->
    <div class="login-bg">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-container">
      <!-- Logo & Brand -->
      <div class="login-brand">
        <div class="logo-icon">
          <span class="material-icons">admin_panel_settings</span>
        </div>
        <h1>BillSense</h1>
        <p class="brand-subtitle">Admin Control Panel</p>
      </div>

      <!-- Login Card -->
      <div class="login-card">
        <div class="card-header">
          <h2>Welcome Back</h2>
          <p>Sign in to access the admin dashboard</p>
        </div>

        <form @submit.prevent="handleLogin" class="login-form">
          <!-- Error Message -->
          <div v-if="errorMessage" class="error-alert">
            <span class="material-icons">error_outline</span>
            <span>{{ errorMessage }}</span>
          </div>

          <!-- Username Field -->
          <div class="form-group" :class="{ focused: usernameFocused, filled: credentials.username }">
            <label for="username">
              <span class="material-icons">person</span>
              Username
            </label>
            <input
              id="username"
              type="text"
              v-model="credentials.username"
              @focus="usernameFocused = true"
              @blur="usernameFocused = false"
              placeholder="Enter your username"
              autocomplete="username"
              required
            />
          </div>

          <!-- Password Field -->
          <div class="form-group" :class="{ focused: passwordFocused, filled: credentials.password }">
            <label for="password">
              <span class="material-icons">lock</span>
              Password
            </label>
            <div class="password-wrapper">
              <input
                id="password"
                :type="showPassword ? 'text' : 'password'"
                v-model="credentials.password"
                @focus="passwordFocused = true"
                @blur="passwordFocused = false"
                placeholder="Enter your password"
                autocomplete="current-password"
                required
              />
              <button type="button" class="toggle-password" @click="showPassword = !showPassword" tabindex="-1">
                <span class="material-icons">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
              </button>
            </div>
          </div>

          <!-- Remember Me -->
          <div class="form-options">
            <label class="checkbox-label">
              <input type="checkbox" v-model="rememberMe" />
              <span class="checkmark"></span>
              Remember me
            </label>
          </div>

          <!-- Submit Button -->
          <button type="submit" class="login-btn" :disabled="isLoading">
            <span v-if="isLoading" class="spinner"></span>
            <span v-else class="material-icons">login</span>
            {{ isLoading ? 'Signing in...' : 'Sign In' }}
          </button>
        </form>

        <!-- Footer -->
        <div class="card-footer">
          <p>
            <span class="material-icons">shield</span>
            Secured admin access only
          </p>
        </div>
      </div>

      <!-- Version Info -->
      <p class="version-info">BillSense Admin v1.0.0</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Login',
  data() {
    return {
      credentials: {
        username: '',
        password: ''
      },
      showPassword: false,
      rememberMe: false,
      isLoading: false,
      errorMessage: '',
      usernameFocused: false,
      passwordFocused: false
    }
  },
  created() {
    // Check if user is already logged in
    const isAuthenticated = sessionStorage.getItem('billsense_admin_auth') === 'true' ||
                            localStorage.getItem('billsense_admin_auth') === 'true'
    if (isAuthenticated) {
      this.$router.replace('/')
    }
    // Pre-fill username if remembered
    const savedUsername = localStorage.getItem('billsense_admin_username')
    if (savedUsername) {
      this.credentials.username = savedUsername
      this.rememberMe = true
    }
  },
  methods: {
    async handleLogin() {
      this.errorMessage = ''
      this.isLoading = true

      // Simulate network delay for UX
      await new Promise(resolve => setTimeout(resolve, 800))

      // Validate credentials
      if (this.credentials.username === 'admin' && this.credentials.password === 'admin123') {
        // Store auth state
        if (this.rememberMe) {
          localStorage.setItem('billsense_admin_auth', 'true')
          localStorage.setItem('billsense_admin_username', this.credentials.username)
        } else {
          sessionStorage.setItem('billsense_admin_auth', 'true')
          localStorage.removeItem('billsense_admin_username')
        }

        // Navigate to dashboard
        const redirect = this.$route.query.redirect || '/'
        this.$router.replace(redirect)
      } else {
        this.errorMessage = 'Invalid username or password. Please try again.'
        this.credentials.password = ''
      }

      this.isLoading = false
    }
  }
}
</script>

<style scoped>
.login-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  position: relative;
  overflow: hidden;
  padding: 20px;
}

/* Animated background circles */
.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  z-index: 0;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
}

.bg-circle-1 {
  width: 500px;
  height: 500px;
  background: var(--accent);
  top: -100px;
  right: -100px;
  animation: float1 15s ease-in-out infinite;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  background: #8B5CF6;
  bottom: -80px;
  left: -80px;
  animation: float2 18s ease-in-out infinite;
}

.bg-circle-3 {
  width: 300px;
  height: 300px;
  background: var(--success);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: float3 12s ease-in-out infinite;
}

@keyframes float1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-30px, 30px) scale(1.1); }
  66% { transform: translate(20px, -20px) scale(0.95); }
}

@keyframes float2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(40px, -20px) scale(1.05); }
  66% { transform: translate(-20px, 40px) scale(0.9); }
}

@keyframes float3 {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-50%, -50%) scale(1.15); }
}

.login-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
}

/* Brand Section */
.login-brand {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, var(--accent), #8B5CF6);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  box-shadow: 0 8px 32px rgba(79, 134, 247, 0.3);
}

.logo-icon .material-icons {
  font-size: 36px;
  color: white;
}

.login-brand h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text);
  letter-spacing: -0.5px;
}

.brand-subtitle {
  color: var(--text-muted);
  font-size: 14px;
  margin-top: 4px;
}

/* Login Card */
.login-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

.card-header {
  text-align: center;
  margin-bottom: 28px;
}

.card-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
}

.card-header p {
  color: var(--text-muted);
  font-size: 14px;
}

/* Error Alert */
.error-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #FCA5A5;
  font-size: 13px;
  animation: shake 0.5s ease-in-out;
}

.error-alert .material-icons {
  font-size: 20px;
  color: var(--danger);
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-8px); }
  40% { transform: translateX(8px); }
  60% { transform: translateX(-4px); }
  80% { transform: translateX(4px); }
}

/* Form Groups */
.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-muted);
  margin-bottom: 8px;
  transition: color 0.2s;
}

.form-group label .material-icons {
  font-size: 16px;
}

.form-group.focused label {
  color: var(--accent);
}

.form-group input[type="text"],
.form-group input[type="password"] {
  width: 100%;
  padding: 12px 16px;
  background: var(--bg);
  border: 1.5px solid var(--border);
  border-radius: 10px;
  color: var(--text);
  font-size: 14px;
  font-family: inherit;
  transition: all 0.2s;
  outline: none;
}

.form-group input::placeholder {
  color: #4A5568;
}

.form-group.focused input {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(79, 134, 247, 0.15);
}

.form-group.filled input {
  border-color: var(--border);
}

.password-wrapper {
  position: relative;
}

.password-wrapper input {
  padding-right: 44px;
}

.toggle-password {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  border-radius: 6px;
  transition: color 0.2s;
}

.toggle-password:hover {
  color: var(--text);
}

.toggle-password .material-icons {
  font-size: 20px;
}

/* Form Options */
.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-muted);
  user-select: none;
}

.checkbox-label input[type="checkbox"] {
  display: none;
}

.checkmark {
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--border);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  background: var(--bg);
}

.checkbox-label input:checked + .checkmark {
  background: var(--accent);
  border-color: var(--accent);
}

.checkbox-label input:checked + .checkmark::after {
  content: '';
  width: 5px;
  height: 9px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  margin-bottom: 2px;
}

/* Login Button */
.login-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, var(--accent), #6C63FF);
  border: none;
  border-radius: 10px;
  color: white;
  font-size: 15px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(79, 134, 247, 0.35);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.login-btn .material-icons {
  font-size: 20px;
}

/* Spinner */
.spinner {
  width: 20px;
  height: 20px;
  border: 2.5px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Card Footer */
.card-footer {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  text-align: center;
}

.card-footer p {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.card-footer .material-icons {
  font-size: 14px;
  color: var(--success);
}

/* Version Info */
.version-info {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: #4A5568;
}

/* Responsive */
@media (max-width: 480px) {
  .login-card {
    padding: 24px 20px;
  }

  .logo-icon {
    width: 60px;
    height: 60px;
  }

  .logo-icon .material-icons {
    font-size: 28px;
  }

  .login-brand h1 {
    font-size: 24px;
  }
}
</style>
