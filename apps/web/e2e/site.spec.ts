import { test, expect } from '@playwright/test'
import { content, id, mockApi } from './fixtures'
const pageErrors = new WeakMap<object, string[]>()
test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  pageErrors.set(page, errors)
  page.on('pageerror', (error) => errors.push(error.message))
})
test.afterEach(async ({ page }) => {
  expect(pageErrors.get(page)).toEqual([])
})
test('真实 v0 只读公开链路及未登录拦截', async ({ page, request }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  const response = await request.get('/api/v1/public/home')
  expect(response.ok()).toBeTruthy()
  const home = (await response.json()).data
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('让好奇心')
  for (const path of [
    '/competitions',
    '/resources',
    '/recruitments',
    '/feedback',
    '/join',
    '/about',
    '/auth/sign-in',
    '/auth/activate',
    '/unknown-path',
  ]) {
    await page.goto(path)
    await expect(page.locator('body')).not.toBeEmpty()
    await expect(page.locator('vite-error-overlay')).toHaveCount(0)
  }
  for (const item of [...home.competitions, ...home.resources, ...home.recruitments]) {
    await page.goto('/content/' + item.id)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(item.title)
    await expect(page.getByText('来源与说明')).toBeVisible()
  }
  await page.goto('/workspace')
  await expect(page.getByRole('link', { name: '前往登录' })).toBeVisible()
  expect(errors).toEqual([])
})
test('主题跨页面、刷新、标签同步，跟随系统与首帧', async ({ page, context }) => {
  await mockApi(page)
  await page.emulateMedia({ colorScheme: 'light' })
  await page.goto('/')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByRole('button', { name: '深色', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.getByRole('link', { name: '关于', exact: true }).click()
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  const second = await context.newPage()
  await mockApi(second)
  await second.goto('/auth/sign-in')
  await second.getByRole('button', { name: '浅色', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  await page.getByRole('button', { name: '跟随系统', exact: true }).click()
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.route('**/src/main.tsx*', (route) => route.abort())
  await page.reload()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await second.close()
})
test('搜索、游标翻页、详情返回与分享', async ({ page, context }) => {
  await mockApi(page)
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  await page.goto('/')
  await page.keyboard.press('Control+K')
  const searchDialog = page.getByRole('dialog', { name: '搜索公开内容' })
  await expect(searchDialog).toBeVisible()
  await searchDialog.getByLabel('搜索竞赛、团队招募与学习资源').fill('隔离')
  await searchDialog.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/\/search\?q=/)
  await expect(page.getByRole('heading', { name: '团队招募', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '学习资源', exact: true })).toBeVisible()
  await page.goto('/competitions')
  await page.getByLabel('搜索竞赛').fill('不存在')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/q=/)
  await expect(page.getByText('没有找到匹配内容')).toBeVisible()
  await page.getByRole('button', { name: '清空搜索' }).click()
  await page.getByRole('link', { name: '下一页' }).click()
  await expect(page).toHaveURL(/cursor=next-test-cursor/)
  await page.getByRole('link', { name: '隔离测试竞赛', exact: true }).click()
  await expect(page.getByRole('navigation', { name: '本文目录' })).toBeVisible()
  await expect(page.getByText('同类竞赛一', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '复制链接', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('链接已复制')
  await page.getByRole('button', { name: '返回浏览目录' }).click()
  await expect(page).toHaveURL(/cursor=next-test-cursor/)
})
test('公开表单三种类型、校验、失败保留和合作申请', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/feedback')
  await page.getByRole('button', { name: '提交审核' }).click()
  expect(state.writes.length).toBe(0)
  for (const label of ['内容线索', '产品反馈', '版权/下线反馈']) {
    await page.getByLabel(label, { exact: true }).check()
    await page.getByLabel('标题', { exact: true }).fill('隔离测试标题')
    await page.getByLabel('内容', { exact: true }).fill('这是模拟内容，不会发送到真实后端。')
    await page.getByRole('button', { name: '提交审核' }).click()
    await expect(page.getByRole('status')).toContainText('已收到')
  }
  expect(state.writes.map((item) => item.body.submissionType)).toEqual(['CONTENT', 'FEEDBACK', 'COPYRIGHT'])
  state.fail = '/public/submissions'
  await page.getByLabel('标题', { exact: true }).fill('保留输入')
  await page.getByLabel('内容', { exact: true }).fill('模拟失败')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByLabel('标题', { exact: true })).toHaveValue('保留输入')
  state.fail = undefined
  await page.goto('/join')
  await page.getByLabel('展示名称', { exact: true }).fill('隔离申请')
  await page.getByLabel('你想维护什么内容？').fill('仅供模拟验证的申请')
  await page.getByRole('button', { name: '提交合作方申请' }).click()
  await expect(page.getByRole('status')).toContainText('申请已进入审核队列')
})
test('HTTP 非安全上下文使用安全随机兼容层，招募别名可直接访问和刷新', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
  })
  const state = await mockApi(page)
  await page.goto('/join')
  await expect(page.getByRole('heading', { name: /一起/ })).toBeVisible()
  await page.reload()
  await page.getByLabel('展示名称', { exact: true }).fill('安全随机测试')
  await page.getByLabel('你想维护什么内容？').fill('验证 HTTP 下仍只使用安全随机源。')
  await page.getByRole('button', { name: '提交合作方申请' }).click()
  await expect(page.getByRole('status')).toContainText('申请已进入审核队列')
  await page.goto('/feedback')
  await page.getByLabel('标题', { exact: true }).fill('安全随机测试')
  await page.getByLabel('内容', { exact: true }).fill('验证公开投稿。')
  await page.getByRole('button', { name: '提交审核' }).click()
  const keys = state.writes
    .filter((write) => write.path.startsWith('/public/'))
    .map((write) => write.headers['idempotency-key'])
  expect(keys).toHaveLength(2)
  expect(
    keys.every((key) => /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(key)),
  ).toBeTruthy()
  await page.goto('/recruitment')
  await expect(page).toHaveURL(/\/recruitments$/)
  await expect(page.getByRole('heading', { name: /团队招募/ })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: /团队招募/ })).toBeVisible()
})
test('错误边界在 SPA 导航后复位，硬刷新可恢复原页面', async ({ page }) => {
  await mockApi(page)
  let broken = true
  await page.route('**/api/v1/public/home', async (route) => {
    if (broken) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: {}, requestId: 'recoverable-test' }),
      })
    } else {
      await route.fallback()
    }
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '暂时无法显示此页面' })).toBeVisible()
  await page.getByRole('link', { name: '返回登录' }).click()
  await expect(page).toHaveURL(/\/auth\/sign-in$/)
  await expect(page.getByRole('heading', { name: /欢迎回来/ })).toBeVisible()

  await page.goto('/')
  await expect(page.getByRole('heading', { name: '暂时无法显示此页面' })).toBeVisible()
  broken = false
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('heading', { name: /让好奇心/ })).toBeVisible()
})
test('缺少全部安全随机能力时明确拒绝写请求', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
    Object.defineProperty(Crypto.prototype, 'getRandomValues', { configurable: true, value: undefined })
  })
  const state = await mockApi(page)
  await page.goto('/join')
  await page.getByLabel('展示名称', { exact: true }).fill('拒绝弱随机')
  await page.getByLabel('你想维护什么内容？').fill('不得使用 Math.random。')
  await page.getByRole('button', { name: '提交合作方申请' }).click()
  await expect(page.getByRole('alert')).toContainText('不支持安全随机数')
  expect(state.writes).toEqual([])
})
test('管理员生命周期与关键操作原因', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
  })
  const state = await mockApi(page)
  await page.goto('/workspace?section=content')
  await page.getByRole('button', { name: '送审', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认送审', exact: true }).click()
  await page.getByRole('link', { name: '查看原文并审核', exact: true }).click()
  await page.getByLabel('审核决定').selectOption('APPROVE')
  await page.getByRole('button', { name: '保存审核决定', exact: true }).click()
  await page.getByRole('button', { name: '执行检查', exact: true }).click()
  await page.getByRole('button', { name: '正式发布', exact: true }).click()
  await page.goto('/workspace?section=content')
  for (const label of ['隐藏', '重新公开', '归档', '恢复']) {
    await page.getByRole('button', { name: label, exact: true }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    if (await dialog.getByLabel('操作原因').count()) await dialog.getByLabel('操作原因').fill('隔离测试原因')
    await dialog.getByRole('button', { name: '确认' + label, exact: true }).click()
    await expect(dialog).toBeHidden()
  }
  expect(state.recordState).toBe('ACTIVE')
  expect(
    state.writes.every((item) => item.headers['idempotency-key'] && item.headers['x-csrf-token']),
  ).toBeTruthy()
  expect(state.writes.find((item) => item.path.includes('/reviews/'))?.headers['if-match']).toBe('7')
})
test('候选修订保留富文本与隐藏字段，冲突不丢输入', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
  })
  const state = await mockApi(page, 'PARTNER')
  await page.goto('/workspace?section=content')
  await page.getByRole('link', { name: '编辑', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/workspace/content/${id}/edit$`))
  await page.reload()
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(editor).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await editor.getByLabel('标题', { exact: true }).fill('修改后的标题')
  await editor
    .getByLabel('选择图片')
    .setInputFiles({ name: 'test.png', mimeType: 'image/png', buffer: Buffer.from('not-a-real-png') })
  await editor.getByLabel('图片说明').fill('隔离图片说明')
  await editor.getByRole('button', { name: '上传并插入' }).click()
  await expect(editor.getByLabel('Markdown 原文')).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  state.conflict = true
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  await expect(editor.getByRole('alert')).toContainText('已有新版本')
  await expect(editor.getByLabel('标题', { exact: true })).toHaveValue('修改后的标题')
  const write = state.writes.at(-1)!
  expect(write.headers['if-match']).toBe('7')
  expect(write.body.markdownSource).toContain(content.markdownSource)
  expect(write.body.markdownSource).toContain('media:01HZX9Z8NQ2J3K4M5P6R7S8T9U')
  expect(write.body.details).toEqual(content.details)
  expect(write.body.customMetadata).toBe('keep-me')
  state.conflict = false
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace\?section=content$/)
  await expect(page.getByRole('status').filter({ hasText: '已保存' })).toBeVisible()
  await page.reload()
  await expect(page).toHaveURL(/section=content/)
  await expect(page.getByRole('heading', { name: '把想法，认真整理' })).toBeVisible()
})
test('独立编辑页桌面分栏、小屏单栏与大正文输入保持流畅', async ({ page }) => {
  test.setTimeout(60000)
  await mockApi(page, 'PARTNER')
  for (const viewport of [
    { width: 1440, height: 1000 },
    { width: 3828, height: 1962 },
  ]) {
    await page.setViewportSize(viewport)
    await page.goto(`/workspace/content/${id}/edit`)
    const editor = page.getByRole('form', { name: '内容编辑器' })
    await expect(editor).toBeVisible()
    await expect(editor.getByRole('button', { name: '分屏' })).toHaveAttribute('aria-pressed', 'true')
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
    ).toBeTruthy()
    const columns = await editor
      .locator('.markdown-workbench')
      .evaluate((node) => getComputedStyle(node).gridTemplateColumns.split(' ').filter(Boolean).length)
    expect(columns).toBe(2)
  }

  const textarea = page.getByLabel('Markdown 原文')
  const large = '# 性能验证\n\n' + '连续输入保持流畅。'.repeat(15000)
  await textarea.fill(large)
  const sampleTyping = () =>
    textarea.evaluate(async (input) => {
      const samples: number[] = []
      const longTasks: number[] = []
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!
      const frame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
      const baseline = input.value
      for (let warm = 0; warm < 12; warm += 1) {
        setter.call(input, input.value + '预')
        input.dispatchEvent(new Event('input', { bubbles: true }))
        await frame()
      }
      setter.call(input, baseline)
      input.dispatchEvent(new Event('input', { bubbles: true }))
      await frame()
      await frame()
      const observer =
        'PerformanceObserver' in window
          ? new PerformanceObserver((list) => {
              longTasks.push(...list.getEntries().map((entry) => entry.duration))
            })
          : null
      try {
        observer?.observe({ type: 'longtask', buffered: true })
      } catch {
        /* browser may omit longtask */
      }
      for (let index = 0; index < 24; index += 1) {
        const started = performance.now()
        setter.call(input, input.value + '字')
        input.dispatchEvent(new Event('input', { bubbles: true }))
        await frame()
        samples.push(performance.now() - started)
      }
      observer?.disconnect()
      setter.call(input, baseline)
      input.dispatchEvent(new Event('input', { bubbles: true }))
      await frame()
      return { samples: samples.sort((a, b) => a - b), longTasks }
    })
  let performance = await sampleTyping()
  if (performance.samples[Math.floor(performance.samples.length * 0.95)] >= 50)
    performance = await sampleTyping()
  const p95 = performance.samples[Math.floor(performance.samples.length * 0.95)]
  await expect(textarea).toHaveValue(large)
  expect(p95).toBeLessThan(50)
  expect(performance.longTasks.filter((duration) => duration > 100)).toEqual([])
  await page.waitForTimeout(200)
  await expect(page.getByLabel('前端预览')).toContainText('性能验证')

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  const mobileEditor = page.getByRole('form', { name: '内容编辑器' })
  await expect(mobileEditor.getByRole('button', { name: '分屏' })).toHaveCount(0)
  await expect(mobileEditor.getByLabel('Markdown 原文')).toBeVisible()
  await mobileEditor.getByRole('button', { name: '阅读预览' }).click()
  await expect(mobileEditor.getByLabel('前端预览')).toBeVisible()
  await expect(mobileEditor.getByLabel('Markdown 原文')).toHaveCount(0)
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
  ).toBeTruthy()
})
test('管理员新建沿用现有接口契约', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace/content/new')
  await page.reload()
  let editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(editor).toBeVisible()
  await editor.getByLabel('标题', { exact: true }).fill('管理员新草稿')
  await editor.getByLabel('中文导读').fill('隔离测试导读')
  await editor.getByLabel('作者展示名').fill('隔离作者')
  await editor.getByLabel('官方规则链接').fill('https://example.com/rules')
  await editor.getByLabel('来源名称').fill('隔离来源')
  await editor.getByLabel('官方或原始来源 URL').fill('https://example.com')
  await editor.getByRole('button', { name: '保存草稿', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace\?section=content$/)
  expect(state.writes.some((write) => write.path === '/admin/content-items')).toBeTruthy()
})
test('合作方保存并送审沿用现有接口契约', async ({ page }) => {
  const state = await mockApi(page, 'PARTNER')
  await page.goto('/workspace/content/new')
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await editor.getByLabel('标题', { exact: true }).fill('合作方新草稿')
  await editor.getByLabel('中文导读').fill('隔离测试导读')
  await editor.getByLabel('作者展示名').fill('隔离作者')
  await editor.getByLabel('官方规则链接').fill('https://example.com/rules')
  await editor.getByLabel('来源名称').fill('隔离来源')
  await editor.getByLabel('官方或原始来源 URL').fill('https://example.com')
  await editor.getByRole('button', { name: '保存并送审', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace\?section=content$/)
  expect(state.writes.some((write) => write.path === '/partner/content-items')).toBeTruthy()
  expect(state.writes.some((write) => write.path.endsWith('/submit-review'))).toBeTruthy()
})
test('PENDING_REVIEW 申请失败保留输入，批准后刷新并只在当前页面显示凭据', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace?section=applications')
  await expect(page.getByLabel('状态')).toHaveValue('PENDING_REVIEW')
  await expect(page.getByRole('button', { name: '拒绝', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '批准并生成凭据' })).toBeVisible()
  await page.getByLabel('受控登录名').fill('isolated-partner')
  await page.getByLabel('审核原因').fill('隔离审批原因')
  state.fail = '/admin/partner-applications/application-1/approve'
  await page.getByRole('button', { name: '批准并生成凭据' }).click()
  await expect(page.getByRole('alert')).toContainText('模拟连接失败')
  await expect(page.getByLabel('受控登录名')).toHaveValue('isolated-partner')
  await expect(page.getByLabel('审核原因')).toHaveValue('隔离审批原因')
  state.fail = undefined
  await page.getByRole('button', { name: '批准并生成凭据' }).click()
  await expect(page.locator('.token-card')).toContainText('isolated-token-not-real')
  await expect(page.getByRole('status').filter({ hasText: '申请处理结果已保存' })).toBeVisible()
  await expect(page.locator('.application-row')).toHaveCount(0)
  expect(state.partnerApplicationState).toBe('APPROVED')
  expect(
    state.writes.find((write) => write.path.endsWith('/partner-applications/application-1/approve'))?.body,
  ).toEqual({ loginId: 'isolated-partner', reason: '隔离审批原因' })
  await page.reload()
  await expect(page.locator('.token-card')).toHaveCount(0)
  await page.getByRole('link', { name: '合作方与归属' }).click()
  await page.getByLabel('公开配额', { exact: true }).fill('3')
  await page.getByLabel('调整原因').fill('模拟配额调整')
  await page.getByRole('button', { name: '保存配额' }).click()
  await page.getByRole('button', { name: '确认调整配额' }).click()
  await expect(page.getByText('当前公开配额 3', { exact: false })).toBeVisible()
  expect(state.quota).toBe(3)
  await page.getByLabel('待转交内容 ID').fill(id)
  await page.getByLabel('转交原因').fill('模拟归属转交')
  await page.getByRole('button', { name: '转交内容', exact: true }).click()
  await page.getByRole('button', { name: '确认转交内容' }).click()
  await expect(page.getByRole('status').filter({ hasText: '内容归属已更新' })).toBeVisible()
})

test('合作方申请筛选使用后端状态，拒绝后刷新列表', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace?section=applications')
  const status = page.getByLabel('状态')
  await expect(status).toHaveValue('PENDING_REVIEW')
  expect(
    await status
      .locator('option')
      .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value)),
  ).toEqual(['PENDING_REVIEW', 'APPROVED', 'REJECTED', 'ALL'])
  await page.getByLabel('审核原因').fill('不符合当前合作范围')
  await page.getByRole('button', { name: '拒绝', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '申请处理结果已保存' })).toBeVisible()
  await expect(page.locator('.application-row')).toHaveCount(0)
  expect(state.partnerApplicationState).toBe('REJECTED')
  expect(
    state.writes.find((write) => write.path.endsWith('/partner-applications/application-1/decision'))?.body,
  ).toEqual({ decision: 'REJECT', reason: '不符合当前合作范围' })
  await status.selectOption('ALL')
  await page.getByRole('button', { name: '应用筛选' }).click()
  await expect(page.locator('.application-row')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '批准并生成凭据' })).toHaveCount(0)
})

test('合作方申请审核表单在桌面和移动端均无横向溢出', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/workspace?section=applications')
  const row = page.locator('.application-row')
  await expect(row).toBeVisible()
  expect(
    await row.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(' ').length),
  ).toBeGreaterThan(1)
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
  ).toBe(true)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await expect(page.getByLabel('受控登录名')).toBeVisible()
  expect(
    await row.evaluate((element) => getComputedStyle(element).gridTemplateColumns.split(' ').length),
  ).toBe(1)
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth),
  ).toBe(true)
})

test('动态模块、公开输入、超级管理员账号与审计', async ({ page }) => {
  const state = await mockApi(page, 'SUPER_ADMIN')
  await page.goto('/resources')
  await page.getByRole('button', { name: '文章 / 经验', exact: true }).click()
  await expect(page).toHaveURL(/module=resource.article/)
  await expect(page.getByLabel('筛选字段')).toBeVisible()
  await page.goto('/workspace?section=submissions')
  await page.getByLabel('处理原因').fill('隔离处理原因')
  await page.getByRole('button', { name: '通过', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '处理结果已保存' })).toBeVisible()
  await page.getByRole('link', { name: '账号治理' }).click()
  await expect(page.getByRole('heading', { name: '账号资格治理' })).toBeVisible()
  const accountReason = page.getByLabel('原因')
  await accountReason.fill('隔离停用原因')
  await expect(accountReason).toHaveValue('隔离停用原因')
  await page.getByRole('button', { name: '停用', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '账号资格状态已更新' })).toBeVisible()
  await page.getByRole('link', { name: '审计记录' }).click()
  await expect(page.getByText('CONTENT_UPDATED')).toBeVisible()
  expect(state.writes.some((entry) => entry.path.includes('/admin/submissions/'))).toBeTruthy()
  expect(state.writes.some((entry) => entry.path.includes('/admin/accounts/'))).toBeTruthy()
})
test('五种宽度、双主题、抽屉焦点与低动效', async ({ page }) => {
  test.setTimeout(60000)
  const state = await mockApi(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  for (const width of [1440, 1280, 1024, 768, 390]) {
    await page.setViewportSize({ width, height: 900 })
    for (const [mode, label] of [
      ['light', '浅色'],
      ['dark', '深色'],
    ]) {
      await page.goto('/auth/sign-in')
      await page.getByRole('button', { name: label, exact: true }).click()
      for (const route of [
        '/',
        '/resources',
        '/content/' + id,
        '/feedback',
        '/join',
        '/about',
        '/auth/sign-in',
        '/auth/activate',
        '/workspace?section=content',
        '/missing-page',
      ]) {
        await page.goto(route)
        await expect(page.locator('body')).not.toBeEmpty()
        await expect(page.locator('html')).toHaveAttribute('data-theme', mode)
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        ).toBeTruthy()
      }
    }
    await page.goto('/')
    if (width < 1180) {
      const trigger = page.getByRole('button', { name: '打开菜单' })
      await trigger.click()
      const dialog = page.getByRole('dialog', { name: '导航菜单' })
      await expect(dialog).toBeVisible()
      await dialog.getByRole('button', { name: '深色', exact: true }).click()
      await page.keyboard.press('Escape')
      await expect(dialog).toHaveCount(0)
      await expect(trigger).toBeFocused()
    } else {
      await page.getByRole('button', { name: '深色', exact: true }).click()
      await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible()
    }
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await page.screenshot({ path: `test-results/home-dark-${width}.png`, fullPage: true })
  }
  state.fail = '/public/home'
  await page.goto('/')
  await expect(page.getByRole('alert')).toContainText('模拟连接失败')
  await expect(page.getByText('下一份机会，正在路上')).toHaveCount(0)
})

test('宽屏首页密度、中文字号与页脚网格', async ({ page }) => {
  await mockApi(page)
  for (const viewport of [
    { width: 1920, height: 1080 },
    { width: 2552, height: 1308 },
  ]) {
    await page.setViewportSize(viewport)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: '本期值得关注' })).toBeVisible()
    await expect(page.locator('.home-edition-line')).toHaveCount(0)
    const result = await page.evaluate(() => {
      const discover = document.querySelector('#discover')?.getBoundingClientRect()
      const footer = document.querySelector('.expanded-footer')?.getBoundingClientRect()
      const footerLinks = document.querySelector('.footer-links')
      const columnTops = [...document.querySelectorAll('.footer-column')].map((node) =>
        Math.round(node.getBoundingClientRect().top),
      )
      const violations: string[] = []
      for (const root of document.querySelectorAll('.public-main main, .public-footer')) {
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
        let node = walker.nextNode()
        while (node) {
          const text = node.nodeValue?.trim() ?? ''
          const parent = node.parentElement
          if (parent && /[\u3400-\u9fff]/.test(text) && parent.getClientRects().length) {
            const size = Number(getComputedStyle(parent).fontSize.replace('px', ''))
            if (size < 13) violations.push(`${text.slice(0, 18)}:${size}px`)
          }
          node = walker.nextNode()
        }
      }
      return {
        discoverTop: discover?.top,
        footerHeight: footer?.height,
        footerDisplay: footerLinks ? getComputedStyle(footerLinks).display : '',
        columnTops,
        violations,
      }
    })
    expect(result.discoverTop).toBeLessThan(760)
    expect(result.footerHeight).toBeLessThan(280)
    expect(result.footerDisplay).toBe('grid')
    expect(Math.max(...result.columnTops) - Math.min(...result.columnTops)).toBeLessThanOrEqual(2)
    expect(result.violations).toEqual([])
  }
  await page.goto('/competitions')
  await expect(page.locator('.public-disclaimer')).toBeVisible()
  expect(
    await page.locator('.public-disclaimer').evaluate((node) => node.getBoundingClientRect().height),
  ).toBeLessThan(64)
})
