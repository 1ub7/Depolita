import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 로컬 Spring Boot(application-local.yml)의 기본 포트가 8081이다.
  const apiTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8081'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    build: {
      rolldownOptions: {
        output: {
          // 자주 안 바뀌는 라이브러리를 따로 묶어 브라우저 캐시를 오래 쓰게 한다
          codeSplitting: {
            groups: [
              {
                name: 'react',
                test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/,
              },
              { name: 'vendor', test: /node_modules[\\/]/ },
            ],
          },
        },
      },
    },
    server: {
      port: 5173,
      // 개발 중에는 같은 출처처럼 보이도록 /api 를 백엔드로 넘긴다 → CORS 설정 불필요
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
      },
    },
  }
})
