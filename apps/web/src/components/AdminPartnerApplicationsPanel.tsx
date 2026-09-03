import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { PendingPartnerApplication, ProvisionedPartner } from '../lib/api'
import { api } from '../lib/api'

type Notice = { tone: 'success' | 'error'; text: string } | null

export function AdminPartnerApplicationsPanel() {
  const [applications, setApplications] = useState<PendingPartnerApplication[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const [provisioned, setProvisioned] = useState<ProvisionedPartner | null>(null)
  const load = useCallback(() => api.adminPartnerApplications(), [])

  useEffect(() => {
    let active = true
    void load().then((items) => { if (active) setApplications(items) }).catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方申请。' }) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [load])

  async function approve(event: FormEvent<HTMLFormElement>, application: PendingPartnerApplication) {
    event.preventDefault()
    const loginId = String(new FormData(event.currentTarget).get('loginId')).trim()
    setBusyId(application.id)
    setNotice(null)
    setProvisioned(null)
    try {
      const result = await api.adminApprovePartnerApplication(application.id, loginId)
      setApplications((items) => items.filter((item) => item.id !== application.id))
      setProvisioned(result)
      setNotice({ tone: 'success', text: `已为“${application.displayName}”创建待激活合作方账号。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '创建合作方账号未完成。' })
    } finally {
      setBusyId(null)
    }
  }

  return <section className="admin-partner-applications" aria-label="合作方申请处理">
    <div className="workspace-section-heading"><div><p className="eyebrow">合作方申请</p><h2>受控创建账号</h2></div><button className="button ghost" type="button" onClick={() => void load().then(setApplications)} disabled={loading}>刷新申请</button></div>
    <p className="workspace-help">申请正文只在管理员受控界面展示。批准后创建待激活账号；系统不会自动发送邮件或保存真实联系方式。</p>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    {provisioned ? <section className="one-time-token" aria-live="assertive"><strong>一次性激活链接（仅显示本次）</strong><p>受控登录名：<code>{provisioned.loginId}</code></p><output>{`${window.location.origin}/auth/activate?token=${encodeURIComponent(provisioned.activationToken)}`}</output><p>请通过已批准的受控渠道交付。关闭、刷新或重复请求后，系统不会再次返回该凭据。</p></section> : null}
    <div className="partner-application-list" aria-live="polite">{loading ? <p>正在加载申请…</p> : applications.length === 0 ? <p>当前没有待处理的合作方申请。</p> : applications.map((application) => <article key={application.id} className="partner-application-row"><div><h3>{application.displayName}</h3><p>{application.introduction}</p>{application.portfolioUrl ? <a href={application.portfolioUrl} target="_blank" rel="noreferrer">查看公开作品链接 ↗</a> : null}</div><form onSubmit={(event) => void approve(event, application)}><label>受控登录名<input name="loginId" required pattern="[A-Za-z0-9._-]{4,100}" minLength={4} maxLength={100} placeholder="partner-name" /></label><button className="button primary" disabled={busyId === application.id}>{busyId === application.id ? '创建中…' : '批准并生成激活凭据'}</button></form></article>)}</div>
  </section>
}
