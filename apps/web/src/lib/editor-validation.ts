import type { AdminDraft, ContentModule } from './api'

export const EDITOR_SECTIONS = [
  { id: 'basics', label: '基础信息' },
  { id: 'module', label: '业务信息' },
  { id: 'body', label: '正文与媒体' },
  { id: 'publish', label: '来源与发布' },
] as const

export type EditorSectionId = (typeof EDITOR_SECTIONS)[number]['id']

export const EDITOR_WORKSPACES = [
  { id: 'body', label: '正文与媒体' },
  { id: 'structured', label: '结构化信息' },
] as const

export type EditorWorkspaceId = (typeof EDITOR_WORKSPACES)[number]['id']

export function editorSectionWorkspace(section: EditorSectionId): EditorWorkspaceId {
  return section === 'body' ? 'body' : 'structured'
}

export type EditorIssue = {
  section: EditorSectionId
  field: string
  message: string
}

function blank(value: unknown) {
  if (value == null) return true
  if (Array.isArray(value)) return value.length === 0
  return String(value).trim() === ''
}

export function collectEditorIssues(
  draft: AdminDraft,
  markdownSource: string,
  module: ContentModule,
): EditorIssue[] {
  const issues: EditorIssue[] = []
  if (blank(draft.title)) issues.push({ section: 'basics', field: 'title', message: '请填写标题。' })
  if (blank(draft.summary)) issues.push({ section: 'basics', field: 'summary', message: '请填写中文导读。' })
  if (blank(markdownSource)) issues.push({ section: 'body', field: 'markdown', message: '请填写正文。' })
  for (const field of module.fields) {
    if (!field.required || field.deprecated) continue
    if (blank(draft.details[field.key])) {
      issues.push({ section: 'module', field: field.key, message: `请填写${field.label}。` })
    }
  }
  if (blank(draft.authorDisplayName)) {
    issues.push({ section: 'publish', field: 'authorDisplayName', message: '请填写作者展示名。' })
  }
  const original = module.allowsOriginalSource && draft.details.sourceMode === 'ORIGINAL'
  if (!original) {
    if (blank(draft.sourceName))
      issues.push({ section: 'publish', field: 'sourceName', message: '请填写来源名称。' })
    if (blank(draft.sourceUrl)) {
      issues.push({ section: 'publish', field: 'sourceUrl', message: '请填写官方或原始来源 URL。' })
    }
  }
  if (blank(draft.rightsBasis)) {
    issues.push({ section: 'publish', field: 'rightsBasis', message: '请填写权利 / 使用说明。' })
  }
  return issues
}

export function editorFieldId(field: string) {
  return `editor-field-${field}`
}
