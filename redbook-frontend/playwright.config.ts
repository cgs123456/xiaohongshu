import { defineConfig } from '@playwright/test'

/**
 * 冒烟测试配置：由 Playwright 拉起 vite dev（代理 /api -> localhost:10010），
 * 需要本地后端（docker-compose.app.yml 的 9 个服务容器）在运行。
 * CI 中不执行（无后端），仅本地：npx playwright test
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'off',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
