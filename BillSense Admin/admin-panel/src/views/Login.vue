<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">
        <img src="/billsense-logo.png" alt="BillSense" />
        <h1>BillSense</h1>
        <p>Admin Dashboard</p>
      </div>

      <form @submit.prevent="onSubmit" class="login-form">
        <label>
          <span>Username</span>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            :disabled="busy"
            placeholder="Username"
            autofocus
          />
        </label>

        <label>
          <span>Password</span>
          <div class="pw-row">
            <input
              v-model="password"
              :type="showPw ? 'text' : 'password'"
              autocomplete="current-password"
              :disabled="busy"
              placeholder="Password"
              @keydown.enter.prevent="onSubmit"
            />
            <button type="button" class="pw-toggle" @click="showPw = !showPw" tabindex="-1">
              <span class="material-icons">{{ showPw ? 'visibility_off' : 'visibility' }}</span>
            </button>
          </div>
        </label>

        <p v-if="error" class="error">
          <span class="material-icons">error</span> {{ error }}
        </p>

        <button type="submit" class="submit" :disabled="busy || !username || !password">
          <span class="material-icons">{{ busy ? 'hourglass_top' : 'login' }}</span>
          {{ busy ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>

      <p class="note">Authorized personnel only.</p>
    </div>
  </div>
</template>

<script>
import { login } from '../services/auth.js'

export default {
  name: 'Login',
  data() {
    return { username: '', password: '', showPw: false, busy: false, error: '' }
  },
  methods: {
    async onSubmit() {
      if (this.busy) return
      this.busy = true
      this.error = ''
      try {
        const ok = await login(this.username.trim(), this.password)
        if (ok) {
          const dest = this.$route.query.redirect || '/'
          this.$router.replace(dest)
        } else {
          this.error = 'Invalid username or password.'
          this.password = ''
        }
      } catch (e) {
        this.error = 'Login failed: ' + e.message
      } finally {
        this.busy = false
      }
    }
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 30% 20%, #1a2342 0%, #0b1020 70%);
  padding: 1.5rem;
}
.login-card {
  width: 100%;
  max-width: 380px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 2.25rem 2rem;
  backdrop-filter: blur(8px);
}
.brand {
  text-align: center;
  margin-bottom: 1.75rem;
}
.brand img {
  width: 56px;
  height: 56px;
  object-fit: contain;
  margin-bottom: 0.5rem;
}
.brand h1 {
  margin: 0;
  font-size: 1.5rem;
  letter-spacing: 0.5px;
}
.brand p {
  margin: 0.15rem 0 0;
  font-size: 0.85rem;
  color: #94a3b8;
}
.login-form { display: flex; flex-direction: column; gap: 1rem; }
.login-form label { display: flex; flex-direction: column; gap: 0.35rem; }
.login-form label span { font-size: 0.8rem; color: #94a3b8; }
.login-form input {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 0.65rem 0.85rem;
  color: inherit;
  font-size: 0.95rem;
  outline: none;
  width: 100%;
  box-sizing: border-box;
}
.login-form input:focus { border-color: #ffa31a; }
.pw-row { position: relative; }
.pw-toggle {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: 0;
  color: #64748b;
  cursor: pointer;
  display: flex;
  padding: 4px;
}
.pw-toggle .material-icons { font-size: 1.15rem; }
.error {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.83rem;
  color: #f87171;
}
.error .material-icons { font-size: 1rem; }
.submit {
  margin-top: 0.5rem;
  background: #ffa31a;
  color: #0f172a;
  border: 0;
  border-radius: 8px;
  padding: 0.7rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  transition: background 0.15s, transform 0.1s;
}
.submit:hover:not(:disabled) { background: #ffb347; }
.submit:active:not(:disabled) { transform: scale(0.98); }
.submit:disabled { background: rgba(255, 163, 26, 0.3); cursor: not-allowed; }
.submit .material-icons { font-size: 1.15rem; }
.note {
  text-align: center;
  margin: 1.5rem 0 0;
  font-size: 0.75rem;
  color: #475569;
}
</style>
