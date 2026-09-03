import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AdminLifecyclePanel } from '../components/AdminLifecyclePanel'
import { AdminAuditPanel } from '../components/AdminAuditPanel'
import { AdminPartnerApplicationsPanel } from '../components/AdminPartnerApplicationsPanel'
import { AdminPartnerProfilesPanel } from '../components/AdminPartnerProfilesPanel'
import { AdminPublicSubmissionsPanel } from '../components/AdminPublicSubmissionsPanel'
import { PartnerLifecyclePanel } from '../components/PartnerLifecyclePanel'
import { api } from '../lib/api'

type Identity = { loginId: string; roles: string[] }
export function WorkspacePage() {
  const [identity, setIdentity] = useState<Identity | null>(null)
  const [message, setMessage] = useState('正在验证受控会话…')
  useEffect(() => { api.me().then(setIdentity).catch(() => setMessage('当前没有有效的受控会话。')) }, [])
  if (!identity) return <main className="auth-page"><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><section className="state"><strong>{message}</strong><Link className="button ghost" to="/auth/sign-in">前往登录</Link></section></main>
  const isAdmin = identity.roles.includes('ADMIN') || identity.roles.includes('SUPER_ADMIN')
  const isPartner = identity.roles.includes('PARTNER')
  return <main className="workspace"><header><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><button className="text-button" onClick={() => api.signOut().finally(() => location.assign('/'))}>退出</button></header><section><p className="eyebrow">受控工作台</p><h1>你好，{identity.loginId}</h1><p>当前角色：{identity.roles.join('、')}。前端路由只改善体验；所有关键操作均由服务端再次检查角色、归属和内容状态。</p>{isAdmin ? <><AdminLifecyclePanel /><AdminPublicSubmissionsPanel /><AdminPartnerApplicationsPanel /><AdminPartnerProfilesPanel /><AdminAuditPanel /></> : isPartner ? <PartnerLifecyclePanel /> : <section className="state"><strong>当前账号没有可用的工作台权限。</strong><span>如账号资格有变化，请联系管理员在受控流程中处理。</span></section>}</section></main>
}
