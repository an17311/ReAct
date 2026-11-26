import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境将 STOMP WebSocket（含降级探测请求）代理到后端，前端代码只用同源相对路径，
      // 不再硬编码 ws://localhost:18081；生产部署同源打包后可直接使用
      '/bs-dialog-websocket': {
        target: 'http://localhost:18081',
        ws: true,
      },
    },
  },
})
