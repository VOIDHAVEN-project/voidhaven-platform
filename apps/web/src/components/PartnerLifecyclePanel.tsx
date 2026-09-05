import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminDraft, ContentModuleSet, EditableContent, PartnerDashboard } from '../lib/api'
import { api } from '../lib/api'
import { ContentEditor } from './ContentEditor'

type Notice = { tone: 'success' | 'error'; text: string } | null
function statusText(status: string) { return ({ DRAFT: '草稿', CHANGES_REQUESTED: '待修改', PENDING_REVIEW: '审核中', APPROVED: '已通过，待发布', PUBLISHED: '已发布', REJECTED: '已拒绝' } as Record<string, string>)[status] ?? status }

export function PartnerLifecyclePanel({ onDirtyChange, confirmDiscard }: { onDirtyChange?: (dirty: boolean) => void; confirmDiscard?: () => boolean }) {
  const [dashboard, setDashboard] = useState<PartnerDashboard | null>(null)
  const [moduleSet, setModuleSet] = useState<ContentModuleSet | null>(null)
  const [editing, setEditing] = useState<EditableContent | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [openingEditor, setOpeningEditor] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [module, setModule] = useState('')
  const load = useCallback(async () => { const [definitions, data] = await Promise.all([api.modules(), api.partnerDashboard({ query, status, module })]); setModuleSet(definitions); setDashboard(data) }, [module, query, status])
  const bootstrap = useCallback(async () => { try { await load() } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方工作台。' }) } }, [load])
  // Initial API synchronization intentionally populates local workspace state after mount.
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void bootstrap() }, [bootstrap])
  async function beginEdit(id: string) { if (confirmDiscard && !confirmDiscard()) return; setBusyId(id); setOpeningEditor(true); setNotice(null); try { setEditing(await api.partnerContent(id)) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法打开该草稿。' }) } finally { setBusyId(null); setOpeningEditor(false) } }
  async function save(draft: AdminDraft, expectedVersion?: number) {
    if (editing) {
      const saved = await api.partnerRevise(editing.id, expectedVersion ?? editing.revision, draft)
      setEditing(await api.partnerContent(saved.id))
      await load(); return saved
    }
    const saved = await api.partnerCreate(draft); setEditing(await api.partnerContent(saved.id)); await load(); setNotice({ tone: 'success', text: `“${saved.title}”已保存。` }); return saved
  }
  async function submit(id: string) { setBusyId(id); try { const updated = await api.partnerSubmit(id); await load(); setNotice({ tone: 'success', text: `“${updated.title}”已送入审核队列。` }) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '送审未完成。' }) } finally { setBusyId(null) } }
  async function submitSaved(saved: { id: string; title: string }) {
    const updated = await api.partnerSubmit(saved.id)
    await load()
    setEditing(null)
    setNotice({ tone: 'success', text: `“${updated.title}”已保存并送入审核队列。` })
  }
  async function loadMore() {
    if (!dashboard?.contentItems.nextCursor) return
    setLoadingMore(true)
    try {
      const page = await api.partnerDashboard({ query, status, module, cursor: dashboard.contentItems.nextCursor })
      setDashboard((current) => current ? { ...page, contentItems: { items: [...current.contentItems.items, ...page.contentItems.items], nextCursor: page.contentItems.nextCursor } } : page)
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页加载失败。' }) }
    finally { setLoadingMore(false) }
  }
  async function refresh() { try { await load() } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新内容列表失败。' }) } }
  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const values = new FormData(event.currentTarget); setQuery(String(values.get('query')).trim()); setStatus(String(values.get('status'))); setModule(String(values.get('module'))) }
  if (!moduleSet) return <section className="partner-lifecycle"><p>正在加载内容编辑器…</p></section>
  return <section className="partner-lifecycle" aria-label="合作方内容工作台">
    <div className="workspace-section-heading"><div><p className="eyebrow">合作方内容工作台</p><h2>{dashboard?.displayName ?? '正在加载…'}</h2></div><button className="button ghost" type="button" onClick={() => void refresh()}>刷新列表</button></div>
    <p className="workspace-help">公开活跃内容：{dashboard?.publicActiveCount ?? '—'} / {dashboard?.quota ?? '—'}。保存草稿不占用公开配额，发布前由服务端重新核验。</p>
    <form className="workspace-filter" onSubmit={search}><label>搜索自己的内容<input name="query" defaultValue={query} placeholder="标题或摘要" /></label><label>状态<select name="status" defaultValue={status}><option value="">全部状态</option>{['DRAFT', 'PENDING_REVIEW', 'APPROVED', 'CHANGES_REQUESTED', 'REJECTED', 'PUBLISHED'].map((value) => <option key={value} value={value}>{statusText(value)}</option>)}</select></label><label>模块<select name="module" defaultValue={module}><option value="">全部模块</option>{moduleSet.modules.map((value) => <option key={value.key} value={value.key}>{value.label}</option>)}</select></label><button className="button ghost">筛选</button></form>
    {openingEditor ? <div className="content-editor" role="status"><p>正在打开目标草稿，请稍候…</p></div> : <ContentEditor key={editing?.id ?? 'new'} moduleSet={moduleSet} initial={editing} contentItemId={editing?.id} saveLabel={editing ? '保存候选草稿' : '保存合作方草稿'} onSave={save} onSubmitForReview={editing && (editing.workflowStatus === 'DRAFT' || editing.workflowStatus === 'CHANGES_REQUESTED') ? submitSaved : undefined} onDirtyChange={onDirtyChange} onCancel={editing ? () => setEditing(null) : undefined} />}
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-content-list" aria-live="polite">{!dashboard ? <p>正在加载自己的内容…</p> : dashboard.contentItems.items.length === 0 ? <p>尚无内容。创建草稿后，它只会在你的受控工作台中可见。</p> : dashboard.contentItems.items.map((item) => <article className="partner-content-row" key={item.id}><div><p className="eyebrow"><span>{item.moduleKey}</span><span>{statusText(item.workflowStatus)}</span></p><h3>{item.title}</h3><p className="admin-state">可见性：{item.visibility} · 存放：{item.recordState}</p>{item.latestReview ? <p className="workspace-help">最近审核：{item.latestReview.decision}{item.latestReview.reason ? ` · ${item.latestReview.reason}` : ''}</p> : null}</div><div className="partner-row-actions">{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED' || item.workflowStatus === 'PUBLISHED') ? <button className="button ghost" type="button" onClick={() => void beginEdit(item.id)} disabled={busyId === item.id}>编辑</button> : null}{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <button className="button primary" type="button" onClick={() => void submit(item.id)} disabled={busyId === item.id || editing?.id === item.id} title={editing?.id === item.id ? '请在编辑器中使用“保存并送审”，确保最新内容已经保存。' : undefined}>送审</button> : null}</div></article>)}</div>
    {dashboard?.contentItems.nextCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? '正在加载…' : '加载更多'}</button> : null}
  </section>
}
