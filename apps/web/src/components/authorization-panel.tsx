import { useEffect, useState } from 'react'
import {
  api,
  type AdminAuthorization,
  type AdminAuthorizationInput,
  type AdminGrant,
  type AuthorizationHistory,
  type AuthorizationPreview,
  type CreatedAdminAccount,
  type PartnerProfile,
} from '../lib/api'
import { useResource } from '../lib/hooks'
import { ErrorState, LoadingRows, Notice, message, type NoticeValue } from './ui'

const scopeLabels = { GLOBAL: '全平台', MODULES: '指定栏目', ORGANIZATIONS: '指定组织' }
async function organizations() {
  const result: PartnerProfile[] = []
  let cursor: string | undefined
  do {
    const page = await api.adminPartnerProfiles({ pageSize: 50, cursor })
    result.push(...page.items)
    const next = page.nextCursor ?? undefined
    if (next && next === cursor) throw new Error('组织列表分页未前进，请重试。')
    cursor = next
  } while (cursor)
  return result
}
export function AuthorizationPanel({
  accountId,
  onDone,
  onCreated,
}: {
  accountId?: string
  onDone?: () => void
  onCreated?: (account: CreatedAdminAccount) => void
}) {
  const catalog = useResource(api.permissionCatalog)
  const modules = useResource(api.modules)
  const profiles = useResource(organizations)
  const [current, setCurrent] = useState<AdminAuthorization | null>(
    accountId ? null : { grants: [], longTerm: false, version: 0 },
  )
  const [grants, setGrants] = useState<AdminGrant[]>([])
  const [period, setPeriod] = useState('')
  const [expiry, setExpiry] = useState('')
  const [reason, setReason] = useState('')
  const [loginId, setLoginId] = useState('')
  const [preview, setPreview] = useState<AuthorizationPreview | null>(null)
  const [previewFingerprint, setPreviewFingerprint] = useState('')
  const [history, setHistory] = useState<AuthorizationHistory[] | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    if (!accountId) return
    let cancelled = false
    api
      .adminAuthorization(accountId)
      .then((value) => {
        if (cancelled) return
        setCurrent(value)
        setGrants(value.grants)
        setPeriod(value.longTerm ? 'long' : value.expiresAt ? 'until' : '')
        setExpiry(
          value.expiresAt
            ? new Date(Date.parse(value.expiresAt) - new Date().getTimezoneOffset() * 60000)
                .toISOString()
                .slice(0, 16)
            : '',
        )
      })
      .catch((error: unknown) => {
        if (!cancelled) setNotice({ tone: 'error', text: message(error) })
      })
    return () => {
      cancelled = true
    }
  }, [accountId])
  const input: AdminAuthorizationInput = {
    grants,
    longTerm: period === 'long',
    expiresAt:
      period === 'until' && expiry && Number.isFinite(Date.parse(expiry))
        ? new Date(expiry).toISOString()
        : null,
    reason,
  }
  const fingerprint = JSON.stringify(input)
  const valid = Boolean(
    current &&
      period &&
      reason.trim() &&
      (period === 'long' || (input.expiresAt && Date.parse(input.expiresAt) > Date.now())) &&
      grants.every((grant) => grant.scopeType === 'GLOBAL' || grant.scopeIds.length),
  )
  const label = (code: string) => catalog.data?.find((permission) => permission.code === code)?.label ?? code
  const update = (code: string, next: Partial<AdminGrant>) =>
    setGrants((values) => values.map((grant) => (grant.permission === code ? { ...grant, ...next } : grant)))
  async function run(action: () => Promise<void>) {
    setBusy(true)
    setNotice(null)
    try {
      await action()
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="workspace-card authorization-panel">
      <h3>{accountId ? '职责与权限' : '创建普通管理员并分配职责'}</h3>
      <p className="muted">选择能力、范围与期限。保存前先预览；修改授权会使目标账号重新登录。</p>
      <Notice value={notice} />
      {catalog.loading || modules.loading || profiles.loading ? (
        <LoadingRows />
      ) : catalog.error || modules.error || profiles.error ? (
        <ErrorState
          error={catalog.error || modules.error || profiles.error || ''}
          retry={() => {
            catalog.reload()
            modules.reload()
            profiles.reload()
          }}
        />
      ) : (
        <>
          {!accountId && (
            <label>
              受控登录名
              <input
                value={loginId}
                onChange={(event) => setLoginId(event.target.value)}
                pattern="[A-Za-z0-9._-]{4,100}"
                maxLength={100}
                placeholder="至少 4 位字母、数字或 . _ -"
              />
            </label>
          )}
          <div className="authorization-permissions">
            {catalog.data?.map((permission) => {
              const grant = grants.find((item) => item.permission === permission.code)
              const options =
                grant?.scopeType === 'MODULES'
                  ? modules.data?.modules.map((module) => ({ id: module.key, label: module.label }))
                  : profiles.data?.map((profile) => ({ id: profile.id, label: profile.displayName }))
              return (
                <fieldset key={permission.code}>
                  <label className="check-label">
                    <input
                      type="checkbox"
                      checked={Boolean(grant)}
                      onChange={(event) =>
                        setGrants((values) =>
                          event.target.checked
                            ? [...values, { permission: permission.code, scopeType: 'GLOBAL', scopeIds: [] }]
                            : values.filter((item) => item.permission !== permission.code),
                        )
                      }
                    />
                    {permission.label}
                  </label>
                  {grant && (
                    <>
                      <label>
                        范围
                        <select
                          value={grant.scopeType}
                          onChange={(event) =>
                            update(permission.code, {
                              scopeType: event.target.value as AdminGrant['scopeType'],
                              scopeIds: [],
                            })
                          }
                        >
                          {permission.scopeTypes.map((scope) => (
                            <option key={scope} value={scope}>
                              {scopeLabels[scope]}
                            </option>
                          ))}
                        </select>
                      </label>
                      {grant.scopeType !== 'GLOBAL' && (
                        <label>
                          {grant.scopeType === 'MODULES' ? '栏目' : '组织'}（可多选）
                          <select
                            multiple
                            value={grant.scopeIds}
                            onChange={(event) =>
                              update(permission.code, {
                                scopeIds: Array.from(event.target.selectedOptions, (option) => option.value),
                              })
                            }
                          >
                            {options?.map((option) => (
                              <option key={option.id} value={option.id}>
                                {option.label}
                              </option>
                            ))}
                          </select>
                        </label>
                      )}
                    </>
                  )}
                </fieldset>
              )
            })}
          </div>
          <label>
            授权期限
            <select value={period} onChange={(event) => setPeriod(event.target.value)}>
              <option value="">请选择</option>
              <option value="until">指定截止时间</option>
              <option value="long">长期有效</option>
            </select>
          </label>
          {period === 'until' && (
            <label>
              截止时间
              <input
                type="datetime-local"
                value={expiry}
                onChange={(event) => setExpiry(event.target.value)}
              />
            </label>
          )}
          <label>
            操作原因
            <input value={reason} maxLength={1000} onChange={(event) => setReason(event.target.value)} />
          </label>
          <div className="toolbar">
            <button
              className="button ghost"
              disabled={busy || !valid}
              onClick={() =>
                run(async () => {
                  const result = await api.adminAuthorizationPreview(accountId, input)
                  setPreview(result)
                  setPreviewFingerprint(fingerprint)
                })
              }
            >
              预览授权影响
            </button>
            <button
              className="button primary"
              disabled={
                busy ||
                !valid ||
                !preview ||
                previewFingerprint !== fingerprint ||
                (!accountId && !/^[A-Za-z0-9._-]{4,100}$/.test(loginId))
              }
              onClick={() =>
                run(async () => {
                  if (accountId) {
                    const value = await api.adminSaveAuthorization(accountId, current!.version, input)
                    setCurrent(value)
                    setPreview(null)
                    setNotice({ tone: 'success', text: '授权已保存，目标账号需要重新登录。' })
                    setHistory(null)
                    onDone?.()
                  } else {
                    const account = await api.adminCreateAdmin(loginId, reason, input)
                    onCreated?.(account)
                    setLoginId('')
                    setReason('')
                    setGrants([])
                    setPeriod('')
                    setExpiry('')
                    setPreview(null)
                    setNotice({ tone: 'success', text: '管理员已创建并完成授权，请交付一次性激活凭据。' })
                  }
                })
              }
            >
              {accountId ? '保存授权' : '创建并授权'}
            </button>
            {accountId && (
              <>
                <button
                  className="button ghost"
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      setCurrent(await api.adminAuthorization(accountId))
                      setPreview(null)
                      setNotice({ tone: 'success', text: '已刷新授权版本，你的输入已保留，请重新预览。' })
                    })
                  }
                >
                  刷新服务器版本
                </button>
                <button
                  className="button ghost"
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      setHistory(await api.adminAuthorizationHistory(accountId))
                    })
                  }
                >
                  查看授权历史
                </button>
                <button
                  className="button danger"
                  disabled={busy || !reason.trim() || !current}
                  onClick={() => {
                    if (window.confirm('收回该管理员全部业务权限，并注销其旧会话？'))
                      void run(async () => {
                        setCurrent(await api.adminRevokeAuthorization(accountId, current!.version, reason))
                        setGrants([])
                        setPreview(null)
                        setHistory(null)
                        onDone?.()
                        setNotice({ tone: 'success', text: '全部授权已撤销。' })
                      })
                  }}
                >
                  收回全部权限
                </button>
              </>
            )}
          </div>
          {preview && previewFingerprint === fingerprint && (
            <div className="notice authorization-preview">
              <strong>授权影响</strong>
              <p>
                新增：{preview.added.map(label).join('、') || '无'}；收回：
                {preview.removed.map(label).join('、') || '无'}
              </p>
              <p>有效期：{input.longTerm ? '长期有效' : new Date(input.expiresAt!).toLocaleString()}</p>
              <ul>
                {preview.proposed.grants.map((grant) => (
                  <li key={grant.permission}>
                    {label(grant.permission)} · {scopeLabels[grant.scopeType]}
                    {grant.scopeIds.length
                      ? `（${grant.scopeIds.map((id) => profiles.data?.find((profile) => profile.id === id)?.displayName ?? modules.data?.modules.find((module) => module.key === id)?.label ?? '对象已不可用').join('、')}）`
                      : ''}
                  </li>
                ))}
              </ul>
              <ul>
                {preview.impacts.map((impact) => (
                  <li key={impact}>{impact}</li>
                ))}
              </ul>
            </div>
          )}
          {history && (
            <div>
              <h4>授权历史</h4>
              {history.length ? (
                history.map((entry) => (
                  <article key={entry.id} className="data-row">
                    <div>
                      <p>
                        {new Date(entry.createdAt).toLocaleString()} · {entry.reason}
                      </p>
                      <p>
                        原权限：
                        {entry.before.grants
                          .map(
                            (grant) =>
                              `${label(grant.permission)}（${scopeLabels[grant.scopeType]}${grant.scopeIds.length ? '：' + grant.scopeIds.join('、') : ''}）`,
                          )
                          .join('、') || '无'}
                      </p>
                      <p>
                        调整后：
                        {entry.after.grants
                          .map(
                            (grant) =>
                              `${label(grant.permission)}（${scopeLabels[grant.scopeType]}${grant.scopeIds.length ? '：' + grant.scopeIds.join('、') : ''}）`,
                          )
                          .join('、') || '无'}
                      </p>
                      <p>
                        期限：{entry.before.longTerm ? '长期' : (entry.before.expiresAt ?? '未授权')} →{' '}
                        {entry.after.longTerm ? '长期' : (entry.after.expiresAt ?? '未授权')}
                      </p>
                      <small>操作账号：{entry.actorAccountId}</small>
                    </div>
                  </article>
                ))
              ) : (
                <p className="muted">暂无保留期内的授权变更记录。</p>
              )}
            </div>
          )}
        </>
      )}
    </section>
  )
}
