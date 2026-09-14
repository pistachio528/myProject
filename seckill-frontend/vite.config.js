import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 用正则精确匹配 /api/chat 开头的请求，转发到智能客服 8082
      '^/api/chat': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      // 其余 /api 请求转发到秒杀服务 8081
      '^/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
