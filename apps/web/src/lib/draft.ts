import type { AdminDraft, ContentModule, DocumentBody } from './api'
import { markdownPreview } from './markdown'
export function createDraft(module: ContentModule, version: number): AdminDraft {
  const details = Object.fromEntries(
    module.fields.map((field) => [
      field.key,
      field.type === 'select'
        ? field.required
          ? (field.options?.[0] ?? '')
          : ''
        : field.type === 'boolean'
          ? false
          : field.type === 'tags'
            ? []
            : '',
    ]),
  )
  return {
    kind: module.legacyKind,
    moduleKey: module.key,
    schemaVersion: version,
    ...(module.resourceConfig ? { resourceConfigVersion: module.resourceConfig.version } : {}),
    title: '',
    summary: '',
    body: markdownPreview(module.template || ''),
    markdownSource: module.template || '',
    languageCode: 'zh-CN',
    sourceUrl: '',
    sourceName: '',
    rightsBasis: '已核验公开来源；站内仅作辅助导读。',
    authorDisplayName: '',
    reprintPermission: 'NOT_ALLOWED',
    adaptationPermission: 'NOT_ALLOWED',
    details,
  }
}
export function updateBody(draft: AdminDraft, body: DocumentBody): AdminDraft {
  // The structured body is authoritative. Do not retain stale optional Markdown after a block edit.
  const next = { ...draft, body }
  delete next.markdownSource
  return next
}
export function updateMarkdown(draft: AdminDraft, markdownSource: string): AdminDraft {
  return { ...draft, markdownSource, body: markdownPreview(markdownSource) }
}
export function prepareDraft(draft: AdminDraft, module: ContentModule, parseMarkdown = false): AdminDraft {
  const markdownSource = draft.markdownSource ?? ''
  const next = {
    ...draft,
    markdownSource,
    body: parseMarkdown ? markdownPreview(markdownSource) : draft.body,
    title: draft.title.trim(),
    summary: draft.summary.trim(),
    details: { ...draft.details },
  }
  if (module.allowsOriginalSource && draft.details.sourceMode === 'ORIGINAL') {
    delete next.sourceUrl
    delete next.sourceName
  }
  return next
}
