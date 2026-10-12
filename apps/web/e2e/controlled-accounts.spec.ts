import { expect, test, type Page } from '@playwright/test'

const enabled = process.env.VOIDHAVEN_E2E_REAL === '1'
test.skip(!enabled, '仅在隔离的真实 API、MySQL 和 MinIO 环境中运行')
async function signIn(page: Page, loginId: string, password: string) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('账号').fill(loginId)
  await page.locator('input[aria-label="密码"]').fill(password)
  await page.getByRole('button', { name: '进入工作台' }).click()
  await expect(page).toHaveURL(/workspace/, { timeout: 15_000 })
}
test('真实受控账号闭环：预览授权、组织限额、成员激活与双主题布局', async ({ page, browser }, testInfo) => {
  test.setTimeout(120_000)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const suffix = Date.now().toString().slice(-9)
  const memberPassword = `Synthetic-Member-${suffix}!`
  await signIn(page, process.env.VOIDHAVEN_E2E_ADMIN_LOGIN!, process.env.VOIDHAVEN_E2E_ADMIN_PASSWORD!)
  await page.goto('/workspace?section=accounts')
  const panel = page.locator('.authorization-panel').first()
  await panel.getByLabel('受控登录名').fill(`controlled.ui.${suffix}`)
  await panel.getByLabel('内容查看', { exact: true }).check()
  await panel.getByLabel('授权期限').selectOption('long')
  await panel.getByLabel('操作原因').fill('合成浏览器授权验收')
  await expect(panel.getByRole('button', { name: '创建并授权' })).toBeDisabled()
  await panel.getByRole('button', { name: '预览授权影响' }).click()
  await expect(panel.getByRole('button', { name: '创建并授权' })).toBeEnabled()
  for (const [theme, label] of [
    ['light', '浅色'],
    ['dark', '深色'],
  ] as const) {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.getByRole('button', { name: label, exact: true }).click()
    await page.setViewportSize({ width: 390, height: 900 })
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
    await expect
      .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      .toBeTruthy()
    await panel.screenshot({ path: testInfo.outputPath(`authorization-${theme}.png`) })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await panel.getByRole('button', { name: '创建并授权' }).click()
  await expect(page.locator('.token-card')).toContainText(`controlled.ui.${suffix}`, { timeout: 15_000 })
  const adminLink = (await page.locator('.token-card code').textContent())!.trim()
  const adminContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL })
  try {
    const activatedAdmin = await adminContext.request.post('/api/v1/auth/activation/consume', {
      data: { token: new URL(adminLink).searchParams.get('token'), password: memberPassword },
    })
    expect(activatedAdmin.ok()).toBeTruthy()
    const viewer = await adminContext.newPage()
    await signIn(viewer, `controlled.ui.${suffix}`, memberPassword)
    await viewer.goto('/workspace?section=content')
    await expect(viewer.getByRole('link', { name: '新建草稿', exact: true })).toHaveCount(0)
    expect((await viewer.request.get('/api/v1/admin/accounts')).status()).toBe(403)
  } finally {
    await adminContext.close()
  }
  const profileResponse = await page.request.get('/api/v1/admin/partner-profiles')
  expect(profileResponse.ok()).toBeTruthy()
  const profiles = (await profileResponse.json()).data.items
  const accountsResponse = await page.request.get(
    '/api/v1/admin/accounts?q=' + encodeURIComponent(process.env.VOIDHAVEN_E2E_PARTNER_LOGIN!),
  )
  const mainAccount = (await accountsResponse.json()).data.items.find(
    (value: { loginId: string }) => value.loginId === process.env.VOIDHAVEN_E2E_PARTNER_LOGIN,
  )
  const profile = profiles.find((value: { accountId: string }) => value.accountId === mainAccount.id)
  expect(profile).toBeTruthy()
  // The existing member quota UI is exercised with current version and CSRF.
  await page.goto('/workspace?section=partners')
  const organization = page.locator('.profile-card').filter({ hasText: profile.displayName })
  await organization.getByLabel('成员限额').fill(String(profile.memberCount + 1))
  await organization.getByLabel('操作原因', { exact: true }).fill('合成浏览器成员限额验收')
  await organization.getByRole('button', { name: '保存成员限额' }).click()
  await expect(page.getByRole('status').filter({ hasText: '组织设置已更新。' })).toBeVisible()
  const mainContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL })
  const main = await mainContext.newPage()
  main.on('pageerror', (error) => errors.push(error.message))
  try {
    await signIn(main, process.env.VOIDHAVEN_E2E_PARTNER_LOGIN!, process.env.VOIDHAVEN_E2E_PARTNER_PASSWORD!)
    await main.goto('/workspace?section=members')
    await expect(main.getByRole('heading', { name: '受控成员账号' })).toBeVisible()
    await main.getByLabel('成员登录名').fill(`controlled.member.${suffix}`)
    await main.getByLabel('开通原因').fill('合成浏览器成员创建验收')
    await main.getByRole('button', { name: '创建成员', exact: true }).click()
    const credential = main.locator('.credential-box')
    await expect(credential).toBeVisible()
    const link = (await credential.locator('code').textContent())!.trim()
    for (const label of ['浅色', '深色']) {
      await main.setViewportSize({ width: 1440, height: 1000 })
      await main.getByRole('button', { name: label, exact: true }).click()
      await main.setViewportSize({ width: 390, height: 900 })
      await expect
        .poll(() => main.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
        .toBeTruthy()
      await main.screenshot({ path: testInfo.outputPath(`members-${label}.png`), fullPage: true })
    }
    const token = new URL(link).searchParams.get('token')
    const activated = await main.request.post('/api/v1/auth/activation/consume', {
      data: { token, password: memberPassword },
    })
    expect(activated.ok()).toBeTruthy()
    const memberContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL })
    try {
      const member = await memberContext.newPage()
      await signIn(member, `controlled.member.${suffix}`, memberPassword)
      await expect(member.getByText('组织成员账号 · 成员名额')).toBeVisible()
      await expect(member.getByRole('link', { name: '成员管理', exact: true })).toHaveCount(0)
      const blocked = await member.request.get('/api/v1/partner/members')
      expect(blocked.status()).toBe(403)
    } finally {
      await memberContext.close()
    }
  } finally {
    await mainContext.close()
  }
  expect(errors).toEqual([])
})
