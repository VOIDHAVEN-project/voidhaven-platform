import { useCallback, useEffect, useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, GripVertical, Plus, Search, Trash2 } from 'lucide-react'
import {
  api,
  categories,
  type CatalogOrderCandidate,
  type CatalogOrderEntry,
  type ContentKind,
} from '../lib/api'
import { catalogSortOptions } from '../lib/catalog'
import {
  CATALOG_KIND_LABELS,
  CATALOG_KINDS,
  catalogOrderFingerprint,
  dropCatalogEntry,
  moveCatalogEntry,
} from '../lib/catalog-order'
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

function formatStamp(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(date)
}

export function CatalogOrderPanel() {
  const [kind, setKind] = useState<ContentKind>('COMPETITION')
  const state = useResource(useCallback((signal) => api.adminCatalogOrder(kind, signal), [kind]))
  const [draft, setDraft] = useState<CatalogOrderEntry[]>([])
  const [defaultSort, setDefaultSort] = useState('default')
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<CatalogOrderCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [dragIndex, setDragIndex] = useState<number | null>(null)

  useEffect(() => {
    if (!state.data) return
    setDraft(state.data.entries)
    setDefaultSort(state.data.defaultSort || 'default')
  }, [state.data])

  const liveFingerprint = useMemo(
    () => (state.data ? catalogOrderFingerprint(state.data.defaultSort, state.data.entries) : ''),
    [state.data],
  )
  const dirty = Boolean(state.data) && catalogOrderFingerprint(defaultSort, draft) !== liveFingerprint
  const selected = new Set(draft.map((item) => item.contentItemId))

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
      .adminCatalogCandidates(kind, '')
      .then((page) => {
        if (active) setCandidates(page.items)
      })
      .catch((error) => {
        if (active) setNotice({ tone: 'error', text: message(error) })
      })
    return () => {
      active = false
    }
  }, [kind])

  async function searchCandidates(event?: { preventDefault(): void }) {
    event?.preventDefault()
    setSearching(true)
    setNotice(null)
    try {
      const page = await api.adminCatalogCandidates(kind, query)
      setCandidates(page.items)
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setSearching(false)
    }
  }

  function addCandidate(candidate: CatalogOrderCandidate) {
    if (selected.has(candidate.id)) return
    setDraft((current) => [
      ...current,
      {
        contentItemId: candidate.id,
        sortOrder: current.length,
        title: candidate.title,
        kind: candidate.kind,
        operationalStatus: candidate.operationalStatus,
        updatedAt: candidate.updatedAt,
        deadline: candidate.deadline,
        publicEligible: candidate.publicEligible,
        issue: candidate.issue,
      },
    ])
  }

  async function publishDraft() {
    if (!state.data) return
    setSaving(true)
    setNotice(null)
    try {
      const saved = await api.adminReplaceCatalogOrder(kind, state.data.version, defaultSort, draft)
      state.setData(saved)
      setDraft(saved.entries)
      setDefaultSort(saved.defaultSort)
      setNotice({
        tone: 'success',
        text: saved.entries.length
          ? `${CATALOG_KIND_LABELS[kind]}已保存，共 ${saved.entries.length} 条人工顺序。`
          : `${CATALOG_KIND_LABELS[kind]}已恢复为系统默认顺序。`,
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
      <PanelHeading eyebrow="CATALOG ORDER" title="目录默认顺序" />
      <p className="panel-help">
        为竞赛、团队招募和资源与分享配置默认排序。公开目录会优先使用访问者在 URL
        中选择的排序；未选择时使用这里的默认顺序，再回退到系统顺序。
      </p>
      <p className={`curation-status ${dirty ? 'dirty' : ''}`} role="status">
        {dirty ? '有未保存修改。' : '当前编辑的就是线上生效版本。'}
      </p>
      <div className="segmented-control" role="tablist" aria-label="目录类型">
        {CATALOG_KINDS.map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={kind === item}
            className={kind === item ? 'active' : undefined}
            onClick={() => {
              if (dirty && !window.confirm('有尚未保存的修改。确定切换目录吗？')) return
              setKind(item)
              setCandidates([])
              setQuery('')
              setNotice(null)
            }}
          >
            {CATALOG_KIND_LABELS[item]}
          </button>
        ))}
      </div>
      <label>
        未指定排序时的默认方式
        <select value={defaultSort} onChange={(event) => setDefaultSort(event.target.value)}>
          {catalogSortOptions(kind).map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <form className="workspace-filter" onSubmit={searchCandidates}>
        <label>
          搜索{categories[kind].label}
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
      {draft.length ? (
        <ol className="curation-list catalog-order-list">
          {draft.map((item, index) => (
            <li
              key={item.contentItemId}
              draggable
              onDragStart={() => setDragIndex(index)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndex == null) return
                setDraft((current) => dropCatalogEntry(current, dragIndex, index))
                setDragIndex(null)
              }}
            >
              <span className="curation-handle" aria-hidden="true">
                <GripVertical size={16} />
              </span>
              <div className="curation-copy">
                <strong>{item.title ?? item.contentItemId}</strong>
                <p>
                  {item.kind ? categories[item.kind].label : categories[kind].label}
                  {item.operationalStatus
                    ? ` · ${statusText[item.operationalStatus] ?? item.operationalStatus}`
                    : ''}
                  {item.deadline ? ` · ${item.deadline}` : ''}
                </p>
                {item.issue ? (
                  <small className={item.publicEligible === false ? 'field-error' : 'field-help'}>
                    {item.issue}
                  </small>
                ) : null}
              </div>
              <div className="heading-actions">
                <button
                  type="button"
                  className="icon-button"
                  aria-label="上移"
                  disabled={index === 0}
                  onClick={() => setDraft((current) => moveCatalogEntry(current, index, -1))}
                >
                  <ArrowUp size={15} />
                </button>
                <button
                  type="button"
                  className="icon-button"
                  aria-label="下移"
                  disabled={index === draft.length - 1}
                  onClick={() => setDraft((current) => moveCatalogEntry(current, index, 1))}
                >
                  <ArrowDown size={15} />
                </button>
                <button
                  type="button"
                  className="icon-button"
                  aria-label="移除"
                  onClick={() =>
                    setDraft((current) =>
                      current
                        .filter((_, rowIndex) => rowIndex !== index)
                        .map((row, order) => ({ ...row, sortOrder: order })),
                    )
                  }
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </li>
          ))}
        </ol>
      ) : (
        <EmptyState
          title={`还没有为${CATALOG_KIND_LABELS[kind]}指定人工顺序`}
          text="搜索并添加公开内容。保存空清单时，目录会使用所选默认排序方式。"
        />
      )}
      <div className="heading-actions">
        <button
          type="button"
          className="button primary"
          disabled={saving || !dirty}
          onClick={() => void publishDraft()}
        >
          <BusyLabel busy={saving}>保存目录顺序</BusyLabel>
        </button>
        {dirty ? (
          <button
            type="button"
            className="button ghost"
            onClick={() => {
              if (!state.data) return
              setDraft(state.data.entries)
              setDefaultSort(state.data.defaultSort)
            }}
          >
            放弃未保存修改
          </button>
        ) : null}
      </div>
      <Notice value={notice} />
    </section>
  )
}
