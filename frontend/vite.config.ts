import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy is used only in real mode. Demo mode never calls the backend.
// Delivery default is localhost:8080; local verification used an isolated test port.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
