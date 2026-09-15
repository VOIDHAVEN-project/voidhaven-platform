import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowUpRight, Copy, Feather, ShieldCheck, Users } from 'lucide-react'
import {
  ApiFailure,
  api,
  type CommunityEntry,
  type CreatedCase,
  type ProductCapabilities,
  type Submission,
} from '../lib/api'
import { ControlledCommunityImage } from '../components/controlled-community-image'
import { BusyLabel, Notice, message, type NoticeValue } from '../components/ui'
import { copyText } from '../lib/copy-text'
import { usePageTitle } from '../lib/hooks'
import { createIdempotencyKey } from '../lib/secure-id'

function useProductCapabilities() {
  const [capabilities, setCapabilities] = useState<ProductCapabilities | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    api
      .capabilities(controller.signal)
      .then(setCapabilities)
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true)
      })
    return () => controller.abort()
  }, [])
  return { capabilities, failed }
}

export function FeedbackPage() {
  usePageTitle('投稿 / 反馈')
  const [params] = useSearchParams()
  const [type, setType] = useState<Submission['submissionType']>('CONTENT')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [receipt, setReceipt] = useState<CreatedCase | null>(null)
  const { capabilities, failed: capabilitiesFailed } = useProductCapabilities()
  const privateContactEnabled = capabilities?.privateContactEnabled === true
  const idempotencyKey = useRef<string | null>(null)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    const form = e.currentTarget
    const data = new FormData(form)
    const title = String(data.get('title')).trim()
    const text = String(data.get('message')).trim()
    if (type === 'COPYRIGHT' && !privateContactEnabled) {
      setNotice({ tone: 'error', text: '版权请求暂不可提交：私密联系方式能力尚未安全启用。' })
      return
    }
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
      const created = await api.submit(
        {
          submissionType: type,
          title,
          message: text,
          linkUrl: String(data.get('linkUrl')).trim() || undefined,
          imageDescription: String(data.get('imageDescription')).trim() || undefined,
          contact: String(data.get('contact') ?? '').trim() || undefined,
        },
        key,
      )
      form.reset()
      setReceipt(created)
      idempotencyKey.current = null
      setNotice({
        tone: 'success',
        text: '已收到。请立即保存下方回执号和查询码；查询码关闭页面后无法找回。',
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
        <form
          className="paper-form"
          onSubmit={submit}
          onChange={() => {
            idempotencyKey.current = null
            setFieldErrors({})
          }}
        >
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
                    disabled={value === 'COPYRIGHT' && !privateContactEnabled}
                    onChange={() => setType(value)}
                  />
                  <span>
                    {label}
                    {value === 'COPYRIGHT' && !privateContactEnabled ? '（暂不可用）' : ''}
                  </span>
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
            {privateContactEnabled ? (
              <label>
                联系方式 <small>{type === 'COPYRIGHT' ? '版权请求必填' : '可选'}</small>
                <input
                  name="contact"
                  required={type === 'COPYRIGHT'}
                  maxLength={500}
                  placeholder="邮箱、微信或其他可联系到你的方式"
                />
                {fieldErrors.contact ? <small className="field-error">{fieldErrors.contact}</small> : null}
              </label>
            ) : null}
            <p className="field-help">
              {privateContactEnabled
                ? '联系方式仅用于处理当前事项，不会公开；普通反馈可不填写。公开投稿不接受正文图片上传。'
                : '当前仅接受不含联系方式的内容线索和产品反馈；版权请求将在私密信息能力启用后开放。'}
            </p>
            <button className="button primary" disabled={busy}>
              <BusyLabel busy={busy}>
                提交审核 <ArrowUpRight size={16} />
              </BusyLabel>
            </button>
          </fieldset>
          {receipt ? <ReceiptCard receipt={receipt} /> : null}
          <Notice value={notice} />
          {capabilitiesFailed ? (
            <Notice value={{ tone: 'error', text: '功能状态暂时无法确认，请稍后刷新页面。' }} />
          ) : null}
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
            <Link to="/partners/apply">
              申请成为合作方 <ArrowUpRight size={13} />
            </Link>
          </p>
        </aside>
      </div>
    </>
  )
}
export function PartnerApplyPage() {
  usePageTitle('合作方申请')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [receipt, setReceipt] = useState<CreatedCase | null>(null)
  const { capabilities, failed: capabilitiesFailed } = useProductCapabilities()
  const available = capabilities?.privateContactEnabled === true
  const idempotencyKey = useRef<string | null>(null)
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    const form = e.currentTarget
    const data = new FormData(form)
    const displayName = String(data.get('displayName')).trim()
    const introduction = String(data.get('introduction')).trim()
    const contact = String(data.get('contact')).trim()
    if (!available) {
      setNotice({ tone: 'error', text: '合作申请暂未开放：私密联系方式能力尚未安全启用。' })
      return
    }
    if (!displayName || !introduction || !contact) {
      setNotice({ tone: 'error', text: '请填写展示名称、内容介绍和联系方式，不能只输入空格。' })
      return
    }
    setBusy(true)
    setNotice(null)
    setFieldErrors({})
    try {
      const key = idempotencyKey.current ?? createIdempotencyKey()
      idempotencyKey.current = key
      const created = await api.partnerApply(
        {
          displayName,
          introduction,
          portfolioUrl: String(data.get('portfolioUrl')).trim() || undefined,
          contact,
        },
        key,
      )
      form.reset()
      setReceipt(created)
      idempotencyKey.current = null
      setNotice({
        tone: 'success',
        text: '申请已进入审核队列。请立即保存回执号和查询码；通过后管理员会人工交付激活凭据。',
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
        <form
          className="paper-form"
          onSubmit={submit}
          onChange={() => {
            idempotencyKey.current = null
            setFieldErrors({})
          }}
        >
          <h2>申请成为合作方</h2>
          <fieldset disabled={busy || !available}>
            <legend className="sr-only">合作方申请信息</legend>
            <label>
              展示名称
              <input name="displayName" required maxLength={120} placeholder="团队或项目名称" />
              {fieldErrors.displayName ? (
                <small className="field-error">{fieldErrors.displayName}</small>
              ) : null}
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
              {fieldErrors.introduction ? (
                <small className="field-error">{fieldErrors.introduction}</small>
              ) : null}
            </label>
            <label>
              公开作品链接 <small>可选</small>
              <input name="portfolioUrl" type="url" maxLength={2048} placeholder="https://" />
              {fieldErrors.portfolioUrl ? (
                <small className="field-error">{fieldErrors.portfolioUrl}</small>
              ) : null}
            </label>
            <label>
              联系方式 <small>必填，不会公开</small>
              <input name="contact" required maxLength={500} placeholder="邮箱、微信或其他可联系到你的方式" />
              {fieldErrors.contact ? <small className="field-error">{fieldErrors.contact}</small> : null}
            </label>
            <p className="field-help">
              联系方式只用于申请审核和人工交付账号；账号与首次激活由管理员受控交付。
            </p>
            <button className="button primary" disabled={busy}>
              <BusyLabel busy={busy}>
                提交合作方申请 <ArrowUpRight size={16} />
              </BusyLabel>
            </button>
          </fieldset>
          {receipt ? <ReceiptCard receipt={receipt} /> : null}
          <Notice value={notice} />
          {!available && capabilities ? (
            <Notice value={{ tone: 'error', text: '合作申请暂未开放，请联系运营人员完成安全配置。' }} />
          ) : null}
          {capabilitiesFailed ? (
            <Notice value={{ tone: 'error', text: '功能状态暂时无法确认，请稍后刷新页面。' }} />
          ) : null}
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
          <Link className="button ghost" to="/join">
            使用邀请码加入社群
          </Link>
        </aside>
      </div>
    </>
  )
}

function ReceiptCard({ receipt }: { receipt: CreatedCase }) {
  const text = `回执号：${receipt.id}\n查询码：${receipt.queryCode}`
  return (
    <section className="token-card" aria-label="事项查询凭据">
      <ShieldCheck size={21} />
      <div>
        <strong>请立即保存查询凭据</strong>
        <code>{receipt.id}</code>
        <code>{receipt.queryCode}</code>
        <small>服务端不会保存查询码明文。关闭或刷新页面后无法找回，可在“进度查询”中使用。</small>
        <button
          className="button ghost"
          type="button"
          onClick={() => void navigator.clipboard.writeText(text)}
        >
          <Copy size={14} /> 复制回执和查询码
        </button>
      </div>
    </section>
  )
}

export function JoinPage() {
  usePageTitle('加入社群')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [entries, setEntries] = useState<CommunityEntry[]>([])
  const [grantToken, setGrantToken] = useState('')
  const { capabilities, failed: capabilitiesFailed } = useProductCapabilities()
  const available = capabilities?.communityGateEnabled === true
  useEffect(() => {
    let active = true
    api
      .communityEntries()
      .then((publicEntries) => {
        if (active) setEntries(publicEntries)
      })
      .catch(() => {
        if (active) setNotice({ tone: 'error', text: '公开社群入口暂时无法加载，请稍后刷新。' })
      })
    return () => {
      active = false
    }
  }, [])
  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const inviteCode = String(new FormData(form).get('inviteCode') ?? '').trim()
    if (!inviteCode) return
    if (!available) {
      setNotice({ tone: 'error', text: '社群入口暂未开放，请等待运营人员完成配置。' })
      return
    }
    setBusy(true)
    setNotice(null)
    try {
      const grant = await api.verifyCommunityInvite(inviteCode)
      setGrantToken(grant.grant)
      setEntries(await api.communityEntries(grant.grant))
      form.reset()
      setNotice({
        tone: 'success',
        text: `验证成功，本次页面授权有效至 ${new Date(grant.expiresAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}。`,
      })
    } catch (error) {
      api
        .communityEntries()
        .then(setEntries)
        .catch(() => setEntries([]))
      setGrantToken('')
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">CONTINUE IN THE COMMUNITY</p>
        <h1>
          验证一次，
          <br />
          <span>找到可靠的下一步。</span>
        </h1>
        <p>公开入口可直接查看；使用工作人员提供的批次邀请码后，还可查看受控入口。</p>
      </header>
      <div className="public-form-layout">
        <form className="paper-form" onSubmit={verify}>
          <h2>验证邀请码</h2>
          <fieldset disabled={busy || !available}>
            <label>
              邀请码
              <input name="inviteCode" required minLength={8} maxLength={256} autoComplete="off" />
            </label>
            <p className="field-help">邀请码不会写入浏览器存储；页面刷新后需要重新验证。</p>
            <button className="button primary" disabled={busy}>
              <BusyLabel busy={busy}>
                查看社群入口 <ArrowUpRight size={16} />
              </BusyLabel>
            </button>
          </fieldset>
          <Notice value={notice} />
          {!available && capabilities ? (
            <Notice value={{ tone: 'error', text: '邀请码入口暂未开放；这不是邀请码错误。' }} />
          ) : null}
          {capabilitiesFailed ? (
            <Notice value={{ tone: 'error', text: '功能状态暂时无法确认，请稍后刷新页面。' }} />
          ) : null}
        </form>
        <aside className="form-aside">
          <Users size={30} strokeWidth={1.2} />
          <h2>
            多个入口，
            <br />
            一个主要去处。
          </h2>
          {entries.length ? (
            entries.map((entry) => (
              <article key={entry.id} className="community-entry">
                <strong>
                  {entry.primary ? '主入口 · ' : ''}
                  {entry.name}
                </strong>
                {entry.description ? <p>{entry.description}</p> : null}
                {entry.value ? <CommunityEntryValue entry={entry} /> : null}
                {entry.mediaUrl && entry.accessLevel === 'PUBLIC' ? (
                  <img className="community-qr" src={entry.mediaUrl} alt={`${entry.name}二维码`} />
                ) : entry.mediaUrl && grantToken ? (
                  <ControlledCommunityImage
                    path={entry.mediaUrl}
                    grant={grantToken}
                    alt={`${entry.name}二维码`}
                  />
                ) : null}
              </article>
            ))
          ) : (
            <p>当前没有公开入口；验证成功后，受控入口会显示在这里。</p>
          )}
        </aside>
      </div>
    </>
  )
}

function CommunityEntryValue({ entry }: { entry: CommunityEntry }) {
  const href =
    entry.contactChannel === 'EMAIL'
      ? `mailto:${entry.value}`
      : entry.contactChannel === 'PHONE'
        ? `tel:${entry.value}`
        : entry.presentationType === 'LINK'
          ? entry.value
          : null
  return href ? (
    <a
      className="button ghost"
      href={href ?? undefined}
      target={href.startsWith('https://') ? '_blank' : undefined}
      rel="noreferrer"
    >
      {entry.contactChannel === 'EMAIL'
        ? '发送邮件'
        : entry.contactChannel === 'PHONE'
          ? '拨打电话'
          : '打开入口'}{' '}
      <ArrowUpRight size={14} />
    </a>
  ) : (
    <button className="button ghost" type="button" onClick={() => void copyText(entry.value ?? '')}>
      <Copy size={14} /> 复制{entry.name}
    </button>
  )
}

export function CaseStatusPage() {
  usePageTitle('进度查询')
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [result, setResult] = useState<Awaited<ReturnType<typeof api.submissionStatus>> | null>(null)
  async function query(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    try {
      const id = String(data.get('id') ?? '').trim()
      const queryCode = String(data.get('queryCode') ?? '').trim()
      const status =
        data.get('caseType') === 'PARTNER_APPLICATION'
          ? await api.partnerApplicationStatus(id, queryCode)
          : await api.submissionStatus(id, queryCode)
      setResult(status)
      setNotice(null)
    } catch (error) {
      setResult(null)
      setNotice({ tone: 'error', text: message(error) })
    }
  }
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">TRACK THE NEXT STEP</p>
        <h1>
          知道事项，
          <br />
          <span>走到了哪里。</span>
        </h1>
        <p>查询码只用于本次事项，不需要注册账号。</p>
      </header>
      <div className="public-form-layout">
        <form className="paper-form" onSubmit={query}>
          <label>
            事项类型
            <select name="caseType">
              <option value="SUBMISSION">投稿 / 反馈 / 版权请求</option>
              <option value="PARTNER_APPLICATION">合作方申请</option>
            </select>
          </label>
          <label>
            回执号
            <input name="id" required minLength={26} maxLength={26} autoComplete="off" />
          </label>
          <label>
            查询码
            <input name="queryCode" required minLength={16} maxLength={256} autoComplete="off" />
          </label>
          <button className="button primary">
            查询进度 <ArrowUpRight size={16} />
          </button>
          <Notice value={notice} />
        </form>
        <aside className="form-aside">
          <ShieldCheck size={30} strokeWidth={1.2} />
          {result ? (
            <>
              <h2>{result.status}</h2>
              <p>{result.publicResult || '事项正在处理中，暂时没有新的公开答复。'}</p>
              {result.dueAt ? (
                <small>预计处理时间：{new Date(result.dueAt).toLocaleString('zh-CN')}</small>
              ) : null}
            </>
          ) : (
            <>
              <h2>
                回执与查询码，
                <br />
                缺一不可。
              </h2>
              <p>为了避免泄露，失败结果不会提示某个回执是否真实存在。</p>
            </>
          )}
        </aside>
      </div>
    </>
  )
}
