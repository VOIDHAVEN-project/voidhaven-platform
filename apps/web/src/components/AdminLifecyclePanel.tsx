import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { AdminDraft, ContentModuleSet, EditableContent, ManagedContent, ManagementQuery } from '../lib/api'
import { api } from '../lib/api'
import { ContentEditor } from './ContentEditor'

type Notice = { tone: 'success' | 'error'; text: string } | null
type Action = 'submit' | 'publish' | 'hide' | 'show' | 'archive' | 'restore' | 'reopen'

function actionLabel(item: ManagedContent) {
  if (item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') return '待送审'
  if (item.workflowStatus === 'PENDING_REVIEW') return '审核中'
  if (item.workflowStatus === 'APPROVED') return '待发布'
  if (item.workflowStatus === 'PUBLISHED') return item.visibility === 'PUBLIC' ? '公开中' : '已隐藏'
  if (item.workflowStatus === 'REJECTED') return '已拒绝'
  return item.workflowStatus
}

function needsConfirmation(action: Action) {
  return action === 'publish' || action === 'hide' || action === 'archive' || action === 'reopen'
}

export function AdminLifecyclePanel({ isSuperAdmin = false, onDirtyChange, confirmDiscard }: { isSuperAdmin?: boolean; onDirtyChange?: (dirty: boolean) => void; confirmDiscard?: () => boolean }) {
  const [items, setItems] = useState<ManagedContent[]>([])
  const [moduleSet, setModuleSet] = useState<ContentModuleSet | null>(null)
  const [editing, setEditing] = useState<EditableContent | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [filters, setFilters] = useState<ManagementQuery>({})
  const [busyId, setBusyId] = useState<string | null>(null)
  const [openingEditor, setOpeningEditor] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)
  const load = useCallback(async () => {
    const [definitions, content] = await Promise.all([api.modules(), api.adminContentItems(filters)])
    setModuleSet(definitions); setItems(content.items); setNextCursor(content.nextCursor)
  }, [filters])

  const bootstrap = useCallback(async () => {
    try { await load() }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载管理员内容。' }) }
    finally { setLoading(false) }
  }, [load])
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void bootstrap() }, [bootstrap])

  async function reload() {
    setLoading(true)
    try { await load() }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新失败。' }) }
    finally { setLoading(false) }
  }

  async function loadMore() {
    if (!nextCursor) return
    setLoadingMore(true)
    try {
      const page = await api.adminContentItems({ ...filters, cursor: nextCursor })
      setItems((current) => [...current, ...page.items]); setNextCursor(page.nextCursor)
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页加载失败。' }) }
    finally { setLoadingMore(false) }
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    setFilters({ query: String(values.get('query')).trim(), status: String(values.get('status')), module: String(values.get('module')) })
  }

  async function beginEdit(id: string) {
    if (confirmDiscard && !confirmDiscard()) return
    setBusyId(id); setOpeningEditor(true); setNotice(null)
    try { setEditing(await api.adminContent(id)) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法打开该草稿。' }) }
    finally { setBusyId(null); setOpeningEditor(false) }
  }

  async function save(draft: AdminDraft, expectedVersion?: number) {
    if (editing) {
      const saved = await api.adminRevise(editing.id, expectedVersion ?? editing.revision, draft)
      setEditing(await api.adminContent(saved.id))
      await load()
      return saved
    }
    const saved = await api.adminCreate(draft)
    setEditing(await api.adminContent(saved.id))
    await load()
    setNotice({ tone: 'success', text: `“${saved.title}”已保存。` })
    return saved
  }

  async function submitSaved(saved: ManagedContent) {
    const updated = await api.adminSubmit(saved.id)
    await load()
    setEditing(null)
    setNotice({ tone: 'success', text: `“${updated.title}”已保存并送入审核队列。` })
  }

  async function run(item: ManagedContent, action: Action) {
    if (needsConfirmation(action) && !window.confirm(`确定要对“${item.title}”执行此操作吗？`)) return
    setBusyId(item.id); setNotice(null)
    try {
      if (action === 'publish') {
        const readiness = await api.adminPublicationReadiness(item.id)
        if (!readiness.ready) throw new Error(readiness.blockers.map((blocker) => blocker.message).join(' '))
      }
      const updated = action === 'submit' ? await api.adminSubmit(item.id)
        : action === 'publish' ? await api.adminPublish(item.id)
          : action === 'hide' ? await api.adminHide(item.id, '管理员下线操作')
            : action === 'show' ? await api.adminShow(item.id)
              : action === 'restore' ? await api.adminRestore(item.id, '管理员恢复操作')
                : action === 'reopen' ? await api.adminReopen(item.id, '重新开放该稿件，由作者补充后再次送审。')
                  : await api.adminArchive(item.id, '管理员归档操作')
      setItems((current) => current.map((candidate) => candidate.id === updated.id ? updated : candidate))
      setNotice({ tone: 'success', text: `“${updated.title}”当前状态：${actionLabel(updated)}。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '操作未完成。' })
    } finally { setBusyId(null) }
  }

  if (!moduleSet) return <section className="admin-lifecycle"><p>正在加载内容编辑器…</p></section>
  return <section className="admin-lifecycle" aria-label="管理员内容工作台">
    <div className="workspace-section-heading"><div><p className="eyebrow">管理员内容闭环</p><h2>创作、审核与发布</h2></div><div className="admin-actions"><Link className="button ghost" to="/workspace/reviews">打开审核队列</Link><button className="button ghost" type="button" onClick={() => void reload()} disabled={loading}>刷新列表</button></div></div>
    <form className="workspace-filter" onSubmit={applyFilters}><label>搜索<input name="query" defaultValue={filters.query} placeholder="标题或摘要" /></label><label>状态<select name="status" defaultValue={filters.status}><option value="">全部状态</option>{['DRAFT', 'PENDING_REVIEW', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED', 'PUBLISHED'].map((value) => <option key={value} value={value}>{value}</option>)}</select></label><label>模块<select name="module" defaultValue={filters.module}><option value="">全部模块</option>{moduleSet.modules.map((value) => <option key={value.key} value={value.key}>{value.label}</option>)}</select></label><button className="button ghost" disabled={loading}>筛选</button></form>
    {openingEditor ? <div className="content-editor" role="status"><p>正在打开目标草稿，请稍候…</p></div> : <ContentEditor key={editing?.id ?? 'new'} moduleSet={moduleSet} initial={editing} contentItemId={editing?.id} saveLabel={editing ? '保存候选草稿' : '保存管理员草稿'} onSave={save} onSubmitForReview={editing && (editing.workflowStatus === 'DRAFT' || editing.workflowStatus === 'CHANGES_REQUESTED') ? submitSaved : undefined} onDirtyChange={onDirtyChange} onCancel={editing ? () => setEditing(null) : undefined} />}
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="admin-content-list" aria-live="polite">{loading ? <p>正在加载受控内容…</p> : items.length === 0 ? <p>当前没有管理员可操作的内容。</p> : items.map((item) => <article key={item.id} className="admin-content-row"><div><p className="eyebrow"><span>{item.moduleKey}</span><span>{actionLabel(item)}</span></p><h3>{item.title}</h3><p className="admin-state">审核：{item.workflowStatus} · 公开：{item.publicationStatus} · 可见性：{item.visibility} · 存放：{item.recordState}</p>{item.latestReview?.reason ? <p className="workspace-help">最近审核意见：{item.latestReview.reason}</p> : null}</div><div className="admin-actions">
      {(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED' || item.workflowStatus === 'PUBLISHED') ? <button className="button ghost" type="button" onClick={() => void beginEdit(item.id)} disabled={busyId === item.id}>编辑</button> : null}
      {(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <button className="button ghost" type="button" onClick={() => void run(item, 'submit')} disabled={busyId === item.id || editing?.id === item.id} title={editing?.id === item.id ? '请在编辑器中使用“保存并送审”，确保最新内容已经保存。' : undefined}>送审</button> : null}
      {item.workflowStatus === 'PENDING_REVIEW' ? <Link className="button primary" to={`/workspace/reviews/${item.id}`}>查看并审核</Link> : null}
      {item.workflowStatus === 'APPROVED' ? <button className="button primary" type="button" onClick={() => void run(item, 'publish')} disabled={busyId === item.id}>检查并发布</button> : null}
      {item.workflowStatus === 'REJECTED' && isSuperAdmin ? <button className="button ghost" type="button" onClick={() => void run(item, 'reopen')} disabled={busyId === item.id}>重新开放</button> : null}
      {item.workflowStatus === 'PUBLISHED' && item.visibility === 'PUBLIC' ? <><button className="button ghost" type="button" onClick={() => void run(item, 'hide')} disabled={busyId === item.id}>隐藏</button><button className="button ghost" type="button" onClick={() => void run(item, 'archive')} disabled={busyId === item.id}>归档</button></> : null}
      {item.workflowStatus === 'PUBLISHED' && item.visibility === 'HIDDEN' && item.recordState === 'ACTIVE' ? <button className="button primary" type="button" onClick={() => void run(item, 'show')} disabled={busyId === item.id}>重新公开</button> : null}
      {item.recordState === 'ARCHIVED' ? <button className="button ghost" type="button" onClick={() => void run(item, 'restore')} disabled={busyId === item.id}>恢复为待发布</button> : null}
    </div></article>)}</div>
    {nextCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? '正在加载…' : '加载更多'}</button> : null}
  </section>
}
