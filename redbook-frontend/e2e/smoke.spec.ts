import { test, expect } from '@playwright/test'

/**
 * 冒烟：游客视角主链路（需本地后端运行，vite 代理 /api -> 10010）。
 * 覆盖：信息流匿名可读、侧栏渲染、游客可浏览商城（本次白名单改造）、
 * 未登录浏览购物页不再被强制弹登录。
 */

test('首页匿名可浏览信息流', async ({ page }) => {
  await page.goto('/')
  // 未登录弹窗出现则先关闭（首次访问可能引导登录）
  const overlay = page.locator('.modal-overlay')
  if (await overlay.isVisible({ timeout: 2000 }).catch(() => false)) {
    await overlay.click({ position: { x: 8, y: 8 } })
  }
  // 分类条与瀑布流卡片出现（后端 /note/getNoteList 匿名白名单）
  await expect(page.locator('.category-item.active')).toBeVisible()
  await expect(page.locator('.note-card').first()).toBeVisible({ timeout: 15_000 })
})

test('侧栏导航渲染完整', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('.nav-item')).toHaveCount(6)
})

test('游客可浏览商城（不再强制登录）', async ({ page }) => {
  await page.goto('/shopping')
  // 白名单放行后商品列表匿名可读；出现商品卡片且没有登录弹窗
  await expect(page.locator('.product-card').first()).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.modal-overlay')).toHaveCount(0)
})
