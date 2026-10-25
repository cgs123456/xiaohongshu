import { test, expect } from '@playwright/test'
import { readFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 登录态 UI 冒烟：token 由 scripts（宿主侧完成 send/verify）写入 .run/ui-token，
 * 不在测试或会话中回显。覆盖 store 修复后的登录态渲染：
 * header 显示用户头像（不再恒游客态）、受保护路由可进入。
 */
const tokenFile = resolve(fileURLToPath(new URL('.', import.meta.url)), '../../.run/ui-token')

test.skip(!existsSync(tokenFile), '需要 .run/ui-token（宿主侧登录脚本生成）')

test.beforeEach(async ({ page }) => {
  const token = readFileSync(tokenFile, 'utf-8').trim()
  await page.addInitScript(t => {
    localStorage.setItem('token', t)
    localStorage.setItem('token_expiry', String(Date.now() + 3600_000))
  }, token)
})

test('登录态 header 显示头像而非登录按钮', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.user-avatar').first()).toBeVisible({ timeout: 10_000 })
  await expect(page.locator('.login-btn-header')).toHaveCount(0)
})

test('受保护路由购物车可进入', async ({ page }) => {
  await page.goto('/cart')
  // 不被守卫弹回首页
  await expect(page).toHaveURL(/\/cart/)
})
