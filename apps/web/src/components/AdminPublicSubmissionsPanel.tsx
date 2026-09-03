import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { PublicSubmission } from '../lib/api'
import { api } from '../lib/api'

type Notice = { tone: 'success' | 'error'; text: string } | null

export function AdminPublicSubmissionsPanel() {
  const [submissions, setSubmissions] = useState<PublicSubmission[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const load = useCallback(() => api.adminSubmissions(), [])

  useEffect(() => {
    let active = true
    void load().then((items) => { if (active) setSubmissions(items) }).catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载公开输入。' }) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [load])

  async function review(event: FormEvent<HTMLFormElement>, submission: PublicSubmission) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const decision = String(values.get('decision')) as 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT'
    const reason = String(values.get('reason')).trim()
    setBusyId(submission.id)
    setNotice(null)
    try {
      const result = await api.adminDecideSubmission(submission.id, decision, reason)
      setSubmissions((items) => items.filter((item) => item.id !== submission.id))
      setNotice({ tone: 'success', text: `“${submission.title}”已更新为 ${result.state}。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '审核操作未完成。' })
    } finally { setBusyId(null) }
  }

  return <section className="admin-public-submissions" aria-label="公开输入审核">
    <div className="workspace-section-heading"><div><p className="eyebrow">公开输入审核</p><h2>投稿、反馈与版权请求</h2></div><button className="button ghost" type="button" onClick={() => void load().then(setSubmissions)} disabled={loading}>刷新队列</button></div>
    <p className="workspace-help">只显示公开访客提交的文本、链接和图片说明；私密联系方式不进入本界面、前端状态或审计摘要。</p>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-application-list" aria-live="polite">{loading ? <p>正在加载待审核输入…</p> : submissions.length === 0 ? <p>当前没有待审核的投稿、反馈或版权请求。</p> : submissions.map((submission) => <article key={submission.id} className="partner-application-row"><div><p className="eyebrow">{submission.submissionType}</p><h3>{submission.title}</h3><p>{submission.message}</p>{submission.linkUrl ? <a href={submission.linkUrl} target="_blank" rel="noreferrer">查看提交的公开链接 ↗</a> : null}{submission.imageDescription ? <p className="workspace-help">图片说明：{submission.imageDescription}</p> : null}</div><form onSubmit={(event) => void review(event, submission)}><label>审核决定<select name="decision" defaultValue="APPROVE"><option value="APPROVE">通过</option><option value="CHANGES_REQUESTED">退回修改</option><option value="REJECT">拒绝</option></select></label><label>审核原因<textarea name="reason" required maxLength={1000} rows={3} /></label><button className="button primary" disabled={busyId === submission.id}>{busyId === submission.id ? '处理中…' : '保存审核决定'}</button></form></article>)}</div>
  </section>
}
