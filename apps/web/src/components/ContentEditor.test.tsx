import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AdminDraft, ContentModuleSet, ManagedContent } from '../lib/api'
import { ContentEditor } from './ContentEditor'

const moduleSet: ContentModuleSet = {
  version: 1,
  modules: [{
    key: 'resource.article', label: '文章 / 经验', legacyKind: 'RESOURCE', active: true,
    allowsOriginalSource: true, requiresOfficialRules: false, deadlinePolicy: 'NONE',
    template: '# 写下你的经验\n\n从一个具体问题开始。',
    fields: [
      { key: 'sourceMode', label: '来源方式', type: 'select', required: true, filterable: true, options: ['ORIGINAL', 'EXTERNAL'] },
      { key: 'resourceType', label: '资源类型', type: 'select', required: true, filterable: true, options: ['ARTICLE'] },
    ],
  }],
}

describe('ContentEditor', () => {
  it('creates an original Markdown article through the shared module editor', async () => {
    const saved: ManagedContent = { id: '01JARTICLE00000000000000000', kind: 'RESOURCE', moduleKey: 'resource.article', workflowStatus: 'DRAFT', publicationStatus: 'NONE', visibility: 'HIDDEN', recordState: 'ACTIVE', title: '一次复盘', revision: 0 }
    const onSave = vi.fn(async (_draft: AdminDraft): Promise<ManagedContent> => saved)
    render(<ContentEditor moduleSet={moduleSet} saveLabel="保存草稿" onSave={onSave} />)

    expect(screen.getByRole('heading', { name: '写下你的经验' })).toBeInTheDocument()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('标题'), '一次复盘')
    await user.type(screen.getByLabelText('中文导读'), '记录一次完整的学习过程。')
    await user.type(screen.getByLabelText('作者展示名'), '合成作者')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    await waitFor(() => expect(onSave).toHaveBeenCalledOnce())
    expect(onSave.mock.calls[0][0]).toMatchObject({ moduleKey: 'resource.article', markdownSource: expect.stringContaining('# 写下你的经验'), sourceUrl: undefined, sourceName: undefined })
    expect(await screen.findByText('“一次复盘”已保存为候选修订。')).toBeInTheDocument()
  })
})
