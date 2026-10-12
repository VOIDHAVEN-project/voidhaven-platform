import {
  forwardRef,
  memo,
  startTransition,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from 'react'
import { createPortal } from 'react-dom'
import { Copy, ImagePlus, Maximize2, Minimize2, Trash2 } from 'lucide-react'
import { api, type AdminDraft, type EditablePartnerContent, type ModuleSet } from '../lib/api'
import { copyText } from '../lib/copy-text'
import { createDraft, prepareDraft } from '../lib/draft'
import {
  applyStoredEditorDraft,
  clearEditorDraft,
  editorDraftKey,
  editorDraftsDiffer,
  EDITOR_DRAFT_CHANNEL,
  formatDraftSavedAt,
  readEditorDraft,
  sanitizeEditorDraft,
  writeEditorDraft,
  type EditorDraftRecord,
} from '../lib/editor-draft-store'
import { applyMarkdownIndent } from '../lib/editor-indent'
import {
  mediaAltInBody,
  mediaInsertToken,
  mediaUploadState,
  referencedMediaIds,
  replaceMediaAltText,
  stripMediaReferences,
} from '../lib/editor-media'
import {
  collectEditorIssues,
  EDITOR_SECTIONS,
  EDITOR_WORKSPACES,
  editorFieldId,
  editorSectionWorkspace,
  type EditorIssue,
  type EditorWorkspaceId,
} from '../lib/editor-validation'
import { DynamicField } from './module-field'
import { ResourceFields } from './resource-fields'
import { resourceEditorConfig } from '../lib/resource-config'
import { useResource } from '../lib/hooks'
import { markdownPreview } from '../lib/markdown'
import { BusyLabel, ErrorState, LoadingRows, Notice, message, type NoticeValue } from './ui'
import { DocumentView } from './document'

export type EditorSaveMode = { continueEditing: boolean }

export function ContentEditor({
  editing,
  onSave,
  onSaveAndSubmit,
  onContinueAfterSave,
  onClose,
  onDirtyChange,
}: {
  editing?: EditablePartnerContent
  onSave: (draft: AdminDraft, mode: EditorSaveMode) => Promise<void>
  onSaveAndSubmit?: (draft: AdminDraft) => Promise<void>
  onContinueAfterSave?: () => void | Promise<void>
  onClose: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const state = useResource(api.modules)
  if (state.loading)
    return (
      <section className="content-editor-page">
        <LoadingRows />
      </section>
    )
  if (state.error || !state.data?.modules.length) {
    return (
      <section className="content-editor-page">
        <ErrorState error={state.error || '当前没有可用的内容模板。'} retry={state.reload} />
      </section>
    )
  }
  return (
    <DraftEditor
      modules={state.data}
      editing={editing}
      onSave={onSave}
      onClose={onClose}
      onSaveAndSubmit={onSaveAndSubmit}
      onContinueAfterSave={onContinueAfterSave}
      onDirtyChange={onDirtyChange}
    />
  )
}

type MarkdownHandle = {
  getSource: () => string
  insert: (token: string) => void
  applySource: (value: string) => void
  focusSource: () => void
  exitFocus: () => void
}

function createTabId() {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  } catch {
    /* fall through */
  }
  return `tab-${Date.now().toString(36)}-${Math.random().toString(16).slice(2)}`
}

function DraftEditor({
  modules,
  editing,
  onSave,
  onSaveAndSubmit,
  onContinueAfterSave,
  onClose,
  onDirtyChange,
}: {
  modules: ModuleSet
  editing?: EditablePartnerContent
  onSave: (draft: AdminDraft, mode: EditorSaveMode) => Promise<void>
  onSaveAndSubmit?: (draft: AdminDraft) => Promise<void>
  onContinueAfterSave?: () => void | Promise<void>
  onClose: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const first =
    modules.modules.find((item) => item.key === editing?.draft.moduleKey) ??
    modules.modules.find((item) => item.legacyKind === editing?.draft.kind) ??
    modules.modules[0]
  const [draft, setDraft] = useState<AdminDraft>(() =>
    structuredClone(editing?.draft ?? createDraft(first, modules.version)),
  )
  const [dirty, setDirty] = useState(false)
  const dirtyRef = useRef(false)
  const draftRef = useRef(draft)
  draftRef.current = draft
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [media, setMedia] = useState(editing?.media ?? [])
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [issues, setIssues] = useState<EditorIssue[]>([])
  const [workspace, setWorkspace] = useState<EditorWorkspaceId>('body')
  const [focusMode, setFocusMode] = useState(false)
  const [restoreOffer, setRestoreOffer] = useState<EditorDraftRecord | null>(null)
  const [tabConflict, setTabConflict] = useState(false)
  const [draftSavedAt, setDraftSavedAt] = useState<number | null>(null)
  const [workbenchKey, setWorkbenchKey] = useState(0)
  const markdownRef = useRef<MarkdownHandle>(null)
  const submitIntent = useRef<'draft' | 'draft-continue' | 'submit'>('draft')
  const persistTimer = useRef(0)
  const tabId = useRef(createTabId()).current
  const module = modules.modules.find((item) => item.key === draft.moduleKey) ?? first
  const [refreshedResourceConfig, setRefreshedResourceConfig] = useState<
    import('../lib/api').ResourceConfig | null
  >(null)
  const resourceConfig = resourceEditorConfig(
    refreshedResourceConfig ?? module.resourceConfig,
    editing?.resourceConfig,
    draft.details,
  )
  const original = module.allowsOriginalSource && draft.details.sourceMode === 'ORIGINAL'
  const storageKey = editorDraftKey(editing?.id)
  const invalid = issues.map((item) => item.field)

  const persistLocal = useCallback(async () => {
    if (!dirtyRef.current) return
    try {
      const savedAt = Date.now()
      await writeEditorDraft(storageKey, {
        version: 1,
        savedAt,
        tabId,
        markdownSource: markdownRef.current?.getSource() ?? draftRef.current.markdownSource ?? '',
        draft: sanitizeEditorDraft(draftRef.current),
      })
      setDraftSavedAt(savedAt)
    } catch {
      setDraftSavedAt(null)
    }
  }, [storageKey, tabId])

  const schedulePersist = useCallback(() => {
    window.clearTimeout(persistTimer.current)
    persistTimer.current = window.setTimeout(() => {
      void persistLocal()
    }, 2000)
  }, [persistLocal])

  const markDirty = useCallback(() => {
    schedulePersist()
    if (dirtyRef.current) return
    dirtyRef.current = true
    setDirty(true)
    onDirtyChange?.(true)
  }, [onDirtyChange, schedulePersist])
  const set = useCallback(
    (key: keyof AdminDraft, value: unknown) => {
      markDirty()
      setDraft((current) => ({ ...current, [key]: value }))
    },
    [markDirty],
  )
  const setDetail = useCallback(
    (key: string, value: unknown) => {
      markDirty()
      setDraft((current) => ({ ...current, details: { ...current.details, [key]: value } }))
    },
    [markDirty],
  )

  useEffect(() => {
    if (!dirty) return
    const prevent = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])

  useEffect(() => {
    let cancelled = false
    void readEditorDraft(storageKey).then((record) => {
      if (cancelled || !record) return
      const current = draftRef.current
      const markdown = markdownRef.current?.getSource() ?? current.markdownSource ?? ''
      if (editorDraftsDiffer(record, current, markdown)) setRestoreOffer(record)
    })
    return () => {
      cancelled = true
    }
  }, [storageKey])

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return
    const channel = new BroadcastChannel(EDITOR_DRAFT_CHANNEL)
    const send = (type: string) => channel.postMessage({ type, key: storageKey, tabId })
    send('editing')
    const timer = window.setInterval(() => send('editing'), 3000)
    channel.onmessage = (event) => {
      const data = event.data as { type?: string; key?: string; tabId?: string } | null
      if (!data || data.key !== storageKey || data.tabId === tabId) return
      if (data.type === 'editing') setTabConflict(true)
      if (data.type === 'closed' || data.type === 'saved') setTabConflict(false)
    }
    return () => {
      window.clearInterval(timer)
      send('closed')
      channel.close()
    }
  }, [storageKey, tabId])

  useEffect(
    () => () => {
      window.clearTimeout(persistTimer.current)
      if (dirtyRef.current) void persistLocal()
    },
    [persistLocal],
  )

  function close() {
    if (busy || uploading) return
    if (dirtyRef.current && !window.confirm('有尚未保存的修改。确定放弃并离开编辑器吗？')) return
    onDirtyChange?.(false)
    onClose()
  }

  function selectWorkspace(next: EditorWorkspaceId) {
    if (next === workspace) return
    markdownRef.current?.exitFocus()
    setWorkspace(next)
  }

  function focusIssue(issue: EditorIssue) {
    markdownRef.current?.exitFocus()
    setWorkspace(editorSectionWorkspace(issue.section))
    if (issue.field === 'markdown') {
      markdownRef.current?.focusSource()
      return
    }
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        const node = document.getElementById(editorFieldId(issue.field))
        node?.scrollIntoView({ block: 'center' })
        if (node instanceof HTMLElement) node.focus()
      }),
    )
  }

  function restoreLocal() {
    if (!restoreOffer) return
    setDraft(applyStoredEditorDraft(draftRef.current, restoreOffer))
    setWorkbenchKey((value) => value + 1)
    setRestoreOffer(null)
    markDirty()
  }

  async function discardLocal() {
    setRestoreOffer(null)
    await clearEditorDraft(storageKey)
    setDraftSavedAt(null)
  }

  return (
    <section className="content-editor-page" aria-label="内容编辑器">
      <div className="editor-page-heading">
        <div>
          <p className="eyebrow">内容生命周期 / 编辑</p>
          <h2>{editing ? '编辑候选修订' : '新建内容草稿'}</h2>
          {draftSavedAt ? (
            <p className="editor-draft-status" role="status">
              本地草稿已保存 {formatDraftSavedAt(draftSavedAt)}
            </p>
          ) : (
            <p className="editor-draft-status">本地草稿仅用于恢复，正式候选版本仍需点击保存。</p>
          )}
        </div>
        <button className="button ghost" type="button" onClick={close} disabled={busy || uploading}>
          返回内容列表
        </button>
      </div>
      <div className="editor-shell" inert={focusMode || undefined}>
        <form
          className="editor-form expanded-editor"
          aria-label="内容编辑器"
          noValidate
          onSubmit={async (event) => {
            event.preventDefault()
            if (busy || uploading) return
            const markdownSource = markdownRef.current?.getSource() ?? draft.markdownSource ?? ''
            const found = collectEditorIssues(
              {
                ...draft,
                markdownSource,
                ...(module.legacyKind === 'RESOURCE' && resourceConfig
                  ? { resourceConfigVersion: resourceConfig.version }
                  : {}),
              },
              markdownSource,
              module,
            )
            if (found.length) {
              setIssues(found)
              setNotice(null)
              focusIssue(found[0])
              return
            }
            setIssues([])
            setBusy(true)
            setNotice(null)
            const intent = submitIntent.current
            submitIntent.current = 'draft'
            try {
              const prepared = prepareDraft(
                {
                  ...draft,
                  schemaVersion: modules.version,
                  markdownSource,
                  ...(module.legacyKind === 'RESOURCE' && resourceConfig
                    ? { resourceConfigVersion: resourceConfig.version }
                    : {}),
                },
                module,
                markdownSource !== (draft.markdownSource ?? ''),
              )
              await (intent === 'submit' && onSaveAndSubmit
                ? onSaveAndSubmit(prepared)
                : onSave(prepared, { continueEditing: intent === 'draft-continue' }))
              dirtyRef.current = false
              setDirty(false)
              onDirtyChange?.(false)
              window.clearTimeout(persistTimer.current)
              await clearEditorDraft(storageKey)
              if (typeof BroadcastChannel !== 'undefined') {
                const channel = new BroadcastChannel(EDITOR_DRAFT_CHANNEL)
                channel.postMessage({ type: 'saved', key: storageKey, tabId })
                channel.close()
              }
              if (intent === 'draft-continue') await onContinueAfterSave?.()
              else onClose()
            } catch (error) {
              setNotice({ tone: 'error', text: message(error) })
            } finally {
              setBusy(false)
            }
          }}
        >
          {restoreOffer ? (
            <aside className="editor-restore" role="region" aria-label="本地草稿">
              <p>发现未保存的本地草稿（{formatDraftSavedAt(restoreOffer.savedAt)}）。可恢复或放弃。</p>
              <div className="heading-actions">
                <button className="button primary" type="button" onClick={restoreLocal}>
                  恢复
                </button>
                <button className="button ghost" type="button" onClick={() => void discardLocal()}>
                  放弃
                </button>
              </div>
            </aside>
          ) : null}
          {tabConflict ? (
            <aside className="editor-restore warn" role="status">
              另一个标签页正在编辑同一内容。请避免同时保存以免互相覆盖。
            </aside>
          ) : null}
          <p className="field-help">
            保存草稿后可从列表继续送审。发生版本冲突时，本地 Markdown 与发布字段会继续保留。
          </p>
          <div className="segmented-control editor-workspace-tabs" role="tablist" aria-label="编辑工作区">
            {EDITOR_WORKSPACES.map((item) => (
              <button
                key={item.id}
                id={`editor-tab-${item.id}`}
                type="button"
                role="tab"
                aria-selected={workspace === item.id}
                aria-controls={`editor-workspace-${item.id}`}
                tabIndex={workspace === item.id ? 0 : -1}
                className={workspace === item.id ? 'active' : ''}
                onClick={() => selectWorkspace(item.id)}
                onKeyDown={(event) => {
                  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
                  event.preventDefault()
                  const index = EDITOR_WORKSPACES.findIndex((entry) => entry.id === workspace)
                  const delta = event.key === 'ArrowRight' ? 1 : -1
                  const next =
                    EDITOR_WORKSPACES[(index + delta + EDITOR_WORKSPACES.length) % EDITOR_WORKSPACES.length]
                  selectWorkspace(next.id)
                  document.getElementById(`editor-tab-${next.id}`)?.focus()
                }}
              >
                {item.label}
              </button>
            ))}
          </div>
          <fieldset disabled={busy || uploading}>
            <legend className="sr-only">内容编辑</legend>
            <div
              id="editor-workspace-body"
              role="tabpanel"
              aria-labelledby="editor-tab-body"
              hidden={workspace !== 'body'}
            >
              <section id="editor-section-body" className="editor-block">
                <MarkdownWorkbench
                  key={`${draft.moduleKey}-${workbenchKey}`}
                  ref={markdownRef}
                  initialSource={draft.markdownSource ?? ''}
                  media={media}
                  invalid={invalid.includes('markdown')}
                  onDirty={markDirty}
                  onFocusModeChange={setFocusMode}
                />
                <MediaPanel
                  key={`media-${workbenchKey}`}
                  editing={editing}
                  media={media}
                  setMedia={setMedia}
                  busy={busy}
                  uploading={uploading}
                  setUploading={setUploading}
                  setNotice={setNotice}
                  getMarkdown={() => markdownRef.current?.getSource() ?? ''}
                  applyMarkdown={(value) => markdownRef.current?.applySource(value)}
                  insert={(token) => markdownRef.current?.insert(token)}
                  requestSaveAndContinue={() => {
                    submitIntent.current = 'draft-continue'
                  }}
                  onDirty={markDirty}
                />
              </section>
            </div>
            <div
              id="editor-workspace-structured"
              role="tabpanel"
              aria-labelledby="editor-tab-structured"
              hidden={workspace !== 'structured'}
            >
              <section id="editor-section-basics" className="editor-block">
                <h3 className="editor-section-title">基础信息</h3>
                <div className="form-grid">
                  <label>
                    内容模板
                    <select
                      value={draft.moduleKey ?? module.key}
                      disabled={Boolean(editing)}
                      onChange={(event) => {
                        const next = modules.modules.find((item) => item.key === event.target.value)!
                        markDirty()
                        setDraft((current) => ({
                          ...createDraft(next, modules.version),
                          title: current.title,
                          summary: current.summary,
                          authorDisplayName: current.authorDisplayName,
                        }))
                        setWorkbenchKey((value) => value + 1)
                      }}
                    >
                      {modules.modules
                        .filter(
                          (item) =>
                            (item.active && item.createVisible !== false) ||
                            item.key === editing?.draft.moduleKey,
                        )
                        .map((item) => (
                          <option key={item.key} value={item.key}>
                            {item.label}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    内容语言
                    <select
                      value={draft.languageCode}
                      onChange={(event) => set('languageCode', event.target.value)}
                    >
                      <option value="zh-CN">简体中文</option>
                      <option value="en">English</option>
                      <option value="ja">日本語</option>
                    </select>
                  </label>
                  <label className="wide">
                    标题
                    <input
                      id={editorFieldId('title')}
                      value={draft.title}
                      required
                      aria-invalid={invalid.includes('title')}
                      maxLength={240}
                      onChange={(event) => set('title', event.target.value)}
                    />
                  </label>
                  <label className="wide">
                    中文导读
                    <textarea
                      id={editorFieldId('summary')}
                      value={draft.summary}
                      rows={3}
                      required
                      aria-invalid={invalid.includes('summary')}
                      maxLength={1000}
                      onChange={(event) => set('summary', event.target.value)}
                    />
                  </label>
                </div>
              </section>

              <section id="editor-section-module" className="editor-block">
                <h3 className="editor-section-title">业务信息</h3>
                <p className="field-help">只显示当前内容模板需要的字段；历史内容未填写的可选项可以留空。</p>
                <div className="form-grid">
                  {module.fields.map((definition) => {
                    const field =
                      definition.key === 'resourceType' && resourceConfig
                        ? {
                            ...definition,
                            options: resourceConfig.categories
                              .filter(
                                (c) =>
                                  c.key !== 'GENERAL' && (c.enabled || c.key === draft.details.resourceType),
                              )
                              .map((c) => c.key),
                            optionLabels: Object.fromEntries(
                              resourceConfig.categories.map((c) => [c.key, c.label]),
                            ),
                          }
                        : definition
                    return (
                      <DynamicField
                        key={field.key}
                        field={field}
                        value={
                          field.key === 'resourceType' && draft.details[field.key] === 'GENERAL'
                            ? ''
                            : draft.details[field.key]
                        }
                        invalid={invalid.includes(field.key)}
                        onChange={setDetail}
                      />
                    )
                  })}
                </div>
                {module.legacyKind === 'RESOURCE' && resourceConfig ? (
                  <ResourceFields
                    config={resourceConfig}
                    details={draft.details}
                    onDetails={(details) => set('details', details)}
                    onTemplate={(source) => {
                      markDirty()
                      markdownRef.current?.applySource(source)
                      set('markdownSource', source)
                    }}
                    hasBody={() => Boolean(markdownRef.current?.getSource().trim())}
                    onRefresh={async () => {
                      const latest = await api.modules()
                      const config = latest.modules.find((m) => m.key === module.key)?.resourceConfig
                      if (config) {
                        setRefreshedResourceConfig(config)
                        setNotice({ tone: 'success', text: '已刷新配置，输入仍保留，请核对后再保存。' })
                      }
                    }}
                  />
                ) : null}
              </section>

              <section id="editor-section-publish" className="editor-block">
                <h3 className="editor-section-title">来源与发布</h3>
                <div className="form-grid">
                  <label>
                    作者展示名
                    <input
                      id={editorFieldId('authorDisplayName')}
                      value={draft.authorDisplayName ?? ''}
                      required
                      aria-invalid={invalid.includes('authorDisplayName')}
                      maxLength={120}
                      onChange={(event) => set('authorDisplayName', event.target.value)}
                    />
                  </label>
                  <label>
                    非中文原始标题 <small>可选</small>
                    <input
                      value={draft.originalTitle ?? ''}
                      maxLength={240}
                      onChange={(event) => set('originalTitle', event.target.value)}
                    />
                  </label>
                  {!original ? (
                    <>
                      <label>
                        来源名称
                        <input
                          id={editorFieldId('sourceName')}
                          value={draft.sourceName ?? ''}
                          required
                          aria-invalid={invalid.includes('sourceName')}
                          maxLength={240}
                          onChange={(event) => set('sourceName', event.target.value)}
                        />
                      </label>
                      <label className="wide">
                        官方或原始来源 URL
                        <input
                          id={editorFieldId('sourceUrl')}
                          type="url"
                          value={draft.sourceUrl ?? ''}
                          required
                          aria-invalid={invalid.includes('sourceUrl')}
                          maxLength={2048}
                          onChange={(event) => set('sourceUrl', event.target.value)}
                        />
                      </label>
                    </>
                  ) : null}
                  <label className="wide">
                    权利 / 使用说明
                    <textarea
                      id={editorFieldId('rightsBasis')}
                      value={draft.rightsBasis}
                      required
                      aria-invalid={invalid.includes('rightsBasis')}
                      maxLength={120}
                      rows={2}
                      onChange={(event) => set('rightsBasis', event.target.value)}
                    />
                  </label>
                  {(['reprintPermission', 'adaptationPermission'] as const).map((key) => (
                    <label key={key}>
                      {key === 'reprintPermission' ? '署名转载' : '署名改编'}
                      <select
                        value={draft[key] ?? 'NOT_ALLOWED'}
                        onChange={(event) => set(key, event.target.value)}
                      >
                        <option value="NOT_ALLOWED">不允许</option>
                        <option value="ATTRIBUTION_ALLOWED">允许署名使用</option>
                      </select>
                    </label>
                  ))}
                </div>
              </section>
            </div>
          </fieldset>
          <div className="editor-save">
            <EditorIssueList issues={issues} onJump={focusIssue} />
            <Notice value={notice} />
            <div className="editor-save-actions">
              <button className="button ghost" type="button" onClick={close} disabled={busy || uploading}>
                取消编辑
              </button>
              <button
                className="button ghost"
                disabled={busy || uploading}
                onClick={() => {
                  submitIntent.current = 'draft'
                }}
              >
                <BusyLabel busy={busy}>{editing ? '保存候选修订' : '保存草稿'}</BusyLabel>
              </button>
              {onSaveAndSubmit ? (
                <button
                  className="button primary"
                  disabled={busy || uploading}
                  onClick={() => {
                    submitIntent.current = 'submit'
                  }}
                >
                  保存并送审
                </button>
              ) : null}
            </div>
          </div>
        </form>
      </div>
    </section>
  )
}

function EditorIssueList({
  issues,
  onJump,
}: {
  issues: EditorIssue[]
  onJump: (issue: EditorIssue) => void
}) {
  if (!issues.length) return null
  const groups = EDITOR_SECTIONS.filter((section) => issues.some((issue) => issue.section === section.id))
  return (
    <section className="editor-issues" role="alert" aria-label="待补齐字段">
      <p className="editor-issues-title">还有 {issues.length} 项需要补齐，点击可直接定位：</p>
      <div className="editor-issues-scroll">
        {groups.map((section) => (
          <div key={section.id} className="editor-issues-group">
            <p className="editor-issues-section">{section.label}</p>
            <ul>
              {issues
                .filter((issue) => issue.section === section.id)
                .map((issue) => (
                  <li key={`${issue.section}-${issue.field}`}>
                    <button type="button" onClick={() => onJump(issue)}>
                      {issue.message}
                    </button>
                  </li>
                ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

const MarkdownPreview = memo(function MarkdownPreview({
  source,
  media,
}: {
  source: string
  media: EditablePartnerContent['media']
}) {
  const body = useMemo(() => markdownPreview(source), [source])
  return (
    <div className="markdown-preview" aria-label="前端预览">
      <DocumentView body={body} media={media} />
    </div>
  )
})

const MarkdownWorkbench = forwardRef<
  MarkdownHandle,
  {
    initialSource: string
    media: EditablePartnerContent['media']
    invalid?: boolean
    onDirty: () => void
    onFocusModeChange?: (focused: boolean) => void
  }
>(function MarkdownWorkbench({ initialSource, media, invalid, onDirty, onFocusModeChange }, ref) {
  const smallQuery = '(max-width: 820px)'
  const [small, setSmall] = useState(() => window.matchMedia(smallQuery).matches)
  const [view, setView] = useState<'write' | 'split' | 'preview'>(() =>
    window.matchMedia(smallQuery).matches ? 'write' : 'split',
  )
  const [focus, setFocus] = useState(false)
  const [source, setSource] = useState(initialSource)
  const [previewSource, setPreviewSource] = useState(initialSource)
  const sourceRef = useRef(source)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const escapeArmed = useRef(false)

  const updateSource = useCallback(
    (value: string) => {
      sourceRef.current = value
      setSource(value)
      onDirty()
    },
    [onDirty],
  )

  useImperativeHandle(
    ref,
    () => ({
      getSource: () => sourceRef.current,
      insert: (token) => {
        const textarea = textareaRef.current
        const current = sourceRef.current
        const start = textarea?.selectionStart ?? current.length
        const end = textarea?.selectionEnd ?? current.length
        updateSource(current.slice(0, start) + token + current.slice(end))
        setView((currentView) => (currentView === 'preview' ? 'write' : currentView))
        requestAnimationFrame(() => {
          if (textarea) {
            const cursor = start + token.length
            textarea.focus()
            textarea.setSelectionRange(cursor, cursor)
          }
        })
      },
      applySource: (value) => updateSource(value),
      exitFocus: () => setFocus(false),
      focusSource: () => {
        setFocus(false)
        setView('write')
        requestAnimationFrame(() => textareaRef.current?.focus())
      },
    }),
    [updateSource],
  )

  useEffect(() => {
    const mediaQuery = window.matchMedia(smallQuery)
    const update = () => {
      const isSmall = mediaQuery.matches
      setSmall(isSmall)
      setView((current) => (isSmall && current === 'split' ? 'write' : current))
    }
    mediaQuery.addEventListener('change', update)
    return () => mediaQuery.removeEventListener('change', update)
  }, [])

  useEffect(() => {
    if (view === 'write') return
    const timer = window.setTimeout(() => startTransition(() => setPreviewSource(sourceRef.current)), 150)
    return () => window.clearTimeout(timer)
  }, [source, view])

  useEffect(() => {
    if (!focus) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setFocus(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [focus])

  useEffect(() => {
    onFocusModeChange?.(focus)
  }, [focus, onFocusModeChange])

  const chooseView = (next: 'write' | 'split' | 'preview') => {
    if (next !== 'write') startTransition(() => setPreviewSource(sourceRef.current))
    setView(next)
  }
  const views = small
    ? ([
        ['write', '写作'],
        ['preview', '阅读预览'],
      ] as const)
    : ([
        ['write', '写作'],
        ['split', '分屏'],
        ['preview', '阅读预览'],
      ] as const)

  const stage = (
    <div className={`markdown-stage${focus ? ' is-focus' : ''}`}>
      <div className="editor-toolbar">
        <div>
          <h3>Markdown 正文</h3>
          <p className="field-help">
            Tab 插入两个空格或缩进选区，Shift+Tab 减少缩进。先按 Esc 再按 Tab
            可离开正文。支持标题、列表、引用、代码、HTTPS 链接和受控图片。
          </p>
        </div>
        <div className="editor-toolbar-actions">
          <div className="segmented-control" aria-label="正文视图">
            {views.map(([value, label]) => (
              <button
                key={value}
                type="button"
                className={view === value ? 'active' : ''}
                aria-pressed={view === value}
                onClick={() => chooseView(value)}
              >
                {label}
              </button>
            ))}
          </div>
          <button
            type="button"
            className="button ghost"
            aria-pressed={focus}
            onClick={() => setFocus((current) => !current)}
          >
            {focus ? <Minimize2 size={15} /> : <Maximize2 size={15} />}
            {focus ? '退出全屏' : '全屏专注'}
          </button>
        </div>
      </div>
      <div className={`markdown-workbench view-${view}`}>
        {view !== 'preview' ? (
          <label className="markdown-source">
            Markdown 原文
            <textarea
              id={editorFieldId('markdown')}
              ref={textareaRef}
              value={source}
              rows={22}
              maxLength={262144}
              spellCheck
              aria-invalid={invalid}
              aria-describedby="markdown-indent-help"
              onChange={(event) => updateSource(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  if (focus) {
                    event.preventDefault()
                    setFocus(false)
                    return
                  }
                  escapeArmed.current = true
                  return
                }
                if (event.key === 'Tab' && !escapeArmed.current) {
                  event.preventDefault()
                  const textarea = event.currentTarget
                  const next = applyMarkdownIndent(
                    sourceRef.current,
                    textarea.selectionStart,
                    textarea.selectionEnd,
                    event.shiftKey ? -1 : 1,
                  )
                  updateSource(next.source)
                  requestAnimationFrame(() => textarea.setSelectionRange(next.start, next.end))
                  return
                }
                escapeArmed.current = false
              }}
            />
          </label>
        ) : null}
        {view !== 'write' ? <MarkdownPreview source={previewSource} media={media} /> : null}
      </div>
      <p id="markdown-indent-help" className="sr-only">
        Tab 缩进，Shift+Tab 减少缩进。先按 Escape 再按 Tab 可以离开编辑区。
      </p>
    </div>
  )
  return focus ? createPortal(stage, document.body) : stage
})

const MediaPanel = memo(function MediaPanel({
  editing,
  media,
  setMedia,
  busy,
  uploading,
  setUploading,
  setNotice,
  getMarkdown,
  applyMarkdown,
  insert,
  requestSaveAndContinue,
  onDirty,
}: {
  editing?: EditablePartnerContent
  media: EditablePartnerContent['media']
  setMedia: Dispatch<SetStateAction<EditablePartnerContent['media']>>
  busy: boolean
  uploading: boolean
  setUploading: (value: boolean) => void
  setNotice: (value: NoticeValue) => void
  getMarkdown: () => string
  applyMarkdown: (value: string) => void
  insert: (token: string) => void
  requestSaveAndContinue: () => void
  onDirty: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [altText, setAltText] = useState('')
  const [editingAltId, setEditingAltId] = useState<string | null>(null)
  const [altDraft, setAltDraft] = useState('')
  const [hiddenIds, setHiddenIds] = useState<string[]>([])
  const [referenced, setReferenced] = useState<string[]>([])

  useEffect(() => {
    const tick = () => setReferenced([...referencedMediaIds(getMarkdown())])
    tick()
    const timer = window.setInterval(tick, 400)
    return () => window.clearInterval(timer)
  }, [getMarkdown, media.length])

  const visible = media.filter((item) => !hiddenIds.includes(item.id))
  const uploadState = mediaUploadState(editing)

  return (
    <section className="media-panel">
      <div>
        <h3>正文图片</h3>
        <p className="field-help">
          JPEG、PNG 或 WebP，单张不超过 5
          MB；图片说明必填。从草稿移除不会立即删除文件，未引用图片由后台异步清理。
        </p>
      </div>
      {uploadState === 'ready' && editing ? (
        <div className="media-upload">
          <label>
            选择图片
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <label>
            图片说明
            <input value={altText} maxLength={240} onChange={(event) => setAltText(event.target.value)} />
          </label>
          <button
            type="button"
            className="button ghost"
            disabled={uploading}
            onClick={async () => {
              if (uploading) return
              const description = altText.trim()
              if (!file?.size || !description) {
                setNotice({ tone: 'error', text: '请选择图片并填写图片说明。' })
                return
              }
              if (file.size > 5 * 1024 * 1024) {
                setNotice({ tone: 'error', text: '单张图片不能超过 5 MB。' })
                return
              }
              setUploading(true)
              setNotice(null)
              try {
                const uploaded = await api.uploadContentMedia(editing.id, file, description)
                setMedia((current) => [...current, uploaded])
                insert(mediaInsertToken(uploaded.id, description))
                setFile(null)
                setAltText('')
                setNotice({ tone: 'success', text: '图片已上传并插入正文光标位置。' })
              } catch (error) {
                setNotice({ tone: 'error', text: message(error) })
              } finally {
                setUploading(false)
              }
            }}
          >
            <ImagePlus size={15} />
            {uploading ? '上传中…' : '上传并插入'}
          </button>
        </div>
      ) : uploadState === 'blocked' ? (
        <p className="inline-info">当前内容状态不可添加正文图片。</p>
      ) : (
        <div className="media-save-first">
          <p className="inline-info">
            {uploadState === 'needs-new-draft'
              ? '图片需要先属于一份草稿。点这里会先保存这份草稿，然后你可以继续编辑并上传图片。'
              : '编辑已发布内容会先产生一份候选修订，保存后即可上传图片，线上版本不受影响。'}
          </p>
          <button
            className="button ghost"
            type="submit"
            disabled={busy || uploading}
            onClick={requestSaveAndContinue}
          >
            {busy
              ? '保存中…'
              : uploadState === 'needs-new-draft'
                ? '保存草稿并继续编辑'
                : '保存候选修订并继续编辑'}
          </button>
        </div>
      )}
      {visible.length ? (
        <ul className="media-list">
          {visible.map((item) => {
            const used = referenced.includes(item.id)
            const bodyAlt = used ? mediaAltInBody(getMarkdown(), item.id) : null
            const displayAlt = bodyAlt ?? item.altText
            return (
              <li key={item.id} className="media-card">
                {item.url ? (
                  <img className="media-thumb" src={item.url} alt={displayAlt} width={72} height={72} />
                ) : (
                  <span className="media-thumb" aria-hidden="true" />
                )}
                <div className="media-card-meta">
                  <strong>{displayAlt}</strong>
                  <code>media:{item.id}</code>
                  <span className="media-ref-state">{used ? '正文已引用' : '正文未引用'}</span>
                </div>
                <div className="media-card-actions">
                  <button
                    type="button"
                    className="button ghost"
                    onClick={() => insert(mediaInsertToken(item.id, item.altText))}
                  >
                    插入正文
                  </button>
                  <button
                    type="button"
                    className="button ghost"
                    disabled={!used}
                    title={used ? undefined : '先插入正文，再修改说明。'}
                    onClick={() => {
                      setEditingAltId(item.id)
                      setAltDraft(displayAlt)
                    }}
                  >
                    修改说明
                  </button>
                  <button
                    type="button"
                    className="button ghost"
                    onClick={async () => {
                      try {
                        await copyText(`media:${item.id}`)
                        setNotice({ tone: 'success', text: '已复制 media:id。' })
                      } catch (error) {
                        setNotice({ tone: 'error', text: message(error) })
                      }
                    }}
                  >
                    <Copy size={14} />
                    复制 media:id
                  </button>
                  <button
                    type="button"
                    className="button ghost"
                    onClick={() => {
                      if (
                        !window.confirm(
                          '将从当前草稿移除这张图片，并从正文中删除对它的引用。移除在保存后才生效；保存后如果它不再被已发布版本或其它内容使用，会由后台在保留期后清理，届时需要重新上传才能再次引用。',
                        )
                      )
                        return
                      applyMarkdown(stripMediaReferences(getMarkdown(), item.id))
                      setHiddenIds((current) => [...current, item.id])
                      onDirty()
                    }}
                  >
                    <Trash2 size={14} />
                    从当前草稿移除
                  </button>
                </div>
                {editingAltId === item.id ? (
                  <div className="media-alt-editor">
                    <label>
                      正文图片说明
                      <input
                        value={altDraft}
                        maxLength={240}
                        autoFocus
                        onChange={(event) => setAltDraft(event.target.value)}
                      />
                    </label>
                    <div className="heading-actions">
                      <button
                        type="button"
                        className="button primary"
                        onClick={() => {
                          applyMarkdown(replaceMediaAltText(getMarkdown(), item.id, altDraft))
                          setEditingAltId(null)
                          setNotice({ tone: 'success', text: '正文图片说明已更新。' })
                        }}
                      >
                        保存说明
                      </button>
                      <button type="button" className="button ghost" onClick={() => setEditingAltId(null)}>
                        取消
                      </button>
                    </div>
                  </div>
                ) : null}
              </li>
            )
          })}
        </ul>
      ) : null}
    </section>
  )
})
