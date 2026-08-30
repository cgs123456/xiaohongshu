import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const isProduction = mode === 'production'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    },
    // 仅在生产构建时移除 console/debugger
    esbuild: isProduction ? { drop: ['console', 'debugger'] } : {},
    server: {
      port: 5173,
      host: true,
      proxy: {
        // .env.development 中 VITE_API_BASE_URL=/api，代理到后端网关并去掉 /api 前缀
        '/api': {
          target: 'http://localhost:10010',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        },
        '/socket': {
          target: 'ws://localhost:10010',
          ws: true,
          changeOrigin: true
        }
      }
    },
    build: {
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['vue', 'vue-router', 'axios'],
            purify: ['dompurify']
          }
        }
      }
    }
  }
})
