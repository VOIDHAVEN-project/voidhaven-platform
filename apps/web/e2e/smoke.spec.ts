import { expect, test } from 'playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const evidenceDirectory = process.env.VOIDHAVEN_E2E_EVIDENCE_DIR
  ? path.resolve(process.env.VOIDHAVEN_E2E_EVIDENCE_DIR)
  : path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../docs/evidence')

test('公开首页、稳定详情路由和受控入口可用', async ({ page, request }, testInfo) => {
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })

  const response = await page.goto('/')
  expect(response?.status()).toBe(200)
  await expect(page.locator('body')).not.toHaveText('')
  await expect(page.getByRole('link', { name: '竞赛', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '投稿/反馈', exact: true })).toBeVisible()
  expect(await page.locator('.vite-error-overlay').count()).toBe(0)

  const homeResponse = await request.get('/api/v1/public/home')
  expect(homeResponse.ok()).toBeTruthy()
  const home = (await homeResponse.json()) as { data: { competitions: { id: string; title: string }[] } }
  expect(home.data.competitions.length).toBeGreaterThan(0)
  const content = home.data.competitions[0]
  const detailResponse = await page.goto(`/content/${content.id}`)
  expect(detailResponse?.status()).toBe(200)
  await expect(page.getByRole('heading', { name: content.title })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: content.title })).toBeVisible()

  if (testInfo.project.name === 'desktop-chromium') {
    await page.screenshot({ path: path.join(evidenceDirectory, 'admin-remediation-public-smoke.png'), fullPage: true })
  }
  expect(consoleErrors).toEqual([])
})

test('未认证访客不能进入管理后台', async ({ page }) => {
  await page.goto('/workspace/reviews')
  await expect(page.getByText('当前没有有效的受控会话。')).toBeVisible()
  await expect(page.getByRole('link', { name: '前往登录' })).toBeVisible()
})
