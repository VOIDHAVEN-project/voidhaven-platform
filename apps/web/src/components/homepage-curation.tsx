import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, GripVertical, Plus, Search, Trash2 } from 'lucide-react'
import {
  api,
  categories,
  type HomeContent,
  type HomepageCandidate,
  type HomepagePlacement,
  type HomepageSlot,
} from '../lib/api'
import {
  HOMEPAGE_SLOT_LABELS,
  HOMEPAGE_SLOTS,
  curationFingerprint,
  dropPlacement,
  movePlacement,
  placementsInSlot,
  replaceSlot,
} from '../lib/homepage-placements'
import { useResource } from '../lib/hooks'
import {
  BusyLabel,
  EmptyState,
  ErrorState,
  LoadingRows,
  Notice,
  PanelHeading,
  message,
  statusText,
  type NoticeValue,
} from './ui'

export function HomepageManagementPanel() {
  const state = useResource(useCallback((signal) => api.adminHomepagePlacements(signal), []))
  const [draft, setDraft] = useState<HomepagePlacement[]>([])
  const [slot, setSlot] = useState<HomepageSlot>('FOCUS')
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<HomepageCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [preview, setPreview] = useState<HomeContent | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [dragIndex, setDragIndex] = useState<number | null>(null)

  useEffect(() => {
    if (state.data) setDraft(state.data.placements)
  }, [state.data])

  const liveFingerprint = useMemo(
    () => (state.data ? curationFingerprint(state.data.placements) : ''),
    [state.data],
  )
  const dirty = Boolean(state.data) && curationFingerprint(draft) !== liveFingerprint
  const current = placementsInSlot(draft, slot)
  const selected = new Set(current.map((item) => item.contentItemId))

  useEffect(() => {
    if (!dirty) return
    const onLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onLeave)
    return () => window.removeEventListener('beforeunload', onLeave)
  }, [dirty])

  useEffect(() => {
    let active = true
    api
      .adminHomepageCandidates(slot, '')
      .then((page) => {
        if (active) setCandidates(page.items)
      })
      .catch((error) => {
        if (active) setNotice({ tone: 'error', text: message(error) })
      })
    return () => {
      active = false
    }
  }, [slot])

  async function searchCandidates(event?: { preventDefault(): void }) {
    event?.preventDefault()
    setSearching(true)
    setNotice(null)
    try {
      const page = await api.adminHomepageCandidates(slot, query)
      setCandidates(page.items)
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setSearching(false)
    }
  }

  function addCandidate(candidate: HomepageCandidate) {
    if (selected.has(candidate.id)) return
    setPreview(null)
    setDraft((currentDraft) =>
      replaceSlot(currentDraft, slot, [
        ...placementsInSlot(currentDraft, slot),
        {
          slot,
          contentItemId: candidate.id,
          sortOrder: placementsInSlot(currentDraft, slot).length,
          startsAt: null,
          endsAt: null,
          title: candidate.title,
          kind: candidate.kind,
          operationalStatus: candidate.operationalStatus,
          updatedAt: candidate.updatedAt,
          deadline: candidate.deadline,
          publicEligible: candidate.publicEligible,
          issue: candidate.issue,
        },
      ]),
    )
  }

  function updateRow(index: number, patch: Partial<HomepagePlacement>) {
    setPreview(null)
    setDraft((currentDraft) => {
      const rows = placementsInSlot(currentDraft, slot).map((item, rowIndex) =>
        rowIndex === index ? { ...item, ...patch } : item,
      )
      return replaceSlot(currentDraft, slot, rows)
    })
  }

  async function previewDraft() {
    setPreviewing(true)
    setNotice(null)
    try {
      setPreview(await api.adminPreviewHomepagePlacements(draft))
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setPreviewing(false)
    }
  }

  async function publishDraft() {
    if (!state.data) return
    setSaving(true)
    setNotice(null)
    try {
      const saved = await api.adminReplaceHomepagePlacements(state.data.version, draft)
      state.setData(saved)
      setDraft(saved.placements)
      setPreview(null)
      setNotice({
        tone: 'success',
        text: saved.placements.length
          ? `首页编排已发布，共 ${saved.placements.length} 条。`
          : '已清空人工编排，公开首页将按有效内容自动补位。',
      })
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setSaving(false)
    }
  }

  if (state.loading) return <LoadingRows />
  if (state.error) return <ErrorState error={state.error} retry={state.reload} />

  return (
    <section className="workspace-stack">
      <PanelHeading eyebrow="HOMEPAGE CURATION" title="首页人工编排" />
      <p className="panel-help">
        四个栏目分别编排「本期值得关注、竞赛资源、团队招募、资源与分享」。保存后立即发布到公开首页；未保存的修改可以先预览，不会覆盖线上版本。
      </p>
      <p className={`curation-status ${dirty ? 'dirty' : ''}`} role="status">
        {dirty ? '有未保存修改，当前预览不会自动等于线上生效版本。' : '当前编辑的就是线上生效版本。'}
      </p>
      <div className="segmented-control" role="tablist" aria-label="首页栏目">
        {HOMEPAGE_SLOTS.map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={slot === item}
            className={slot === item ? 'active' : undefined}
            onClick={() => {
              setSlot(item)
              setCandidates([])
              setQuery('')
            }}
          >
            {HOMEPAGE_SLOT_LABELS[item]}
          </button>
        ))}
      </div>
      <form className="workspace-filter" onSubmit={searchCandidates}>
        <label>
          搜索{HOMEPAGE_SLOT_LABELS[slot]}
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="标题或关键词"
          />
        </label>
        <button className="button" disabled={searching}>
          <Search size={15} /> <BusyLabel busy={searching}>搜索</BusyLabel>
        </button>
      </form>
      {candidates.length ? (
        <ul className="curation-candidates">
          {candidates.map((candidate) => (
            <li key={candidate.id}>
              <div>
                <strong>{candidate.title}</strong>
                <p>
                  {categories[candidate.kind].label}
                  {candidate.operationalStatus
                    ? ` · ${statusText[candidate.operationalStatus] ?? candidate.operationalStatus}`
                    : ''}
                  {candidate.deadline ? ` · ${candidate.deadline}` : ''}
                  {candidate.updatedAt ? ` · 更新 ${formatStamp(candidate.updatedAt)}` : ''}
                </p>
                {candidate.issue ? (
                  <small className={candidate.publicEligible ? 'field-help' : 'field-error'}>
                    {candidate.issue}
                  </small>
                ) : null}
              </div>
              <button
                type="button"
                className="button ghost"
                disabled={selected.has(candidate.id) || candidate.publicEligible === false}
                onClick={() => addCandidate(candidate)}
              >
                <Plus size={15} /> {selected.has(candidate.id) ? '已添加' : '添加'}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      {current.length ? (
        <ol className="curation-list">
          {current.map((item, index) => (
            <li
              key={`${item.slot}-${item.contentItemId}`}
              draggable
              onDragStart={() => setDragIndex(index)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndex == null) return
                setPreview(null)
                setDraft((currentDraft) => dropPlacement(currentDraft, slot, dragIndex, index))
                setDragIndex(null)
              }}
            >
              <span className="curation-handle" aria-hidden="true">
                <GripVertical size={16} />
              </span>
              <div className="curation-copy">
                <strong>{item.title ?? item.contentItemId}</strong>
                <p>
                  {item.kind ? categories[item.kind].label : '未知类型'}
                  {item.operationalStatus
                    ? ` · ${statusText[item.operationalStatus] ?? item.operationalStatus}`
                    : ''}
                  {item.deadline ? ` · ${item.deadline}` : ''}
                  {item.updatedAt ? ` · 更新 ${formatStamp(item.updatedAt)}` : ''}
                </p>
                {item.issue ? (
                  <small className={item.publicEligible === false ? 'field-error' : 'field-help'}>
                    {item.issue}
                  </small>
                ) : null}
              </div>
              <label>
                开始
                <input
                  type="datetime-local"
                  value={toLocalInput(item.startsAt)}
                  onChange={(event) => updateRow(index, { startsAt: fromLocalInput(event.target.value) })}
                />
              </label>
              <label>
                结束
                <input
                  type="datetime-local"
                  value={toLocalInput(item.endsAt)}
                  onChange={(event) => updateRow(index, { endsAt: fromLocalInput(event.target.value) })}
                />
              </label>
              <div className="heading-actions">
                <button
                  type="button"
                  className="icon-button"
                  aria-label="上移"
                  disabled={index === 0}
                  onClick={() => {
                    setPreview(null)
                    setDraft((currentDraft) => movePlacement(currentDraft, slot, index, -1))
                  }}
                >
                  <ArrowUp size={15} />
                </button>
                <button
                  type="button"
                  className="icon-button"
                  aria-label="下移"
                  disabled={index === current.length - 1}
                  onClick={() => {
                    setPreview(null)
                    setDraft((currentDraft) => movePlacement(currentDraft, slot, index, 1))
                  }}
                >
                  <ArrowDown size={15} />
                </button>
                <button
                  type="button"
                  className="icon-button"
                  aria-label="移除"
                  onClick={() => {
                    setPreview(null)
                    setDraft((currentDraft) =>
                      replaceSlot(
                        currentDraft,
                        slot,
                        placementsInSlot(currentDraft, slot).filter((_, rowIndex) => rowIndex !== index),
                      ),
                    )
                  }}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </li>
          ))}
        </ol>
      ) : (
        <EmptyState
          title={`还没有编排${HOMEPAGE_SLOT_LABELS[slot]}`}
          text="搜索并添加公开内容。保存空栏目时，首页会用仍可公开的内容自动补位。"
        />
      )}
      {current.length > 4 ? (
        <p className="panel-help">
          公开首页每个栏目最多展示 4 条。当前多出的条目会保留在编排清单中，但不会出现在首页。
        </p>
      ) : null}
      <div className="heading-actions">
        <button type="button" className="button" disabled={previewing} onClick={() => void previewDraft()}>
          <BusyLabel busy={previewing}>预览未发布结果</BusyLabel>
        </button>
        <button
          type="button"
          className="button primary"
          disabled={saving || !dirty}
          onClick={() => void publishDraft()}
        >
          <BusyLabel busy={saving}>保存并发布</BusyLabel>
        </button>
        {dirty ? (
          <button
            type="button"
            className="button ghost"
            onClick={() => {
              if (state.data) setDraft(state.data.placements)
              setPreview(null)
            }}
          >
            放弃未保存修改
          </button>
        ) : null}
      </div>
      <Notice value={notice} />
      {preview ? <HomepagePreview preview={preview} /> : null}
    </section>
  )
}

function HomepagePreview({ preview }: { preview: HomeContent }) {
  const sections: { key: keyof HomeContent; label: string }[] = [
    { key: 'focus', label: '01 本期值得关注' },
    { key: 'competitions', label: '03 竞赛资源' },
    { key: 'recruitments', label: '04 团队招募' },
    { key: 'resources', label: '05 资源与分享' },
  ]
  return (
    <section className="curation-preview" aria-label="未发布编排预览">
      <p className="eyebrow">UNPUBLISHED PREVIEW</p>
      <h3>保存前预览</h3>
      <p>这是未保存草稿经过补位、去重和有效性校验后的公开首页结果，不是线上生效版本。</p>
      {sections.map((section) => (
        <div key={section.key}>
          <h4>{section.label}</h4>
          {preview[section.key].length ? (
            <ol>
              {preview[section.key].map((item) => (
                <li key={item.id}>
                  <span>{categories[item.kind].label}</span>
                  {item.title}
                </li>
              ))}
            </ol>
          ) : (
            <p>暂无内容，保存后该栏会保持空态或继续自动补位。</p>
          )}
        </div>
      ))}
    </section>
  )
}

function toLocalInput(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function fromLocalInput(value: string) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

function formatStamp(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}
