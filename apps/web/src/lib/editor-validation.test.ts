import { describe, expect, it } from 'vitest'
import type { AdminDraft, ContentModule } from './api'
import { collectEditorIssues } from './editor-validation'

const module: ContentModule = {
  key: 'competition',
  label: '竞赛',
  legacyKind: 'COMPETITION',
  active: true,
  allowsOriginalSource: false,
  fields: [
    { key: 'officialRulesUrl', label: '官方规则链接', type: 'url', required: true, filterable: false },
    { key: 'deadlineDate', label: '截止日期', type: 'date', required: false, filterable: true },
  ],
}

const article: ContentModule = {
  key: 'resource.article',
  label: '文章',
  legacyKind: 'RESOURCE',
  active: true,
  allowsOriginalSource: true,
  fields: [{ key: 'sourceMode', label: '来源方式', type: 'select', required: true, filterable: true }],
}

function draft(overrides: Partial<AdminDraft> = {}): AdminDraft {
  return {
    kind: 'COMPETITION',
    moduleKey: 'competition',
    title: '标题',
    summary: '导读',
    body: { version: 1, blocks: [] },
    languageCode: 'zh-CN',
    sourceName: '来源',
    sourceUrl: 'https://example.com',
    rightsBasis: '说明',
    authorDisplayName: '作者',
    reprintPermission: 'NOT_ALLOWED',
    details: { officialRulesUrl: 'https://example.com/rules' },
    ...overrides,
  }
}

describe('collectEditorIssues', () => {
  it('returns no issues for a complete competition draft', () => {
    expect(collectEditorIssues(draft(), '# 正文', module)).toEqual([])
  })

  it('points missing required values to their sections', () => {
    const issues = collectEditorIssues(
      draft({
        title: ' ',
        summary: '',
        authorDisplayName: '',
        sourceName: '',
        sourceUrl: '',
        rightsBasis: '',
        details: {},
      }),
      '   ',
      module,
    )
    expect(issues.map((item) => `${item.section}:${item.field}`)).toEqual([
      'basics:title',
      'basics:summary',
      'body:markdown',
      'module:officialRulesUrl',
      'publish:authorDisplayName',
      'publish:sourceName',
      'publish:sourceUrl',
      'publish:rightsBasis',
    ])
  })

  it('skips source url checks for original articles', () => {
    const issues = collectEditorIssues(
      draft({
        kind: 'RESOURCE',
        moduleKey: 'resource.article',
        sourceName: '',
        sourceUrl: '',
        details: { sourceMode: 'ORIGINAL' },
      }),
      '# 正文',
      article,
    )
    expect(issues.map((item) => item.field)).toEqual([])
  })
})
