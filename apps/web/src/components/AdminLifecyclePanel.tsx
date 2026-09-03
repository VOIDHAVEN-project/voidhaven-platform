import { useCallback, useEffect, useState } from 'react'
import type { AdminDraft, ContentModuleSet, EditableContent, ManagedContent } from '../lib/api'
import { api } from '../lib/api'
import { ContentEditor } from './ContentEditor'

type Notice = { tone: 'success' | 'error'; text: string } | null
type Action = 'submit' | 'approve' | 'changes' | 'reject' | 'publish' | 'hide' | 'show' | 'archive' | 'restore'

function actionLabel(item: ManagedContent) {
  if (item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') return '待送审'
  if (item.workflowStatus === 'PENDING_REVIEW') return '审核中'
  if (item.workflowStatus === 'APPROVED') return '待发布'
  if (item.workflowStatus === 'PUBLISHED') return item.visibility === 'PUBLIC' ? '公开中' : '已隐藏'
  return item.workflowStatus
}

export function AdminLifecyclePanel() {
  const [items, setItems] = useState<ManagedContent[]>([])
  const [moduleSet, setModuleSet] = useState<ContentModuleSet | null>(null)
  const [editing, setEditing] = useState<EditableContent | null>(null)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const load = useCallback(async () => {
    const [definitions, content] = await Promise.all([api.modules(), api.adminContentItems()])
    setModuleSet(definitions); setItems(content)
  }, [])

  const bootstrap = useCallback(async () => { try { await load() } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载管理员内容。' }) } finally { setLoading(false) } }, [load])
  // Initial API synchronization intentionally populates local workspace state after mount.
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void bootstrap() }, [bootstrap])
  async function reload() { setLoading(true); try { await load() } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新失败。' }) } finally { setLoading(false) } }
  async function beginEdit(id: string) { setBusyId(id); setNotice(null); try { setEditing(await api.adminContent(id)) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法打开该草稿。' }) } finally { setBusyId(null) } }
  async function save(draft: AdminDraft) {
    const saved = editing ? await api.adminRevise(editing.id, editing.revision, draft) : await api.adminCreate(draft)
    setEditing(await api.adminContent(saved.id)); await reload(); return saved
  }
  async function run(item: ManagedContent, action: Action) {
    setBusyId(item.id); setNotice(null)
    try {
      const updated = action === 'submit' ? await api.adminSubmit(item.id)
        : action === 'approve' ? await api.adminDecision(item.id, 'APPROVE')
          : action === 'changes' ? await api.adminDecision(item.id, 'CHANGES_REQUESTED', '请补充或核验内容后重新提交。')
            : action === 'reject' ? await api.adminDecision(item.id, 'REJECT', '当前内容不满足发布要求。')
              : action === 'publish' ? await api.adminPublish(item.id)
                : action === 'hide' ? await api.adminHide(item.id, '管理员下线操作')
                  : action === 'show' ? await api.adminShow(item.id)
                    : action === 'restore' ? await api.adminRestore(item.id, '管理员恢复操作')
                      : await api.adminArchive(item.id, '管理员归档操作')
      setItems((current) => current.map((candidate) => candidate.id === updated.id ? updated : candidate))
      setNotice({ tone: 'success', text: `“${updated.title}”已完成${actionLabel(updated)}操作。` })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '操作未完成。' }) } finally { setBusyId(null) }
  }
  if (!moduleSet) return <section className="admin-lifecycle"><p>正在加载内容编辑器…</p></section>
  return <section className="admin-lifecycle" aria-label="管理员内容工作台">
    <div className="workspace-section-heading"><div><p className="eyebrow">管理员内容闭环</p><h2>创作、审核与发布</h2></div><button className="button ghost" type="button" onClick={() => void reload()} disabled={loading}>刷新列表</button></div>
    <ContentEditor key={editing ? `${editing.id}:${editing.revision}` : 'new'} moduleSet={moduleSet} initial={editing} contentItemId={editing?.id} saveLabel={editing ? '保存候选修订' : '保存管理员草稿'} onSave={save} onCancel={editing ? () => setEditing(null) : undefined} />
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="admin-content-list" aria-live="polite">{loading ? <p>正在加载受控内容…</p> : items.length === 0 ? <p>当前没有管理员可操作的内容。</p> : items.map((item) => <article key={item.id} className="admin-content-row"><div><p className="eyebrow"><span>{item.moduleKey}</span><span>{actionLabel(item)}</span></p><h3>{item.title}</h3><p className="admin-state">审核：{item.workflowStatus} · 公开：{item.publicationStatus} · 可见性：{item.visibility} · 存放：{item.recordState}</p></div><div className="admin-actions">{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED' || item.workflowStatus === 'PUBLISHED') ? <button className="button ghost" onClick={() => void beginEdit(item.id)} disabled={busyId === item.id}>编辑</button> : null}{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <button className="button ghost" onClick={() => void run(item, 'submit')} disabled={busyId === item.id}>送审</button> : null}{item.workflowStatus === 'PENDING_REVIEW' ? <><button className="button ghost" onClick={() => void run(item, 'changes')} disabled={busyId === item.id}>退回修改</button><button className="button ghost" onClick={() => void run(item, 'reject')} disabled={busyId === item.id}>拒绝</button><button className="button primary" onClick={() => void run(item, 'approve')} disabled={busyId === item.id}>审核通过</button></> : null}{item.workflowStatus === 'APPROVED' ? <button className="button primary" onClick={() => void run(item, 'publish')} disabled={busyId === item.id}>发布</button> : null}{item.workflowStatus === 'PUBLISHED' && item.visibility === 'PUBLIC' ? <><button className="button ghost" onClick={() => void run(item, 'hide')} disabled={busyId === item.id}>隐藏</button><button className="button ghost" onClick={() => void run(item, 'archive')} disabled={busyId === item.id}>归档</button></> : null}{item.workflowStatus === 'PUBLISHED' && item.visibility === 'HIDDEN' && item.recordState === 'ACTIVE' ? <button className="button primary" onClick={() => void run(item, 'show')} disabled={busyId === item.id}>重新公开</button> : null}{item.recordState === 'ARCHIVED' ? <button className="button ghost" onClick={() => void run(item, 'restore')} disabled={busyId === item.id}>恢复为待发布</button> : null}</div></article>)}</div>
  </section>
}
