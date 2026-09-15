import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api, request, type AdminDraft, type ManagedContent, type PublicContent } from './api'
import { prepareDraft, updateBody } from './draft'
import { availableActions } from '../pages/workspace'
import { extractDocumentHeadings } from '../components/document'
import { deadlineState } from '../pages/public'
import { markdownPreview } from './markdown'
afterEach(() => vi.restoreAllMocks())
describe('v0 contract protection', () => {
  it('preserves untouched rich blocks, details and additional fields', () => {
    const draft: AdminDraft = {
      kind: 'RESOURCE',
      title: '  标题 ',
      summary: '导读',
      languageCode: 'en',
      body: {
        version: 2,
        extra: 'keep',
        blocks: [
          {
            type: 'heading',
            level: 2,
            inlines: [{ type: 'link', text: '文本', href: 'https://example.com' }],
          },
          { type: 'image', mediaId: 'original', alt: '图片' },
          { type: 'future-block', nested: { retain: true } },
        ],
      },
      details: { audience: '原有', custom: { keep: true } },
      rightsBasis: '授权说明',
      reprintPermission: 'NOT_ALLOWED',
      markdownSource: '# 原稿',
      customMetadata: true,
    }
    const result = prepareDraft(draft, {
      key: 'resource',
      label: '资源',
      legacyKind: 'RESOURCE',
      active: true,
      allowsOriginalSource: false,
      fields: [],
    })
    expect(result.body).toEqual(draft.body)
    expect(result.details).toEqual(draft.details)
    expect(result.customMetadata).toBe(true)
    expect(result.markdownSource).toBe(draft.markdownSource)
    expect(result.languageCode).toBe('en')
    expect(draft.title).toBe('  标题 ')
    const edited = updateBody(result, {
      ...result.body,
      blocks: [...result.body.blocks, { type: 'paragraph', text: '新增' }],
    })
    expect(edited.body.blocks.slice(0, 3)).toEqual(draft.body.blocks)
    expect(edited.body.extra).toBe('keep')
    expect(edited.markdownSource).toBeUndefined()
  })
  it('never exposes publish or show controls for archived content', () => {
    const item = {
      id: 'x',
      kind: 'COMPETITION',
      title: '测试',
      revision: 1,
      workflowStatus: 'PUBLISHED',
      visibility: 'PUBLIC',
      recordState: 'ARCHIVED',
    } as ManagedContent
    expect(availableActions(item)).toEqual(['restore'])
  })
  it('sends v0 cookie/CSRF/revision/idempotency headers', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } })),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { id: 'content' } })))
    await api.partnerRevise('a/b', 7, { title: 'body' } as AdminDraft)
    const [url, init] = fetcher.mock.calls[1]
    expect(url).toBe('/api/v1/partner/content-items/a%2Fb')
    expect(init?.method).toBe('PATCH')
    expect(init?.credentials).toBe('include')
    expect(init?.headers).toMatchObject({
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'If-Match': '7',
      'X-CSRF-TOKEN': 'test-csrf',
    })
    expect((init!.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy()
  })
  it('rejects a frontend fallback HTML response instead of reporting empty data', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>wrong proxy</html>'))
    await expect(request('/public/home')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('keeps search and cursor server-side', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ data: { items: [] } })))
    await api.list('RESOURCE', ' hello world ', 'cursor/next')
    expect(fetcher.mock.calls[0][0]).toBe(
      '/api/v1/public/resources?pageSize=20&q=hello+world&cursor=cursor%2Fnext',
    )
  })
  it('forwards verification filters to the managed content list', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ data: { items: [], nextCursor: null } })))
    await api.adminContentItems({ verification: 'due' })
    expect(String(fetcher.mock.calls[0][0])).toBe('/api/v1/admin/content-items?pageSize=20&verification=due')
  })
  it('extracts stable document headings from text and rich inlines', () => {
    const headings = extractDocumentHeadings({
      version: 2,
      blocks: [
        { type: 'heading', level: 1, text: '开始' },
        { type: 'paragraph', text: '正文' },
        { type: 'heading', level: 2, inlines: [{ type: 'strong', text: '准备' }] },
        { type: 'heading', level: 2, text: '准备' },
      ],
    })
    expect(headings).toEqual([
      { id: 'section-0', level: 1, label: '开始', blockIndex: 0 },
      { id: 'section-2', level: 2, label: '准备', blockIndex: 2 },
      { id: 'section-3', level: 2, label: '准备', blockIndex: 3 },
    ])
  })
  it('derives public deadline state without inventing a backend field', () => {
    const item = {
      kind: 'COMPETITION',
      details: { deadlineDate: '2030-10-15' },
    } as unknown as PublicContent
    expect(deadlineState(item, new Date('2030-10-14T00:00:00+08:00').getTime())).toBe('active')
    expect(deadlineState(item, new Date('2030-10-16T00:00:00+08:00').getTime())).toBe('expired')
    expect(deadlineState({ ...item, details: {} })).toBe('unknown')
  })
  it('keeps aggregate search groups independent when one public endpoint fails', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/recruitments'))
        return new Response(JSON.stringify({ code: 'UNAVAILABLE', message: '招募检索失败' }), { status: 503 })
      const items = url.includes('/competitions') ? [{ id: 'one' }] : []
      return new Response(JSON.stringify({ data: { items } }))
    })
    const groups = await api.searchAll('  方向  ')
    expect(groups.map((group) => group.status)).toEqual(['success', 'error', 'empty'])
    expect(groups[1].error).toBe('招募检索失败')
    expect(fetcher.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/v1/public/competitions?pageSize=6&q=%E6%96%B9%E5%90%91',
      '/api/v1/public/recruitments?pageSize=6&q=%E6%96%B9%E5%90%91',
      '/api/v1/public/resources?pageSize=6&q=%E6%96%B9%E5%90%91',
    ])
  })
  it('sends the reviewed revision through If-Match', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: { headerName: 'X-CSRF-TOKEN', token: 'csrf' } })),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { id: 'item', revision: 9 } })))
    await api.adminDecision('item/a', 9, 'CHANGES_REQUESTED', '补充来源')
    expect(fetcher.mock.calls[1][0]).toBe('/api/v1/admin/reviews/item%2Fa/decision')
    expect(fetcher.mock.calls[1][1]?.headers).toMatchObject({ 'If-Match': '9', 'X-CSRF-TOKEN': 'csrf' })
  })
  it('preserves backend diagnostics and retry delay', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'RATE_LIMITED',
          message: '稍后重试',
          requestId: 'req-1',
          fieldErrors: { title: '标题无效' },
          retryable: true,
        }),
        { status: 429, headers: { 'Retry-After': '900' } },
      ),
    )
    const error = (await request('/public/home').catch((value) => value)) as ApiFailure
    expect(error).toMatchObject({
      code: 'RATE_LIMITED',
      requestId: 'req-1',
      retryAfterSeconds: 900,
      fieldErrors: { title: '标题无效' },
      retryable: true,
    })
  })
  it('passes module and one exact field filter to the generic public endpoint', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ data: { items: [] } })))
    await api.listModule('resource.article', {
      query: '经验',
      filterKey: 'sourceMode',
      filterValue: 'ORIGINAL',
      cursor: 'next',
    })
    expect(fetcher.mock.calls[0][0]).toBe(
      '/api/v1/public/content?pageSize=20&module=resource.article&q=%E7%BB%8F%E9%AA%8C&cursor=next&filterKey=sourceMode&filterValue=ORIGINAL',
    )
  })
  it('creates a safe local preview for markdown and controlled media', () => {
    expect(
      markdownPreview('# 标题\n\n正文 **重点**\n\n![图](media:01HZX9Z8NQ2J3K4M5P6R7S8T9U)').blocks,
    ).toEqual([
      { type: 'heading', level: 1, inlines: [{ type: 'text', text: '标题' }] },
      {
        type: 'paragraph',
        inlines: [
          { type: 'text', text: '正文 ' },
          { type: 'strong', text: '重点' },
        ],
      },
      { type: 'image', alt: '图', mediaId: '01HZX9Z8NQ2J3K4M5P6R7S8T9U' },
    ])
  })
  it('reuses a caller-provided idempotency key for a logical public submission', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ data: { id: 'submission' } })))
    await api.submit({ submissionType: 'FEEDBACK', title: '标题', message: '正文' }, 'same-attempt')
    expect(fetcher.mock.calls[0][1]?.headers).toMatchObject({ 'Idempotency-Key': 'same-attempt' })
  })
  it('parses the latest markdown once for save while preserving other draft fields', () => {
    const draft = {
      kind: 'RESOURCE',
      title: '标题',
      summary: '导读',
      languageCode: 'zh-CN',
      body: { version: 2, blocks: [] },
      markdownSource: '# 新正文',
      details: { hidden: { keep: true } },
      rightsBasis: '说明',
      reprintPermission: 'NOT_ALLOWED',
      customMetadata: 'keep-me',
    } as AdminDraft
    const result = prepareDraft(
      draft,
      {
        key: 'resource',
        label: '资源',
        legacyKind: 'RESOURCE',
        active: true,
        allowsOriginalSource: false,
        fields: [],
      },
      true,
    )
    expect(result.body).toEqual(markdownPreview('# 新正文'))
    expect(result.details).toEqual({ hidden: { keep: true } })
    expect(result.customMetadata).toBe('keep-me')
  })
  it('creates a regular admin with an idempotency key and does not persist the activation token in the client helper', async () => {
    const fetcher = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ data: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } })),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            data: {
              id: 'account',
              loginId: 'admin.example',
              qualificationStatus: 'PENDING',
              roles: ['ADMIN'],
              activationToken: 'once',
              activationExpiresAt: '2030-01-01T00:00:00Z',
            },
          }),
        ),
      )
    const created = await api.adminCreateAdmin('admin.example', '新增内容审核管理员')
    expect(created.roles).toEqual(['ADMIN'])
    expect(created.qualificationStatus).toBe('PENDING')
    const [, init] = fetcher.mock.calls[1]
    expect(fetcher.mock.calls[1][0]).toBe('/api/v1/admin/accounts')
    expect((init!.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy()
  })
})
