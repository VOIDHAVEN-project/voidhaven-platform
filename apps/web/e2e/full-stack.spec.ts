import { expect, test, type Page } from '@playwright/test'

const enabled = process.env.VOIDHAVEN_E2E_REAL === '1'
const adminLogin = process.env.VOIDHAVEN_E2E_ADMIN_LOGIN || 'local-admin'
const adminPassword = process.env.VOIDHAVEN_E2E_ADMIN_PASSWORD || 'change-me-before-first-local-run'

test.skip(!enabled, '仅在隔离的真实 API、MySQL 和 MinIO 环境中运行')

async function signIn(page: Page, loginId: string, password: string) {
  await page.goto('/auth/sign-in')
  await page.getByLabel('账号').fill(loginId)
  await page.locator('input[aria-label="密码"]').fill(password)
  await page.getByRole('button', { name: '进入工作台' }).click()
  await expect(page).toHaveURL(/\/workspace/)
}

async function readReceipt(page: Page) {
  const values = await page.locator('.token-card code').allTextContents()
  expect(values).toHaveLength(2)
  return { id: values[0].trim(), queryCode: values[1].trim() }
}

test('真实产品闭环：回执、合作激活、社群、招募审核、核验、待办与人工密码恢复', async ({
  page,
  browser,
  request,
}) => {
  test.setTimeout(180_000)
  await expect.poll(async () => (await request.get('/healthz')).status(), { timeout: 30_000 }).toBe(200)
  await expect
    .poll(async () => (await request.get('/api/v1/public/capabilities')).status(), { timeout: 30_000 })
    .toBe(200)
  const suffix = Date.now().toString().slice(-9)
  const submissionTitle = `端到端反馈-${suffix}`
  const partnerName = `端到端合作方-${suffix}`
  const partnerLogin = `partner.${suffix}`
  const activationPassword = `Partner-Activate-${suffix}!`
  const changedPassword = `Partner-Changed-${suffix}!`
  const resetPassword = `Partner-Reset-${suffix}!`
  const communityName = `端到端社群-${suffix}`
  const actionLabel = `端到端报名-${suffix}`
  const qrLabel = `端到端二维码-${suffix}`
  const qrPng = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
  )

  await page.goto('/')
  await expect(page.getByRole('link', { name: '申请成为合作方' })).toHaveAttribute('href', '/partners/apply')

  await page.goto('/submit')
  await page.getByRole('radio', { name: '产品反馈' }).check()
  await page.getByLabel('标题').fill(submissionTitle)
  await page
    .getByRole('textbox', { name: '内容', exact: true })
    .fill('这是隔离环境中的虚构端到端反馈，不包含真实个人信息。')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByText('已收到。请立即保存')).toBeVisible()
  const submission = await readReceipt(page)

  await page.goto('/status')
  await page.getByLabel('回执号').fill(submission.id)
  await page.getByLabel('查询码').fill(submission.queryCode)
  await page.getByRole('button', { name: '查询进度' }).click()
  await expect(page.getByRole('heading', { name: 'RECEIVED' })).toBeVisible()

  await page.goto('/partners/apply')
  await page.getByLabel('展示名称').fill(partnerName)
  await page.getByLabel('你想维护什么内容？').fill('仅用于隔离端到端验收的虚构内容维护申请。')
  await page.getByLabel('联系方式').fill(`test-${suffix}@example.org`)
  await page.getByRole('button', { name: '提交合作方申请' }).click()
  await expect(page.getByText('申请已进入审核队列')).toBeVisible()
  const partnerApplication = await readReceipt(page)

  await signIn(page, adminLogin, adminPassword)
  await page.goto('/workspace?section=applications')
  const applicationRow = page.locator('.application-row').filter({ hasText: partnerName })
  await expect(applicationRow).toBeVisible()
  await applicationRow.getByLabel('受控登录名').fill(partnerLogin)
  await applicationRow.getByLabel('审核原因').fill('隔离端到端验收批准')
  await applicationRow.getByRole('button', { name: '批准并生成凭据' }).click()
  const activationCard = page.locator('.token-card').filter({ hasText: partnerLogin })
  await expect(activationCard).toBeVisible()
  const activationUrl = (await activationCard.locator('code').textContent())?.trim()
  expect(activationUrl).toMatch(/\/auth\/activate\?token=/)
  await activationCard.getByRole('button', { name: '确认已人工交付' }).click()
  await expect(activationCard).toBeHidden()

  await page.goto(activationUrl!)
  await page.locator('input[name="password"]').fill(activationPassword)
  await page.locator('input[name="confirm"]').fill(activationPassword)
  await page.getByRole('button', { name: '激活账号' }).click()
  await expect(page.getByText(`账号 ${partnerLogin} 已激活`)).toBeVisible()

  await page.goto('/status')
  await page.getByLabel('事项类型').selectOption('PARTNER_APPLICATION')
  await page.getByLabel('回执号').fill(partnerApplication.id)
  await page.getByLabel('查询码').fill(partnerApplication.queryCode)
  await page.getByRole('button', { name: '查询进度' }).click()
  await expect(page.getByRole('heading', { name: 'ACTIVATED' })).toBeVisible()

  await page.goto('/workspace?section=community')
  await expect(page.getByRole('heading', { name: '社群入口与邀请码' })).toBeVisible()
  const inviteForm = page.locator('form.panel').filter({ hasText: '创建批次共享邀请码' })
  await inviteForm.getByLabel('批次名称').fill(`端到端批次-${suffix}`)
  await inviteForm.getByRole('button', { name: '创建并显示一次性邀请码' }).click()
  const inviteCard = page.locator('.token-card').filter({ hasText: `端到端批次-${suffix}` })
  await expect(inviteCard).toBeVisible()
  const inviteCode = (await inviteCard.locator('code').textContent())?.trim()
  expect(inviteCode?.length).toBeGreaterThan(20)

  const entryForm = page.locator('form.panel').filter({ hasText: '新增社群入口' })
  await entryForm.getByLabel('名称').fill(communityName)
  await entryForm.getByLabel('入口内容').fill('https://github.com/voidhaven')
  await entryForm.getByText('设为主入口').click()
  await entryForm.getByRole('button', { name: '创建入口' }).click()
  await expect(page.getByText('社群入口已创建。')).toBeVisible()

  await page.goto('/join')
  await page.getByLabel('邀请码').fill(inviteCode!)
  await page.getByRole('button', { name: '查看社群入口' }).click()
  const communityEntry = page.locator('.community-entry').filter({ hasText: communityName })
  await expect(communityEntry).toBeVisible()
  await expect(communityEntry.getByRole('link', { name: '打开入口' })).toHaveAttribute(
    'href',
    'https://github.com/voidhaven',
  )

  await page.goto('/workspace?section=content')
  const recruitmentRow = page
    .getByRole('heading', { name: '数据可视化小组招募（示例）' })
    .locator('xpath=ancestor::article')
  const editHref = await recruitmentRow.getByRole('link', { name: '编辑' }).getAttribute('href')
  expect(editHref).toMatch(/\/workspace\/content\/.+\/edit/)
  const contentId = editHref!.split('/')[3]
  await recruitmentRow.locator('.verification-control').getByText('来源核验').click()
  const verificationForm = recruitmentRow.locator('.verification-form')
  await verificationForm.getByLabel('结果').selectOption('VALID')
  await verificationForm.getByLabel('下次复核日期').fill('2030-12-31')
  await verificationForm.getByRole('button', { name: '保存核验结果' }).click()
  await expect(verificationForm.getByText('核验结果已保存')).toBeVisible()
  await page.goto(editHref!)
  const actions = page.locator('.action-editor-panel')
  await expect(actions.getByRole('heading', { name: '招募行动入口' })).toBeVisible()
  await actions.getByRole('button', { name: '添加入口' }).click()
  const actionRow = actions.locator('.action-editor-row').last()
  await actionRow.getByLabel('展示名称').fill(actionLabel)
  await actionRow.getByLabel('访问级别').selectOption('PUBLIC')
  await actionRow.getByLabel('入口内容').fill('https://github.com/voidhaven')
  await actions.getByRole('button', { name: '添加入口' }).click()
  const qrRow = actions.locator('.action-editor-row').last()
  await qrRow.getByLabel('展示形式').selectOption('QR_CODE')
  await qrRow.getByLabel('展示名称').fill(qrLabel)
  await qrRow.getByLabel('访问级别').selectOption('PUBLIC')
  await qrRow.getByText('我确认该联系方式或二维码将对所有访客公开').check()
  await qrRow.getByLabel('二维码图片').setInputFiles({
    name: 'qr.png',
    mimeType: 'image/png',
    buffer: qrPng,
  })
  const uploadResponse = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/media/action-uploads') &&
      response.request().method() === 'POST' &&
      response.ok(),
  )
  await actions.getByRole('button', { name: '保存行动入口' }).click()
  const uploaded = await (await uploadResponse).json()
  const qrId = uploaded.data.id as string
  await expect(actions.getByText('已创建候选版本，当前线上入口尚未改变')).toBeVisible()
  const editable = await page.request.get(`/api/v1/admin/content-items/${contentId}`)
  expect(editable.ok()).toBeTruthy()
  const editableBody = await editable.json()
  expect((editableBody.data.media ?? []).map((item: { id: string }) => item.id)).not.toContain(qrId)

  await page.goto(`/content/${contentId}`)
  await expect(page.locator('.recruitment-actions a[href="https://github.com/voidhaven"]')).toHaveCount(0)
  await expect(page.getByRole('img', { name: new RegExp(qrLabel) })).toHaveCount(0)

  await page.goto('/workspace?section=content')
  const candidateRow = page.locator('.data-row').filter({ hasText: '数据可视化小组招募（示例）' })
  await candidateRow.getByRole('button', { name: '送审', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认送审' }).click()
  await candidateRow.getByRole('link', { name: '查看原文并审核' }).click()
  await page.getByLabel('审核决定').selectOption('APPROVE')
  await page.getByRole('button', { name: '保存审核决定', exact: true }).click()
  await page.getByRole('button', { name: '执行检查', exact: true }).click()
  await page.getByRole('button', { name: '正式发布', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '内容已公开发布' })).toBeVisible()

  await page.goto(`/content/${contentId}`)
  await expect(page.locator('.recruitment-actions a[href="https://github.com/voidhaven"]')).toBeVisible()
  await expect(page.getByRole('img', { name: new RegExp(qrLabel) })).toHaveAttribute(
    'src',
    new RegExp(`/api/v1/public/media/${qrId}$`),
  )
  await expect(page.getByText(/最近来源核验/)).toBeVisible()
  await expect(page.getByText(/有效/)).toBeVisible()

  await page.goto('/workspace')
  await expect(page.getByText('待处理投稿与反馈')).toBeVisible()

  const partnerContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL })
  const partnerPage = await partnerContext.newPage()
  await signIn(partnerPage, partnerLogin, activationPassword)
  await partnerPage.goto('/workspace?section=security')
  await partnerPage.getByLabel('当前密码').fill(activationPassword)
  await partnerPage.locator('input[name="newPassword"]').fill(changedPassword)
  await partnerPage.locator('input[name="confirm"]').fill(changedPassword)
  await partnerPage.getByRole('button', { name: '修改密码并注销全部会话' }).click()
  await expect(partnerPage).toHaveURL(/\/auth\/sign-in/)
  await signIn(partnerPage, partnerLogin, changedPassword)

  await page.goto('/workspace?section=accounts')
  const accountRow = page.locator('.data-row').filter({ hasText: partnerLogin })
  await expect(accountRow).toBeVisible()
  await accountRow.getByLabel('原因').fill('隔离端到端密码重置验收')
  await accountRow.getByRole('button', { name: '签发密码重置' }).click()
  const resetCard = page.locator('.token-card').filter({ hasText: partnerLogin })
  await expect(resetCard).toBeVisible()
  const resetToken = (await resetCard.locator('code').textContent())?.trim()
  expect(resetToken?.length).toBeGreaterThan(20)
  await resetCard.getByRole('button', { name: '确认已人工交付' }).click()

  const resetContext = await browser.newContext({ baseURL: process.env.VOIDHAVEN_E2E_BASE_URL })
  const resetPage = await resetContext.newPage()
  await resetPage.goto('/auth/reset-password')
  await resetPage.getByLabel('重置凭据').fill(resetToken!)
  await resetPage.locator('input[name="newPassword"]').fill(resetPassword)
  await resetPage.locator('input[name="confirm"]').fill(resetPassword)
  await resetPage.getByRole('button', { name: '重置密码' }).click()
  await expect(resetPage.getByText(`账号 ${partnerLogin} 的密码已重置`)).toBeVisible()
  await signIn(resetPage, partnerLogin, resetPassword)
  await resetContext.close()
  await partnerContext.close()
})
