import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, KeyRound, ShieldCheck } from 'lucide-react'
import { api, ApiFailure } from '../lib/api'
import { usePageTitle } from '../lib/hooks'
import { ThemeButton } from '../components/theme'
import { BusyLabel, Notice, message, type NoticeValue } from '../components/ui'
function AuthShell({ activation = false, children }: { activation?: boolean; children: ReactNode }) {
  return (
    <main className="auth-shell">
      <section className="auth-aside">
        <Link className="wordmark" to="/">
          VOIDHAVEN<span>／</span>
        </Link>
        <div className="auth-aside-copy">
          <p className="eyebrow">{activation ? 'FIRST ACCESS' : 'CONTROLLED ACCESS'}</p>
          <h1>
            {activation ? (
              <>
                Start with
                <br />
                <em>care.</em>
              </>
            ) : (
              <>
                Make good
                <br />
                <em>work</em> visible.
              </>
            )}
          </h1>
          <p>
            {activation
              ? '一次性激活链接仅用于初始化受控账号。请勿转发，完成后它将失效。'
              : '面向合作方与管理员的工作入口。让可靠的内容，在认真维护中被更多人看见。'}
          </p>
        </div>
        <div className="auth-aside-foot">
          <ShieldCheck size={15} /> Session-protected workspace
        </div>
      </section>
      <section className="auth-main">
        <div className="auth-topbar">
          <Link className="text-link" to="/">
            <ArrowLeft size={14} />
            返回首页
          </Link>
          <ThemeButton />
        </div>
        {children}
      </section>
    </main>
  )
}
export function SignInPage() {
  usePageTitle('受控登录')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const navigate = useNavigate()
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    const form = new FormData(e.currentTarget)
    setBusy(true)
    setNotice(null)
    setFieldErrors({})
    try {
      await api.signIn(String(form.get('loginId')).trim(), String(form.get('password')))
      navigate('/workspace', { replace: true })
    } catch (err) {
      if (err instanceof ApiFailure) setFieldErrors(err.fieldErrors)
      setNotice({
        tone: 'error',
        text:
          err instanceof ApiFailure && err.status === 401
            ? `账号或密码不正确，请检查后重试。${err.requestId ? `（请求编号：${err.requestId}）` : ''}`
            : message(err),
      })
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthShell>
      <form className="auth-card" onSubmit={submit}>
        <p className="eyebrow">受控访问</p>
        <h2>欢迎回来</h2>
        <p className="muted">使用管理员创建的受控账号登录。</p>
        <label>
          账号
          <input name="loginId" autoComplete="username" required placeholder="your-account" disabled={busy} />
          {fieldErrors.loginId ? <small className="field-error">{fieldErrors.loginId}</small> : null}
        </label>
        {fieldErrors.password ? <small className="field-error">{fieldErrors.password}</small> : null}
        <label>
          密码
          <span className="password-field">
            <input
              name="password"
              aria-label="密码"
              type={show ? 'text' : 'password'}
              autoComplete="current-password"
              required
              disabled={busy}
            />
            <button type="button" onClick={() => setShow(!show)} aria-label={show ? '隐藏密码' : '显示密码'}>
              {show ? '隐藏' : '显示'}
            </button>
          </span>
        </label>
        <button className="button primary" disabled={busy}>
          <BusyLabel busy={busy}>
            进入工作台 <ArrowRight size={15} />
          </BusyLabel>
        </button>
        <Notice value={notice} />
        <p className="form-foot">
          <KeyRound size={14} />
          首次激活请使用管理员提供的一次性链接。
        </p>
      </form>
    </AuthShell>
  )
}
export function ActivationPage() {
  usePageTitle('首次激活')
  const [params, setParams] = useSearchParams()
  const [token] = useState(() => params.get('token') ?? '')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy || done) return
    const form = e.currentTarget
    const data = new FormData(form)
    if (data.get('password') !== data.get('confirm')) {
      setNotice({ tone: 'error', text: '两次输入的密码不一致。' })
      return
    }
    setBusy(true)
    setNotice(null)
    setFieldErrors({})
    try {
      const result = await api.consumeActivation(token, String(data.get('password')))
      form.reset()
      setDone(true)
      setParams({}, { replace: true })
      setNotice({ tone: 'success', text: `账号 ${result.loginId} 已激活，请使用新密码登录。` })
    } catch (err) {
      if (err instanceof ApiFailure) setFieldErrors(err.fieldErrors)
      setNotice({ tone: 'error', text: message(err) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <AuthShell activation>
      <form className="auth-card" onSubmit={submit}>
        <p className="eyebrow">账号激活</p>
        <h2>{done ? '一切准备就绪' : '设置新密码'}</h2>
        {!token ? (
          <Notice value={{ tone: 'error', text: '激活链接缺少凭据，请联系管理员获取有效链接。' }} />
        ) : (
          !done && (
            <>
              <p className="muted">密码至少 12 位，并仅用于本次受控账号。</p>
              <label>
                新密码
                <span className="password-field">
                  <input
                    name="password"
                    aria-label="新密码"
                    type={show ? 'text' : 'password'}
                    autoComplete="new-password"
                    minLength={12}
                    required
                    disabled={busy}
                  />
                  <button type="button" onClick={() => setShow(!show)}>
                    {show ? '隐藏' : '显示'}
                  </button>
                </span>
              </label>
              {fieldErrors.password ? <small className="field-error">{fieldErrors.password}</small> : null}
              <label>
                确认密码
                <input
                  name="confirm"
                  type={show ? 'text' : 'password'}
                  autoComplete="new-password"
                  minLength={12}
                  required
                  disabled={busy}
                />
              </label>
              <button className="button primary" disabled={busy}>
                <BusyLabel busy={busy}>激活账号</BusyLabel>
              </button>
            </>
          )
        )}
        <Notice value={notice} />
        {done && (
          <Link className="button primary" to="/auth/sign-in">
            前往登录 <ArrowRight size={15} />
          </Link>
        )}
      </form>
    </AuthShell>
  )
}
