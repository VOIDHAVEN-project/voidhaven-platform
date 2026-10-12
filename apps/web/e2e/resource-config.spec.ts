import { test, expect, type Page } from '@playwright/test'
import { mockApi, modules, content, id } from './fixtures'
import type { ResourceConfig } from '../src/lib/api'

const initial: ResourceConfig = {
  version: 1,
  categories: [
    { key: 'GENERAL', label: '通用分享', enabled: true },
    { key: 'EVENT', label: '活动通知', enabled: true },
  ],
  fields: [
    { key: 'eventDate', label: '活动日期', type: 'date', help: '仅用于展示', options: [], enabled: true },
  ],
  templates: [
    {
      key: 'event',
      label: '活动通知',
      markdown: '# 活动介绍\n\n隔离测试模板。',
      fieldKeys: ['eventDate'],
      enabled: true,
    },
  ],
}

async function setup(page: Page) {
  const state = await mockApi(page)
  let config = structuredClone(initial)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  page.on('console', (entry) => {
    if (entry.type() === 'error' && entry.text().includes('cannot')) errors.push(entry.text())
  })
  await page.route('**/api/v1/admin/resource-config', async (route) => {
    if (route.request().method() === 'PUT') {
      expect(route.request().headers()['if-match']).toBe(String(config.version))
      expect(route.request().headers()['idempotency-key']).toBeTruthy()
      config = { ...route.request().postDataJSON(), version: config.version + 1 }
    }
    await route.fulfill({ json: { data: config, requestId: 'isolated-test' } })
  })
  await page.route('**/api/v1/content-modules', (route) =>
    route.fulfill({
      json: {
        data: {
          ...modules,
          modules: modules.modules.map((m) =>
            m.legacyKind !== 'RESOURCE'
              ? m
              : {
                  ...m,
                  allowsOriginalSource: true,
                  createVisible: m.key === 'resource',
                  resourceConfig: config,
                  fields: [
                    {
                      key: 'sourceMode',
                      label: '来源方式',
                      type: 'select',
                      required: false,
                      filterable: true,
                      options: ['EXTERNAL', 'ORIGINAL'],
                    },
                    {
                      key: 'resourceType',
                      label: '内容分类',
                      type: 'select',
                      required: false,
                      filterable: true,
                      options: config.categories.map((c) => c.key),
                    },
                  ],
                },
          ),
        },
        requestId: 'isolated-test',
      },
    }),
  )
  return {
    state,
    errors,
    config: () => config,
    setConfig: (next: ResourceConfig) => {
      config = next
    },
  }
}

for (const theme of ['light', 'dark'])
  test(`管理员配置可新增字段，编辑器即时使用 · ${theme}`, async ({ page }) => {
    const mock = await setup(page)
    await page.addInitScript((value) => localStorage.setItem('voidhaven-theme', value), theme)
    await page.goto('/workspace?section=resource-config')
    await expect(page.getByRole('heading', { name: '资源与分享设置' })).toBeVisible()
    await page.getByRole('button', { name: '公共字段', exact: true }).click()
    await page.getByRole('button', { name: '新增公共字段' }).click()
    await page.getByLabel('名称', { exact: true }).last().fill('活动地点')
    await page.getByRole('button', { name: '保存并生效' }).click()
    await expect(page.getByText('配置已保存并生效。')).toBeVisible()
    expect(mock.config().fields).toHaveLength(2)
    await page.screenshot({ path: `test-results/resource-config-${theme}.png`, fullPage: true })
    await page.goto('/workspace/content/new')
    await page.getByRole('tab', { name: '结构化信息' }).click()
    await page.getByRole('combobox', { name: '内容模板', exact: true }).selectOption('resource')
    await expect(
      page
        .getByRole('combobox', { name: '内容模板', exact: true })
        .locator('option[value="resource.article"]'),
    ).toHaveCount(0)
    await page.getByRole('combobox', { name: '来源方式', exact: true }).selectOption('ORIGINAL')
    await page.getByLabel('标题', { exact: true }).fill('隔离活动分享')
    await page.getByLabel('中文导读', { exact: true }).fill('合成的测试内容，不包含真实用户数据。')
    await page.getByLabel('作者展示名', { exact: true }).fill('测试作者')
    await page.getByRole('combobox', { name: '添加信息项', exact: true }).selectOption({ label: '活动地点' })
    await page.getByLabel('活动地点', { exact: true }).fill('合成教室')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect.poll(() => mock.state.writes.filter((w) => w.path === '/admin/content-items').length).toBe(1)
    const write = mock.state.writes.find((w) => w.path === '/admin/content-items')!
    expect(write.body.resourceConfigVersion).toBe(2)
    expect(write.body.details).toMatchObject({
      sourceMode: 'ORIGINAL',
      customFields: { [mock.config().fields[1].key]: '合成教室' },
    })
    expect(mock.errors).toEqual([])
  })

test('公开详情按发布快照展示停用字段，移动端不横向溢出', async ({ page }) => {
  const mock = await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route(`**/api/v1/public/content/${id}`, (route) =>
    route.fulfill({
      json: {
        data: {
          ...content,
          kind: 'RESOURCE',
          moduleKey: 'resource',
          details: { resourceType: 'EVENT', customFields: { eventDate: '2030-10-01' } },
          resourcePresentation: {
            category: '历史活动分类',
            fields: [{ ...initial.fields[0], label: '原活动日期', enabled: false }],
          },
        },
        requestId: 'isolated-test',
      },
    }),
  )
  await page.goto(`/content/${id}`)
  await expect(page.getByText('历史活动分类', { exact: true })).toBeVisible()
  await expect(page.getByText('原活动日期', { exact: true })).toBeVisible()
  await expect(page.getByText('2030-10-01', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/resource-detail-mobile.png', fullPage: true })
  expect(mock.errors).toEqual([])
})

test('配置冲突保留输入，核对后使用最新版本保存', async ({ page }) => {
  const mock = await setup(page)
  await page.goto('/workspace?section=resource-config')
  await page.getByLabel('名称', { exact: true }).last().fill('我的活动分类')
  mock.setConfig({ ...mock.config(), version: 2 })
  let conflict = true
  await page.route('**/api/v1/admin/resource-config', async (route) => {
    if (route.request().method() === 'PUT' && conflict) {
      conflict = false
      await route.fulfill({
        status: 409,
        json: { code: 'RESOURCE_CONFIG_VERSION_CONFLICT', message: '配置已更新，请核对最新版本。' },
      })
    } else await route.fallback()
  })
  await page.getByRole('button', { name: '保存并生效' }).click()
  await expect(page.getByText('配置已更新，请核对最新版本。')).toBeVisible()
  await expect(page.getByLabel('名称', { exact: true }).last()).toHaveValue('我的活动分类')
  await page.getByRole('button', { name: '核对最新版本', exact: true }).click()
  await expect(page.getByRole('heading', { name: '服务器配置 · 版本 2' })).toBeVisible()
  await page.getByRole('button', { name: '已核对，保留输入继续编辑' }).click()
  await page.getByRole('button', { name: '保存并生效' }).click()
  await expect(page.getByText('配置已保存并生效。')).toBeVisible()
  expect(mock.config().version).toBe(3)
  expect(mock.config().categories[1].label).toBe('我的活动分类')
  expect(mock.errors).toEqual([])
})

test('模板替换先确认，取消不丢失正文，应用后添加推荐字段', async ({ page }) => {
  const mock = await setup(page)
  await page.goto('/workspace/content/new')
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await page.getByRole('combobox', { name: '内容模板', exact: true }).selectOption('resource')
  await page.getByRole('tab', { name: '正文与媒体' }).click()
  await page.getByLabel('Markdown 原文').fill('# 必须保留的正文')
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await page.getByRole('combobox', { name: '写作模板', exact: true }).selectOption('event')
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('tab', { name: '正文与媒体' }).click()
  await expect(page.getByLabel('Markdown 原文')).toHaveValue('# 必须保留的正文')
  await page.getByRole('tab', { name: '结构化信息' }).click()
  await page.getByRole('combobox', { name: '写作模板', exact: true }).selectOption('event')
  await page.getByRole('dialog').getByRole('button', { name: '确认应用写作模板', exact: true }).click()
  await expect(page.getByLabel('活动日期', { exact: false })).toBeVisible()
  await page.getByRole('tab', { name: '正文与媒体' }).click()
  await expect(page.getByLabel('Markdown 原文')).toHaveValue(initial.templates[0].markdown)
  expect(mock.state.writes).toHaveLength(0)
  expect(mock.errors).toEqual([])
})
