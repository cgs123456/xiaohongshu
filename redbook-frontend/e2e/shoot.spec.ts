import { test } from '@playwright/test'
import { readFileSync, existsSync } from 'node:fs'
import { resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * README 界面截图采集：登录态实拍六个页面到 docs/screenshots/。
 * 需本地后端运行 + .run/ui-token（宿主侧 send/verify 生成，不回显）。
 * 运行：npx playwright test e2e/shoot.spec.ts
 */
const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..')
const out = resolve(root, 'docs/screenshots')
const tokenFile = resolve(root, '.run/ui-token')

test.skip(!existsSync(tokenFile), '需要 .run/ui-token（宿主侧登录脚本生成），否则截图为游客态被登录弹窗遮挡')

test('capture screens', async ({ page }) => {
  const viewport = { width: 1280, height: 800 }
  await page.setViewportSize(viewport)

  const token = readFileSync(tokenFile, 'utf-8').trim()
  await page.addInitScript(t => {
    localStorage.setItem('token', t)
    localStorage.setItem('token_expiry', String(Date.now() + 3600_000))
  }, token)

  // 1) 首页信息流
  await page.goto('/')
  await page.waitForSelector('.note-card', { timeout: 20000 })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: join(out, 'home.png') })

  // 2) 笔记详情弹窗
  await page.locator('.note-card').first().click({ force: true })
  await page.waitForTimeout(2500)
  await page.screenshot({ path: join(out, 'note-detail.png') })
  await page.evaluate(() => document.querySelector('.modal-overlay')?.dispatchEvent(new MouseEvent('click', { bubbles: true })))
  await page.waitForTimeout(800)

  // 3) 购物商城
  await page.goto('/shopping')
  await page.waitForSelector('.product-card', { timeout: 20000 })
  await page.waitForTimeout(2000)
  await page.screenshot({ path: join(out, 'shopping.png') })

  // 4) 购物车（先加购一件拍有货状态，截后清空复原）
  await page.evaluate(async () => {
    const token = localStorage.getItem('token') || ''
    await fetch('/api/cart/addToCart', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', token },
      body: JSON.stringify({ productId: 1, quantity: 2, attributes: { '颜色': '红棕' } }),
    })
  })
  await page.goto('/cart')
  await page.waitForTimeout(2500)
  await page.screenshot({ path: join(out, 'cart.png') })
  await page.evaluate(async () => {
    const token = localStorage.getItem('token') || ''
    await fetch('/api/cart/clear', { method: 'DELETE', headers: { token } })
  })

  // 5) 订单列表
  await page.goto('/orders')
  await page.waitForTimeout(2500)
  await page.screenshot({ path: join(out, 'orders.png') })

  // 6) 移动端首页（响应式）
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.waitForTimeout(2500)
  await page.screenshot({ path: join(out, 'home-mobile.png') })
})
