import { useState } from 'react'
import { api, type PartnerProfile } from '../lib/api'
import { can, useIdentity } from '../lib/authorization'
import { useResource } from '../lib/hooks'
import { copyText } from '../lib/copy-text'
import { AccountActions } from './workspace-panels'
import { PanelHeading, Notice, ErrorState, LoadingRows, EmptyState, message, type NoticeValue } from './ui'

export function PartnerMembersPanel() {
  const state = useResource(api.partnerMembers)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [credential, setCredential] = useState<{
    login: string
    token: string
    expires: string
    reset: boolean
  } | null>(null)
  return (
    <section className="panel">
      <PanelHeading
        eyebrow="组织协作"
        title="受控成员账号"
        action={
          <button className="button ghost" onClick={state.reload}>
            刷新
          </button>
        }
      />
      <p className="panel-help">
        成员共享组织候选内容，可以创建、编辑和提交审核。公开发布由平台控制。暂时停用仍占名额，永久撤销后释放，内容归属保持在组织。
      </p>
      <Notice value={notice} />
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : (
        <>
          <p>
            成员名额：{state.data?.organization.memberCount} / {state.data?.organization.memberQuota}
            （不含主账号）
          </p>
          <form
            className="account-actions"
            onSubmit={async (event) => {
              event.preventDefault()
              const form = event.currentTarget
              const input = new FormData(form)
              setBusy(true)
              setCredential(null)
              setNotice(null)
              let created = false
              try {
                const member = await api.partnerCreateMember(
                  String(input.get('loginId')),
                  String(input.get('reason')),
                )
                created = true
                state.reload()
                form.reset()
                const link = await api.partnerMemberActivation(member.id, '创建成员后签发激活链接')
                setCredential({
                  login: link.loginId,
                  token: link.activationToken,
                  expires: link.activationExpiresAt,
                  reset: false,
                })
                setNotice({ tone: 'success', text: '成员已创建，请将一次性链接交给账号使用者设置密码。' })
              } catch (error) {
                setNotice({
                  tone: 'error',
                  text: (created ? '成员已创建，可在列表重新签发激活链接。' : '') + message(error),
                })
              } finally {
                setBusy(false)
              }
            }}
          >
            <label>
              成员登录名
              <input
                name="loginId"
                required
                pattern="[A-Za-z0-9._-]{4,100}"
                minLength={4}
                maxLength={100}
                autoComplete="off"
              />
            </label>
            <label>
              开通原因
              <input name="reason" required maxLength={1000} />
            </label>
            <button
              className="button"
              disabled={
                busy ||
                !state.data ||
                state.data.organization.memberCount >= state.data.organization.memberQuota
              }
            >
              创建成员
            </button>
          </form>
          {credential && (
            <section className="credential-box">
              <strong>
                {credential.login} · 一次性{credential.reset ? '密码恢复' : '激活'}链接
              </strong>
              <p>
                有效至 {new Date(credential.expires).toLocaleString('zh-CN')}
                ；账号使用者自行设置密码。恢复凭据在 /auth/reset-password 页面手动粘贴。
              </p>
              <code>
                {credential.reset
                  ? credential.token
                  : `${window.location.origin}/auth/activate?token=${credential.token}`}
              </code>
              <div className="actions">
                <button
                  className="button ghost"
                  onClick={() =>
                    void copyText(
                      credential.reset
                        ? credential.token
                        : `${window.location.origin}/auth/activate?token=${credential.token}`,
                    )
                  }
                >
                  复制链接
                </button>
                <button className="button ghost" onClick={() => setCredential(null)}>
                  清除凭据
                </button>
              </div>
            </section>
          )}
          {state.data?.items.length ? (
            <div className="profiles">
              {state.data.items.map((member) => (
                <article className="profile-card" key={member.id}>
                  <h3>{member.loginId}</h3>
                  <p>{member.qualificationStatus}</p>
                  {member.qualificationStatus !== 'REVOKED' && (
                    <AccountActions
                      account={{ ...member, roles: ['PARTNER'] }}
                      canReset
                      run={async (action, reason) => {
                        setCredential(null)
                        if (action === 'reissue') {
                          const link = await api.partnerMemberActivation(member.id, reason)
                          setCredential({
                            login: link.loginId,
                            token: link.activationToken,
                            expires: link.activationExpiresAt,
                            reset: false,
                          })
                        } else if (action === 'reset') {
                          const link = await api.partnerMemberReset(member.id, reason)
                          setCredential({
                            login: link.loginId,
                            token: link.resetToken,
                            expires: link.expiresAt,
                            reset: true,
                          })
                        } else await api.partnerMemberStatus(member.id, action, reason)
                        state.reload()
                        setNotice({ tone: 'success', text: '成员操作已完成。' })
                      }}
                    />
                  )}
                </article>
              ))}
            </div>
          ) : (
            <EmptyState title="尚无成员" text="平台核定成员限额后，主账号可以在这里开通成员。" />
          )}
        </>
      )}
    </section>
  )
}

export function OrganizationControls({
  profile,
  reload,
  notify,
}: {
  profile: PartnerProfile
  reload: () => void
  notify: (notice: NoticeValue) => void
}) {
  const identity = useIdentity()
  const scope = { ownerPartnerProfileId: profile.id }
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="account-actions"
      onSubmit={async (event) => {
        event.preventDefault()
        const input = new FormData(event.currentTarget)
        const action = ((event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement)?.value
        const reason = String(input.get('reason')).trim()
        if (!reason) return
        if (
          action === 'status' &&
          !window.confirm(
            profile.active
              ? '停用整个组织将撤销主账号及全部成员的会话和链接，已公开内容保持展示。确认停用？'
              : '恢复组织不会恢复旧会话或链接，单独停用的账号仍保持停用。确认恢复？',
          )
        )
          return
        setBusy(true)
        notify(null)
        try {
          if (action === 'quota')
            await api.adminSetMemberQuota(
              profile.id,
              Number(input.get('memberQuota')),
              profile.version,
              reason,
            )
          else await api.adminOrganizationStatus(profile.id, !profile.active, profile.version, reason)
          reload()
          notify({ tone: 'success', text: '组织设置已更新。' })
        } catch (error) {
          reload()
          notify({ tone: 'error', text: message(error) })
        } finally {
          setBusy(false)
        }
      }}
    >
      {can(identity, 'QUOTA_CONFIG', scope) && (
        <label>
          成员限额
          <input
            name="memberQuota"
            type="number"
            min={0}
            max={1000}
            defaultValue={profile.memberQuota}
            key={profile.version}
          />
        </label>
      )}
      {(can(identity, 'QUOTA_CONFIG', scope) || can(identity, 'ACCOUNT_OPERATE', scope)) && (
        <>
          <label>
            操作原因
            <input name="reason" required maxLength={1000} />
          </label>
          <div>
            {can(identity, 'QUOTA_CONFIG', scope) && (
              <button className="button ghost" name="action" value="quota" disabled={busy}>
                保存成员限额
              </button>
            )}
            {can(identity, 'ACCOUNT_OPERATE', scope) && (
              <button
                className={profile.active ? 'button danger' : 'button ghost'}
                name="action"
                value="status"
                disabled={busy}
              >
                {profile.active ? '停用整个组织' : '恢复组织'}
              </button>
            )}
          </div>
        </>
      )}
    </form>
  )
}
