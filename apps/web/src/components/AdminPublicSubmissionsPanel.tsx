import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { PublicSubmission } from '../lib/api'
import { api } from '../lib/api'

type Notice = { tone: 'success' | 'error'; text: string } | null

export function AdminPublicSubmissionsPanel() {
  const [submissions, setSubmissions] = useState<PublicSubmission[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('PENDING_REVIEW')
  const [notice, setNotice] = useState<Notice>(null)
  const load = useCallback(() => api.adminSubmissions({ query, status }), [query, status])

  useEffect(() => {
    let active = true
    void load().then((page) => { if (active) { setSubmissions(page.items); setNextCursor(page.nextCursor) } }).catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载公开输入。' }) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [load])

  async function loadMore() {
    if (!nextCursor) return
    setLoadingMore(true)
    try { const page = await api.adminSubmissions({ query, status, cursor: nextCursor }); setSubmissions((items) => [...items, ...page.items]); setNextCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页加载失败。' }) }
    finally { setLoadingMore(false) }
  }

  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const values = new FormData(event.currentTarget); setQuery(String(values.get('query')).trim()); setStatus(String(values.get('status'))) }

  async function refresh() {
    setNotice(null)
    try { const page = await load(); setSubmissions(page.items); setNextCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新公开输入失败。' }) }
  }

  async function review(event: FormEvent<HTMLFormElement>, submission: PublicSubmission) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const decision = String(values.get('decision')) as 'APPROVE' | 'REJECT'
    const reason = String(values.get('reason')).trim()
    setBusyId(submission.id)
    setNotice(null)
    try {
      const result = await api.adminDecideSubmission(submission.id, decision, reason)
      setSubmissions((items) => status === 'ALL' ? items.map((item) => item.id === submission.id ? { ...item, state: result.state } : item) : items.filter((item) => item.id !== submission.id))
      setNotice({ tone: 'success', text: `“${submission.title}”已更新为 ${result.state}。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '审核操作未完成。' })
    } finally { setBusyId(null) }
  }

  return <section className="admin-public-submissions" aria-label="公开输入审核">
    <div className="workspace-section-heading"><div><p className="eyebrow">公开输入审核</p><h2>投稿、反馈与版权请求</h2></div><button className="button ghost" type="button" onClick={() => void refresh()} disabled={loading}>刷新队列</button></div>
    <p className="workspace-help">只显示公开访客提交的文本、链接和图片说明；私密联系方式不进入本界面、前端状态或审计摘要。</p>
    <form className="workspace-filter" onSubmit={search}><label>搜索公开输入<input name="query" defaultValue={query} placeholder="标题或正文" /></label><label>状态<select name="status" defaultValue={status}><option value="PENDING_REVIEW">待审核</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option><option value="ALL">全部</option></select></label><button className="button ghost">筛选</button></form>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-application-list" aria-live="polite">{loading ? <p>正在加载公开输入…</p> : submissions.length === 0 ? <p>当前筛选条件下没有投稿、反馈或版权请求。</p> : submissions.map((submission) => <article key={submission.id} className="partner-application-row"><div><p className="eyebrow">{submission.submissionType} · {submission.state}</p><h3>{submission.title}</h3><p>{submission.message}</p>{submission.linkUrl ? <a href={submission.linkUrl} target="_blank" rel="noopener noreferrer">外部链接：查看提交内容 ↗</a> : null}{submission.imageDescription ? <p className="workspace-help">图片说明：{submission.imageDescription}</p> : null}</div>{submission.state === 'PENDING_REVIEW' ? <form onSubmit={(event) => void review(event, submission)}><label>审核决定<select name="decision" defaultValue="" required><option value="" disabled>请选择</option><option value="APPROVE">通过</option><option value="REJECT">拒绝</option></select></label><label>审核原因<textarea name="reason" required maxLength={1000} rows={3} /></label><button className="button primary" disabled={busyId === submission.id}>{busyId === submission.id ? '处理中…' : '保存审核决定'}</button></form> : <p className="workspace-help">该记录已处理，仅供追溯查看。</p>}</article>)}</div>
    {nextCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? '正在加载…' : '加载更多'}</button> : null}
  </section>
}
