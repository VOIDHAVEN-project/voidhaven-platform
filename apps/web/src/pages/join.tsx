import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight, Bug, Copy, Megaphone, PenLine, Terminal } from 'lucide-react'
import { api, type CommunityEntry, type ProjectJoinEntry } from '../lib/api'
import { ControlledCommunityImage } from '../components/controlled-community-image'
import { useCommunityGrant, type CommunityGrantState } from '../components/community-grant'
import { BusyLabel, Notice, message, type NoticeValue } from '../components/ui'
import { copyText } from '../lib/copy-text'
import { usePageTitle, useProductCapabilities } from '../lib/hooks'

const directions = [
  { icon: Terminal, title: '开发', text: '参与前后端功能、自动化测试与本地运行环境的改进。' },
  { icon: PenLine, title: '内容', text: '整理竞赛、招募与资源与分享线索，核对来源与截止时间。' },
  { icon: Bug, title: '测试', text: '按验收清单执行黑盒测试，记录缺陷与可复现步骤。' },
  { icon: Megaphone, title: '运营', text: '维护社群秩序、收集反馈，并同步项目当前进展。' },
]

type EntryValue = Pick<CommunityEntry, 'name' | 'presentationType' | 'contactChannel' | 'value'>

/**
 * CAMPUS 01 文化栏：湖心亭是 VOIDHAVEN 的校园符号，属于纯视觉层，
 * 不承载任何业务数据，两个入口页共用同一套文案与线稿。
 */
function CampusAside() {
  return (
    <aside className="campus-aside">
      <p className="campus-aside-mark" aria-hidden="true">
        VOIDHAVEN
      </p>
      <div className="campus-aside-body">
        <p className="campus-eyebrow">CAMPUS 01</p>
        <h2>湖心亭</h2>
        <p className="campus-lead">从这里，找到同行的人。</p>
        <p className="campus-note">大学有很多路，但你不必一个人走。</p>
        <span className="campus-rule" aria-hidden="true" />
        <p className="campus-signature">A haven for seekers.</p>
      </div>
    </aside>
  )
}

/** CAMPUS 01 湖景：按主题切换的纯装饰画面，业务内容始终叠在其上方。 */
function CampusScene() {
  return <div className="campus-scene" aria-hidden="true" />
}

/**
 * 社群页与项目组页共用同一个邀请码表单：授权只写入内存上下文，
 * 页面之间可以互相复用，刷新浏览器后必须重新验证。
 */
function InviteVerifyForm({
  available,
  capabilitiesFailed,
  submitLabel,
  onVerified,
  onCleared,
}: {
  available: boolean
  capabilitiesFailed: boolean
  submitLabel: string
  onVerified: (grant: CommunityGrantState) => void
  onCleared: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const inviteCode = String(new FormData(form).get('inviteCode') ?? '').trim()
    if (!inviteCode) return
    if (!available) {
      setNotice({ tone: 'error', text: '邀请码入口暂未开放，请等待运营人员完成配置。' })
      return
    }
    setBusy(true)
    setNotice(null)
    try {
      const grant = await api.verifyCommunityInvite(inviteCode)
      onVerified({ grant: grant.grant, expiresAt: grant.expiresAt })
      form.reset()
      setNotice({
        tone: 'success',
        text: `验证成功，本次页面授权有效至 ${new Date(grant.expiresAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}。`,
      })
    } catch (error) {
      onCleared()
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  return (
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
            {submitLabel} <ArrowUpRight size={16} />
          </BusyLabel>
        </button>
      </fieldset>
      <Notice value={notice} />
      {!available && !capabilitiesFailed ? (
        <Notice value={{ tone: 'error', text: '邀请码入口暂未开放；这不是邀请码错误。' }} />
      ) : null}
      {capabilitiesFailed ? (
        <Notice value={{ tone: 'error', text: '功能状态暂时无法确认，请稍后刷新页面。' }} />
      ) : null}
    </form>
  )
}

function EntryAction({ entry }: { entry: EntryValue }) {
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

export function JoinCommunityPage() {
  usePageTitle('加入社群')
  const { grant, setGrant, clearGrant } = useCommunityGrant()
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [entries, setEntries] = useState<CommunityEntry[]>([])
  const { capabilities, failed: capabilitiesFailed } = useProductCapabilities()
  const available = capabilities?.communityGateEnabled === true
  useEffect(() => {
    let active = true
    api
      .communityEntries(grant ?? undefined)
      .then((publicEntries) => {
        if (active) setEntries(publicEntries)
      })
      .catch(() => {
        if (active) setNotice({ tone: 'error', text: '公开社群入口暂时无法加载，请稍后刷新。' })
      })
    return () => {
      active = false
    }
  }, [grant])
  return (
    <div className="campus-page join-community">
      <div className="campus-hero">
        <div className="campus-main">
          <header className="campus-heading">
            <p className="eyebrow">CONTINUE IN THE COMMUNITY</p>
            <h1>
              验证一次，
              <br />
              <span>找到可靠的下一步。</span>
            </h1>
            <p>公开入口可直接查看；使用工作人员提供的批次邀请码后，还可查看受控入口。</p>
          </header>
          <InviteVerifyForm
            available={available}
            capabilitiesFailed={capabilitiesFailed}
            submitLabel="查看社群入口"
            onVerified={setGrant}
            onCleared={clearGrant}
          />
          <section className="campus-entries" aria-labelledby="community-entries-title">
            <h2 id="community-entries-title">多个入口，一个主要去处。</h2>
            <Notice value={notice} />
            {entries.length ? (
              entries.map((entry) => (
                <article key={entry.id} className="community-entry">
                  <strong>
                    {entry.primary ? '主入口 · ' : ''}
                    {entry.name}
                  </strong>
                  {entry.description ? <p>{entry.description}</p> : null}
                  {entry.value ? <EntryAction entry={entry} /> : null}
                  {entry.mediaUrl && entry.accessLevel === 'PUBLIC' ? (
                    <img className="community-qr" src={entry.mediaUrl} alt={`${entry.name}二维码`} />
                  ) : entry.mediaUrl && grant ? (
                    <ControlledCommunityImage
                      path={entry.mediaUrl}
                      grant={grant}
                      alt={`${entry.name}二维码`}
                    />
                  ) : null}
                </article>
              ))
            ) : (
              <p>当前没有公开入口；验证成功后，受控入口会显示在这里。</p>
            )}
          </section>
        </div>
        <CampusAside />
        <CampusScene />
      </div>
    </div>
  )
}

export function JoinProjectPage() {
  usePageTitle('加入项目组')
  const { grant, setGrant, clearGrant } = useCommunityGrant()
  const { capabilities, failed: capabilitiesFailed } = useProductCapabilities()
  const enabled = capabilities?.projectJoinEnabled === true
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [entries, setEntries] = useState<ProjectJoinEntry[]>([])
  useEffect(() => {
    if (!enabled) return
    let active = true
    api
      .projectJoinEntries(grant ?? undefined)
      .then((list) => {
        if (active) setEntries(list)
      })
      .catch(() => {
        if (active) setNotice({ tone: 'error', text: '项目组入口暂时无法加载，请稍后刷新。' })
      })
    return () => {
      active = false
    }
  }, [enabled, grant])
  return (
    <div className="campus-page join-project">
      <div className="campus-hero">
        <div className="campus-main">
          <header className="campus-heading">
            <p className="eyebrow">BUILD VOIDHAVEN TOGETHER</p>
            <h1>
              把一次好奇，
              <br />
              <span>变成长期的共建。</span>
            </h1>
            <p>
              项目组围绕开发、内容、测试与运营持续推进。项目起源与参与说明随代码维护，联系入口由后台配置。
            </p>
          </header>
          <section className="join-directions" aria-label="可参与方向">
            {directions.map(({ icon: Icon, title, text }) => (
              <article key={title} className="join-direction">
                <Icon size={22} strokeWidth={1.4} aria-hidden="true" />
                <h2>{title}</h2>
                <p>{text}</p>
              </article>
            ))}
          </section>
          {capabilities === null && !capabilitiesFailed ? (
            <section className="paper-form join-project-status" aria-live="polite">
              <h2>正在确认项目组入口状态…</h2>
              <p>请稍候，页面会在确认后显示公开入口或邀请码验证入口。</p>
            </section>
          ) : enabled ? (
            <>
              <InviteVerifyForm
                available={capabilities?.communityGateEnabled === true}
                capabilitiesFailed={capabilitiesFailed}
                submitLabel="查看受控入口"
                onVerified={setGrant}
                onCleared={clearGrant}
              />
              <section className="campus-entries" aria-labelledby="project-entries-title">
                <h2 id="project-entries-title">固定的介绍，开放的行动入口。</h2>
                <Notice value={notice} />
                {entries.length ? (
                  entries.map((entry) => (
                    <article key={entry.id} className="community-entry">
                      <strong>{entry.name}</strong>
                      {entry.description ? <p>{entry.description}</p> : null}
                      {entry.value ? <EntryAction entry={entry} /> : null}
                      {entry.mediaUrl && entry.accessLevel === 'PUBLIC' ? (
                        <img className="community-qr" src={entry.mediaUrl} alt={`${entry.name}二维码`} />
                      ) : entry.mediaUrl && grant ? (
                        <ControlledCommunityImage
                          path={entry.mediaUrl}
                          grant={grant}
                          alt={`${entry.name}二维码`}
                        />
                      ) : null}
                    </article>
                  ))
                ) : (
                  <p>当前没有公开入口；验证邀请码后会显示受控入口。</p>
                )}
              </section>
            </>
          ) : (
            <section className="paper-form join-project-status" aria-labelledby="join-project-status-title">
              <h2 id="join-project-status-title">当前暂未开放</h2>
              <p>
                项目组行动入口尚未配置。开放后会在这里展示公开入口，或凭邀请码查看受控联系方式；这里不提供站内申请，也不保存申请过程。
              </p>
              <Link className="button ghost" to="/join/community">
                先了解用户社群 <ArrowUpRight size={15} />
              </Link>
            </section>
          )}
        </div>
        <CampusAside />
        <CampusScene />
      </div>
    </div>
  )
}
