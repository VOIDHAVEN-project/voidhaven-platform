import { useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowUpRight, Feather, ShieldCheck } from 'lucide-react'
import { ApiFailure, api, type Submission } from '../lib/api'
import { BusyLabel, Notice, message, type NoticeValue } from '../components/ui'
import { usePageTitle } from '../lib/hooks'
import { createIdempotencyKey } from '../lib/secure-id'
export function FeedbackPage() {
  usePageTitle('投稿 / 反馈')
  const [params] = useSearchParams()
  const [type, setType] = useState<Submission['submissionType']>('CONTENT')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKey = useRef<string | null>(null)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    const form = e.currentTarget
    const data = new FormData(form)
    const title = String(data.get('title')).trim()
    const text = String(data.get('message')).trim()
    if (!title || !text) {
      setNotice({ tone: 'error', text: '请填写标题和内容，不能只输入空格。' })
      return
    }
    setBusy(true)
    setNotice(null)
    setFieldErrors({})
    try {
      const key = idempotencyKey.current ?? createIdempotencyKey()
      idempotencyKey.current = key
      await api.submit({
        submissionType: type,
        title,
        message: text,
        linkUrl: String(data.get('linkUrl')).trim() || undefined,
        imageDescription: String(data.get('imageDescription')).trim() || undefined,
      }, key)
      form.reset()
      idempotencyKey.current = null
      setNotice({
        tone: 'success',
        text: '已收到。你的内容会进入审核流程，不会直接公开。感谢你让这里更可靠。',
      })
    } catch (err) {
      if (err instanceof ApiFailure) setFieldErrors(err.fieldErrors)
      setNotice({ tone: 'error', text: message(err) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">A GOOD FIND DESERVES TO BE SHARED</p>
        <h1>
          让好信息，
          <br />
          <span>继续流动。</span>
        </h1>
        <p>提供一条线索，指出一处遗漏，或告诉我们哪里可以更好。</p>
      </header>
      <div className="public-form-layout">
        <form className="paper-form" onSubmit={submit} onChange={() => { idempotencyKey.current = null; setFieldErrors({}) }}>
          <fieldset disabled={busy}>
            <legend className="sr-only">投稿 / 反馈</legend>
            <div className="form-section-number">
              01 <span>你想告诉我们什么？</span>
            </div>
            <fieldset className="choice-group">
              <legend>提交类型</legend>
              {(
                [
                  ['CONTENT', '内容线索'],
                  ['FEEDBACK', '产品反馈'],
                  ['COPYRIGHT', '版权/下线反馈'],
                ] as const
              ).map(([value, label]) => (
                <label key={value}>
                  <input
                    type="radio"
                    name="submissionType"
                    value={value}
                    checked={value === type}
                    onChange={() => setType(value)}
                  />
                  <span>{label}</span>
                </label>
              ))}
            </fieldset>
            <label>
              标题
              <input name="title" required maxLength={240} placeholder="用一句话说明你要提交什么" />
              {fieldErrors.title ? <small className="field-error">{fieldErrors.title}</small> : null}
            </label>
            <label>
              内容
              <textarea
                name="message"
                required
                maxLength={5000}
                rows={7}
                placeholder={
                  type === 'CONTENT'
                    ? '描述这条线索，以及它值得被收录的原因。'
                    : type === 'COPYRIGHT'
                      ? '说明涉及的内容、相关权利与处理诉求。'
                      : '描述遇到的问题或你的建议。'
                }
              />
              {fieldErrors.message ? <small className="field-error">{fieldErrors.message}</small> : null}
            </label>
            <div className="form-section-number">
              02 <span>补充一些线索</span>
            </div>
            <label>
              相关链接 <small>可选</small>
              <input
                name="linkUrl"
                type="url"
                maxLength={2048}
                defaultValue={params.get('link') ?? ''}
                placeholder="https://"
              />
              {fieldErrors.linkUrl ? <small className="field-error">{fieldErrors.linkUrl}</small> : null}
            </label>
            <label>
              图片说明 <small>可选，不上传图片</small>
              <input name="imageDescription" maxLength={1000} placeholder="用文字说明图片的内容与来源" />
            </label>
            <p className="field-help">
              请不要填写微信、手机号、私人邮箱等不必要的个人信息。公开投稿不接受正文图片上传。
            </p>
            <button className="button primary" disabled={busy}>
              <BusyLabel busy={busy}>
                提交审核 <ArrowUpRight size={16} />
              </BusyLabel>
            </button>
          </fieldset>
          <Notice value={notice} />
        </form>
        <aside className="form-aside">
          <Feather size={30} strokeWidth={1.2} />
          <h2>
            每一次补充，
            <br />
            都让目录更可靠。
          </h2>
          <p>线索会先进入审核，再决定是否收录。版权或下线反馈也会进入受控处理流程。</p>
          <div className="aside-rule" />
          <p>
            希望长期维护内容？
            <br />
            <Link to="/join">
              申请成为合作方 <ArrowUpRight size={13} />
            </Link>
          </p>
        </aside>
      </div>
    </>
  )
}
export function JoinPage() {
  usePageTitle('加入我们')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const idempotencyKey = useRef<string | null>(null)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    const form = e.currentTarget
    const data = new FormData(form)
    const displayName = String(data.get('displayName')).trim()
    const introduction = String(data.get('introduction')).trim()
    if (!displayName || !introduction) {
      setNotice({ tone: 'error', text: '请填写展示名称和内容介绍，不能只输入空格。' })
      return
    }
    setBusy(true)
    setNotice(null)
    setFieldErrors({})
    try {
      const key = idempotencyKey.current ?? createIdempotencyKey()
      idempotencyKey.current = key
      await api.partnerApply({
        displayName,
        introduction,
        portfolioUrl: String(data.get('portfolioUrl')).trim() || undefined,
      }, key)
      form.reset()
      idempotencyKey.current = null
      setNotice({
        tone: 'success',
        text: '申请已进入审核队列。通过后，管理员会以受控方式交付账号及首次激活链接。',
      })
    } catch (err) {
      if (err instanceof ApiFailure) setFieldErrors(err.fieldErrors)
      setNotice({ tone: 'error', text: message(err) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">MAKE GOOD THINGS VISIBLE, TOGETHER</p>
        <h1>
          一起，把值得的事
          <br />
          <span>照顾得更好。</span>
        </h1>
        <p>如果你愿意持续整理、核对和维护内容，欢迎成为 VOIDHAVEN 的合作方。</p>
      </header>
      <div className="join-steps">
        {['提交申请', '人工审核', '受控激活', '维护内容'].map((step, i) => (
          <div key={step}>
            <span>0{i + 1}</span>
            <strong>{step}</strong>
          </div>
        ))}
      </div>
      <div className="public-form-layout">
        <form className="paper-form" onSubmit={submit} onChange={() => { idempotencyKey.current = null; setFieldErrors({}) }}>
          <h2>申请成为合作方</h2>
          <fieldset disabled={busy}>
            <legend className="sr-only">合作方申请信息</legend>
            <label>
              展示名称
              <input name="displayName" required maxLength={120} placeholder="团队或项目名称" />
              {fieldErrors.displayName ? <small className="field-error">{fieldErrors.displayName}</small> : null}
            </label>
            <label>
              你想维护什么内容？
              <textarea
                name="introduction"
                required
                maxLength={5000}
                rows={7}
                placeholder="说明内容领域、更新方式和公开来源。"
              />
              {fieldErrors.introduction ? <small className="field-error">{fieldErrors.introduction}</small> : null}
            </label>
            <label>
              公开作品链接 <small>可选</small>
              <input name="portfolioUrl" type="url" maxLength={2048} placeholder="https://" />
              {fieldErrors.portfolioUrl ? <small className="field-error">{fieldErrors.portfolioUrl}</small> : null}
            </label>
            <p className="field-help">请勿填写不必要的私人联系方式。账号与首次激活由管理员受控交付。</p>
            <button className="button primary" disabled={busy}>
              <BusyLabel busy={busy}>
                提交合作方申请 <ArrowUpRight size={16} />
              </BusyLabel>
            </button>
          </fieldset>
          <Notice value={notice} />
        </form>
        <aside className="form-aside">
          <ShieldCheck size={30} strokeWidth={1.2} />
          <h2>
            在可靠的入口，
            <br />
            继续沟通。
          </h2>
          <p>合作方负责受控投稿和维护，后续沟通与组队仍在社群中完成。</p>
          <p>站内不保存社群成员、聊天、报名或筛选记录。真实社群入口由受控流程提供。</p>
          <button className="button ghost" disabled>
            社群入口尚未配置
          </button>
        </aside>
      </div>
    </>
  )
}
