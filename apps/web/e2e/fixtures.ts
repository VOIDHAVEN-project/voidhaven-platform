import type { Page, Route } from '@playwright/test'
export const id = 'AAAAAAAAAAAAAAAAAAAAAAAAAA'
export const content = {
  id,
  kind: 'COMPETITION',
  moduleKey: 'competition',
  schemaVersion: 1,
  title: '隔离测试竞赛',
  summary: '用于独立前端验证的模拟信息。',
  body: {
    version: 2,
    blocks: [
      { type: 'paragraph', text: '第一段正文' },
      { type: 'heading', level: 2, inlines: [{ type: 'strong', text: '丰富的标题' }] },
      { type: 'paragraph', inlines: [{ type: 'link', text: '官方来源', href: 'https://example.com' }] },
      { type: 'heading', level: 2, text: '参赛要求' },
      { type: 'paragraph', text: '核对资格和提交要求。' },
      { type: 'heading', level: 2, text: '时间安排' },
      { type: 'paragraph', text: '确认截止时间与官方安排。' },
      { type: 'future-block', payload: { keep: true } },
    ],
  },
  markdownSource:
    '# 隔离测试竞赛\n\n第一段正文\n\n## 丰富的标题\n\n[官方来源](https://example.com)\n\n## 参赛要求\n\n核对资格和提交要求。\n\n## 时间安排\n\n确认截止时间与官方安排。',
  languageCode: 'zh-CN',
  languageLabel: '中文',
  sourceName: '测试来源',
  sourceUrl: 'https://example.com',
  authorDisplayName: '隔离测试作者',
  rightsBasis: '仅用于自动化测试',
  reprintPermission: 'NOT_ALLOWED',
  adaptationPermission: 'ATTRIBUTION_ALLOWED',
  details: {
    deadlineDate: '2030-10-15',
    officialRulesUrl: 'https://example.com/rules',
    customHidden: { keep: true },
  },
  revision: 7,
  media: [],
  customMetadata: 'keep-me',
}
export const modules = {
  version: 1,
  modules: [
    {
      key: 'competition',
      label: '竞赛',
      legacyKind: 'COMPETITION',
      active: true,
      allowsOriginalSource: false,
      template: '# 标题\n\n开始写作。',
      fields: [
        { key: 'officialRulesUrl', label: '官方规则链接', type: 'url', required: true, filterable: false },
        { key: 'deadlineDate', label: '截止日期', type: 'date', required: false, filterable: true },
      ],
    },
    {
      key: 'recruitment',
      label: '团队招募',
      legacyKind: 'RECRUITMENT',
      active: true,
      allowsOriginalSource: false,
      template: '# 招募说明\n\n开始写作。',
      fields: [{ key: 'deadlineDate', label: '截止日期', type: 'date', required: false, filterable: true }],
    },
    {
      key: 'resource',
      label: '学习资源',
      legacyKind: 'RESOURCE',
      active: true,
      allowsOriginalSource: false,
      template: '# 使用说明\n\n开始写作。',
      fields: [
        {
          key: 'resourceType',
          label: '资源类型',
          type: 'select',
          required: true,
          filterable: true,
          options: ['GUIDE', 'COURSE', 'TOOL', 'DOCUMENT', 'ARTICLE'],
        },
        { key: 'audience', label: '适用人群', type: 'text', required: false, filterable: true },
      ],
    },
    {
      key: 'resource.article',
      label: '文章 / 经验',
      legacyKind: 'RESOURCE',
      active: true,
      allowsOriginalSource: true,
      template: '# 文章标题\n\n开始写作。',
      fields: [
        {
          key: 'sourceMode',
          label: '来源模式',
          type: 'select',
          required: true,
          filterable: true,
          options: ['ORIGINAL', 'EXTERNAL'],
        },
        {
          key: 'resourceType',
          label: '资源类型',
          type: 'select',
          required: true,
          filterable: true,
          options: ['ARTICLE'],
        },
      ],
    },
  ],
}
export type MockState = {
  role: 'ADMIN' | 'SUPER_ADMIN' | 'PARTNER'
  status: string
  recordState: string
  visibility: string
  quota: number
  partnerApplicationState: 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
  writes: { path: string; method: string; body: any; headers: Record<string, string> }[]
  fail?: string
  conflict: boolean
}
export async function mockApi(page: Page, role: 'ADMIN' | 'SUPER_ADMIN' | 'PARTNER' = 'ADMIN') {
  const state: MockState = {
    role,
    status: 'DRAFT',
    recordState: 'ACTIVE',
    visibility: 'HIDDEN',
    quota: 2,
    partnerApplicationState: 'PENDING_REVIEW',
    writes: [],
    conflict: false,
  }
  const managed = () => ({
    id,
    kind: content.kind,
    moduleKey: content.moduleKey,
    title: content.title,
    revision: 7,
    workflowStatus: state.status,
    visibility: state.visibility,
    recordState: state.recordState,
    publicationStatus: state.status === 'PUBLISHED' ? 'PUBLISHED' : 'UNPUBLISHED',
    submittedAt: state.status === 'PENDING_REVIEW' ? '2030-01-01T10:00:00Z' : null,
    latestReview: null,
  })
  await page.route('**/api/v1/**', async (route: Route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = req.method()
    const reply = (data: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(status < 400 ? { data, requestId: 'isolated-test' } : data),
      })
    if (state.fail && path === state.fail) return reply({ code: 'UNAVAILABLE', message: '模拟连接失败' }, 503)
    if (method !== 'GET') {
      const body =
        req.postData() && req.headers()['content-type']?.includes('application/json')
          ? JSON.parse(req.postData()!)
          : null
      state.writes.push({ path, method, body, headers: req.headers() })
      if (path === '/auth/session')
        return method === 'DELETE'
          ? route.fulfill({ status: 204 })
          : reply({ loginId: 'isolated-user', roles: [role] })
      if (path === '/auth/activation/consume') return reply({ loginId: 'isolated-user' })
      if (path.startsWith('/public/')) return reply({ id: 'isolated-receipt' }, 201)
      if (method === 'PATCH' && path.includes('/content-items/')) {
        if (state.conflict) return reply({ code: 'REVISION_MISMATCH' }, 412)
        return reply(managed())
      }
      if (path.endsWith('/submit-review')) state.status = 'PENDING_REVIEW'
      if (path.includes('/reviews/'))
        state.status =
          body.decision === 'APPROVE'
            ? 'APPROVED'
            : body.decision === 'REJECT'
              ? 'REJECTED'
              : 'CHANGES_REQUESTED'
      if (path.endsWith('/publish')) {
        state.status = 'PUBLISHED'
        state.visibility = 'PUBLIC'
      }
      if (path.endsWith('/hide')) state.visibility = 'HIDDEN'
      if (path.endsWith('/show')) state.visibility = 'PUBLIC'
      if (path.endsWith('/archive')) {
        state.recordState = 'ARCHIVED'
        state.visibility = 'HIDDEN'
      }
      if (path.endsWith('/restore')) state.recordState = 'ACTIVE'
      if (path.endsWith('/reopen')) state.status = 'CHANGES_REQUESTED'
      if (path === '/media/uploads')
        return reply(
          {
            id: '01HZX9Z8NQ2J3K4M5P6R7S8T9U',
            url: '/api/v1/media/preview',
            mediaType: 'image/png',
            width: 600,
            height: 400,
            altText: '测试图片',
          },
          201,
        )
      if (path.includes('/partner-applications/')) {
        if (path.endsWith('/approve')) {
          state.partnerApplicationState = 'APPROVED'
          return reply({
            partnerProfileId: 'partner-1',
            loginId: body.loginId,
            activationToken: 'isolated-token-not-real',
          })
        }
        state.partnerApplicationState = 'REJECTED'
        return reply({ id: 'application-1', state: 'REJECTED' })
      }
      if (path.endsWith('/content-quota')) {
        state.quota = body.quota
        return reply({ id: 'partner-1', displayName: '测试合作方', contentQuota: state.quota, active: true })
      }
      return reply(managed())
    }
    if (path === '/auth/me') return reply({ loginId: 'isolated-user', roles: [role] })
    if (path === '/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'isolated-csrf' })
    if (path === '/public/home')
      return reply({
        competitions: [content],
        resources: [
          { ...content, id: 'BBBBBBBBBBBBBBBBBBBBBBBBBB', kind: 'RESOURCE', title: '隔离测试资源' },
        ],
        recruitments: [
          { ...content, id: 'CCCCCCCCCCCCCCCCCCCCCCCCCC', kind: 'RECRUITMENT', title: '隔离测试招募' },
        ],
      })
    if (path.startsWith('/public/content/')) return reply(content)
    if (path === '/public/content-modules') return reply(modules)
    if (path === '/public/content') {
      const items = [
        content,
        { ...content, id: 'DDDDDDDDDDDDDDDDDDDDDDDDDD', title: '同类竞赛一' },
        { ...content, id: 'EEEEEEEEEEEEEEEEEEEEEEEEEE', title: '同类竞赛二' },
      ]
      return reply({ items, nextCursor: null })
    }
    if (/^\/public\/(competitions|resources|recruitments)$/.test(path)) {
      const kind = path.endsWith('resources')
        ? 'RESOURCE'
        : path.endsWith('recruitments')
          ? 'RECRUITMENT'
          : 'COMPETITION'
      const title =
        kind === 'RESOURCE' ? '隔离测试资源' : kind === 'RECRUITMENT' ? '隔离测试招募' : '隔离测试竞赛'
      const baseItem = {
        ...content,
        kind,
        moduleKey: kind === 'RESOURCE' ? 'resource' : kind === 'RECRUITMENT' ? 'recruitment' : 'competition',
        title,
        details:
          kind === 'RESOURCE'
            ? { resourceType: 'GUIDE', audience: '零基础' }
            : kind === 'RECRUITMENT'
              ? { deadlineDate: '2030-09-30', followUpUrl: 'https://example.com/community' }
              : content.details,
      }
      const items =
        kind === 'COMPETITION'
          ? [
              baseItem,
              { ...baseItem, id: 'DDDDDDDDDDDDDDDDDDDDDDDDDD', title: '同类竞赛一' },
              { ...baseItem, id: 'EEEEEEEEEEEEEEEEEEEEEEEEEE', title: '同类竞赛二' },
            ]
          : [baseItem]
      return reply({
        items: url.searchParams.get('q') === '不存在' ? [] : items,
        nextCursor: url.searchParams.get('cursor') ? undefined : 'next-test-cursor',
      })
    }
    if (path === '/content-modules') return reply(modules)
    if (path === '/admin/content-items') return reply({ items: [managed()], nextCursor: null })
    if (path === '/admin/review-queue')
      return reply({ items: state.status === 'PENDING_REVIEW' ? [managed()] : [], nextCursor: null })
    if (path.endsWith('/publication-readiness')) return reply({ ready: true, blockers: [] })
    if (path === '/partner/dashboard')
      return reply({
        displayName: '测试合作方',
        quota: state.quota,
        publicActiveCount: state.visibility === 'PUBLIC' ? 1 : 0,
        contentItems: { items: [managed()], nextCursor: null },
      })
    if (path.includes('/content-items/'))
      return reply({ ...managed(), draft: content, media: [], reviews: [] })
    if (path === '/admin/partner-applications') {
      const requestedStatus = url.searchParams.get('status') || 'PENDING_REVIEW'
      const visible = requestedStatus === 'ALL' || requestedStatus === state.partnerApplicationState
      return reply({
        items: visible
          ? [
              {
                id: 'application-1',
                displayName: '测试合作方',
                introduction: '模拟申请，不提交真实记录。',
                portfolioUrl: 'https://example.com',
                state: state.partnerApplicationState,
              },
            ]
          : [],
        nextCursor: null,
      })
    }
    if (path === '/admin/partner-profiles')
      return reply({
        items: [
          {
            id: 'partner-1',
            accountId: 'account-1',
            displayName: '测试合作方',
            contentQuota: state.quota,
            active: true,
            qualificationStatus: 'ACTIVE',
          },
        ],
        nextCursor: null,
      })
    if (path === '/admin/submissions')
      return reply({
        items: [
          {
            id: 'submission-1',
            submissionType: 'FEEDBACK',
            title: '测试反馈',
            message: '模拟公开输入',
            linkUrl: null,
            imageDescription: null,
            state: 'PENDING',
          },
        ],
        nextCursor: null,
      })
    if (path === '/admin/accounts')
      return reply({
        items: [{ id: 'account-2', loginId: 'other-admin', qualificationStatus: 'ACTIVE', roles: ['ADMIN'] }],
        nextCursor: null,
      })
    if (path === '/admin/audit-logs')
      return reply({
        items: [
          {
            id: 'audit-1',
            createdAt: '2030-01-01T10:00:00Z',
            actorAccountId: 'account-1',
            actorRole: 'ADMIN',
            action: 'CONTENT_UPDATED',
            objectType: 'CONTENT',
            objectId: id,
            reason: '测试',
            requestId: 'isolated-test',
          },
        ],
        nextCursor: null,
      })
    return reply({ code: 'UNEXPECTED_TEST_REQUEST', message: path }, 500)
  })
  return state
}
