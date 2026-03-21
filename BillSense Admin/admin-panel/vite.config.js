import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3001,
    proxy: {
      // Proxy dev server API (build, emulator, ADB, distribution)
      '/api/dev': {
        target: 'http://localhost:3003',
        changeOrigin: true
      },
      // Proxy GitNexus main page (same-origin iframe for DOM auto-fill)
      '/gitnexus-proxy': {
        target: 'https://gitnexus.vercel.app',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/gitnexus-proxy/, ''),
        headers: { 'Host': 'gitnexus.vercel.app' }
      },
      // Proxy tree-sitter WASM files used by GitNexus code parser
      '/wasm': {
        target: 'https://gitnexus.vercel.app',
        changeOrigin: true,
        headers: { 'Host': 'gitnexus.vercel.app' }
      }
    }
  }
})
