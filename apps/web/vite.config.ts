import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      port: 4173,
      host: '127.0.0.1',
      strictPort: true,
      proxy: { '/api': { target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true } },
    },
    test: { environment: 'jsdom', include: ['src/**/*.test.ts'] },
  }
})
