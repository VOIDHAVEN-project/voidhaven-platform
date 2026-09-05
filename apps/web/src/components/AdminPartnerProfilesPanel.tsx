import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AccountStatus, PartnerProfile } from '../lib/api'
import { api } from '../lib/api'

type Notice = { tone: 'success' | 'error'; text: string } | null

export function AdminPartnerProfilesPanel({ isSuperAdmin = false }: { isSuperAdmin?: boolean }) {
  const [profiles, setProfiles] = useState<PartnerProfile[]>([])
  const [accounts, setAccounts] = useState<AccountStatus[]>([])
  const [notice, setNotice] = useState<Notice>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [profileCursor, setProfileCursor] = useState<string | null>(null)
  const [accountCursor, setAccountCursor] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const load = useCallback(async () => {
    const [profileValues, accountValues] = await Promise.all([api.adminPartnerProfiles({ query }), isSuperAdmin ? api.adminAccounts({ query }) : Promise.resolve({ items: [], nextCursor: null })])
    setProfiles(profileValues.items); setProfileCursor(profileValues.nextCursor); setAccounts(accountValues.items); setAccountCursor(accountValues.nextCursor)
  }, [isSuperAdmin, query])

  useEffect(() => {
    let active = true
    // Initial server synchronization populates the protected management view.
    // oxlint-disable-next-line react/set-state-in-effect
    void load().catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方档案。' }) })
    return () => { active = false }
  }, [load])

  async function loadMoreProfiles() {
    if (!profileCursor) return
    setBusy('profiles:more')
    try { const page = await api.adminPartnerProfiles({ query, cursor: profileCursor }); setProfiles((items) => [...items, ...page.items]); setProfileCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页合作方加载失败。' }) }
    finally { setBusy(null) }
  }

  async function loadMoreAccounts() {
    if (!accountCursor) return
    setBusy('accounts:more')
    try { const page = await api.adminAccounts({ query, cursor: accountCursor }); setAccounts((items) => [...items, ...page.items]); setAccountCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页账号加载失败。' }) }
    finally { setBusy(null) }
  }

  async function refresh() {
    setNotice(null)
    try { await load() }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新合作方档案失败。' }) }
  }

  function search(event: FormEvent<HTMLFormElement>) { event.preventDefault(); setQuery(String(new FormData(event.currentTarget).get('query')).trim()) }

  async function updateQuota(event: FormEvent<HTMLFormElement>, profile: PartnerProfile) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    setBusy(`quota:${profile.id}`)
    try {
      const updated = await api.adminSetPartnerQuota(profile.id, Number(values.get('quota')), String(values.get('reason')).trim())
      setProfiles((items) => items.map((item) => item.id === updated.id ? updated : item))
      setNotice({ tone: 'success', text: `“${updated.displayName}”的公开配额已更新为 ${updated.contentQuota}。` })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '配额更新未完成。' }) }
    finally { setBusy(null) }
  }

  async function transfer(event: FormEvent<HTMLFormElement>, profile: PartnerProfile) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    if (!window.confirm(`确定将该内容转交给“${profile.displayName}”吗？`)) return
    setBusy(`transfer:${profile.id}`)
    try {
      await api.adminTransferOwnership(String(values.get('contentItemId')).trim(), profile.id, String(values.get('reason')).trim())
      setNotice({ tone: 'success', text: `内容归属已转交给“${profile.displayName}”。` }); form.reset()
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '归属转交未完成。' }) }
    finally { setBusy(null) }
  }

  async function changeAccount(event: FormEvent<HTMLFormElement>, account: AccountStatus) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const action = String(values.get('action')) as 'suspend' | 'reactivate' | 'revoke'
    const reason = String(values.get('reason')).trim()
    if (!window.confirm(action === 'revoke' ? `撤销“${account.loginId}”后不可恢复，确定继续吗？` : `确定对“${account.loginId}”执行该账号操作吗？`)) return
    setBusy(`account:${account.id}`)
    try {
      const updated = action === 'suspend' ? await api.adminSuspendAccount(account.id, reason) : action === 'reactivate' ? await api.adminReactivateAccount(account.id, reason) : await api.adminRevokeAccount(account.id, reason)
      setAccounts((items) => items.map((item) => item.id === updated.id ? updated : item))
      setProfiles((items) => items.map((profile) => profile.accountId === updated.id ? { ...profile, qualificationStatus: updated.qualificationStatus } : profile))
      setNotice({ tone: 'success', text: `账号“${updated.loginId}”当前状态：${updated.qualificationStatus}。` })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '账号操作未完成。' }) }
    finally { setBusy(null) }
  }

  return <section className="admin-partner-profiles" aria-label="合作方配额、归属和账号管理">
    <div className="workspace-section-heading"><div><p className="eyebrow">合作方运营</p><h2>配额、归属与账号</h2></div><button className="button ghost" type="button" onClick={() => void refresh()}>刷新档案</button></div>
    <p className="workspace-help">配额只计算公开且活跃的内容。停用账号不会自动隐藏其已发布内容；撤销账号不可恢复。</p>
    <form className="workspace-filter" onSubmit={search}><label>搜索合作方或账号<input name="query" defaultValue={query} /></label><button className="button ghost">搜索</button></form>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-profile-list">{profiles.length === 0 ? <p>当前没有合作方档案。</p> : profiles.map((profile) => <article className="partner-profile-row" key={profile.id}><div><h3>{profile.displayName}</h3><p className="admin-state">账号：{profile.accountId} · 资格：{profile.qualificationStatus} · 档案：{profile.active ? '启用' : '停用'} · 配额：{profile.contentQuota}</p></div><div className="partner-profile-actions"><form onSubmit={(event) => void updateQuota(event, profile)}><label>公开配额<input name="quota" type="number" min="0" max="5" required defaultValue={profile.contentQuota} /></label><label>调整原因<input name="reason" required maxLength={1000} /></label><button className="button ghost" disabled={busy === `quota:${profile.id}`}>保存配额</button></form><form onSubmit={(event) => void transfer(event, profile)}><label>待转交内容 ID<input name="contentItemId" required minLength={26} maxLength={26} /></label><label>转交原因<input name="reason" required maxLength={1000} /></label><button className="button ghost" disabled={busy === `transfer:${profile.id}`}>转交内容</button></form></div></article>)}</div>
    {profileCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMoreProfiles()} disabled={busy === 'profiles:more'}>{busy === 'profiles:more' ? '正在加载…' : '加载更多合作方'}</button> : null}
    {isSuperAdmin ? <section className="account-management"><div className="workspace-section-heading"><div><p className="eyebrow">超级管理员</p><h2>账号资格</h2></div></div><div className="partner-profile-list">{accounts.map((account) => <article className="partner-profile-row" key={account.id}><div><h3>{account.loginId}</h3><p className="admin-state">{account.roles.join('、')} · {account.qualificationStatus} · {account.id}</p></div><form onSubmit={(event) => void changeAccount(event, account)}><label>账号操作<select name="action" defaultValue="" required><option value="" disabled>请选择</option>{account.qualificationStatus === 'ACTIVE' ? <option value="suspend">停用</option> : null}{account.qualificationStatus === 'SUSPENDED' ? <option value="reactivate">恢复</option> : null}{account.qualificationStatus !== 'REVOKED' ? <option value="revoke">永久撤销</option> : null}</select></label><label>操作原因<input name="reason" required maxLength={1000} /></label><button className="button ghost" disabled={busy === `account:${account.id}`}>执行账号操作</button></form></article>)}</div>{accountCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMoreAccounts()} disabled={busy === 'accounts:more'}>{busy === 'accounts:more' ? '正在加载…' : '加载更多账号'}</button> : null}</section> : <p className="workspace-help">账号停用、恢复和撤销仅超级管理员可操作。</p>}
  </section>
}
