import { expect, test } from 'playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const evidenceDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../docs/evidence')

const adminLogin = process.env.VOIDHAVEN_E2E_ADMIN_LOGIN
const adminPassword = process.env.VOIDHAVEN_E2E_ADMIN_PASSWORD
const partnerLogin = process.env.VOIDHAVEN_E2E_PARTNER_LOGIN
const partnerPassword = process.env.VOIDHAVEN_E2E_PARTNER_PASSWORD

async function signIn(page: import('playwright').Page, loginId: string, password: string) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('账号').fill(loginId)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/workspace/)
  await expect(page.getByText(/FACTOR_PASSWORD/)).toHaveCount(0)
}

async function signOut(page: import('playwright').Page) {
  await page.getByRole('button', { name: '退出' }).click()
  await expect(page).toHaveURL(/\/$/)
}

async function protectedPost(page: import('playwright').Page, path: string, data: unknown) {
  const csrfResponse = await page.request.get('/api/v1/auth/csrf')
  expect(csrfResponse.ok()).toBeTruthy()
  const csrf = await csrfResponse.json() as { data: { headerName: string; token: string } }
  return page.request.post(path, {
    headers: { [csrf.data.headerName]: csrf.data.token, 'Idempotency-Key': crypto.randomUUID() },
    data,
  })
}

test('合作方上传送审、管理员查看原文、退回、通过、配额检查、发布与隐藏闭环', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-chromium', '完整写入闭环只在桌面项目串行执行。')
  test.skip(!adminLogin || !adminPassword || !partnerLogin || !partnerPassword, '需要显式注入本地合成 E2E 账号。')
  const title = `E2E 管理闭环 ${Date.now()}`
  const revisedSentence = '退回后已补充可核验的过程说明。'
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64')

  await signIn(page, partnerLogin, partnerPassword)
  await page.getByLabel('内容模板').selectOption('resource.article')
  await page.getByLabel('标题', { exact: true }).fill(title)
  await page.getByLabel('中文导读').fill('验证合作方创作、图片、审核与发布闭环。')
  await page.getByLabel('Markdown 正文').fill('# 完整原文\n\n管理员必须先看到这一段，再作出审核决定。')
  await page.getByText('发布设置与审核信息').click()
  await page.getByLabel('作者展示名').fill('本地合成合作方')
  await page.getByRole('button', { name: '保存合作方草稿' }).click()
  await expect(page.getByText(new RegExp(`“${title}”已保存`)).first()).toBeVisible()

  const dashboardResponse = await page.request.get('/api/v1/partner/dashboard?pageSize=20')
  expect(dashboardResponse.ok()).toBeTruthy()
  const dashboard = await dashboardResponse.json() as { data: { contentItems: { items: { id: string; title: string }[] } } }
  const contentId = dashboard.data.contentItems.items.find((item) => item.title === title)?.id
  expect(contentId).toBeTruthy()

  const uploadRegion = page.getByRole('region', { name: '正文图片上传' })
  await uploadRegion.locator('input[type="file"]').setInputFiles({ name: 'e2e-evidence.png', mimeType: 'image/png', buffer: png })
  await uploadRegion.getByLabel('图片说明').fill('本地合成审核证据图')
  await uploadRegion.getByRole('button', { name: '上传图片' }).click()
  await expect(page.getByText('图片已插入 Markdown 草稿。请保存草稿，使正文引用进入下一候选修订。')).toBeVisible()
  await expect(page.getByLabel('Markdown 正文')).toHaveValue(/media:/)
  await expect(page.locator('.editor-save-state')).toContainText('已保存', { timeout: 15_000 })

  const editableResponse = await page.request.get(`/api/v1/partner/content-items/${contentId}`)
  const editable = await editableResponse.json() as { data: { media: { id: string }[] } }
  const mediaId = editable.data.media[0]?.id
  expect(mediaId).toBeTruthy()
  await page.getByRole('button', { name: '保存并送审' }).click()
  await expect(page.getByText(`“${title}”已保存并送入审核队列。`)).toBeVisible()
  await signOut(page)

  await signIn(page, adminLogin, adminPassword)
  await page.goto('/workspace/reviews')
  const queueRow = page.locator('.admin-content-row').filter({ hasText: title })
  await expect(queueRow).toBeVisible()
  await queueRow.getByRole('link', { name: '查看原文并审核' }).click()
  await expect(page.getByText('管理员必须先看到这一段，再作出审核决定。')).toBeVisible()
  await expect(page.getByRole('img', { name: '本地合成审核证据图' })).toBeVisible()
  await page.screenshot({ path: path.join(evidenceDirectory, 'admin-remediation-review-detail.png'), fullPage: true })
  await page.getByLabel('审核决定').selectOption('CHANGES_REQUESTED')
  await page.getByLabel('审核说明').fill('请补充过程说明后再次送审。')
  await page.getByRole('button', { name: '保存审核决定' }).click()
  await expect(page.getByText('审核决定已保存，作者将在工作台看到审核意见。')).toBeVisible()
  await signOut(page)

  await signIn(page, partnerLogin, partnerPassword)
  const returnedRow = page.locator('.partner-content-row').filter({ hasText: title })
  await expect(returnedRow).toContainText('请补充过程说明后再次送审。')
  await returnedRow.getByRole('button', { name: '编辑' }).click()
  await expect(page.getByRole('heading', { name: '编辑候选修订' })).toBeVisible()
  const returnedMarkdown = page.getByLabel('Markdown 正文')
  await expect(returnedMarkdown).toHaveValue(/管理员必须先看到这一段/)
  await returnedMarkdown.fill(`# 完整原文\n\n管理员必须先看到这一段，再作出审核决定。\n\n${revisedSentence}\n\n![本地合成审核证据图](media:${mediaId})`)
  await page.getByRole('button', { name: '保存并送审' }).click()
  await expect(page.getByText(`“${title}”已保存并送入审核队列。`)).toBeVisible()
  await signOut(page)

  await signIn(page, adminLogin, adminPassword)
  await page.goto('/workspace/partners')
  const profile = page.locator('.partner-profile-row').filter({ hasText: '本地合作方（合成）' }).first()
  await profile.getByLabel('公开配额').fill('0')
  await profile.getByLabel('调整原因').fill('验证发布配额阻断提示')
  await profile.getByRole('button', { name: '保存配额' }).click()
  await expect(page.getByText(/公开配额已更新为 0/)).toBeVisible()

  await page.goto(`/workspace/reviews/${contentId}`)
  await expect(page.getByText(revisedSentence)).toBeVisible()
  await page.getByLabel('审核决定').selectOption('APPROVE')
  await page.getByLabel('审核说明').fill('正文、图片和来源说明已核验。')
  await page.getByRole('button', { name: '保存审核决定' }).click()
  await expect(page.getByText(/公开活跃内容已达到配额 \d+ \/ 0/)).toBeVisible()
  await expect(page.getByRole('button', { name: '正式发布' })).toBeDisabled()

  await page.goto('/workspace/partners')
  const profileAfterReview = page.locator('.partner-profile-row').filter({ hasText: '本地合作方（合成）' }).first()
  await profileAfterReview.getByLabel('公开配额').fill('3')
  await profileAfterReview.getByLabel('调整原因').fill('结束配额阻断验收并恢复本地设置')
  await profileAfterReview.getByRole('button', { name: '保存配额' }).click()
  await expect(page.getByText(/公开配额已更新为 3/)).toBeVisible()

  await page.goto(`/workspace/reviews/${contentId}`)
  await expect(page.getByText('发布检查已通过，可以正式发布。')).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '正式发布' }).click()
  await expect(page.getByText('内容已正式发布，可从公开详情页访问。')).toBeVisible()
  const publicContent = await page.request.get(`/api/v1/public/content/${contentId}`)
  expect(publicContent.status()).toBe(200)
  const publicMedia = await page.request.get(`/api/v1/public/media/${mediaId}`)
  expect(publicMedia.status()).toBe(200)

  await page.goto('/workspace/content')
  const publishedRow = page.locator('.admin-content-row').filter({ hasText: title })
  page.once('dialog', (dialog) => dialog.accept())
  await publishedRow.getByRole('button', { name: '隐藏' }).click()
  await expect(page.getByText(new RegExp(`“${title}”当前状态`))).toBeVisible()
  expect((await page.request.get(`/api/v1/public/content/${contentId}`)).status()).toBe(404)
  expect((await page.request.get(`/api/v1/public/media/${mediaId}`)).status()).toBe(404)
})

test('超级管理员停用合作方后已有会话立即失效，并可恢复账号', async ({ page, browser }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop-chromium', '账号生命周期只在桌面项目串行执行。')
  test.skip(!adminLogin || !adminPassword || !partnerLogin || !partnerPassword, '需要显式注入本地合成 E2E 账号。')

  await signIn(page, adminLogin, adminPassword)
  const accountsResponse = await page.request.get('/api/v1/admin/accounts?pageSize=50')
  expect(accountsResponse.ok()).toBeTruthy()
  const accounts = await accountsResponse.json() as { data: { items: { id: string; loginId: string; qualificationStatus: string }[] } }
  const partner = accounts.data.items.find((account) => account.loginId === partnerLogin)
  expect(partner?.qualificationStatus).toBe('ACTIVE')

  const partnerContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL ?? 'http://127.0.0.1:8080' })
  const partnerPage = await partnerContext.newPage()
  let suspended = false
  try {
    await signIn(partnerPage, partnerLogin, partnerPassword)
    const suspendedResponse = await protectedPost(page, `/api/v1/admin/accounts/${partner!.id}/suspend`, { reason: '本地合成账号即时失效验收' })
    expect(suspendedResponse.ok()).toBeTruthy()
    suspended = true

    const staleSession = await partnerPage.request.get('/api/v1/auth/me')
    expect(staleSession.status()).toBe(401)
    await partnerPage.goto('/workspace')
    await expect(partnerPage.getByText('当前没有有效的受控会话。')).toBeVisible()
  } finally {
    if (suspended) {
      const reactivatedResponse = await protectedPost(page, `/api/v1/admin/accounts/${partner!.id}/reactivate`, { reason: '结束本地合成账号即时失效验收并恢复账号' })
      expect(reactivatedResponse.ok()).toBeTruthy()
    }
    await partnerContext.close()
  }
})
