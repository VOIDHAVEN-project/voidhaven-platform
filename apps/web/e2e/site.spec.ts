import { test, expect } from '@playwright/test'
import { content, createdContentId, id, mockApi } from './fixtures'
const pageErrors = new WeakMap<object, string[]>()
test.beforeEach(async ({ page }) => {
  const errors: string[] = []
  pageErrors.set(page, errors)
  page.on('pageerror', (error) => errors.push(error.message))
})
test.afterEach(async ({ page }) => {
  expect(pageErrors.get(page)).toEqual([])
})
function uniqueHomeItems(home: {
  focus?: { id: string; title: string }[]
  competitions?: { id: string; title: string }[]
  recruitments?: { id: string; title: string }[]
  resources?: { id: string; title: string }[]
}) {
  const seen = new Set<string>()
  return [
    ...(home.focus ?? []),
    ...(home.competitions ?? []),
    ...(home.recruitments ?? []),
    ...(home.resources ?? []),
  ].filter((item) => {
    if (seen.has(item.id)) return false
    seen.add(item.id)
    return true
  })
}
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
    '/join/community',
    '/about',
    '/privacy',
    '/auth/sign-in',
    '/auth/activate',
    '/unknown-path',
  ]) {
    await page.goto(path)
    await expect(page.locator('body')).not.toBeEmpty()
    await expect(page.locator('vite-error-overlay')).toHaveCount(0)
  }
  for (const item of uniqueHomeItems(home)) {
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
  await searchDialog.getByLabel('搜索竞赛、团队招募与资源与分享').fill('隔离')
  await searchDialog.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/\/search\?q=/)
  await expect(page.getByRole('heading', { name: '团队招募', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '资源与分享', exact: true })).toBeVisible()
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
  await expect(page.getByRole('heading', { name: '关键信息' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '相关团队招募' })).toBeVisible()
  await expect(page.getByRole('link', { name: '关联招募一' })).toBeVisible()
  await expect(page.getByText('同类竞赛一', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /阅读官方规则/ }).first()).toBeVisible()
  await page.getByRole('button', { name: '复制链接', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('链接已复制')
  await page.getByRole('button', { name: '返回浏览目录' }).click()
  await expect(page).toHaveURL(/cursor=next-test-cursor/)
})
test('详情页结束状态隐藏行动入口，桌面不出现左侧关联栏', async ({ page }) => {
  await mockApi(page)
  await page.goto('/content/PPPPPPPPPPPPPPPPPPPPPPPPPP')
  await expect(page.getByRole('heading', { name: '已暂停招募' })).toBeVisible()
  await expect(page.getByText('本次招募已暂停，联系与社群入口暂不可用。')).toBeVisible()
  await expect(page.getByRole('button', { name: '验证并显示' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '打开入口' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '所属竞赛' })).toBeVisible()
  await expect(page.locator('.related-rail')).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 840 })
  await expect(page.getByRole('heading', { name: '关键信息' })).toBeVisible()
  await expect(page.getByRole('button', { name: '验证并显示' })).toHaveCount(0)
})
test('公开表单三种类型、校验、失败保留和合作申请', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/feedback')
  await expect(page).toHaveURL(/\/submit$/)
  await page.getByRole('button', { name: '提交审核' }).click()
  expect(state.writes.length).toBe(0)
  for (const label of ['内容线索', '产品反馈']) {
    await page.getByLabel(label, { exact: true }).check()
    await page.getByLabel('标题', { exact: true }).fill('隔离测试标题')
    await page.getByLabel('内容', { exact: true }).fill('这是模拟内容，不会发送到真实后端。')
    await page.getByRole('button', { name: '提交审核' }).click()
    await expect(page.getByRole('status')).toContainText('已收到')
    await expect(page.getByLabel('事项查询凭据')).toContainText('isolated-query-code-value')
  }
  await page.getByLabel('版权/下线反馈', { exact: true }).check()
  await page.getByLabel('标题', { exact: true }).fill('版权请求')
  await page.getByLabel('内容', { exact: true }).fill('说明权利主张。')
  await expect(page.getByLabel(/联系方式/)).toHaveAttribute('required', '')
  await page.getByLabel(/联系方式/).fill('wechat:isolated')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByRole('status')).toContainText('已收到')
  expect(
    state.writes
      .filter((item) => item.path === '/public/submissions')
      .map((item) => item.body.submissionType),
  ).toEqual(['CONTENT', 'FEEDBACK', 'COPYRIGHT'])
  state.fail = '/public/submissions'
  await page.getByLabel('标题', { exact: true }).fill('保留输入')
  await page.getByLabel('内容', { exact: true }).fill('模拟失败')
  await page.getByLabel(/联系方式/).fill('wechat:isolated')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByLabel('标题', { exact: true })).toHaveValue('保留输入')
  state.fail = undefined
  await page.goto('/partners/apply')
  await page.getByLabel('展示名称', { exact: true }).fill('隔离申请')
  await page.getByLabel('你想维护什么内容？').fill('仅供模拟验证的申请')
  await page.getByLabel(/联系方式/).fill('mail:isolated@example.com')
  await page.getByRole('button', { name: '提交合作申请' }).click()
  await expect(page.getByRole('status')).toContainText('申请已进入审核队列')
  await expect(page.getByLabel('事项查询凭据')).toContainText('01JRECEIPT0000000000000001')
})
test('HTTP 非安全上下文使用安全随机兼容层，招募别名可直接访问和刷新', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'randomUUID', { configurable: true, value: undefined })
  })
  const state = await mockApi(page)
  await page.goto('/partners/apply')
  await expect(page.getByRole('heading', { name: /一起/ })).toBeVisible()
  await page.reload()
  await page.getByLabel('展示名称', { exact: true }).fill('安全随机测试')
  await page.getByLabel('你想维护什么内容？').fill('验证 HTTP 下仍只使用安全随机源。')
  await page.getByLabel(/联系方式/).fill('mail:isolated@example.com')
  await page.getByRole('button', { name: '提交合作申请' }).click()
  await expect(page.getByRole('status')).toContainText('申请已进入审核队列')
  await page.goto('/submit')
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
  await page.goto('/partners/apply')
  await page.getByLabel('展示名称', { exact: true }).fill('拒绝弱随机')
  await page.getByLabel('你想维护什么内容？').fill('不得使用 Math.random。')
  await page.getByLabel(/联系方式/).fill('mail:isolated@example.com')
  await page.getByRole('button', { name: '提交合作申请' }).click()
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
  await expect(page.getByRole('status').filter({ hasText: '内容已公开发布' })).toBeVisible()
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
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await editor.getByLabel('标题', { exact: true }).fill('修改后的标题')
  await page.getByRole('tab', { name: '正文与媒体' }).click()
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
  if (performance.samples[Math.floor(performance.samples.length * 0.95)] >= 80)
    performance = await sampleTyping()
  const p95 = performance.samples[Math.floor(performance.samples.length * 0.95)]
  await expect(textarea).toHaveValue(large)
  expect(p95).toBeLessThan(80)
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
test('编辑器双工作区切换、校验定位、本地草稿与多标签提示', async ({ page, context }) => {
  await mockApi(page, 'PARTNER')
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(editor).toBeVisible()
  const tabs = page.getByRole('tablist', { name: '编辑工作区' })
  await expect(tabs.getByRole('tab', { name: '正文与媒体' })).toHaveAttribute('aria-selected', 'true')
  await expect(editor.getByLabel('Markdown 原文')).toBeVisible()
  await tabs.getByRole('tab', { name: '结构化信息' }).click()
  await expect(page.getByRole('heading', { name: '基础信息' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '业务信息' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '来源与发布' })).toBeVisible()
  await expect(editor.getByLabel('官方规则链接')).toBeVisible()
  await editor.getByLabel('标题', { exact: true }).fill(' ')
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  await expect(editor.getByRole('alert')).toContainText('请填写标题。')
  await expect(editor.getByLabel('标题', { exact: true })).toBeFocused()
  await editor.getByLabel('标题', { exact: true }).fill('本地草稿标题')
  await expect
    .poll(async () =>
      page.evaluate((key) => {
        return new Promise<string>((resolve) => {
          const request = indexedDB.open('voidhaven-editor-drafts', 1)
          request.onupgradeneeded = () => {
            if (!request.result.objectStoreNames.contains('drafts'))
              request.result.createObjectStore('drafts')
          }
          request.onsuccess = () => {
            const db = request.result
            if (!db.objectStoreNames.contains('drafts')) {
              db.close()
              resolve('')
              return
            }
            const get = db.transaction('drafts').objectStore('drafts').get(key)
            get.onsuccess = () => {
              const value = get.result as { draft?: { title?: string } } | undefined
              resolve(value?.draft?.title ?? '')
              db.close()
            }
            get.onerror = () => {
              db.close()
              resolve('')
            }
          }
          request.onerror = () => resolve('')
        })
      }, `item:${id}`),
    )
    .toBe('本地草稿标题')
  await expect(page.getByRole('status').filter({ hasText: '本地草稿已保存' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('region', { name: '本地草稿' })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '恢复' }).click()
  await expect(editor.getByLabel('标题', { exact: true })).toHaveValue('本地草稿标题')
  const other = await context.newPage()
  await mockApi(other, 'PARTNER')
  await other.goto(`/workspace/content/${id}/edit`)
  await expect(page.getByText('另一个标签页正在编辑同一内容')).toBeVisible({ timeout: 8000 })
  await other.close()
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace\?section=content$/)
  await page.goto(`/workspace/content/${id}/edit`)
  await expect(page.getByRole('region', { name: '本地草稿' })).toHaveCount(0)
})
test('编辑器缩进、全屏预览与媒体管理', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(
    page.getByRole('tablist', { name: '编辑工作区' }).getByRole('tab', { name: '正文与媒体' }),
  ).toHaveAttribute('aria-selected', 'true')
  await expect(editor.locator('.media-panel')).toBeVisible()
  const textarea = editor.getByLabel('Markdown 原文')
  await textarea.click()
  await textarea.evaluate((node) => {
    const input = node as HTMLTextAreaElement
    input.setSelectionRange(0, 0)
  })
  await textarea.press('Tab')
  await expect(textarea).toHaveValue(/^  # 隔离测试竞赛/)
  await textarea.evaluate((node) => {
    const input = node as HTMLTextAreaElement
    input.setSelectionRange(0, 0)
  })
  await textarea.press('Shift+Tab')
  await expect(textarea).toHaveValue(/^# 隔离测试竞赛/)
  await textarea.press('Escape')
  await textarea.press('Tab')
  await expect(textarea).not.toBeFocused()
  await editor.getByRole('button', { name: '全屏专注' }).click()
  await expect(page.locator('.markdown-stage.is-focus')).toBeVisible()
  await page.getByRole('button', { name: '阅读预览' }).click()
  await expect(page.getByLabel('前端预览')).toBeVisible()
  await page.getByRole('button', { name: '退出全屏' }).click()
  await expect(page.locator('.markdown-stage.is-focus')).toHaveCount(0)
  await editor.getByRole('button', { name: '写作' }).click()
  await editor.getByLabel('选择图片').setInputFiles({
    name: 'test.png',
    mimeType: 'image/png',
    buffer: Buffer.from('not-a-real-png'),
  })
  await editor.getByLabel('图片说明').fill('隔离图片说明')
  await editor.getByRole('button', { name: '上传并插入' }).click()
  await expect(textarea).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  await expect(editor.getByText('正文已引用')).toBeVisible()
  await editor.getByRole('button', { name: '插入正文' }).click()
  await expect(textarea).toBeFocused()
  await expect(textarea).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  await editor.getByRole('button', { name: '复制 media:id' }).click()
  await expect(editor.getByRole('status').filter({ hasText: '已复制 media:id' })).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await editor.getByRole('button', { name: '从当前草稿移除' }).click()
  await expect(textarea).not.toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  await expect(editor.getByText('正文已引用')).toHaveCount(0)
})
test('正文图片说明可在卡片上修改并同步正文与预览', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  const textarea = editor.getByLabel('Markdown 原文')
  await editor.getByLabel('选择图片').setInputFiles({
    name: 'test.png',
    mimeType: 'image/png',
    buffer: Buffer.from('not-a-real-png'),
  })
  await editor.getByLabel('图片说明').fill('初始说明')
  await editor.getByRole('button', { name: '上传并插入' }).click()
  await expect(textarea).toHaveValue(/!\[初始说明]\(media:01HZX9Z8NQ2J3K4M5P6R7S8T9U\)/)
  await expect(editor.locator('.media-card-meta strong')).toHaveText('初始说明')
  await editor.getByRole('button', { name: '修改说明' }).click()
  await editor.getByLabel('正文图片说明').fill('更新后的说明')
  await editor.getByRole('button', { name: '保存说明' }).click()
  await expect(textarea).toHaveValue(/!\[更新后的说明]\(media:01HZX9Z8NQ2J3K4M5P6R7S8T9U\)/)
  await expect(editor.locator('.media-card-meta strong')).toHaveText('更新后的说明')
  await expect(editor.getByLabel('前端预览')).toContainText('更新后的说明')
})
test('移除正文图片前说明保存后才生效', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await editor.getByLabel('选择图片').setInputFiles({
    name: 'test.png',
    mimeType: 'image/png',
    buffer: Buffer.from('not-a-real-png'),
  })
  await editor.getByLabel('图片说明').fill('待移除图片')
  await editor.getByRole('button', { name: '上传并插入' }).click()
  const dialogs: string[] = []
  page.once('dialog', (dialog) => {
    dialogs.push(dialog.message())
    void dialog.dismiss()
  })
  await editor.getByRole('button', { name: '从当前草稿移除' }).click()
  expect(dialogs[0]).toContain('保存后才生效')
  expect(dialogs[0]).toContain('重新上传')
  await expect(editor.getByLabel('Markdown 原文')).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
})
test('编辑器校验清单在视口内，点条目跨工作区定位', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await editor.getByLabel('标题', { exact: true }).fill(' ')
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  const issues = page.getByRole('alert', { name: '待补齐字段' })
  await expect(issues).toBeVisible()
  await expect(issues).toBeInViewport()
  await expect(issues).toContainText('请填写标题。')
  await page.getByRole('tab', { name: '正文与媒体' }).click()
  await issues.getByRole('button', { name: '请填写标题。' }).click()
  await expect(page.getByRole('tab', { name: '结构化信息' })).toHaveAttribute('aria-selected', 'true')
  await expect(editor.getByLabel('标题', { exact: true })).toBeFocused()
})
test('全屏专注退出后仍停在正文工作区且内容不丢', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  const textarea = page.getByLabel('Markdown 原文')
  const before = await textarea.inputValue()
  await editor.getByRole('button', { name: '全屏专注' }).click()
  await expect(page.locator('.markdown-stage.is-focus')).toBeVisible()
  await expect(page.locator('.editor-shell')).toHaveAttribute('inert', '')
  await page.keyboard.press('Escape')
  await expect(page.locator('.markdown-stage.is-focus')).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '正文与媒体' })).toHaveAttribute('aria-selected', 'true')
  await expect(textarea).toHaveValue(before)
})
test('新建内容保存草稿并继续编辑后可以上传图片', async ({ page }) => {
  const state = await mockApi(page, 'PARTNER')
  await page.goto('/workspace/content/new')
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(editor.getByRole('button', { name: '保存草稿并继续编辑' })).toBeVisible()
  await expect(editor.getByLabel('选择图片')).toHaveCount(0)
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await editor.getByLabel('标题', { exact: true }).fill('新建并继续')
  await editor.getByLabel('中文导读').fill('隔离测试导读')
  await editor.getByLabel('作者展示名').fill('隔离作者')
  await editor.getByLabel('官方规则链接').fill('https://example.com/rules')
  await editor.getByLabel('来源名称').fill('隔离来源')
  await editor.getByLabel('官方或原始来源 URL').fill('https://example.com')
  await page.getByRole('tab', { name: '正文与媒体' }).click()
  await editor.getByRole('button', { name: '保存草稿并继续编辑' }).click()
  await expect(page).toHaveURL(new RegExp(`/workspace/content/${createdContentId}/edit$`))
  await expect(page.getByLabel('选择图片')).toBeVisible()
  await page
    .getByLabel('选择图片')
    .setInputFiles({ name: 'test.png', mimeType: 'image/png', buffer: Buffer.from('not-a-real-png') })
  await page.getByLabel('图片说明').fill('继续编辑后上传')
  await page.getByRole('button', { name: '上传并插入' }).click()
  await expect(page.getByLabel('Markdown 原文')).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  expect(state.writes.some((write) => write.path === '/media/uploads')).toBeTruthy()
})
test('已发布内容保存候选修订并继续编辑后可以上传图片', async ({ page }) => {
  const state = await mockApi(page, 'PARTNER')
  state.status = 'PUBLISHED'
  state.visibility = 'PUBLIC'
  await page.goto(`/workspace/content/${id}/edit`)
  const editor = page.getByRole('form', { name: '内容编辑器' })
  const reviseWrites = () => state.writes.filter((write) => write.path.includes('content-items'))
  await expect(editor.getByRole('button', { name: '保存候选修订并继续编辑' })).toBeVisible()
  await expect(editor.getByLabel('选择图片')).toHaveCount(0)
  await editor.getByRole('button', { name: '保存候选修订并继续编辑' }).click()
  await expect(editor.getByLabel('选择图片')).toBeVisible()
  expect(state.status).toBe('DRAFT')
  expect(reviseWrites().at(-1)!.headers['if-match']).toBe('7')
  await editor
    .getByLabel('选择图片')
    .setInputFiles({ name: 'test.png', mimeType: 'image/png', buffer: Buffer.from('not-a-real-png') })
  await editor.getByLabel('图片说明').fill('候选修订上传')
  await editor.getByRole('button', { name: '上传并插入' }).click()
  await expect(editor.getByLabel('Markdown 原文')).toHaveValue(/media:01HZX9Z8NQ2J3K4M5P6R7S8T9U/)
  await editor.getByRole('button', { name: '保存候选修订', exact: true }).click()
  await expect(page).toHaveURL(/\/workspace\?section=content$/)
  expect(reviseWrites().at(-1)!.headers['if-match']).toBe('8')
})
test('新建时必填不全不会保存也不能上传图片', async ({ page }) => {
  const state = await mockApi(page, 'PARTNER')
  await page.goto('/workspace/content/new')
  const editor = page.getByRole('form', { name: '内容编辑器' })
  await editor.getByRole('button', { name: '保存草稿并继续编辑' }).click()
  const issues = page.getByRole('alert', { name: '待补齐字段' })
  await expect(issues).toBeVisible()
  await expect(issues).toBeInViewport()
  await expect(page).toHaveURL(/\/workspace\/content\/new$/)
  await expect(editor.getByLabel('选择图片')).toHaveCount(0)
  await expect(editor.getByLabel('标题', { exact: true })).toBeFocused()
  expect(state.writes.filter((write) => write.path.includes('content-items'))).toEqual([])
})
test('管理员新建沿用现有接口契约', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace/content/new')
  await page.reload()
  let editor = page.getByRole('form', { name: '内容编辑器' })
  await expect(editor).toBeVisible()
  await page.getByRole('tab', { name: '结构化信息' }).click()
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
  await page.getByRole('tab', { name: '结构化信息' }).click()
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
  await expect(page.getByRole('combobox', { name: '状态', exact: true })).toHaveValue('RECEIVED')
  await expect(page.getByRole('button', { name: '拒绝', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '批准并签发激活链接' })).toBeVisible()
  await page.getByLabel('受控登录名').fill('isolated-partner')
  await page.getByLabel('审核原因').fill('隔离审批原因')
  state.fail = '/admin/partner-applications/application-1/approve'
  await page.getByRole('button', { name: '批准并签发激活链接' }).click()
  await expect(page.getByRole('alert')).toContainText('模拟连接失败')
  await expect(page.getByLabel('受控登录名')).toHaveValue('isolated-partner')
  await expect(page.getByLabel('审核原因')).toHaveValue('隔离审批原因')
  state.fail = undefined
  await page.getByRole('button', { name: '批准并签发激活链接' }).click()
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

test('合作申请筛选使用后端状态，拒绝后刷新列表', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace?section=applications')
  const status = page.getByRole('combobox', { name: '状态', exact: true })
  await expect(status).toHaveValue('RECEIVED')
  expect(
    await status
      .locator('option')
      .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value)),
  ).toEqual([
    'RECEIVED',
    'IN_REVIEW',
    'WAITING_FOR_APPLICANT',
    'APPROVED',
    'ACTIVATION_PENDING',
    'ACTIVATED',
    'REJECTED',
    'CLOSED',
    'ALL',
  ])
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
  await expect(page.getByRole('button', { name: '批准并签发激活链接' })).toHaveCount(0)
})

test('合作申请审核表单在桌面和移动端均无横向溢出', async ({ page }) => {
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
  await expect(page.getByRole('combobox', { name: '状态', exact: true })).toHaveValue('RECEIVED')
  await page.getByLabel('处理原因').fill('隔离处理原因')
  await page.getByRole('button', { name: '保存状态', exact: true }).click()
  await expect(page.getByRole('status').filter({ hasText: '公开输入处理结果已保存' })).toBeVisible()
  await page.getByRole('link', { name: '账号治理' }).click()
  await page.getByLabel('登录名').fill('admin.example')
  await page.locator('.authorization-panel').first().getByLabel('操作原因').fill('新增内容审核管理员')
  await page.locator('.authorization-panel').first().getByLabel('授权期限').selectOption('long')
  await page.locator('.authorization-panel').first().getByRole('button', { name: '预览授权影响' }).click()
  await page.getByRole('button', { name: '创建并授权' }).click()
  await expect(page.locator('.token-card')).toContainText('ADMIN')
  await expect(page.locator('.token-card')).toContainText('PENDING')
  await expect(page.getByRole('status').filter({ hasText: '请立即复制一次性激活链接' })).toBeVisible()
  await expect(page.getByLabel('登录名')).toHaveValue('')
  await expect(page.locator('.authorization-panel').first().getByLabel('操作原因')).toHaveValue('')
  await page.getByRole('button', { name: '复制链接' }).click()
  await expect(page.getByRole('button', { name: '已复制' })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: '激活链接已复制' })).toBeVisible()
  await page.reload()
  await expect(page.locator('.token-card')).toHaveCount(0)
  await page.getByRole('link', { name: '账号治理' }).click()
  await page.locator('article').filter({ hasText: 'pending-admin' }).getByLabel('原因').fill('补发激活链接')
  await page.getByRole('button', { name: '重新签发' }).click()
  await expect(page.locator('.token-card')).toContainText('pending-admin')
  const pendingAdmin = page.locator('article').filter({ hasText: 'pending-admin' })
  await pendingAdmin.getByLabel('原因').fill('撤销未激活管理员')
  await pendingAdmin.getByLabel('撤销确认').fill('pending-admin')
  await pendingAdmin.getByRole('button', { name: '永久撤销' }).click()
  await expect
    .poll(() => state.writes.some((entry) => entry.path === '/admin/accounts/account-pending/revoke'))
    .toBeTruthy()
  await page.locator('article').filter({ hasText: 'other-admin' }).getByLabel('原因').fill('隔离停用原因')
  await page
    .locator('article')
    .filter({ hasText: 'other-admin' })
    .getByRole('button', { name: '停用', exact: true })
    .click()
  await expect(page.getByRole('status').filter({ hasText: '账号资格状态已更新' })).toBeVisible()
  state.conflict = true
  await page.getByLabel('登录名').fill('admin.conflict')
  await page.locator('.authorization-panel').first().getByLabel('操作原因').fill('冲突重试')
  await page.locator('.authorization-panel').first().getByLabel('授权期限').selectOption('long')
  await page.locator('.authorization-panel').first().getByRole('button', { name: '预览授权影响' }).click()
  await page.getByRole('button', { name: '创建并授权' }).click()
  await expect(page.getByRole('alert').filter({ hasText: '账号状态已被其他操作更新' })).toBeVisible()
  await page.getByRole('link', { name: '审计记录' }).click()
  await expect(page.getByText('CONTENT_UPDATED')).toBeVisible()
  expect(state.writes.some((entry) => entry.path.includes('/admin/submissions/'))).toBeTruthy()
  expect(state.writes.some((entry) => entry.path.includes('/admin/accounts/'))).toBeTruthy()
})
test('HTTP 非安全上下文仍能复制一次性激活链接', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'isSecureContext', { configurable: true, get: () => false })
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
  })
  await mockApi(page, 'SUPER_ADMIN')
  await page.goto('/workspace?section=accounts')
  await page.getByLabel('登录名').fill('admin.http')
  await page.locator('.authorization-panel').first().getByLabel('操作原因').fill('HTTP 复制兼容')
  await page.locator('.authorization-panel').first().getByLabel('授权期限').selectOption('long')
  await page.locator('.authorization-panel').first().getByRole('button', { name: '预览授权影响' }).click()
  await page.getByRole('button', { name: '创建并授权' }).click()
  await expect(page.getByRole('status').filter({ hasText: '请立即复制一次性激活链接' })).toBeVisible()
  await page.getByRole('button', { name: '复制链接' }).click()
  await expect(page.getByRole('button', { name: '已复制' })).toBeVisible()
  await expect(page.getByRole('status').filter({ hasText: '激活链接已复制' })).toBeVisible()
})
test('三层参与菜单支持悬停、点击与键盘', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  const trigger = page.getByRole('button', { name: '参与 VOIDHAVEN' })
  const panel = page.locator('#participation-panel')
  const submenu = page.locator('#participation-submenu')
  await expect(panel).toBeHidden()

  await trigger.hover()
  await expect(panel).toBeVisible()
  await page.mouse.move(0, 0)
  await expect(panel).toBeHidden()

  await trigger.click()
  await expect(panel).toBeVisible()
  await expect(panel.getByRole('link', { name: '投稿 / 反馈' })).toBeVisible()
  const joinUs = panel.getByRole('button', { name: '加入我们' })
  await joinUs.click()
  await expect(submenu).toBeVisible()
  await expect(page).toHaveURL(/\/$/)
  await expect(submenu.getByRole('link', { name: '加入 VOIDHAVEN 用户社群' })).toBeVisible()

  await page.keyboard.press('Escape')
  await expect(panel).toBeHidden()
  await expect(trigger).toBeFocused()

  await trigger.press('Enter')
  await expect(panel).toBeVisible()
  await page.keyboard.press('ArrowDown')
  await expect(panel.getByRole('link', { name: '投稿 / 反馈' })).toBeFocused()
  await page.keyboard.press('ArrowDown')
  await expect(panel.getByRole('link', { name: '合作申请' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(panel).toBeHidden()
  await expect(trigger).toBeFocused()

  await trigger.click()
  await panel.getByRole('button', { name: '加入我们' }).click()
  await submenu.getByRole('link', { name: '加入 VOIDHAVEN 项目组' }).click()
  await expect(page).toHaveURL(/\/join\/project$/)
  await expect(page.getByRole('heading', { name: /把一次好奇/ })).toBeVisible()
})

test('触屏宽度下三层参与菜单可折叠并在跳转后关闭抽屉', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: '打开菜单' }).click()
  const dialog = page.getByRole('dialog', { name: '导航菜单' })
  await expect(dialog).toBeVisible()
  const group = dialog.getByRole('button', { name: '参与 VOIDHAVEN' })
  await expect(dialog.locator('#drawer-participation')).toBeHidden()
  await group.click()
  await expect(dialog.locator('#drawer-participation')).toBeVisible()
  const subGroup = dialog.getByRole('button', { name: '加入我们' })
  await expect(dialog.locator('#drawer-join-us')).toBeHidden()
  await subGroup.click()
  await expect(dialog.locator('#drawer-join-us')).toBeVisible()
  await dialog.getByRole('link', { name: '加入 VOIDHAVEN 用户社群' }).click()
  await expect(page).toHaveURL(/\/join\/community$/)
  await expect(dialog).toHaveCount(0)
})

test('旧地址 /join 以替换方式跳转到 /join/community', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await page.goto('/join')
  await expect(page).toHaveURL(/\/join\/community$/)
  await expect(page.getByLabel('邀请码')).toBeVisible()
  await page.goBack()
  await expect(page).toHaveURL(/\/$/)
})

test('项目组公开页在未验证时只显示公开入口', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/join/project')
  await expect(page.getByText('公开项目组邮箱')).toBeVisible()
  await expect(page.getByText('受控项目组群')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '查看受控入口' })).toBeVisible()
})

test('项目组入口复用社群邀请码并跨页共享，刷新后失效', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/join/community')
  await page.getByLabel('邀请码').fill('invite-code-isolated')
  await page.getByRole('button', { name: '查看社群入口' }).click()
  await expect(page.getByRole('status')).toContainText('验证成功')

  await page.getByRole('button', { name: '参与 VOIDHAVEN' }).click()
  await page.getByRole('button', { name: '加入我们' }).click()
  await page.getByRole('link', { name: '加入 VOIDHAVEN 项目组' }).click()
  await expect(page).toHaveURL(/\/join\/project$/)
  await expect(page.getByText('公开项目组邮箱')).toBeVisible()
  await expect(page.getByText('受控项目组群')).toBeVisible()

  await page.reload()
  await expect(page.getByText('受控项目组群')).toHaveCount(0)
  await expect(page.getByText('公开项目组邮箱')).toBeVisible()
})

test('项目组无有效入口时隐藏导航叶子并显示暂未开放', async ({ page }) => {
  const state = await mockApi(page)
  state.projectJoinEnabled = false
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  await page.getByRole('button', { name: '参与 VOIDHAVEN' }).click()
  await page.getByRole('button', { name: '加入我们' }).click()
  await expect(page.getByRole('link', { name: '加入 VOIDHAVEN 用户社群' })).toBeVisible()
  await expect(page.getByRole('link', { name: '加入 VOIDHAVEN 项目组' })).toHaveCount(0)
  await page.goto('/join/project')
  await expect(page.getByRole('heading', { name: '当前暂未开放' })).toBeVisible()
})

test('超级管理员可创建、编辑并停用项目组入口', async ({ page }) => {
  const state = await mockApi(page, 'SUPER_ADMIN')
  await page.goto('/workspace?section=project-join')
  await expect(page.getByRole('heading', { name: '项目组入口', exact: true })).toBeVisible()
  const form = page.locator('form.panel').filter({ hasText: '新增项目组入口' })
  await form.getByLabel('名称').fill('新的项目组入口')
  await form.getByLabel('入口内容').fill('https://example.com/new-project')
  await form.getByRole('button', { name: '创建入口' }).click()
  await expect(page.getByRole('status').filter({ hasText: '项目组入口已创建。' })).toBeVisible()
  expect(state.writes.some((write) => write.path === '/admin/participation/project-join')).toBeTruthy()

  const row = page.locator('article.data-row').filter({ hasText: '新的项目组入口' })
  await row.getByRole('button', { name: '编辑' }).click()
  await row.getByRole('button', { name: '保存入口' }).click()
  await expect(page.getByRole('status').filter({ hasText: '项目组入口已更新。' })).toBeVisible()

  page.once('dialog', (dialog) => void dialog.accept('不再使用'))
  await row.getByRole('button', { name: '停用' }).click()
  await expect(page.getByRole('status').filter({ hasText: '项目组入口已停用。' })).toBeVisible()
  expect(state.writes.some((write) => write.path.endsWith('/deactivate'))).toBeTruthy()
})

test('页脚信任信息、隐私页与来源锚点可达', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/')
  const footer = page.getByRole('navigation', { name: '页脚导航' })
  await footer.getByRole('link', { name: '隐私说明' }).click()
  await expect(page).toHaveURL(/\/privacy$/)
  await expect(page.getByRole('heading', { name: /只收集必须的/ })).toBeVisible()

  await footer.getByRole('link', { name: '来源与核验' }).click()
  await expect(page).toHaveURL(/\/about#sources$/)
  await expect(page.getByRole('heading', { name: '内容整理、核验和来源原则' })).toBeInViewport()

  await footer.getByRole('link', { name: '版权与下线' }).click()
  await expect(page).toHaveURL(/\/submit\?type=COPYRIGHT$/)
  await expect(page.getByRole('radio', { name: '版权/下线反馈' })).toBeChecked()
})

test('导航与页脚统一使用竞赛资源与合作申请文案', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1280, height: 900 })
  await page.goto('/competitions')
  await expect(page).toHaveTitle('竞赛资源 — VOIDHAVEN')
  const nav = page.getByRole('navigation', { name: '主导航' })
  await expect(nav.getByRole('link', { name: '竞赛资源' })).toBeVisible()
  await page.getByRole('button', { name: '参与 VOIDHAVEN' }).click()
  await expect(page.locator('#participation-panel').getByRole('link', { name: '合作申请' })).toBeVisible()
  const footer = page.getByRole('navigation', { name: '页脚导航' })
  await expect(footer.getByRole('link', { name: '竞赛资源' })).toBeVisible()
  await expect(footer.getByRole('link', { name: '合作申请' })).toBeVisible()
})

test('五种宽度、双主题、抽屉焦点与低动效', async ({ page }) => {
  test.setTimeout(120000)
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
        '/join/community',
        '/join/project',
        '/about',
        '/privacy',
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
    await expect(page.getByRole('heading', { name: '竞赛资源' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '团队招募' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '资源与分享' })).toBeVisible()
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

test('提交回执可查询，邀请码可打开社群入口，账号可改密和重置', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/submit')
  await page.getByLabel('标题', { exact: true }).fill('查询闭环')
  await page.getByLabel('内容', { exact: true }).fill('保存回执后查询状态。')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByLabel('事项查询凭据')).toContainText('isolated-query-code-value')
  await page.goto('/status')
  await page.getByLabel('回执号').fill('01JRECEIPT0000000000000001')
  await page.getByLabel('查询码').fill('isolated-query-code-value')
  await page.getByRole('button', { name: '查询进度' }).click()
  await expect(page.getByRole('heading', { name: 'RECEIVED' })).toBeVisible()
  await expect(page.getByText('我们已收到你的提交。')).toBeVisible()
  await page.goto('/join/community')
  await page.getByLabel('邀请码').fill('invite-code-isolated')
  await page.getByRole('button', { name: '查看社群入口' }).click()
  await expect(page.getByRole('status')).toContainText('验证成功')
  await expect(page.getByText('主入口 · 主社群')).toBeVisible()
  await page.goto('/workspace?section=security')
  await page.getByLabel('当前密码').fill('current-password-12')
  await page.getByLabel('新密码', { exact: true }).fill('replacement-password-12')
  await page.getByLabel('确认新密码').fill('replacement-password-12')
  await page.getByRole('button', { name: '修改密码并注销全部会话' }).click()
  await expect(page).toHaveURL(/\/auth\/sign-in/)
  expect(state.writes.some((write) => write.path === '/auth/password/change')).toBeTruthy()
  await mockApi(page, 'SUPER_ADMIN')
  await page.goto('/workspace?section=accounts')
  await page
    .locator('article')
    .filter({ hasText: 'other-admin' })
    .getByLabel('原因', { exact: true })
    .fill('隔离重置原因')
  await page
    .locator('article')
    .filter({ hasText: 'other-admin' })
    .getByRole('button', { name: '签发密码重置' })
    .click()
  await expect(page.getByRole('status').filter({ hasText: '已签发一次性密码重置凭据' })).toBeVisible()
  await page.goto('/auth/reset-password')
  await page.getByLabel('重置凭据').fill('isolated-reset-token-value-32chars')
  await page.getByLabel('新密码').fill('replacement-password-12')
  await page.getByLabel('确认密码').fill('replacement-password-12')
  await page.getByRole('button', { name: '重置密码' }).click()
  await expect(page.getByRole('status')).toContainText('密码已重置')
})

test('管理员可视化编排首页并预览后发布', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace?section=homepage')
  await expect(page.getByRole('heading', { name: '首页人工编排' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '竞赛资源' })).toBeVisible()
  await expect(page.getByText('当前编辑的就是线上生效版本')).toBeVisible()
  await page.getByRole('button', { name: '添加' }).first().click()
  await expect(page.getByText('有未保存修改')).toBeVisible()
  await page.getByRole('button', { name: '预览未发布结果' }).click()
  await expect(page.getByRole('heading', { name: '保存前预览' })).toBeVisible()
  await page.getByRole('button', { name: '保存并发布' }).click()
  await expect(page.getByText('首页编排已发布')).toBeVisible()
  const write = state.writes.find(
    (entry) => entry.path === '/admin/homepage-placements' && entry.method === 'PUT',
  )
  expect(write?.headers['if-match'] ?? write?.headers['If-Match']).toBe('0')
  expect(write?.body.placements[0]).toMatchObject({ slot: 'FOCUS', contentItemId: id, sortOrder: 0 })
})

test('目录页支持卡片列表切换、筛选、排序与清空条件', async ({ page }) => {
  await mockApi(page)
  await page.goto('/competitions')
  await expect(page.getByRole('button', { name: '卡片' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.story-grid')).toBeVisible()
  await page.getByRole('button', { name: '列表' }).click()
  await expect(page).toHaveURL(/view=list/)
  await expect(page.locator('.catalog-list')).toBeVisible()
  await page.getByRole('link', { name: '隔离测试竞赛', exact: true }).click()
  await expect(page).toHaveURL(/\/content\//)
  await page.getByRole('button', { name: '返回浏览目录' }).click()
  await expect(page).toHaveURL(/view=list/)
  await page.getByLabel('筛选字段').selectOption('competitionLevel')
  await page.getByLabel('精确值').selectOption('校内')
  await expect(page).toHaveURL(/filterKey=competitionLevel/)
  await expect(page).toHaveURL(/filterValue=/)
  await page.getByLabel('排序').selectOption('deadline')
  await expect(page).toHaveURL(/sort=deadline/)
  await page.goto('/competitions?q=不存在')
  await expect(page.getByText('没有找到匹配内容')).toBeVisible()
  await page.getByRole('button', { name: '清空筛选条件' }).click()
  await expect(page).not.toHaveURL(/q=/)
  await page.goto('/resources')
  await expect(page.getByRole('button', { name: '列表' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.catalog-list')).toBeVisible()
})

test('管理员可保存目录默认顺序', async ({ page }) => {
  const state = await mockApi(page)
  await page.goto('/workspace?section=catalog')
  await expect(page.getByRole('heading', { name: '目录默认顺序' })).toBeVisible()
  await page.getByRole('button', { name: '添加' }).first().click()
  await expect(page.getByText('有未保存修改')).toBeVisible()
  await page.getByRole('button', { name: '保存目录顺序' }).click()
  await expect(page.getByText('已保存')).toBeVisible()
  const write = state.writes.find(
    (entry) => entry.path === '/admin/catalog-order/COMPETITION' && entry.method === 'PUT',
  )
  expect(write?.headers['if-match'] ?? write?.headers['If-Match']).toBe('0')
  expect(write?.body.defaultSort).toBe('default')
  expect(write?.body.entries[0]).toMatchObject({ contentItemId: id, sortOrder: 0 })
})

test('公开 404 与搜索空状态展示插画，且不掩盖文案', async ({ page }) => {
  await mockApi(page)
  await page.goto('/unknown-path')
  await expect(page.getByRole('heading', { name: '这条小径，暂时没有内容。' })).toBeVisible()
  await expect(page.locator('.not-found-figure')).toBeVisible()
  await expect(page.getByRole('link', { name: '返回首页' })).toBeVisible()
  await page.goto('/search')
  await expect(page.getByText('输入一个关键词')).toBeVisible()
  await expect(page.locator('.empty-state-figure')).toBeVisible()
  await page.goto('/competitions?q=不存在')
  await expect(page.getByText('没有找到匹配内容')).toBeVisible()
  await expect(page.locator('.empty-state-figure')).toBeVisible()
  await page.getByRole('button', { name: '深色', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByText('没有找到匹配内容')).toBeVisible()
  await expect(page.locator('.empty-state-figure')).toBeVisible()
})
