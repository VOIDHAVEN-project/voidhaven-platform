import { test, expect } from '@playwright/test'
import { content, id, mockApi } from './fixtures'

test('登录、激活校验和失败原因可理解', async ({ page }) => {
  await mockApi(page, 'PARTNER')
  await page.goto('/auth/sign-in')
  await page.getByLabel('账号', { exact: true }).fill('isolated-test')
  await page.getByLabel('密码', { exact: true }).fill('isolated-test-password')
  await page.getByRole('button', { name: '显示密码' }).click()
  await expect(page.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '进入工作台' }).click()
  await expect(page).toHaveURL(/workspace/)
  await page.getByRole('button', { name: '退出会话' }).click()
  await expect(page).toHaveURL(/auth\/sign-in/)
  await page.goto('/auth/activate?token=isolated-not-a-real-token')
  await page.getByLabel('新密码', { exact: true }).fill('isolated-new-password')
  await page.getByLabel('确认密码', { exact: true }).fill('isolated-different-password')
  await page.getByRole('button', { name: '激活账号' }).click()
  await expect(page.getByRole('alert')).toContainText('不一致')
  await page.getByLabel('确认密码', { exact: true }).fill('isolated-new-password')
  await page.getByRole('button', { name: '激活账号' }).click()
  await expect(page.getByRole('status')).toContainText('已激活')
  await expect(page).not.toHaveURL(/token=/)
  await expect(page.getByRole('button', { name: '激活账号' })).toHaveCount(0)
})

test('无权限、限流与配额不足不被空状态掩盖', async ({ page }) => {
  const state = await mockApi(page)
  await page.route('**/api/v1/admin/content-items?*', (route) =>
    route.fulfill({
      status: 403,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'FORBIDDEN' }),
    }),
  )
  state.status = 'APPROVED'
  await page.goto('/workspace?section=content')
  await expect(page.getByRole('alert')).toContainText('没有执行此操作的权限')
  await page.route('**/api/v1/public/submissions', (route) =>
    route.fulfill({
      status: 429,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'TOO_MANY_REQUESTS' }),
    }),
  )
  await page.goto('/feedback')
  await page.getByLabel('标题', { exact: true }).fill('限流测试')
  await page.getByLabel('内容', { exact: true }).fill('模拟限流')
  await page.getByRole('button', { name: '提交审核' }).click()
  await expect(page.getByRole('alert')).toContainText('提交过于频繁')
  await page.unroute('**/api/v1/admin/content-items?*')
  await page.route('**/api/v1/admin/content-items/' + id + '/publish', (route) =>
    route.fulfill({
      status: 409,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'QUOTA_EXCEEDED', message: '公开配额不足，无法发布。' }),
    }),
  )
  await page.route('**/api/v1/admin/content-items?*', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          items: [
            {
              ...content,
              workflowStatus: 'APPROVED',
              publicationStatus: 'UNPUBLISHED',
              visibility: 'HIDDEN',
              recordState: 'ACTIVE',
            },
          ],
          nextCursor: null,
        },
      }),
    }),
  )
  await page.goto('/workspace?section=content')
  await page.getByRole('link', { name: '发布检查', exact: true }).click()
  await page.getByRole('button', { name: '执行检查', exact: true }).click()
  await page.getByRole('button', { name: '正式发布', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('配额')
})

test('聚合搜索允许单类失败后独立重试，并区分全部失败', async ({ page }) => {
  await mockApi(page)
  const recruitmentPattern = '**/api/v1/public/recruitments?*'
  await page.route(recruitmentPattern, (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'UNAVAILABLE', message: '招募检索暂时不可用' }),
    }),
  )
  await page.goto('/search?q=隔离')
  await expect(page.getByRole('link', { name: '隔离测试竞赛', exact: true })).toBeVisible()
  const recruitmentGroup = page.getByRole('region', { name: '团队招募' })
  await expect(recruitmentGroup.getByRole('alert')).toContainText('招募检索暂时不可用')
  await page.unroute(recruitmentPattern)
  await recruitmentGroup.getByRole('button', { name: '重新尝试' }).click()
  await expect(recruitmentGroup.getByRole('link', { name: '隔离测试招募', exact: true })).toBeVisible()

  for (const category of ['competitions', 'recruitments', 'resources']) {
    await page.route(`**/api/v1/public/${category}?*`, (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'UNAVAILABLE', message: '全部检索不可用' }),
      }),
    )
  }
  await page.goto('/search?q=全部失败')
  await expect(page.getByRole('alert')).toContainText('三类公开内容暂时都无法检索')
})

test('图片慢加载保持占位，分享降级与滚动恢复', async ({ page, context }) => {
  await mockApi(page)
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  let releaseImage!: () => void
  const imageReady = new Promise<void>((resolve) => {
    releaseImage = resolve
  })
  await page.route('**/test-image.svg', async (route) => {
    await imageReady
    await route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#e4d7c7"/></svg>',
    })
  })
  await page.route('**/api/v1/public/content/' + id, (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          ...content,
          body: {
            version: 2,
            blocks: [{ type: 'image', mediaId: 'test-image', alt: '隔离图片' }, ...content.body.blocks],
          },
          media: [{ id: 'test-image', url: '/test-image.svg', width: 600, height: 400 }],
        },
      }),
    }),
  )
  await page.goto('/content/' + id, { waitUntil: 'domcontentloaded' })
  const img = page.getByAltText('隔离图片')
  await expect(img).toBeVisible()
  const before = await img.boundingBox()
  releaseImage()
  await expect.poll(() => img.evaluate((image) => (image as HTMLImageElement).complete)).toBeTruthy()
  const after = await img.boundingBox()
  expect(Math.abs(before!.height - after!.height)).toBeLessThan(1)
  await page.evaluate(() => {
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true })
  })
  await page.getByRole('button', { name: '分享内容' }).click()
  await expect(page.getByRole('status')).toContainText('链接已复制')
  await page.route('**/api/v1/public/competitions?*', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          items: Array.from({ length: 12 }, (_, i) => ({
            ...content,
            id: i === 0 ? id : String(i).repeat(26),
            title: '滚动目录 ' + i,
          })),
        },
      }),
    }),
  )
  await page.goto('/competitions?q=保留条件')
  await expect(page.getByRole('link', { name: '滚动目录 6', exact: true })).toBeAttached()
  await page.getByRole('link', { name: '滚动目录 6', exact: true }).scrollIntoViewIfNeeded()
  await page.mouse.wheel(0, 30)
  const position = await page.evaluate(() => window.scrollY)
  await page.getByRole('link', { name: '滚动目录 6', exact: true }).click()
  await page.getByRole('button', { name: '返回浏览目录' }).click()
  await expect(page).toHaveURL(/q=/)
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(position - 100)
})
