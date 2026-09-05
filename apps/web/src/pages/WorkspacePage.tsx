import { useEffect, useState } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { AdminLifecyclePanel } from '../components/AdminLifecyclePanel'
import { AdminAuditPanel } from '../components/AdminAuditPanel'
import { AdminPartnerApplicationsPanel } from '../components/AdminPartnerApplicationsPanel'
import { AdminPartnerProfilesPanel } from '../components/AdminPartnerProfilesPanel'
import { AdminPublicSubmissionsPanel } from '../components/AdminPublicSubmissionsPanel'
import { AdminReviewWorkspace } from '../components/AdminReviewWorkspace'
import { PartnerLifecyclePanel } from '../components/PartnerLifecyclePanel'
import { api } from '../lib/api'
import { authorizeDraftDiscard } from '../lib/draft-guard'

type Identity = { loginId: string; roles: string[] }
type Notice = { tone: 'success' | 'error'; text: string } | null

function AdminNavigation() {
  return <nav className="workspace-navigation" aria-label="管理后台导航">
    <Link to="/workspace/content">内容管理</Link>
    <Link to="/workspace/reviews">审核队列</Link>
    <Link to="/workspace/submissions">公开投稿</Link>
    <Link to="/workspace/applications">合作方申请</Link>
    <Link to="/workspace/partners">合作方与账号</Link>
    <Link to="/workspace/audit">审计记录</Link>
  </nav>
}

export function WorkspacePage() {
  const [identity, setIdentity] = useState<Identity | null>(null)
  const [message, setMessage] = useState('正在验证受控会话…')
  const [notice, setNotice] = useState<Notice>(null)
  const [signingOut, setSigningOut] = useState(false)
  const [draftDirty, setDraftDirty] = useState(false)
  const routerLocation = useLocation()
  useEffect(() => { api.me().then(setIdentity).catch(() => setMessage('当前没有有效的受控会话。')) }, [])

  function confirmUnsavedDraft() {
    return !draftDirty || window.confirm('当前还有未保存修改，确定放弃并离开吗？')
  }

  function confirmDiscard() {
    if (!confirmUnsavedDraft()) return false
    authorizeDraftDiscard()
    setDraftDirty(false)
    return true
  }

  function guardWorkspaceLink(event: ReactMouseEvent<HTMLElement>) {
    if (!(event.target as Element).closest('a[href]') || confirmDiscard()) return
    event.preventDefault()
    event.stopPropagation()
  }

  async function signOut() {
    if (!confirmUnsavedDraft()) return
    setSigningOut(true); setNotice(null)
    try { await api.signOut(); authorizeDraftDiscard(); setDraftDirty(false); window.location.assign('/') }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '退出失败，请重试。' }); setSigningOut(false) }
  }

  if (!identity) return <main className="auth-page"><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><section className="state"><strong>{message}</strong><Link className="button ghost" to="/auth/sign-in">前往登录</Link></section></main>
  const isAdmin = identity.roles.includes('ADMIN') || identity.roles.includes('SUPER_ADMIN')
  const isSuperAdmin = identity.roles.includes('SUPER_ADMIN')
  const isPartner = identity.roles.includes('PARTNER')
  const section = routerLocation.pathname.split('/')[2] || 'content'
  const reviewId = section === 'reviews' ? routerLocation.pathname.split('/')[3] : undefined

  let workspace = <section className="state"><strong>当前账号没有可用的工作台权限。</strong><span>如账号资格有变化，请联系管理员在受控流程中处理。</span></section>
  if (isAdmin) {
    workspace = section === 'reviews' ? <AdminReviewWorkspace contentId={reviewId} />
      : section === 'submissions' ? <AdminPublicSubmissionsPanel />
        : section === 'applications' ? <AdminPartnerApplicationsPanel />
          : section === 'partners' ? <AdminPartnerProfilesPanel isSuperAdmin={isSuperAdmin} />
            : section === 'audit' ? <AdminAuditPanel />
              : <AdminLifecyclePanel isSuperAdmin={isSuperAdmin} onDirtyChange={setDraftDirty} confirmDiscard={confirmDiscard} />
  } else if (isPartner) workspace = <PartnerLifecyclePanel onDirtyChange={setDraftDirty} confirmDiscard={confirmDiscard} />

  return <main className="workspace" onClickCapture={guardWorkspaceLink}><header><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><button className="text-button" type="button" disabled={signingOut} onClick={() => void signOut()}>{signingOut ? '正在退出…' : '退出'}</button></header><section><p className="eyebrow">受控工作台</p><h1>你好，{identity.loginId}</h1><p>当前角色：{identity.roles.join('、')}。所有关键操作均由服务端再次检查角色、归属和内容状态。</p>{notice ? <p className={`notice notice-${notice.tone}`} role="alert">{notice.text}</p> : null}{isAdmin ? <AdminNavigation /> : null}{workspace}</section></main>
}
