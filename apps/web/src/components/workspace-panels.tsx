import { can, useIdentity } from '../lib/authorization'
import { AuthorizationPanel } from './authorization-panel'
import { useCallback, useState, type FormEvent, type ReactNode } from 'react'
import { ArrowUpRight, Copy, KeyRound, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  api,
  ApiFailure,
  type AccountStatus,
  type AuditLogEntry,
  type EditablePartnerContent,
  type ManagedContent,
  type ManagementPage,
  type ManagementQuery,
  type PendingPartnerApplication,
  type CreatedAdminAccount,
  type PublicSubmission,
  type PublicationReadiness,
} from '../lib/api'
import { copyText } from '../lib/copy-text'
import { useResource } from '../lib/hooks'
import { isReviewablePartnerApplication, partnerApplicationStatusOptions } from '../lib/partner-applications'
import { isReviewablePublicSubmission, publicSubmissionStatusOptions } from '../lib/public-submissions'
import { DocumentView } from './document'
import {
  Badge,
  EmptyState,
  ErrorState,
  LoadingRows,
  Notice,
  PanelHeading,
  message,
  type NoticeValue,
} from './ui'

function time(value?: string | null) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
    : '—'
}
function instant(value: FormDataEntryValue | null) {
  if (!value) return ''
  const date = new Date(String(value))
  return Number.isNaN(date.getTime()) ? '' : date.toISOString()
}
function localDateTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function PagedPanel<T>({
  eyebrow,
  title,
  description,
  loader,
  render,
  empty,
  controls,
  initialOptions = {},
}: {
  eyebrow: string
  title: string
  description: string
  loader: (options: ManagementQuery) => Promise<ManagementPage<T>>
  render: (item: T, reload: () => void) => ReactNode
  empty: string
  controls?: ReactNode
  initialOptions?: ManagementQuery
}) {
  const [options, setOptions] = useState<ManagementQuery>(initialOptions)
  const load = useCallback(() => loader(options), [loader, options])
  const state = useResource(load)
  return (
    <section className="panel">
      <PanelHeading
        eyebrow={eyebrow}
        title={title}
        action={
          <button className="button ghost" onClick={state.reload}>
            <RefreshCw size={15} />
            刷新
          </button>
        }
      />
      <p className="panel-help">{description}</p>
      <form
        className="workspace-filter"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          setOptions({
            query: String(data.get('q') ?? '').trim(),
            status: String(data.get('status') ?? ''),
            module: String(data.get('module') ?? ''),
            action: String(data.get('action') ?? ''),
            actorAccountId: String(data.get('actorAccountId') ?? '').trim(),
            objectType: String(data.get('objectType') ?? '').trim(),
            from: instant(data.get('from')),
            to: instant(data.get('to')),
          })
        }}
      >
        <label>
          搜索
          <input name="q" placeholder="标题、名称或关键词" />
        </label>
        {controls}
        <button className="button ghost">应用筛选</button>
      </form>
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : state.data?.items.length ? (
        <div className="data-list">{state.data.items.map((item) => render(item, state.reload))}</div>
      ) : (
        <EmptyState title={empty} text="调整筛选条件或稍后刷新。" />
      )}
      {state.data?.nextCursor ? (
        <button
          className="button ghost load-more"
          onClick={() => setOptions((value) => ({ ...value, cursor: state.data?.nextCursor ?? undefined }))}
        >
          加载更多
        </button>
      ) : null}
    </section>
  )
}

const moduleSelect = (
  <label>
    内容模块
    <select name="module">
      <option value="">全部模块</option>
      <option value="competition">竞赛</option>
      <option value="recruitment">团队招募</option>
      <option value="resource">资源与分享</option>
      <option value="resource.article">文章 / 经验</option>
    </select>
  </label>
)

export function ReviewWorkspace({ itemId, isSuperAdmin }: { itemId?: string; isSuperAdmin: boolean }) {
  return itemId ? (
    <ReviewDetail itemId={itemId} isSuperAdmin={isSuperAdmin} />
  ) : (
    <PagedPanel<ManagedContent>
      eyebrow="审核中心"
      title="待审核内容"
      description="完整查看候选正文、动态字段、来源、媒体与历史记录，再针对当前修订作出决定。"
      loader={api.adminQueue}
      empty="当前没有待审核内容"
      controls={moduleSelect}
      render={(item) => (
        <article className="data-row" key={item.id}>
          <div>
            <div className="row-meta">
              <Badge value={item.kind} />
              <Badge value={item.workflowStatus} />
              <span>修订 {item.revision}</span>
            </div>
            <h3>{item.title}</h3>
            <p>
              送审：{time(item.submittedAt)} · {item.moduleKey}
            </p>
          </div>
          <Link
            className="button primary"
            to={`/workspace?section=reviews&item=${encodeURIComponent(item.id)}`}
          >
            查看原文并审核
          </Link>
        </article>
      )}
    />
  )
}

function ReviewDetail({ itemId, isSuperAdmin }: { itemId: string; isSuperAdmin: boolean }) {
  const identity = useIdentity()
  const loader = useCallback(() => api.adminContent(itemId), [itemId])
  const state = useResource(loader)
  const [readiness, setReadiness] = useState<PublicationReadiness | null>(null)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const detail = state.data
  async function decision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!detail || busy) return
    const data = new FormData(event.currentTarget)
    const value = String(data.get('decision')) as 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT'
    const reason = String(data.get('reason') ?? '').trim()
    if (!value || (value !== 'APPROVE' && !reason)) {
      setNotice({ tone: 'error', text: '退回修改或拒绝时必须填写原因。' })
      return
    }
    setBusy(true)
    setNotice(null)
    try {
      const updated = await api.adminDecision(itemId, detail.revision, value, reason || undefined)
      setReadiness(null)
      state.reload()
      setNotice({ tone: 'success', text: '审核决定已保存，并与当前修订版本绑定。' })
      if (updated.workflowStatus === 'APPROVED') setReadiness(await api.adminPublicationReadiness(itemId))
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  async function check() {
    setBusy(true)
    setNotice(null)
    try {
      setReadiness(await api.adminPublicationReadiness(itemId))
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="panel review-panel">
      <Link className="text-link" to="/workspace?section=reviews">
        ← 返回审核队列
      </Link>
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : detail ? (
        <>
          <PanelHeading eyebrow={`候选修订 · ${detail.draft.moduleKey}`} title={detail.draft.title} />
          <Notice value={notice} />
          <div className="review-metadata">
            <dl>
              <div>
                <dt>修订</dt>
                <dd>{detail.revision}</dd>
              </div>
              <div>
                <dt>送审时间</dt>
                <dd>{time(detail.submittedAt)}</dd>
              </div>
              <div>
                <dt>归属</dt>
                <dd>{detail.ownerPartnerProfileId ?? '管理员代录'}</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>{detail.draft.sourceName || '原创内容'}</dd>
              </div>
              <div>
                <dt>权利说明</dt>
                <dd>{detail.draft.rightsBasis}</dd>
              </div>
              {Object.entries(detail.draft.details).map(([key, value]) => (
                <div key={key}>
                  <dt>{key}</dt>
                  <dd>{Array.isArray(value) ? value.join('、') : String(value ?? '—')}</dd>
                </div>
              ))}
            </dl>
          </div>
          <article className="review-document">
            <DocumentView body={detail.draft.body} media={detail.media} />
          </article>
          <section className="review-history">
            <h3>审核历史</h3>
            {detail.reviews?.length ? (
              <ol>
                {detail.reviews.map((record) => (
                  <li key={`${record.revisionId}-${record.createdAt}`}>
                    <strong>{record.decision}</strong> · {time(record.createdAt)}
                    {record.reason ? <p>{record.reason}</p> : null}
                  </li>
                ))}
              </ol>
            ) : (
              <p>暂无历史审核记录。</p>
            )}
          </section>
          {can(identity, 'CONTENT_REVIEW', {
            ownerPartnerProfileId: detail.ownerPartnerProfileId,
            moduleKey: detail.draft.moduleKey,
          }) &&
          (detail.workflowStatus === 'PENDING_REVIEW' ||
            (detail.workflowStatus === 'APPROVED' && detail.recordState === 'ACTIVE')) ? (
            <form className="review-decision-form" onSubmit={decision}>
              <label>
                审核决定
                <select name="decision" required defaultValue="">
                  <option value="" disabled>
                    请选择
                  </option>
                  {detail.workflowStatus === 'PENDING_REVIEW' ? <option value="APPROVE">通过</option> : null}
                  <option value="CHANGES_REQUESTED">退回修改</option>
                  {detail.workflowStatus === 'PENDING_REVIEW' ? <option value="REJECT">拒绝</option> : null}
                </select>
              </label>
              <label>
                审核说明
                <textarea name="reason" rows={4} maxLength={1000} />
              </label>
              <button className="button primary" disabled={busy}>
                保存审核决定
              </button>
            </form>
          ) : null}
          {detail.workflowStatus === 'APPROVED' ? (
            <section className="readiness">
              <h3>发布前检查</h3>
              <button className="button ghost" disabled={busy} onClick={() => void check()}>
                执行检查
              </button>
              {readiness ? (
                readiness.ready ? (
                  <Notice value={{ tone: 'success', text: '发布检查已通过，可以正式发布。' }} />
                ) : (
                  <div className="publication-blockers">
                    <strong>当前存在阻断项</strong>
                    <ul>
                      {readiness.blockers.map((blocker) => (
                        <li key={blocker.code}>{blocker.message}</li>
                      ))}
                    </ul>
                  </div>
                )
              ) : null}
              <button
                className="button primary"
                disabled={
                  busy ||
                  !readiness?.ready ||
                  !can(identity, 'CONTENT_PUBLISH', {
                    ownerPartnerProfileId: detail.ownerPartnerProfileId,
                    moduleKey: detail.draft.moduleKey,
                  })
                }
                onClick={async () => {
                  setBusy(true)
                  try {
                    await api.adminPublish(itemId)
                    state.reload()
                    setNotice({ tone: 'success', text: '内容已公开发布。' })
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
                  } finally {
                    setBusy(false)
                  }
                }}
              >
                正式发布
              </button>
            </section>
          ) : null}
          {isSuperAdmin && detail.workflowStatus === 'REJECTED' ? (
            <ReopenButton
              detail={detail}
              onDone={() => {
                state.reload()
                setNotice({ tone: 'success', text: '稿件已重新开放为待修改。' })
              }}
            />
          ) : null}
        </>
      ) : null}
    </section>
  )
}

export function SubmissionsPanel() {
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <>
      <Notice value={notice} />
      <PagedPanel<PublicSubmission>
        eyebrow="公开输入"
        title="投稿、反馈与版权请求"
        description="公开输入只在受控工作区处理；通过并不意味着内容会自动公开。"
        loader={api.adminSubmissions}
        empty="当前没有待处理公开输入"
        initialOptions={{ status: 'RECEIVED' }}
        controls={
          <label>
            状态
            <select name="status" defaultValue="RECEIVED">
              {publicSubmissionStatusOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        }
        render={(item, reload) => (
          <article className="application-row" key={item.id}>
            <div>
              <div className="row-meta">
                <Badge value={item.submissionType} />
                <Badge value={item.state} />
              </div>
              <h3>{item.title}</h3>
              <p>{item.message}</p>
              {item.linkUrl ? (
                <a href={item.linkUrl} rel="noreferrer" target="_blank">
                  相关链接 <ArrowUpRight size={14} />
                </a>
              ) : null}
              {item.imageDescription ? <small>图片说明：{item.imageDescription}</small> : null}
              {item.publicResult ? <p className="review-note">公开答复：{item.publicResult}</p> : null}
            </div>
            {isReviewablePublicSubmission(item.state) ? (
              <CaseWorkflow
                statuses={['IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'RESOLVED', 'REJECTED', 'CLOSED']}
                contactAvailable={item.contactAvailable}
                dueAt={item.dueAt}
                deliverySubject={{
                  subjectType: 'SUBMISSION',
                  subjectId: item.id,
                  ready: Boolean(item.publicResult),
                }}
                onReveal={(reason) => api.adminRevealSubmissionContact(item.id, reason)}
                onRun={async (status, publicResult, dueAt, clearDueAt, reason) => {
                  await api.adminTransitionSubmission(
                    item.id,
                    item.version,
                    status,
                    publicResult,
                    dueAt,
                    clearDueAt,
                    reason,
                  )
                  reload()
                  setNotice({ tone: 'success', text: '公开输入处理结果已保存。' })
                }}
              />
            ) : null}
          </article>
        )}
      />
    </>
  )
}

function CaseWorkflow({
  statuses,
  contactAvailable,
  dueAt,
  onReveal,
  onRun,
  deliverySubject,
}: {
  statuses: string[]
  contactAvailable: boolean
  dueAt?: string | null
  onReveal: (reason: string) => Promise<{ contact: string }>
  onRun: (
    status: string,
    publicResult: string,
    dueAt: string | undefined,
    clearDueAt: boolean,
    reason: string,
  ) => Promise<void>
  deliverySubject?: {
    subjectType: 'SUBMISSION' | 'PARTNER_APPLICATION'
    subjectId: string
    ready: boolean
  }
}) {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const identity = useIdentity()
  const [contact, setContact] = useState('')
  const [clearDueAt, setClearDueAt] = useState(false)
  return (
    <form
      className="decision-inline"
      onSubmit={async (event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const action = ((event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null)?.value
        const reason = String(data.get('reason') ?? '').trim()
        const status = String(data.get('status') ?? '')
        const publicResult = String(data.get('publicResult') ?? '').trim()
        const submittedDueAt = String(data.get('dueAt') ?? '')
        const originalDueAt = localDateTime(dueAt)
        const dueAtValue =
          clearDueAt || !submittedDueAt || submittedDueAt === originalDueAt
            ? undefined
            : instant(submittedDueAt) || undefined
        if (!reason) {
          setNotice({ tone: 'error', text: '请填写审核原因。' })
          return
        }
        setBusy(true)
        try {
          if (action === 'reveal') {
            setContact((await onReveal(reason)).contact)
            setNotice({ tone: 'success', text: '联系人仅在当前页面临时显示，本次查看已记录审计。' })
          } else if (action === 'deliver' && deliverySubject) {
            await api.adminRecordManualDelivery({
              subjectType: deliverySubject.subjectType,
              subjectId: deliverySubject.subjectId,
              deliveryType: 'STATUS_RESULT',
              note: reason,
            })
            setNotice({ tone: 'success', text: '已记录本次用户可见结果的人工通知。' })
          } else await onRun(status, publicResult, dueAtValue, clearDueAt, reason)
        } catch (error) {
          setNotice({ tone: 'error', text: message(error) })
        } finally {
          setBusy(false)
        }
      }}
    >
      <label>
        处理原因
        <input name="reason" required maxLength={1000} />
      </label>
      <label>
        下一状态
        <select name="status">
          {statuses.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
      </label>
      <label>
        用户可见答复
        <textarea name="publicResult" maxLength={2000} rows={3} />
      </label>
      <label>
        处理截止时间
        <input
          name="dueAt"
          type="datetime-local"
          defaultValue={localDateTime(dueAt)}
          disabled={clearDueAt || busy}
        />
      </label>
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={clearDueAt}
          onChange={(event) => setClearDueAt(event.target.checked)}
          disabled={busy}
        />
        明确清除处理截止时间
      </label>
      <div>
        {contactAvailable && can(identity, 'PRIVATE_DATA_READ', {}) ? (
          <button className="button ghost" name="action" value="reveal" disabled={busy}>
            查看联系人
          </button>
        ) : null}
        {deliverySubject?.ready && identity?.roles.includes('SUPER_ADMIN') ? (
          <button className="button ghost" name="action" value="deliver" disabled={busy}>
            记录结果已通知
          </button>
        ) : null}
        <button className="button primary" name="action" value="transition" disabled={busy}>
          保存状态
        </button>
      </div>
      {contact ? <code>{contact}</code> : null}
      <Notice value={notice} />
    </form>
  )
}

export function ApplicationsPanel() {
  const identity = useIdentity()
  const [credential, setCredential] = useState<CreatedAdminAccount | null>(null)
  const [deliverySubjectId, setDeliverySubjectId] = useState('')
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <>
      {credential ? (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>一次性激活链接 · {credential.loginId}</strong>
            <code>{`${window.location.origin}/auth/activate?token=${encodeURIComponent(credential.activationToken)}`}</code>
            <small>
              凭据只保存在当前页面内存，关闭或刷新后清除。只有实际完成交付后，才点击下方按钮记录留痕。
            </small>
            <button
              className="button ghost"
              type="button"
              onClick={async () => {
                if (deliverySubjectId && identity?.roles.includes('SUPER_ADMIN')) {
                  try {
                    await api.adminRecordManualDelivery({
                      subjectType: 'PARTNER_APPLICATION',
                      subjectId: deliverySubjectId,
                      deliveryType: 'ACTIVATION',
                      note: '已当面交付激活凭据',
                    })
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
                    return
                  }
                }
                setCredential(null)
                setDeliverySubjectId('')
              }}
            >
              确认已人工交付
            </button>
          </div>
        </section>
      ) : null}
      <Notice value={notice} />
      <PagedPanel<PendingPartnerApplication>
        eyebrow="合作申请"
        title="新的内容维护者"
        description="批准后建立待激活组织主账号；激活凭据由具备账号运营权限的人签发。"
        loader={api.adminPartnerApplications}
        empty="当前没有合作申请"
        initialOptions={{ status: 'RECEIVED' }}
        controls={
          <label>
            状态
            <select name="status" defaultValue="RECEIVED">
              {partnerApplicationStatusOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        }
        render={(item, reload) => (
          <article className="application-row" key={item.id}>
            <div>
              <div className="row-meta">
                <Badge value={item.state} />
              </div>
              <h3>{item.displayName}</h3>
              <p>{item.introduction}</p>
              {item.portfolioUrl ? (
                <a href={item.portfolioUrl} rel="noreferrer" target="_blank">
                  查看公开作品 <ArrowUpRight size={14} />
                </a>
              ) : null}
              {item.publicResult ? <p className="review-note">申请人可见答复：{item.publicResult}</p> : null}
            </div>
            {isReviewablePartnerApplication(item.state) ? (
              <form
                className="decision-inline"
                onSubmit={async (event) => {
                  event.preventDefault()
                  const data = new FormData(event.currentTarget)
                  const decision = ((event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null)
                    ?.value
                  const reason = String(data.get('reason') ?? '').trim()
                  const loginId = String(data.get('loginId') ?? '').trim()
                  if (!reason || (decision === 'APPROVE' && !loginId)) {
                    setNotice({ tone: 'error', text: '批准需填写登录名和原因；拒绝需填写原因。' })
                    return
                  }
                  let provisioned = false
                  try {
                    if (decision === 'APPROVE') {
                      setDeliverySubjectId(item.id)
                      const approved = await api.adminApprovePartnerApplication(item.id, loginId, reason)
                      provisioned = true
                      if (
                        can(identity, 'ACCOUNT_OPERATE', { ownerPartnerProfileId: approved.partnerProfileId })
                      )
                        setCredential(await api.adminReissueActivation(approved.accountId, reason))
                    } else await api.adminDecidePartnerApplication(item.id, reason)
                    reload()
                    setNotice({ tone: 'success', text: '申请处理结果已保存。' })
                  } catch (error) {
                    if (provisioned) reload()
                    setNotice({
                      tone: 'error',
                      text:
                        (provisioned ? '申请已批准，激活链接未签发，可在账号运营中重新签发。' : '') +
                        message(error),
                    })
                  }
                }}
              >
                <label>
                  受控登录名
                  <input name="loginId" pattern="[A-Za-z0-9._-]{4,100}" />
                </label>
                <label>
                  审核原因
                  <input name="reason" required maxLength={1000} />
                </label>
                <div>
                  <button className="button ghost" name="decision" value="REJECT">
                    拒绝
                  </button>
                  <button className="button primary" name="decision" value="APPROVE">
                    {can(identity, 'ACCOUNT_OPERATE', {}) ? '批准并签发激活链接' : '批准申请'}
                  </button>
                </div>
              </form>
            ) : null}
            {!isReviewablePartnerApplication(item.state) &&
            item.state !== 'REJECTED' &&
            item.state !== 'ACTIVATED' &&
            item.state !== 'CLOSED' ? (
              <CaseWorkflow
                statuses={[
                  'IN_REVIEW',
                  'WAITING_FOR_APPLICANT',
                  'APPROVED',
                  'ACTIVATION_PENDING',
                  'ACTIVATED',
                  'REJECTED',
                  'CLOSED',
                ]}
                contactAvailable={item.contactAvailable}
                dueAt={item.dueAt}
                deliverySubject={{
                  subjectType: 'PARTNER_APPLICATION',
                  subjectId: item.id,
                  ready: Boolean(item.publicResult),
                }}
                onReveal={(reason) => api.adminRevealPartnerContact(item.id, reason)}
                onRun={async (status, publicResult, dueAt, clearDueAt, reason) => {
                  await api.adminTransitionPartnerApplication(
                    item.id,
                    item.version,
                    status,
                    publicResult,
                    dueAt,
                    clearDueAt,
                    reason,
                  )
                  reload()
                  setNotice({ tone: 'success', text: '合作申请状态已更新。' })
                }}
              />
            ) : null}
          </article>
        )}
      />
    </>
  )
}

const accountConflicts = new Set([
  'ACCOUNT_VERSION_CONFLICT',
  'LOGIN_ID_ALREADY_EXISTS',
  'SENSITIVE_RESULT_NOT_REPLAYABLE',
  'REQUEST_IN_PROGRESS',
])
function activationUrl(token: string) {
  return `${window.location.origin}/auth/activate?token=${encodeURIComponent(token)}`
}

export function AccountsPanel({ currentLoginId }: { currentLoginId: string }) {
  const identity = useIdentity()
  const isSuper = identity?.roles.includes('SUPER_ADMIN') ?? false
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [authorizationAccount, setAuthorizationAccount] = useState<string | null>(null)
  const [credential, setCredential] = useState<CreatedAdminAccount | null>(null)
  const [resetCredential, setResetCredential] = useState<{
    accountId: string
    loginId: string
    resetToken: string
    expiresAt: string
  } | null>(null)
  const [copied, setCopied] = useState(false)
  async function onConflict(error: unknown, reload: () => void) {
    if (error instanceof ApiFailure && accountConflicts.has(error.code)) {
      reload()
    }
    setNotice({ tone: 'error', text: message(error) })
  }
  return (
    <>
      {credential ? (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>一次性激活链接 · {credential.loginId}</strong>
            <code>{activationUrl(credential.activationToken)}</code>
            <small>
              凭据只保存在当前页面内存，关闭或刷新后清除。角色 {credential.roles.join(' / ')} ·{' '}
              {credential.qualificationStatus}
            </small>
            <div>
              <button
                className="button ghost"
                type="button"
                onClick={async () => {
                  try {
                    await copyText(activationUrl(credential.activationToken))
                    setCopied(true)
                    setNotice({ tone: 'success', text: '激活链接已复制，可直接粘贴交付。' })
                  } catch (error) {
                    setCopied(false)
                    setNotice({ tone: 'error', text: message(error) })
                  }
                }}
              >
                <Copy size={15} />
                {copied ? '已复制' : '复制链接'}
              </button>
              <button
                className="button ghost"
                type="button"
                onClick={async () => {
                  try {
                    if (isSuper)
                      await api.adminRecordManualDelivery({
                        subjectType: 'ACCOUNT',
                        subjectId: credential.id,
                        deliveryType: 'ACTIVATION',
                        note: '已当面交付激活凭据',
                      })
                    setCredential(null)
                    setCopied(false)
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
                  }
                }}
              >
                确认已人工交付
              </button>
            </div>
          </div>
        </section>
      ) : null}
      {resetCredential ? (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>一次性密码重置凭据 · {resetCredential.loginId}</strong>
            <code>{resetCredential.resetToken}</code>
            <small>
              30 分钟内有效，只能使用一次。请让用户在 /auth/reset-password 页面手动粘贴，凭据不要放入 URL。
            </small>
            <div>
              <button
                className="button ghost"
                type="button"
                onClick={() => void copyText(resetCredential.resetToken)}
              >
                复制重置凭据
              </button>
              <button
                className="button ghost"
                type="button"
                onClick={async () => {
                  try {
                    if (isSuper)
                      await api.adminRecordManualDelivery({
                        subjectType: 'ACCOUNT',
                        subjectId: resetCredential.accountId,
                        deliveryType: 'PASSWORD_RESET',
                        note: '已当面交付密码重置凭据',
                      })
                    setResetCredential(null)
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
                  }
                }}
              >
                确认已人工交付
              </button>
            </div>
          </div>
        </section>
      ) : null}
      <Notice value={notice} />
      {isSuper && (
        <AuthorizationPanel
          onCreated={(created) => {
            setCredential(created)
            setCopied(false)
            setNotice({ tone: 'success', text: '普通管理员已创建，请立即复制一次性激活链接。' })
          }}
        />
      )}

      <PagedPanel<AccountStatus>
        eyebrow={isSuper ? '超级管理员' : '合作方账号运营'}
        title="账号资格治理"
        description="永久撤销不可逆；当前登录账号和超级管理员账号不提供停用或撤销入口。"
        loader={api.adminAccounts}
        empty="没有匹配账号"
        controls={
          <label>
            资格状态
            <select name="status">
              <option value="">全部状态</option>
              <option value="PENDING">PENDING</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="SUSPENDED">SUSPENDED</option>
              <option value="REVOKED">REVOKED</option>
            </select>
          </label>
        }
        render={(account, reload) => (
          <article className="data-row" key={account.id}>
            <div>
              <div className="row-meta">
                <Badge value={account.qualificationStatus} />
                {account.roles.map((role) => (
                  <Badge key={role} value={role} />
                ))}
              </div>
              <h3>{account.loginId}</h3>
            </div>
            {isSuper &&
              account.roles.includes('ADMIN') &&
              !account.roles.includes('SUPER_ADMIN') &&
              account.qualificationStatus !== 'REVOKED' && (
                <details
                  onToggle={(event) => {
                    if (event.currentTarget.open) setAuthorizationAccount(account.id)
                    else setAuthorizationAccount((current) => (current === account.id ? null : current))
                  }}
                >
                  <summary className="button ghost">配置职责与权限</summary>
                  {authorizationAccount === account.id && (
                    <AuthorizationPanel accountId={account.id} onDone={reload} />
                  )}
                </details>
              )}
            {account.loginId === currentLoginId ||
            account.qualificationStatus === 'REVOKED' ||
            account.roles.includes('SUPER_ADMIN') ? (
              <span className="muted">只读</span>
            ) : (
              <AccountActions
                account={account}
                canReset={isSuper}
                run={async (action, reason) => {
                  try {
                    if (action === 'suspend') await api.adminSuspendAccount(account.id, reason)
                    if (action === 'reactivate') await api.adminReactivateAccount(account.id, reason)
                    if (action === 'revoke') await api.adminRevokeAccount(account.id, reason)
                    if (action === 'reissue')
                      setCredential(await api.adminReissueActivation(account.id, reason))
                    if (action === 'reset')
                      setResetCredential(await api.adminIssuePasswordReset(account.id, reason))
                    reload()
                    setNotice({
                      tone: 'success',
                      text:
                        action === 'reissue'
                          ? '已重新签发激活链接。'
                          : action === 'reset'
                            ? '已签发一次性密码重置凭据。'
                            : '账号资格状态已更新。',
                    })
                  } catch (error) {
                    await onConflict(error, reload)
                    throw error
                  }
                }}
              />
            )}
          </article>
        )}
      />
    </>
  )
}

export function AccountActions({
  account,
  run,
  canReset,
}: {
  canReset: boolean
  account: AccountStatus
  run: (action: 'suspend' | 'reactivate' | 'revoke' | 'reissue' | 'reset', reason: string) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  return (
    <form
      className="account-actions"
      onSubmit={async (event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const action = ((event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null)?.value as
          | 'suspend'
          | 'reactivate'
          | 'revoke'
          | 'reissue'
          | 'reset'
        const reason = String(data.get('reason') ?? '').trim()
        const confirmLogin = String(data.get('confirmLogin') ?? '').trim()
        if (!reason || (action === 'revoke' && confirmLogin !== account.loginId)) {
          setError(action === 'revoke' ? '永久撤销前请输入完整登录名。' : '请填写操作原因。')
          return
        }
        setBusy(true)
        setError('')
        try {
          await run(action, reason)
        } catch (e) {
          setError(message(e))
        } finally {
          setBusy(false)
        }
      }}
    >
      <label>
        原因
        <input name="reason" required maxLength={1000} />
      </label>
      <label>
        撤销确认
        <input name="confirmLogin" placeholder={`永久撤销时输入 ${account.loginId}`} />
      </label>
      <div>
        {account.qualificationStatus === 'PENDING' ? (
          <>
            <button className="button ghost" name="action" value="reissue" disabled={busy}>
              重新签发
            </button>
            <button className="button ghost" name="action" value="suspend" disabled={busy}>
              停用
            </button>
          </>
        ) : account.qualificationStatus === 'ACTIVE' ? (
          <>
            {canReset && (
              <button className="button ghost" name="action" value="reset" disabled={busy}>
                签发密码重置
              </button>
            )}
            <button className="button ghost" name="action" value="suspend" disabled={busy}>
              停用
            </button>
          </>
        ) : (
          <button className="button ghost" name="action" value="reactivate" disabled={busy}>
            恢复
          </button>
        )}
        <button className="button danger" name="action" value="revoke" disabled={busy}>
          永久撤销
        </button>
      </div>
      {error ? <small className="field-error">{error}</small> : null}
    </form>
  )
}

export function AuditPanel() {
  return (
    <PagedPanel<AuditLogEntry>
      eyebrow="审计记录"
      title="只读操作摘要"
      description="仅显示最小审计信息，不展示正文、联系方式、密码或激活凭据。"
      loader={api.adminAuditLogs}
      empty="没有匹配的审计记录"
      controls={
        <>
          <label>
            动作
            <input name="action" />
          </label>
          <label>
            操作人 ID
            <input name="actorAccountId" />
          </label>
          <label>
            对象类型
            <input name="objectType" />
          </label>
          <label>
            起始时间
            <input name="from" type="datetime-local" />
          </label>
          <label>
            结束时间
            <input name="to" type="datetime-local" />
          </label>
        </>
      }
      render={(entry) => (
        <article className="audit-row" key={entry.id}>
          <div>
            <strong>{entry.action}</strong>
            <span>
              {entry.objectType} · {entry.objectId}
            </span>
          </div>
          <dl>
            <div>
              <dt>时间</dt>
              <dd>{time(entry.createdAt)}</dd>
            </div>
            <div>
              <dt>操作人</dt>
              <dd>
                {entry.actorRole} · {entry.actorAccountId ?? '系统'}
              </dd>
            </div>
            <div>
              <dt>原因</dt>
              <dd>{entry.reason || '—'}</dd>
            </div>
            <div>
              <dt>请求编号</dt>
              <dd>{entry.requestId}</dd>
            </div>
          </dl>
        </article>
      )}
    />
  )
}

export function ReopenButton({ detail, onDone }: { detail: EditablePartnerContent; onDone: () => void }) {
  if (detail.workflowStatus !== 'REJECTED') return null
  return (
    <form
      className="decision-inline"
      onSubmit={async (event) => {
        event.preventDefault()
        const reason = String(new FormData(event.currentTarget).get('reason') ?? '').trim()
        if (!reason) return
        await api.adminReopen(detail.id, reason)
        onDone()
      }}
    >
      <label>
        重新开放原因
        <input name="reason" required />
      </label>
      <button className="button primary">重新开放为待修改</button>
    </form>
  )
}
