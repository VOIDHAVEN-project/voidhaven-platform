import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { PartnerProfile } from '../lib/api'
import { api } from '../lib/api'

type Notice = { tone: 'success' | 'error'; text: string } | null

export function AdminPartnerProfilesPanel() {
  const [profiles, setProfiles] = useState<PartnerProfile[]>([])
  const [notice, setNotice] = useState<Notice>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const load = useCallback(() => api.adminPartnerProfiles(), [])

  useEffect(() => {
    let active = true
    void load().then((items) => { if (active) setProfiles(items) }).catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方档案。' }) })
    return () => { active = false }
  }, [load])

  async function updateQuota(event: FormEvent<HTMLFormElement>, profile: PartnerProfile) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    setBusy(`quota:${profile.id}`)
    try {
      const updated = await api.adminSetPartnerQuota(profile.id, Number(values.get('quota')), String(values.get('reason')).trim())
      setProfiles((items) => items.map((item) => item.id === updated.id ? updated : item))
      setNotice({ tone: 'success', text: `“${updated.displayName}”的公开配额已更新为 ${updated.contentQuota}。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '配额更新未完成。' })
    } finally { setBusy(null) }
  }

  async function transfer(event: FormEvent<HTMLFormElement>, profile: PartnerProfile) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    setBusy(`transfer:${profile.id}`)
    try {
      await api.adminTransferOwnership(String(values.get('contentItemId')).trim(), profile.id, String(values.get('reason')).trim())
      setNotice({ tone: 'success', text: `内容归属已转交给“${profile.displayName}”，请由该合作方在工作台继续维护。` })
      form.reset()
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '归属转交未完成。' })
    } finally { setBusy(null) }
  }

  return <section className="admin-partner-profiles" aria-label="合作方配额和归属管理">
    <div className="workspace-section-heading"><div><p className="eyebrow">合作方运营</p><h2>配额与内容归属</h2></div><button className="button ghost" type="button" onClick={() => void load().then(setProfiles)}>刷新档案</button></div>
    <p className="workspace-help">配额只计算公开且活跃的内容。内容转交仅限管理员操作，提交时必须给出原因并由服务端审计。</p>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-profile-list">{profiles.length === 0 ? <p>当前没有已激活的合作方档案。</p> : profiles.map((profile) => <article className="partner-profile-row" key={profile.id}><div><h3>{profile.displayName}</h3><p className="admin-state">档案状态：{profile.active ? '启用' : '停用'} · 当前配额：{profile.contentQuota}</p></div><div className="partner-profile-actions"><form onSubmit={(event) => void updateQuota(event, profile)}><label>公开配额<input name="quota" type="number" min="0" max="5" required defaultValue={profile.contentQuota} /></label><label>调整原因<input name="reason" required maxLength={1000} /></label><button className="button ghost" disabled={busy === `quota:${profile.id}`}>保存配额</button></form><form onSubmit={(event) => void transfer(event, profile)}><label>待转交内容 ID<input name="contentItemId" required minLength={26} maxLength={26} /></label><label>转交原因<input name="reason" required maxLength={1000} /></label><button className="button ghost" disabled={busy === `transfer:${profile.id}`}>转交内容</button></form></div></article>)}</div>
  </section>
}
