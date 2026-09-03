import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'

export function ActivationPage() {
  const [params] = useSearchParams()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const token = params.get('token') ?? ''

  async function activate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) { setNotice('激活链接缺少凭据，请联系管理员重新生成。'); return }
    if (password !== confirm) { setNotice('两次输入的密码不一致。'); return }
    setBusy(true); setNotice(null)
    try {
      const result = await api.consumeActivation(token, password)
      setNotice(`账号 ${result.loginId} 已激活。请使用新密码登录。`)
      setPassword(''); setConfirm('')
    } catch (error) { setNotice(error instanceof Error ? error.message : '激活未完成，请联系管理员。') } finally { setBusy(false) }
  }

  return <main className="auth-page"><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><form onSubmit={activate}><p className="eyebrow">受控首次激活</p><h1>设置你的密码</h1><p>此链接只能使用一次，默认 24 小时内有效。请勿转发给他人。</p><label>新密码<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={12} maxLength={128} autoComplete="new-password" required /></label><label>确认新密码<input type="password" value={confirm} onChange={(event) => setConfirm(event.target.value)} minLength={12} maxLength={128} autoComplete="new-password" required /></label><button className="button primary" disabled={busy}>{busy ? '激活中…' : '激活账号'}</button>{notice ? <p className="notice" role="status">{notice}</p> : null}{notice?.includes('已激活') ? <Link className="button ghost" to="/auth/sign-in">前往受控登录</Link> : null}</form></main>
}
