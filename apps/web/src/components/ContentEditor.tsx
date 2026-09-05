import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import type { AdminDraft, ContentField, ContentModule, ContentModuleSet, EditableContent, ManagedContent, PublicMedia, ReusePermission } from '../lib/api'
import { markdownPreview } from '../lib/markdown-preview'
import { DRAFT_DISCARD_AUTHORIZED_EVENT } from '../lib/draft-guard'
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

export function ContentEditor({ moduleSet, initial, contentItemId, saveLabel, onSave, onSubmitForReview, onDirtyChange, onCancel }: {
  moduleSet: ContentModuleSet; initial?: EditableContent | null; contentItemId?: string; saveLabel: string; onSave: (draft: AdminDraft, expectedVersion?: number) => Promise<ManagedContent>; onSubmitForReview?: (saved: ManagedContent) => Promise<void>; onDirtyChange?: (dirty: boolean) => void; onCancel?: () => void
}) {
  const availableModules = moduleSet.modules.filter((module) => module.active)
  const initialModule = availableModules.find((module) => module.key === initial?.draft.moduleKey) ?? availableModules[0]
  const [draft, setDraft] = useState<AdminDraft>(() => initial?.draft ?? emptyDraft(initialModule, moduleSet.version))
  const [editorMode, setEditorMode] = useState<EditorMode>('split')
  const [saving, setSaving] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)
  const [uploaded, setUploaded] = useState<PublicMedia[]>(() => initial?.media ?? [])
  const [savedSignature, setSavedSignature] = useState(() => JSON.stringify(initial?.draft ?? emptyDraft(initialModule, moduleSet.version)))
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null)
  const draftRef = useRef(draft)
  const savedSignatureRef = useRef(savedSignature)
  const expectedVersion = useRef(initial?.revision)
  const savePromise = useRef<Promise<ManagedContent> | null>(null)
  const markdownInput = useRef<HTMLTextAreaElement>(null)
  const pendingSelection = useRef<number | null>(null)
  const discardAuthorized = useRef(false)
  const module = availableModules.find((candidate) => candidate.key === draft.moduleKey) ?? initialModule
  draftRef.current = draft
  const dirty = JSON.stringify(draft) !== savedSignature

  useEffect(() => {
    if (dirty) discardAuthorized.current = false
    onDirtyChange?.(dirty)
    return () => onDirtyChange?.(false)
  }, [dirty, onDirtyChange])

  useEffect(() => {
    const authorize = () => { discardAuthorized.current = true }
    window.addEventListener(DRAFT_DISCARD_AUTHORIZED_EVENT, authorize)
    return () => window.removeEventListener(DRAFT_DISCARD_AUTHORIZED_EVENT, authorize)
  }, [])

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { if (!discardAuthorized.current) event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  useEffect(() => {
    if (pendingSelection.current == null || editorMode === 'preview') return
    const cursor = pendingSelection.current
    pendingSelection.current = null
    markdownInput.current?.focus()
    markdownInput.current?.setSelectionRange(cursor, cursor)
  }, [draft.markdownSource, editorMode])

  function chooseModule(event: ChangeEvent<HTMLSelectElement>) {
    const selected = availableModules.find((candidate) => candidate.key === event.target.value)
    if (!selected) return
    const next = emptyDraft(selected, moduleSet.version)
    setDraft({ ...next, title: draft.title, summary: draft.summary, authorDisplayName: draft.authorDisplayName })
  }

  function updateDetail(key: string, value: unknown) { setDraft((current) => ({ ...current, details: { ...current.details, [key]: value } })) }

  function insertUploaded(media: PublicMedia) {
    setUploaded((current) => [...current, media])
    setDraft((current) => {
      if (current.markdownSource.includes(`media:${media.id}`)) return current
      const input = markdownInput.current
      const start = input?.selectionStart ?? current.markdownSource.length
      const end = input?.selectionEnd ?? start
      const marker = `![${media.altText}](media:${media.id})`
      const before = current.markdownSource.slice(0, start)
      const after = current.markdownSource.slice(end)
      const prefix = before.length === 0 || before.endsWith('\n\n') ? '' : before.endsWith('\n') ? '\n' : '\n\n'
      const suffix = after.length === 0 || after.startsWith('\n\n') ? '\n' : after.startsWith('\n') ? '\n' : '\n\n'
      pendingSelection.current = before.length + prefix.length + marker.length
      return { ...current, markdownSource: `${before}${prefix}${marker}${suffix}${after}` }
    })
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

  function outgoingDraft(current: AdminDraft) {
    const currentModule = availableModules.find((candidate) => candidate.key === current.moduleKey) ?? initialModule
    const original = sourceIsOriginal(currentModule, current.details)
    return {
      ...current, kind: currentModule.legacyKind, schemaVersion: moduleSet.version, body: markdownPreview(current.markdownSource),
      sourceUrl: original ? undefined : current.sourceUrl?.trim(), sourceName: original ? undefined : current.sourceName?.trim(),
    } satisfies AdminDraft
  }

  async function persistDraft(mode: 'manual' | 'auto' = 'manual'): Promise<ManagedContent> {
    if (savePromise.current) {
      const result = await savePromise.current
      if (JSON.stringify(draftRef.current) !== savedSignatureRef.current) return persistDraft(mode)
      return result
    }
    const localDraft = draftRef.current
    const current = outgoingDraft(localDraft)
    const capturedSignature = JSON.stringify(localDraft)
    setSaving(true)
    if (mode === 'manual') setNotice(null)
    const task = Promise.resolve().then(() => onSave(current, expectedVersion.current))
    savePromise.current = task
    try {
      const result = await task
      expectedVersion.current = result.revision
      savedSignatureRef.current = capturedSignature
      setSavedSignature(capturedSignature)
      setLastSavedAt(new Date())
      if (mode === 'manual') setNotice({ tone: 'success', text: `“${result.title}”已保存。` })
      return result
    } catch (error) {
      const message = error instanceof Error ? error.message : '草稿未保存。'
      setNotice({ tone: 'error', text: `${message} 本地内容仍保留，可先导出 Markdown。` })
      throw error
    } finally {
      savePromise.current = null
      setSaving(false)
    }
  }

  useEffect(() => {
    if (!contentItemId || !dirty || saving) return
    const timer = window.setTimeout(() => { void persistDraft('auto').catch(() => undefined) }, 2000)
    return () => window.clearTimeout(timer)
    // persistDraft intentionally reads the latest draft through refs.
    // oxlint-disable-next-line react-hooks/exhaustive-deps
  }, [contentItemId, dirty, draft, saving])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null
    if (submitter?.dataset.intent !== 'review' || !onSubmitForReview) {
      await persistDraft('manual').catch(() => undefined)
      return
    }
    setSubmitting(true)
    let draftSaved = false
    try {
      const saved = await persistDraft('manual')
      draftSaved = true
      await onSubmitForReview(saved)
    } catch (error) {
      if (draftSaved) {
        const message = error instanceof Error ? error.message : '送审未完成。'
        setNotice({ tone: 'error', text: `${message} 草稿已经保存，可修正后重新送审。` })
      }
    } finally {
      setSubmitting(false)
    }
  }

  function cancel() {
    if (dirty && !window.confirm('当前还有未保存修改，确定放弃并离开编辑器吗？')) return
    onCancel?.()
  }

  const preview = markdownPreview(draft.markdownSource)
  const original = sourceIsOriginal(module, draft.details)
  return <form className="content-editor" onSubmit={save}>
    <div className="editor-heading"><div><p className="eyebrow">创作编辑器</p><h2>{initial ? '编辑候选修订' : '从一篇内容开始'}</h2></div><div className="editor-tools"><label className="button ghost file-button">导入 .md<input type="file" accept=".md,text/markdown,text/plain" onChange={(event) => void importMarkdown(event)} /></label><button type="button" className="button ghost" onClick={exportMarkdown}>导出 .md</button></div></div>
    <p className="workspace-help">正文以 Markdown 创作为主。发布设置只收集筛选、来源、审核与版权所必需的信息；保存和送审均由服务端再次校验。</p>
    <div className="editor-basics">
      <label>内容模板<select value={draft.moduleKey} onChange={chooseModule} disabled={Boolean(initial) || saving}>{availableModules.map((candidate) => <option value={candidate.key} key={candidate.key}>{candidate.label}</option>)}</select></label>
      <label>标题<input value={draft.title} onChange={(event) => setDraft((current) => updateText(current, 'title', event.target.value))} required maxLength={240} /></label>
      <label className="full">中文导读<textarea value={draft.summary} onChange={(event) => setDraft((current) => updateText(current, 'summary', event.target.value))} required maxLength={1000} rows={3} /></label>
    </div>
    <div className="editor-tabs" role="tablist" aria-label="正文编辑模式">{([['write', '写作'], ['split', '分屏预览'], ['preview', '阅读预览']] as const).map(([value, label]) => <button key={value} type="button" role="tab" aria-selected={editorMode === value} className={editorMode === value ? 'active' : ''} onClick={() => setEditorMode(value)}>{label}</button>)}</div>
    <div className={`editor-body editor-${editorMode}`}>
      {editorMode !== 'preview' ? <label className="markdown-input"><span className="sr-only">Markdown 正文</span><textarea ref={markdownInput} value={draft.markdownSource} onChange={(event) => setDraft((current) => updateText(current, 'markdownSource', event.target.value))} required maxLength={256 * 1024} rows={18} spellCheck="true" placeholder="# 从一个具体问题开始" /></label> : null}
      {editorMode !== 'write' ? <div className="editor-preview"><p className="eyebrow">预览</p><ContentDocument document={preview} media={uploaded} label="Markdown 预览" /><p className="form-note">预览只用于写作；保存、送审和发布以服务端安全解析结果为准。</p></div> : null}
    </div>
    {contentItemId ? <ContentMediaUploadForm contentItemId={contentItemId} beforeUpload={async () => { if (dirty) await persistDraft('auto') }} onUploaded={insertUploaded} /> : <p className="form-note">先保存草稿后即可上传图片，并将其插入正文位置。</p>}
    <details className="publish-settings"><summary>发布设置与审核信息</summary><div className="editor-settings">
      <label>内容语言<select value={draft.languageCode.startsWith('other:') ? 'other' : draft.languageCode} onChange={(event) => setDraft((current) => updateText(current, 'languageCode', event.target.value))}><option value="zh-CN">中文</option><option value="en">英文</option><option value="zh-CN,en">中英双语</option><option value="other">其他语言</option></select></label>
      {draft.languageCode === 'other' || draft.languageCode.startsWith('other:') ? <label>其他语言名称<input value={draft.languageCode.startsWith('other:') ? draft.languageCode.slice(6) : ''} onChange={(event) => setDraft((current) => updateText(current, 'languageCode', `other:${event.target.value}`))} maxLength={30} /></label> : null}
      <label>原始标题（非中文必填）<input value={draft.originalTitle ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'originalTitle', event.target.value))} maxLength={240} /></label>
      <label>作者展示名<input value={draft.authorDisplayName} onChange={(event) => setDraft((current) => updateText(current, 'authorDisplayName', event.target.value))} required maxLength={120} /></label>
      {module.fields.map((field) => <ModuleFieldInput key={field.key} field={field} value={draft.details[field.key]} busy={false} onChange={(value) => updateDetail(field.key, value)} />)}
      {!original ? <><label className="full">官方或原始来源 URL<input type="url" value={draft.sourceUrl ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'sourceUrl', event.target.value))} required maxLength={2048} placeholder="https://" /></label><label>来源名称<input value={draft.sourceName ?? ''} onChange={(event) => setDraft((current) => updateText(current, 'sourceName', event.target.value))} required maxLength={240} /></label></> : <p className="form-note full">此内容按原创文章提交。引用外部资料时，请在正文中使用 HTTPS 链接并在审核说明中清楚标注。</p>}
      <label className="full">权利来源/使用说明<textarea value={draft.rightsBasis} onChange={(event) => setDraft((current) => updateText(current, 'rightsBasis', event.target.value))} required maxLength={120} rows={2} /></label>
      <label>署名转载<select value={draft.reprintPermission} onChange={(event) => setDraft((current) => ({ ...current, reprintPermission: event.target.value as ReusePermission }))}><option value="NOT_ALLOWED">不允许</option><option value="ATTRIBUTION_ALLOWED">允许署名转载</option></select></label>
      <label>署名改编<select value={draft.adaptationPermission} onChange={(event) => setDraft((current) => ({ ...current, adaptationPermission: event.target.value as ReusePermission }))}><option value="NOT_ALLOWED">不允许</option><option value="ATTRIBUTION_ALLOWED">允许署名改编</option></select></label>
    </div></details>
    <div className="editor-actions"><button className="button primary" type="submit" disabled={saving || submitting}>{saving ? '保存中…' : saveLabel}</button>{onSubmitForReview ? <button className="button ghost" type="submit" data-intent="review" disabled={saving || submitting}>{submitting ? '正在送审…' : '保存并送审'}</button> : null}{onCancel ? <button className="button ghost" type="button" onClick={cancel} disabled={saving || submitting}>取消编辑</button> : null}<span className="editor-save-state" role="status">{submitting ? '正在完成保存并送审…' : saving ? '正在保存…' : dirty ? '有未保存修改' : lastSavedAt ? `已保存 ${lastSavedAt.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '当前草稿已同步'}</span></div>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
  </form>
}
