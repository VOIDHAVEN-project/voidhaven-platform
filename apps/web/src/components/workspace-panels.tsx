import { useCallback, useState, type FormEvent, type ReactNode } from 'react'
import { ArrowUpRight, KeyRound, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  api,
  type AccountStatus,
  type AuditLogEntry,
  type EditablePartnerContent,
  type ManagedContent,
  type ManagementPage,
  type ManagementQuery,
  type PendingPartnerApplication,
  type ProvisionedPartner,
  type PublicSubmission,
  type PublicationReadiness,
} from '../lib/api'
import { useResource } from '../lib/hooks'
import { isReviewablePartnerApplication, partnerApplicationStatusOptions } from '../lib/partner-applications'
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
      <option value="resource">学习资源</option>
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
      if (updated.workflowStatus === 'APPROVED') setReadiness(await api.adminPublicationReadiness(itemId))
      state.reload()
      setNotice({ tone: 'success', text: '审核决定已保存，并与当前修订版本绑定。' })
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
          {detail.workflowStatus === 'PENDING_REVIEW' ? (
            <form className="review-decision-form" onSubmit={decision}>
              <label>
                审核决定
                <select name="decision" required defaultValue="">
                  <option value="" disabled>
                    请选择
                  </option>
                  <option value="APPROVE">通过</option>
                  <option value="CHANGES_REQUESTED">退回修改</option>
                  <option value="REJECT">拒绝</option>
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
                disabled={busy || !readiness?.ready}
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
        controls={
          <label>
            状态
            <select name="status">
              <option value="">全部状态</option>
              <option value="PENDING">待处理</option>
              <option value="APPROVED">已通过</option>
              <option value="REJECTED">已拒绝</option>
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
            </div>
            {item.state === 'PENDING' ? (
              <DecisionButtons
                onRun={async (decision, reason) => {
                  await api.adminDecideSubmission(item.id, decision, reason)
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

function DecisionButtons({
  onRun,
}: {
  onRun: (decision: 'APPROVE' | 'REJECT', reason: string) => Promise<void>
}) {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <form
      className="decision-inline"
      onSubmit={async (event) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const decision = ((event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null)?.value as
          | 'APPROVE'
          | 'REJECT'
        const reason = String(data.get('reason') ?? '').trim()
        if (!reason) {
          setNotice({ tone: 'error', text: '请填写审核原因。' })
          return
        }
        setBusy(true)
        try {
          await onRun(decision, reason)
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
      <div>
        <button className="button ghost" name="decision" value="REJECT" disabled={busy}>
          拒绝
        </button>
        <button className="button primary" name="decision" value="APPROVE" disabled={busy}>
          通过
        </button>
      </div>
      <Notice value={notice} />
    </form>
  )
}

export function ApplicationsPanel() {
  const [credential, setCredential] = useState<ProvisionedPartner | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <>
      {credential ? (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>一次性激活链接 · {credential.loginId}</strong>
            <code>{`${window.location.origin}/auth/activate?token=${encodeURIComponent(credential.activationToken)}`}</code>
            <small>凭据只保存在当前页面内存，关闭或刷新后清除。</small>
            <button className="button ghost" onClick={() => setCredential(null)}>
              已妥善记录，关闭凭据
            </button>
          </div>
        </section>
      ) : null}
      <Notice value={notice} />
      <PagedPanel<PendingPartnerApplication>
        eyebrow="合作方申请"
        title="新的内容维护者"
        description="批准与拒绝都必须写明原因；批准时会生成一次性激活凭据。"
        loader={api.adminPartnerApplications}
        empty="当前没有合作方申请"
        initialOptions={{ status: 'PENDING_REVIEW' }}
        controls={
          <label>
            状态
            <select name="status" defaultValue="PENDING_REVIEW">
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
                  try {
                    if (decision === 'APPROVE')
                      setCredential(await api.adminApprovePartnerApplication(item.id, loginId, reason))
                    else await api.adminDecidePartnerApplication(item.id, reason)
                    reload()
                    setNotice({ tone: 'success', text: '申请处理结果已保存。' })
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
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
                    批准并生成凭据
                  </button>
                </div>
              </form>
            ) : null}
          </article>
        )}
      />
    </>
  )
}

export function AccountsPanel({ currentLoginId }: { currentLoginId: string }) {
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <>
      <Notice value={notice} />
      <PagedPanel<AccountStatus>
        eyebrow="超级管理员"
        title="账号资格治理"
        description="永久撤销不可逆；当前登录账号不提供自我停用或撤销入口。"
        loader={api.adminAccounts}
        empty="没有匹配账号"
        controls={
          <label>
            资格状态
            <select name="status">
              <option value="">全部状态</option>
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
            {account.loginId === currentLoginId || account.qualificationStatus === 'REVOKED' ? (
              <span className="muted">只读</span>
            ) : (
              <AccountActions
                account={account}
                run={async (action, reason) => {
                  if (action === 'suspend') await api.adminSuspendAccount(account.id, reason)
                  if (action === 'reactivate') await api.adminReactivateAccount(account.id, reason)
                  if (action === 'revoke') await api.adminRevokeAccount(account.id, reason)
                  reload()
                  setNotice({ tone: 'success', text: '账号资格状态已更新。' })
                }}
              />
            )}
          </article>
        )}
      />
    </>
  )
}

function AccountActions({
  account,
  run,
}: {
  account: AccountStatus
  run: (action: 'suspend' | 'reactivate' | 'revoke', reason: string) => Promise<void>
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
        {account.qualificationStatus === 'ACTIVE' ? (
          <button className="button ghost" name="action" value="suspend" disabled={busy}>
            停用
          </button>
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
