import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { EditableContent, ManagedContent, PublicationReadiness } from '../lib/api'
import { AdminReviewWorkspace } from './AdminReviewWorkspace'

const mocks = vi.hoisted(() => ({
  adminContent: vi.fn(),
  adminDecision: vi.fn(),
  adminPublicationReadiness: vi.fn(),
  adminPublish: vi.fn(),
  adminQueue: vi.fn(),
}))
vi.mock('../lib/api', () => ({ api: mocks }))

const detail: EditableContent = {
  id: '01JREVIEW00000000000000000', revision: 7, workflowStatus: 'PENDING_REVIEW',
  publicationStatus: 'NONE', visibility: 'HIDDEN', recordState: 'ACTIVE',
  ownerPartnerProfileId: '01JPARTNER000000000000000', submittedAt: '2026-09-05T00:00:00Z',
  media: [{ id: '01JMEDIA00000000000000000', url: '/api/v1/media/01JMEDIA00000000000000000/preview', mediaType: 'image/png', width: 640, height: 360, altText: '审核证据图' }],
  reviews: [],
  draft: {
    kind: 'RESOURCE', moduleKey: 'resource.article', schemaVersion: 1, title: '待审核文章', summary: '管理员必须先看到全文。',
    markdownSource: '这是一段必须审阅的正文', body: { version: 1, blocks: [
      { type: 'paragraph', inlines: [{ type: 'text', text: '这是一段必须审阅的正文' }] },
      { type: 'image', mediaId: '01JMEDIA00000000000000000', alt: '审核证据图' },
    ] }, languageCode: 'zh-CN', sourceName: '原创文章', rightsBasis: '作者原创', authorDisplayName: '合成作者',
    reprintPermission: 'NOT_ALLOWED', adaptationPermission: 'NOT_ALLOWED', details: { resourceType: 'ARTICLE', sourceMode: 'ORIGINAL' },
  },
}

describe('管理员审核详情', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.adminContent.mockResolvedValue(detail) })

  it('shows the submitted document and reviews the exact loaded revision', async () => {
    const approved: ManagedContent = { id: detail.id, kind: 'RESOURCE', moduleKey: 'resource.article', workflowStatus: 'APPROVED', publicationStatus: 'NONE', visibility: 'HIDDEN', recordState: 'ACTIVE', title: '待审核文章', revision: 8 }
    const readiness: PublicationReadiness = { ready: true, blockers: [] }
    mocks.adminDecision.mockResolvedValue(approved)
    mocks.adminPublicationReadiness.mockResolvedValue(readiness)
    render(<MemoryRouter><AdminReviewWorkspace contentId={detail.id} /></MemoryRouter>)

    expect(await screen.findByText('这是一段必须审阅的正文')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '审核证据图' })).toHaveAttribute('src', expect.stringContaining('/preview'))
    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('审核决定'), 'APPROVE')
    await user.type(screen.getByLabelText('审核说明'), '正文与来源均已核验。')
    await user.click(screen.getByRole('button', { name: '保存审核决定' }))

    await waitFor(() => expect(mocks.adminDecision).toHaveBeenCalledWith(detail.id, 7, 'APPROVE', '正文与来源均已核验。'))
    expect(await screen.findByText('发布检查已通过，可以正式发布。')).toBeInTheDocument()
  })

  it('keeps the publication success result visible after refreshing the detail', async () => {
    const approvedDetail: EditableContent = { ...detail, workflowStatus: 'APPROVED' }
    const publishedDetail: EditableContent = { ...detail, workflowStatus: 'PUBLISHED', publicationStatus: 'PUBLISHED', visibility: 'PUBLIC' }
    mocks.adminContent.mockReset().mockResolvedValueOnce(approvedDetail).mockResolvedValue(publishedDetail)
    mocks.adminPublicationReadiness.mockResolvedValue({ ready: true, blockers: [] })
    mocks.adminPublish.mockResolvedValue({})
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    render(<MemoryRouter><AdminReviewWorkspace contentId={detail.id} /></MemoryRouter>)

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '正式发布' }))

    await waitFor(() => expect(mocks.adminPublish).toHaveBeenCalledWith(detail.id))
    expect(await screen.findByText('内容已正式发布，可从公开详情页访问。')).toBeInTheDocument()
    confirm.mockRestore()
  })
})
