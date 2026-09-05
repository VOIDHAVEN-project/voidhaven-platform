import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api'

export function SignInPage() {
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const navigate = useNavigate()

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    setBusy(true); setMessage(null)
    try {
      await api.signIn(String(values.get('loginId')), String(values.get('password')))
      navigate('/workspace')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '登录未成功。请检查受控账号信息，或联系管理员。')
    } finally { setBusy(false) }
  }

  return <main className="auth-page"><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><form onSubmit={submit}><p className="eyebrow">受控访问</p><h1>合作方 / 管理员登录</h1><p>此入口不面向普通访客。请使用管理员受控创建的账号。</p><label>账号<input name="loginId" autoComplete="username" required /></label><label>密码<input name="password" type="password" autoComplete="current-password" required /></label><button className="button primary" disabled={busy}>{busy ? '验证中…' : '登录'}</button>{message ? <p className="notice" role="alert">{message}</p> : null}</form></main>
}
