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
} from 'react'
import { ImagePlus } from 'lucide-react'
import {
  api,
  type AdminDraft,
  type EditablePartnerContent,
  type ModuleField,
  type ModuleSet,
} from '../lib/api'
import { createDraft, prepareDraft } from '../lib/draft'
import { useResource } from '../lib/hooks'
import { markdownPreview } from '../lib/markdown'
import { BusyLabel, ErrorState, LoadingRows, Notice, message, type NoticeValue } from './ui'
import { DocumentView } from './document'

export function ContentEditor({
  editing,
  onSave,
  onSaveAndSubmit,
  onClose,
  onDirtyChange,
}: {
  editing?: EditablePartnerContent
  onSave: (draft: AdminDraft) => Promise<void>
  onSaveAndSubmit?: (draft: AdminDraft) => Promise<void>
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
      onDirtyChange={onDirtyChange}
    />
  )
}

type MarkdownHandle = {
  getSource: () => string
  insert: (token: string) => void
}

function DraftEditor({
  modules,
  editing,
  onSave,
  onSaveAndSubmit,
  onClose,
  onDirtyChange,
}: {
  modules: ModuleSet
  editing?: EditablePartnerContent
  onSave: (draft: AdminDraft) => Promise<void>
  onSaveAndSubmit?: (draft: AdminDraft) => Promise<void>
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
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [media, setMedia] = useState(editing?.media ?? [])
  const [notice, setNotice] = useState<NoticeValue>(null)
  const markdownRef = useRef<MarkdownHandle>(null)
  const submitAfterSave = useRef(false)
  const module = modules.modules.find((item) => item.key === draft.moduleKey) ?? first
  const original = module.allowsOriginalSource && draft.details.sourceMode === 'ORIGINAL'

  const markDirty = useCallback(() => {
    if (dirtyRef.current) return
    dirtyRef.current = true
    setDirty(true)
    onDirtyChange?.(true)
  }, [onDirtyChange])
  const set = useCallback(
    (key: keyof AdminDraft, value: unknown) => {
      markDirty()
      setDraft((current) => ({ ...current, [key]: value }))
    },
    [markDirty],
  )

  useEffect(() => {
    if (!dirty) return
    const prevent = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])

  function close() {
    if (busy || uploading) return
    if (dirtyRef.current && !window.confirm('有尚未保存的修改。确定放弃并离开编辑器吗？')) return
    onDirtyChange?.(false)
    onClose()
  }

  return (
    <section className="content-editor-page" aria-label="内容编辑器">
      <div className="editor-page-heading">
        <div>
          <p className="eyebrow">内容生命周期 / 编辑</p>
          <h2>{editing ? '编辑候选修订' : '新建内容草稿'}</h2>
        </div>
        <button className="button ghost" type="button" onClick={close} disabled={busy || uploading}>
          返回内容列表
        </button>
      </div>
      <form
        className="editor-form expanded-editor"
        aria-label="内容编辑器"
        onSubmit={async (event) => {
          event.preventDefault()
          if (busy || uploading) return
          const markdownSource = markdownRef.current?.getSource() ?? draft.markdownSource ?? ''
          if (!draft.title.trim() || !draft.summary.trim() || !markdownSource.trim()) {
            setNotice({ tone: 'error', text: '标题、导读和正文不能为空。' })
            return
          }
          setBusy(true)
          setNotice(null)
          try {
            const prepared = prepareDraft(
              { ...draft, markdownSource },
              module,
              markdownSource !== (draft.markdownSource ?? ''),
            )
            await (submitAfterSave.current && onSaveAndSubmit ? onSaveAndSubmit(prepared) : onSave(prepared))
            dirtyRef.current = false
            setDirty(false)
            onDirtyChange?.(false)
            onClose()
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          } finally {
            setBusy(false)
          }
        }}
      >
        <p className="field-help">
          保存草稿后可从列表继续送审。发生版本冲突时，本地 Markdown 与发布字段会继续保留。
        </p>
        <fieldset disabled={busy || uploading}>
          <legend className="sr-only">内容编辑</legend>
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
                }}
              >
                {modules.modules
                  .filter((item) => item.active || item.key === module.key)
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
                value={draft.title}
                required
                maxLength={240}
                onChange={(event) => set('title', event.target.value)}
              />
            </label>
            <label className="wide">
              中文导读
              <textarea
                value={draft.summary}
                rows={3}
                required
                maxLength={1000}
                onChange={(event) => set('summary', event.target.value)}
              />
            </label>
          </div>

          <MarkdownWorkbench
            key={draft.moduleKey}
            ref={markdownRef}
            initialSource={draft.markdownSource ?? ''}
            media={media}
            onDirty={markDirty}
          />

          <MediaPanel
            editing={editing}
            media={media}
            setMedia={setMedia}
            uploading={uploading}
            setUploading={setUploading}
            setNotice={setNotice}
            insert={(token) => markdownRef.current?.insert(token)}
          />

          <h3 className="editor-section-title">发布资料</h3>
          <div className="form-grid">
            <label>
              作者展示名
              <input
                value={draft.authorDisplayName ?? ''}
                required
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
            {module.fields.map((field) => (
              <DynamicField
                key={field.key}
                field={field}
                value={draft.details[field.key]}
                onChange={(value) => {
                  markDirty()
                  setDraft((current) => ({ ...current, details: { ...current.details, [field.key]: value } }))
                }}
              />
            ))}
            {!original ? (
              <>
                <label>
                  来源名称
                  <input
                    value={draft.sourceName ?? ''}
                    required
                    maxLength={240}
                    onChange={(event) => set('sourceName', event.target.value)}
                  />
                </label>
                <label className="wide">
                  官方或原始来源 URL
                  <input
                    type="url"
                    value={draft.sourceUrl ?? ''}
                    required
                    maxLength={2048}
                    onChange={(event) => set('sourceUrl', event.target.value)}
                  />
                </label>
              </>
            ) : null}
            <label className="wide">
              权利 / 使用说明
              <textarea
                value={draft.rightsBasis}
                required
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
        </fieldset>
        <Notice value={notice} />
        <div className="editor-save">
          <button className="button ghost" type="button" onClick={close} disabled={busy || uploading}>
            取消编辑
          </button>
          <button
            className="button ghost"
            disabled={busy || uploading}
            onClick={() => {
              submitAfterSave.current = false
            }}
          >
            <BusyLabel busy={busy}>{editing ? '保存候选修订' : '保存草稿'}</BusyLabel>
          </button>
          {onSaveAndSubmit ? (
            <button
              className="button primary"
              disabled={busy || uploading}
              onClick={() => {
                submitAfterSave.current = true
              }}
            >
              保存并送审
            </button>
          ) : null}
        </div>
      </form>
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
  { initialSource: string; media: EditablePartnerContent['media']; onDirty: () => void }
>(function MarkdownWorkbench({ initialSource, media, onDirty }, ref) {
  const smallQuery = '(max-width: 820px)'
  const [small, setSmall] = useState(() => window.matchMedia(smallQuery).matches)
  const [view, setView] = useState<'write' | 'split' | 'preview'>(() =>
    window.matchMedia(smallQuery).matches ? 'write' : 'split',
  )
  const [source, setSource] = useState(initialSource)
  const [previewSource, setPreviewSource] = useState(initialSource)
  const sourceRef = useRef(source)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

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
        requestAnimationFrame(() => {
          if (textarea) {
            const cursor = start + token.length
            textarea.focus()
            textarea.setSelectionRange(cursor, cursor)
          }
        })
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

  return (
    <>
      <div className="editor-toolbar">
        <div>
          <h3>Markdown 正文</h3>
          <p className="field-help">支持标题、列表、引用、代码、HTTPS 链接和受控图片。</p>
        </div>
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
      </div>
      <div className={`markdown-workbench view-${view}`}>
        {view !== 'preview' ? (
          <label className="markdown-source">
            Markdown 原文
            <textarea
              ref={textareaRef}
              value={source}
              rows={22}
              maxLength={262144}
              spellCheck
              onChange={(event) => updateSource(event.target.value)}
            />
          </label>
        ) : null}
        {view !== 'write' ? <MarkdownPreview source={previewSource} media={media} /> : null}
      </div>
    </>
  )
})

const MediaPanel = memo(function MediaPanel({
  editing,
  media,
  setMedia,
  uploading,
  setUploading,
  setNotice,
  insert,
}: {
  editing?: EditablePartnerContent
  media: EditablePartnerContent['media']
  setMedia: React.Dispatch<React.SetStateAction<EditablePartnerContent['media']>>
  uploading: boolean
  setUploading: (value: boolean) => void
  setNotice: (value: NoticeValue) => void
  insert: (token: string) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [altText, setAltText] = useState('')
  return (
    <section className="media-panel">
      <div>
        <h3>正文图片</h3>
        <p className="field-help">JPEG、PNG 或 WebP，单张不超过 5 MB；图片说明必填。</p>
      </div>
      {!editing ? (
        <p className="inline-info">先保存草稿，再次进入编辑即可上传图片。</p>
      ) : (
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
                insert(`\n\n![${description}](media:${uploaded.id})\n\n`)
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
      )}
      {media.length ? (
        <div className="media-list">
          {media.map((item) => (
            <span key={item.id}>
              {item.altText}
              <code>media:{item.id}</code>
            </span>
          ))}
        </div>
      ) : null}
    </section>
  )
})

function DynamicField({
  field,
  value,
  onChange,
}: {
  field: ModuleField
  value: unknown
  onChange: (value: unknown) => void
}) {
  const text = value == null ? '' : String(value)
  return (
    <label className={field.type === 'url' || field.type === 'textarea' ? 'wide' : ''}>
      {field.label}
      {field.required ? '（必填）' : ''}
      {field.type === 'select' ? (
        <select value={text} required={field.required} onChange={(event) => onChange(event.target.value)}>
          {!field.required ? <option value="">未设置</option> : null}
          {field.options?.map((option) => (
            <option key={option}>{option}</option>
          ))}
        </select>
      ) : field.type === 'boolean' ? (
        <select
          value={text}
          required={field.required}
          onChange={(event) => onChange(event.target.value === 'true')}
        >
          <option value="">未设置</option>
          <option value="true">是</option>
          <option value="false">否</option>
        </select>
      ) : field.type === 'textarea' || field.type === 'tags' ? (
        <textarea
          value={Array.isArray(value) ? value.join('，') : text}
          required={field.required}
          rows={2}
          onChange={(event) =>
            onChange(
              field.type === 'tags'
                ? event.target.value
                    .split(/[，,]/)
                    .map((item) => item.trim())
                    .filter(Boolean)
                : event.target.value,
            )
          }
        />
      ) : (
        <input
          value={text}
          required={field.required}
          type={['date', 'url', 'number'].includes(field.type) ? field.type : 'text'}
          onChange={(event) =>
            onChange(
              field.type === 'number' && event.target.value ? Number(event.target.value) : event.target.value,
            )
          }
        />
      )}
      {field.help ? <small className="field-help">{field.help}</small> : null}
    </label>
  )
}
