import { describe, expect, it } from 'vitest'
import type { AdminDraft } from './api'
import {
  applyStoredEditorDraft,
  createMemoryDraftBackend,
  editorDraftKey,
  editorDraftsDiffer,
  isExpiredEditorDraft,
  readEditorDraft,
  sanitizeEditorDraft,
  writeEditorDraft,
  EDITOR_DRAFT_TTL_MS,
} from './editor-draft-store'

const draft: AdminDraft = {
  kind: 'COMPETITION',
  moduleKey: 'competition',
  title: '标题',
  summary: '导读',
  body: { version: 1, blocks: [] },
  markdownSource: '# 正文',
  languageCode: 'zh-CN',
  sourceName: '来源',
  sourceUrl: 'https://example.com',
  rightsBasis: '说明',
  authorDisplayName: '作者',
  reprintPermission: 'NOT_ALLOWED',
  details: { officialRulesUrl: 'https://example.com/rules', activationToken: 'secret' },
}

describe('editor draft store', () => {
  it('keys new drafts and existing items separately', () => {
    expect(editorDraftKey()).toBe('new')
    expect(editorDraftKey('AAAAAAAAAAAAAAAAAAAAAAAAAA')).toBe('item:AAAAAAAAAAAAAAAAAAAAAAAAAA')
  })

  it('omits tokens and keeps editable fields', () => {
    const stored = sanitizeEditorDraft(draft)
    expect(stored.title).toBe('标题')
    expect((stored.details as Record<string, unknown>).officialRulesUrl).toBe('https://example.com/rules')
    expect(stored.details).not.toHaveProperty('activationToken')
    expect(stored).not.toHaveProperty('markdownSource')
  })

  it('expires records after seven days', () => {
    expect(
      isExpiredEditorDraft(
        { version: 1, savedAt: 1, tabId: 'a', markdownSource: '', draft: {} },
        EDITOR_DRAFT_TTL_MS + 2,
      ),
    ).toBe(true)
    expect(
      isExpiredEditorDraft({ version: 1, savedAt: 100, tabId: 'a', markdownSource: '', draft: {} }, 200),
    ).toBe(false)
  })

  it('detects restored content that still differs from the server draft', () => {
    const record = {
      version: 1 as const,
      savedAt: 1,
      tabId: 'a',
      markdownSource: '# 本地',
      draft: sanitizeEditorDraft(draft),
    }
    expect(editorDraftsDiffer(record, draft, '# 正文')).toBe(true)
    expect(editorDraftsDiffer({ ...record, markdownSource: '# 正文' }, draft, '# 正文')).toBe(false)
  })

  it('round-trips through the memory backend and merges onto the current draft', async () => {
    const backend = createMemoryDraftBackend()
    await writeEditorDraft(
      'new',
      {
        version: 1,
        savedAt: Date.now(),
        tabId: 'tab',
        markdownSource: '# 本地',
        draft: sanitizeEditorDraft({ ...draft, title: '本地标题' }),
      },
      backend,
    )
    const stored = await readEditorDraft('new', backend)
    expect(stored?.draft.title).toBe('本地标题')
    expect(applyStoredEditorDraft(draft, stored!).title).toBe('本地标题')
    expect(applyStoredEditorDraft(draft, stored!).markdownSource).toBe('# 本地')
  })
})
