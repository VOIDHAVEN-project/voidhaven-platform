import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import type { AdminDraft, ContentField, ContentModule, ContentModuleSet, EditableContent, ManagedContent, PublicMedia, ReusePermission } from '../lib/api'
import { markdownPreview } from '../lib/markdown-preview'
import { ContentDocument } from './ContentDocument'
import { ContentMediaUploadForm } from './ContentMediaUploadForm'

type EditorMode = 'write' | 'split' | 'preview'
type Notice = { tone: 'success' | 'error'; text: string } | null

function detailsFor(module: ContentModule) {
  return Object.fromEntries(module.fields.map((field) => [field.key,
    field.type === 'select' ? field.options?.[0] ?? '' : field.type === 'boolean' ? false : field.type === 'tags' ? [] : field.type === 'number' ? null : '']))
}

function emptyDraft(module: ContentModule, schemaVersion: number): AdminDraft {
  const details = detailsFor(module)
  const original = module.allowsOriginalSource && details.sourceMode === 'ORIGINAL'
  return {
    kind: module.legacyKind, moduleKey: module.key, schemaVersion, title: '', summary: '', markdownSource: module.template,
    body: markdownPreview(module.template), languageCode: 'zh-CN', sourceUrl: original ? undefined : '', sourceName: original ? '原创文章' : '',
    rightsBasis: original ? '作者原创内容；发布方确认拥有公开发布权限。' : '已核验公开来源；站内仅作辅助导读。', authorDisplayName: '',
    reprintPermission: 'NOT_ALLOWED', adaptationPermission: 'NOT_ALLOWED', details,
  }
}

function sourceIsOriginal(module: ContentModule, details: Record<string, unknown>) { return module.allowsOriginalSource && details.sourceMode === 'ORIGINAL' }

function updateText(draft: AdminDraft, key: keyof AdminDraft, value: string): AdminDraft { return { ...draft, [key]: value } }

function ModuleFieldInput({ field, value, busy, onChange }: { field: ContentField; value: unknown; busy: boolean; onChange: (value: unknown) => void }) {
  const text = value == null ? '' : String(value)
  const control = field.type === 'select'
    ? <select value={text} onChange={(event) => onChange(event.target.value)} required={field.required} disabled={busy}>{!field.required ? <option value="">未设置</option> : null}{field.options?.map((option) => <option value={option} key={option}>{option}</option>)}</select>
    : field.type === 'textarea'
      ? <textarea value={text} onChange={(event) => onChange(event.target.value)} required={field.required} maxLength={2048} rows={3} disabled={busy} />
      : field.type === 'boolean'
        ? <input type="checkbox" checked={value === true} onChange={(event) => onChange(event.target.checked)} disabled={busy} />
        : field.type === 'number'
          ? <input type="number" value={text} onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))} required={field.required} disabled={busy} />
          : field.type === 'tags'
            ? <textarea value={Array.isArray(value) ? value.join('，') : ''} onChange={(event) => onChange(event.target.value.split(/[，,]/).map((tag) => tag.trim()).filter(Boolean))} rows={2} maxLength={820} disabled={busy} placeholder="用逗号分隔，最多 20 个" />
            : <input type={field.type === 'date' ? 'date' : field.type === 'url' ? 'url' : 'text'} value={text} onChange={(event) => onChange(event.target.value)} required={field.required} maxLength={field.type === 'url' ? 2048 : 240} disabled={busy} placeholder={field.type === 'datetime' ? '例如 2026-10-01T12:00:00Z' : undefined} />
  return <label className={['url', 'textarea', 'tags'].includes(field.type) ? 'full' : ''}>{field.label}{field.required ? '（必填）' : ''}{control}{field.help ? <span className="field-help">{field.help}</span> : null}</label>
}

export function ContentEditor({ moduleSet, initial, contentItemId, saveLabel, onSave, onCancel }: {
  moduleSet: ContentModuleSet; initial?: EditableContent | null; contentItemId?: string; saveLabel: string; onSave: (draft: AdminDraft) => Promise<ManagedContent>; onCancel?: () => void
}) {
  const availableModules = moduleSet.modules.filter((module) => module.active)
  const initialModule = availableModules.find((module) => module.key === initial?.draft.moduleKey) ?? availableModules[0]
  const [draft, setDraft] = useState<AdminDraft>(() => initial?.draft ?? emptyDraft(initialModule, moduleSet.version))
  const [editorMode, setEditorMode] = useState<EditorMode>('split')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)
  const [uploaded, setUploaded] = useState<PublicMedia[]>(() => initial?.media ?? [])
  const module = availableModules.find((candidate) => candidate.key === draft.moduleKey) ?? initialModule

  function chooseModule(event: ChangeEvent<HTMLSelectElement>) {
    const selected = availableModules.find((candidate) => candidate.key === event.target.value)
    if (!selected) return
    const next = emptyDraft(selected, moduleSet.version)
    setDraft({ ...next, title: draft.title, summary: draft.summary, authorDisplayName: draft.authorDisplayName })
  }

  function updateDetail(key: string, value: unknown) { setDraft((current) => ({ ...current, details: { ...current.details, [key]: value } })) }

  function insertUploaded(media: PublicMedia) {
    setUploaded((current) => [...current, media])
    setDraft((current) => current.markdownSource.includes(`media:${media.id}`) ? current : { ...current, markdownSource: `${current.markdownSource.trimEnd()}\n\n![${media.altText}](media:${media.id})\n` })
    setNotice({ tone: 'success', text: '图片已插入 Markdown 草稿。请保存草稿，使正文引用进入下一候选修订。' })
  }

  async function importMarkdown(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.item(0)
    event.currentTarget.value = ''
    if (!file) return
    if (file.size > 256 * 1024) { setNotice({ tone: 'error', text: 'Markdown 文件不能超过 256 KiB。' }); return }
    try { const imported = await file.text(); setDraft((current) => ({ ...current, markdownSource: imported })); setNotice({ tone: 'success', text: 'Markdown 已导入到当前草稿，尚未保存。' }) }
    catch { setNotice({ tone: 'error', text: '无法读取该 Markdown 文件。' }) }
  }

  function exportMarkdown() {
    const blob = new Blob([draft.markdownSource], { type: 'text/markdown;charset=utf-8' })
    const href = URL.createObjectURL(blob); const link = document.createElement('a')
    link.href = href; link.download = `${(draft.title || 'voidhaven-draft').replace(/[\\/:*?"<>|]+/g, '-').slice(0, 80)}.md`; link.click(); URL.revokeObjectURL(href)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true); setNotice(null)
    const original = sourceIsOriginal(module, draft.details)
    const outgoing: AdminDraft = {
      ...draft, kind: module.legacyKind, schemaVersion: moduleSet.version, body: markdownPreview(draft.markdownSource),
      sourceUrl: original ? undefined : draft.sourceUrl?.trim(), sourceName: original ? undefined : draft.sourceName?.trim(),
    }
    try {
      const result = await onSave(outgoing)
      setNotice({ tone: 'success', text: `“${result.title}”已保存为候选修订。` })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '草稿未保存。' }) }
    finally { setBusy(false) }
  }

  const preview = markdownPreview(draft.markdownSource)
  const original = sourceIsOriginal(module, draft.details)
  return <form className="content-editor" onSubmit={save}>
    <div className="editor-heading"><div><p className="eyebrow">创作编辑器</p><h2>{initial ? '编辑候选修订' : '从一篇内容开始'}</h2></div><div className="editor-tools"><label className="button ghost file-button">导入 .md<input type="file" accept=".md,text/markdown,text/plain" onChange={(event) => void importMarkdown(event)} /></label><button type="button" className="button ghost" onClick={exportMarkdown}>导出 .md</button></div></div>
    <p className="workspace-help">正文以 Markdown 创作为主。发布设置只收集筛选、来源、审核与版权所必需的信息；保存和送审均由服务端再次校验。</p>
    <div className="editor-basics">
      <label>内容模板<select value={draft.moduleKey} onChange={chooseModule} disabled={Boolean(initial) || busy}>{availableModules.map((candidate) => <option value={candidate.key} key={candidate.key}>{candidate.label}</option>)}</select></label>
      <label>标题<input value={draft.title} onChange={(event) => setDraft((current) => updateText(current, 'title', event.target.value))} required maxLength={240} disabled={busy} /></label>
      <label className="full">中文导读<textarea value={draft.summary} onChange={(event) => setDraft((current) => updateText(current, 'summary', event.target.value))} required maxLength={1000} rows={3} disabled={busy} /></label>
    </div>
    <div className="editor-tabs" role="tablist" aria-label="正文编辑模式">{([['write', '写作'], ['split', '分屏预览'], ['preview', '阅读预览']] as const).map(([value, label]) => <button key={value} type="button" role="tab" aria-selected={editorMode === value} className={editorMode === value ? 'active' : ''} onClick={() => setEditorMode(value)}>{label}</button>)}</div>
    <div className={`editor-body editor-${editorMode}`}>
      {editorMode !== 'preview' ? <label className="markdown-input"><span className="sr-only">Markdown 正文</span><textarea value={draft.markdownSource} onChange={(event) => setDraft((current) => updateText(current, 'markdownSource', event.target.value))} required maxLength={256 * 1024} rows={18} spellCheck="true" disabled={busy} placeholder="# 从一个具体问题开始" /></label> : null}
      {editorMode !== 'write' ? <div className="editor-preview"><p className="eyebrow">预览</p><ContentDocument document={preview} media={uploaded} label="Markdown 预览" /><p className="form-note">预览只用于写作；保存、送审和发布以服务端安全解析结果为准。</p></div> : null}
    </div>
    {contentItemId ? <ContentMediaUploadForm contentItemId={contentItemId} onUploaded={insertUploaded} /> : <p className="form-note">先保存草稿后即可上传图片，并将其插入正文位置。</p>}
    <details className="publish-settings"><summary>发布设置与审核信息</summary><div className="editor-settings">
      <label>内容语言<select value={draft.languageCode.startsWith('other:') ? 'other' : draft.languageCode} onChange={(event) => setDraft((current) => updateText(current, 'languageCode', event.target.value))} disabled={busy}><option value="zh-CN">中文</option><option value="en">英文</option><option value="zh-CN,en">中英双语</option><option value="other">其他语言</option></select></label>
      {draft.languageCode === 'other' || draft.languageCode.startsWith('other:') ? <label>其他语言名称<input value={draft.languageCode.startsWith('other:') ? draft.languageCode.slice(6) : ''} onChange={(event) => setDraft((current) => updateText(current, 'languageCode', `other:${event.target.value}`))} maxLength={30} disabled={busy} /></label> : null}
      <label>原始标题（非中文必填）<input value={draft.originalTitle ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'originalTitle', event.target.value))} maxLength={240} disabled={busy} /></label>
      <label>作者展示名<input value={draft.authorDisplayName} onChange={(event) => setDraft((current) => updateText(current, 'authorDisplayName', event.target.value))} required maxLength={120} disabled={busy} /></label>
      {module.fields.map((field) => <ModuleFieldInput key={field.key} field={field} value={draft.details[field.key]} busy={busy} onChange={(value) => updateDetail(field.key, value)} />)}
      {!original ? <><label className="full">官方或原始来源 URL<input type="url" value={draft.sourceUrl ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'sourceUrl', event.target.value))} required maxLength={2048} placeholder="https://" disabled={busy} /></label><label>来源名称<input value={draft.sourceName ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'sourceName', event.target.value))} required maxLength={240} disabled={busy} /></label></> : <p className="form-note full">此内容按原创文章提交。引用外部资料时，请在正文中使用 HTTPS 链接并在审核说明中清楚标注。</p>}
      <label className="full">权利来源/使用说明<textarea value={draft.rightsBasis} onChange={(event) => setDraft((current) => updateText(current, 'rightsBasis', event.target.value))} required maxLength={120} rows={2} disabled={busy} /></label>
      <label>署名转载<select value={draft.reprintPermission} onChange={(event) => setDraft((current) => ({ ...current, reprintPermission: event.target.value as ReusePermission }))} disabled={busy}><option value="NOT_ALLOWED">不允许</option><option value="ATTRIBUTION_ALLOWED">允许署名转载</option></select></label>
      <label>署名改编<select value={draft.adaptationPermission} onChange={(event) => setDraft((current) => ({ ...current, adaptationPermission: event.target.value as ReusePermission }))} disabled={busy}><option value="NOT_ALLOWED">不允许</option><option value="ATTRIBUTION_ALLOWED">允许署名改编</option></select></label>
    </div></details>
    <div className="editor-actions"><button className="button primary" disabled={busy}>{busy ? '保存中…' : saveLabel}</button>{onCancel ? <button className="button ghost" type="button" onClick={onCancel} disabled={busy}>取消编辑</button> : null}</div>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
  </form>
}
