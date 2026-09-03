import { useCallback, useEffect, useState } from 'react'
import type { AdminDraft, ContentModuleSet, EditableContent, PartnerDashboard } from '../lib/api'
import { api } from '../lib/api'
import { ContentEditor } from './ContentEditor'

type Notice = { tone: 'success' | 'error'; text: string } | null
function statusText(status: string) { return ({ DRAFT: '草稿', CHANGES_REQUESTED: '待修改', PENDING_REVIEW: '审核中', APPROVED: '已通过，待发布', PUBLISHED: '已发布', REJECTED: '已拒绝' } as Record<string, string>)[status] ?? status }

export function PartnerLifecyclePanel() {
  const [dashboard, setDashboard] = useState<PartnerDashboard | null>(null)
  const [moduleSet, setModuleSet] = useState<ContentModuleSet | null>(null)
  const [editing, setEditing] = useState<EditableContent | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const load = useCallback(async () => { const [definitions, data] = await Promise.all([api.modules(), api.partnerDashboard()]); setModuleSet(definitions); setDashboard(data) }, [])
  const bootstrap = useCallback(async () => { try { await load() } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方工作台。' }) } }, [load])
  // Initial API synchronization intentionally populates local workspace state after mount.
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void bootstrap() }, [bootstrap])
  async function beginEdit(id: string) { setBusyId(id); try { setEditing(await api.partnerContent(id)) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法打开该草稿。' }) } finally { setBusyId(null) } }
  async function save(draft: AdminDraft) { const saved = editing ? await api.partnerRevise(editing.id, editing.revision, draft) : await api.partnerCreate(draft); setEditing(await api.partnerContent(saved.id)); await load(); return saved }
  async function submit(id: string) { setBusyId(id); try { const updated = await api.partnerSubmit(id); await load(); setNotice({ tone: 'success', text: `“${updated.title}”已送入审核队列。` }) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '送审未完成。' }) } finally { setBusyId(null) } }
  if (!moduleSet) return <section className="partner-lifecycle"><p>正在加载内容编辑器…</p></section>
  return <section className="partner-lifecycle" aria-label="合作方内容工作台">
    <div className="workspace-section-heading"><div><p className="eyebrow">合作方内容工作台</p><h2>{dashboard?.displayName ?? '正在加载…'}</h2></div><button className="button ghost" type="button" onClick={() => void load()}>刷新列表</button></div>
    <p className="workspace-help">公开活跃内容：{dashboard?.publicActiveCount ?? '—'} / {dashboard?.quota ?? '—'}。保存草稿不占用公开配额，发布前由服务端重新核验。</p>
    <ContentEditor key={editing ? `${editing.id}:${editing.revision}` : 'new'} moduleSet={moduleSet} initial={editing} contentItemId={editing?.id} saveLabel={editing ? '保存候选修订' : '保存合作方草稿'} onSave={save} onCancel={editing ? () => setEditing(null) : undefined} />
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-content-list" aria-live="polite">{!dashboard ? <p>正在加载自己的内容…</p> : dashboard.contentItems.length === 0 ? <p>尚无内容。创建草稿后，它只会在你的受控工作台中可见。</p> : dashboard.contentItems.map((item) => <article className="partner-content-row" key={item.id}><div><p className="eyebrow"><span>{item.moduleKey}</span><span>{statusText(item.workflowStatus)}</span></p><h3>{item.title}</h3><p className="admin-state">可见性：{item.visibility} · 存放：{item.recordState}</p></div><div className="partner-row-actions">{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED' || item.workflowStatus === 'PUBLISHED') ? <button className="button ghost" type="button" onClick={() => void beginEdit(item.id)} disabled={busyId === item.id}>编辑</button> : null}{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <button className="button primary" type="button" onClick={() => void submit(item.id)} disabled={busyId === item.id}>送审</button> : null}</div></article>)}</div>
  </section>
}
