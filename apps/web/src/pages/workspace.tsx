import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type MouseEvent,
  type ReactNode,
} from 'react'
import { Link, useLocation, useMatch, useNavigate, useSearchParams } from 'react-router-dom'
import {
  ArrowUpRight,
  ChevronLeft,
  FilePlus2,
  FileText,
  Gauge,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Menu,
  RefreshCw,
  ScrollText,
  Settings2,
  ShieldCheck,
  ClipboardCheck,
  Inbox,
  UserCog,
  Users,
} from 'lucide-react'
import {
  api,
  type CommunityEntrySummary,
  type ContactChannel,
  type ContentActionInput,
  type EntryAccessLevel,
  type Identity,
  type ManagedAction,
  type ManagedContent,
  type ProvisionedPartner,
  type PresentationType,
} from '../lib/api'
import { parseHomepagePlacementText } from '../lib/homepage-placements'
import { useCollapsed, usePageTitle, useResource } from '../lib/hooks'
import { ThemeButton } from '../components/theme'
import { MobileDrawer } from '../components/shell'
import { ContentEditor } from '../components/editor'
import {
  Badge,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  LoadingRows,
  Notice,
  PanelHeading,
  message,
  statusText,
  type NoticeValue,
} from '../components/ui'
import { safeUrl } from '../components/document'
import {
  AccountsPanel,
  ApplicationsPanel,
  AuditPanel,
  ReviewWorkspace,
  SubmissionsPanel,
} from '../components/workspace-panels'
type Confirmation = {
  title: string
  description: string
  reasonRequired?: boolean
  run: (reason: string) => Promise<void>
}
function itemsOf<T>(value: { items: T[] } | T[] | null | undefined): T[] {
  return Array.isArray(value) ? value : (value?.items ?? [])
}
export function Workspace() {
  usePageTitle('受控工作台')
  const state = useResource(api.me)
  if (state.loading)
    return (
      <main className="session-state">
        <Link className="wordmark" to="/">
          VOIDHAVEN／
        </Link>
        <section>
          <p>正在验证受控会话…</p>
          <LoadingRows />
        </section>
      </main>
    )
  if (state.error || !state.data)
    return (
      <main className="session-state">
        <Link className="wordmark" to="/">
          VOIDHAVEN／
        </Link>
        <section>
          <ThemeButton />
          <ErrorState error={state.error || '当前没有有效会话。'} retry={state.reload} />
          <Link className="button primary" to="/auth/sign-in">
            前往登录
          </Link>
        </section>
      </main>
    )
  // 旧版接口在异常会话或代理返回不完整数据时可能缺少 roles；
  // 先归一化为安全值，避免登录后因 includes 调用直接中断整棵 React 树。
  const identity = {
    ...state.data,
    loginId: state.data.loginId || '受控用户',
    roles: Array.isArray(state.data.roles) ? state.data.roles : [],
  }
  return <WorkspaceFrame identity={identity} />
}
function WorkspaceFrame({ identity }: { identity: Identity }) {
  const [collapsed, toggle] = useCollapsed()
  const [menu, setMenu] = useState(false)
  const [params] = useSearchParams()
  const [signingOut, setSigningOut] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [editorDirty, setEditorDirty] = useState(false)
  const location = useLocation()
  const newEditor = useMatch('/workspace/content/new')
  const editEditor = useMatch('/workspace/content/:contentId/edit')
  const editorRoute = Boolean(newEditor || editEditor)
  const isAdmin = identity.roles.includes('ADMIN') || identity.roles.includes('SUPER_ADMIN')
  const isSuperAdmin = identity.roles.includes('SUPER_ADMIN')
  const isPartner = identity.roles.includes('PARTNER')
  const nav = isAdmin
    ? [
        { id: 'overview', label: '概览', icon: LayoutDashboard },
        { id: 'content', label: '内容生命周期', icon: FileText },
        { id: 'reviews', label: '审核中心', icon: ClipboardCheck },
        { id: 'submissions', label: '公开输入', icon: Inbox },
        { id: 'applications', label: '合作方申请', icon: Users },
        { id: 'partners', label: '合作方与归属', icon: Settings2 },
        { id: 'homepage', label: '首页编排', icon: LayoutDashboard },
        ...(isSuperAdmin ? [{ id: 'community', label: '社群入口', icon: Users }] : []),
        ...(isSuperAdmin ? [{ id: 'accounts', label: '账号治理', icon: UserCog }] : []),
        { id: 'audit', label: '审计记录', icon: ScrollText },
        { id: 'security', label: '账号安全', icon: KeyRound },
      ]
    : [
        { id: 'overview', label: '概览', icon: LayoutDashboard },
        { id: 'content', label: '我的内容', icon: FileText },
        { id: 'security', label: '账号安全', icon: KeyRound },
      ]
  const section = editorRoute
    ? 'content'
    : nav.some((item) => item.id === params.get('section'))
      ? params.get('section')!
      : 'overview'
  const allowNavigation = (event?: MouseEvent) => {
    if (!editorDirty || window.confirm('有尚未保存的修改。确定离开编辑器吗？')) return true
    event?.preventDefault()
    return false
  }
  useEffect(() => {
    const text = (location.state as { workspaceNotice?: string } | null)?.workspaceNotice
    if (text) setNotice({ tone: 'success', text })
  }, [location.key, location.state])
  const contents = (
    <>
      <div className="sidebar-top">
        <Link className="wordmark" to="/">
          VOIDHAVEN<span>／</span>
        </Link>
        <button className="collapse" aria-label={collapsed ? '展开侧栏' : '收起侧栏'} onClick={toggle}>
          <ChevronLeft size={15} />
        </button>
      </div>
      <p className="nav-caption">WORKSPACE</p>
      <nav aria-label="工作台导航">
        {nav.map(({ id, label, icon: Icon }) => (
          <Link
            key={id}
            to={`/workspace?section=${id}`}
            aria-current={section === id ? 'page' : undefined}
            title={label}
            aria-label={label}
            className={section === id ? 'active' : ''}
            onClick={(event) => {
              if (allowNavigation(event)) setMenu(false)
            }}
          >
            <Icon size={16} />
            <span>{label}</span>
          </Link>
        ))}
      </nav>
      <div className="sidebar-bottom">
        <ThemeButton />
        <Link
          className="workspace-entry"
          to="/"
          title="返回公开首页"
          onClick={(event) => {
            allowNavigation(event)
          }}
        >
          <ArrowUpRight size={16} />
          <span>公开首页</span>
        </Link>
        <div className="identity">
          <span className="avatar">{identity.loginId.slice(0, 1).toUpperCase()}</span>
          <span>
            <strong>{identity.loginId}</strong>
            <small>{isAdmin ? '管理员' : isPartner ? '合作方' : '无权限'}</small>
          </span>
        </div>
        <button
          className="signout"
          aria-label="退出会话"
          title="退出会话"
          disabled={signingOut}
          onClick={async () => {
            if (signingOut) return
            if (!allowNavigation()) return
            setSigningOut(true)
            try {
              await api.signOut()
              window.location.assign('/auth/sign-in')
            } catch (e) {
              setNotice({ tone: 'error', text: message(e) })
              setSigningOut(false)
            }
          }}
        >
          <LogOut size={15} />
          <span>{signingOut ? '退出中…' : '退出会话'}</span>
        </button>
      </div>
    </>
  )
  return (
    <div className={`console ${collapsed ? 'collapsed' : ''}`}>
      <a className="skip" href="#workspace-main">
        跳至正文
      </a>
      <aside className="console-sidebar">{contents}</aside>
      <MobileDrawer open={menu} onClose={() => setMenu(false)}>
        {contents}
      </MobileDrawer>
      <button
        className="mobile-menu"
        aria-label="打开菜单"
        aria-expanded={menu}
        onClick={() => setMenu(true)}
      >
        <Menu size={18} />
      </button>
      <main id="workspace-main" className="workspace-main" tabIndex={-1}>
        <header className="workspace-header">
          <div>
            <p className="eyebrow">受控工作台 / {nav.find((item) => item.id === section)?.label}</p>
            <h1>
              {isAdmin ? (
                <>
                  Make the <em>right</em>
                  <br />
                  work visible.
                </>
              ) : (
                <>
                  Your work,
                  <br />
                  <em>in motion.</em>
                </>
              )}
            </h1>
          </div>
          <div className="header-meta">
            <span>
              <ShieldCheck size={15} />
              {isAdmin ? '管理员' : '合作方'} · {identity.loginId}
            </span>
          </div>
        </header>
        <Notice value={notice} />
        {section === 'security' ? (
          <div className="security-panels">
            <PasswordChangePanel />
          </div>
        ) : editorRoute ? (
          <WorkspaceEditorPage
            identity={identity}
            contentId={editEditor?.params.contentId}
            onDirtyChange={setEditorDirty}
          />
        ) : isAdmin ? (
          section === 'reviews' ? (
            <ReviewWorkspace itemId={params.get('item') ?? undefined} isSuperAdmin={isSuperAdmin} />
          ) : section === 'submissions' ? (
            <SubmissionsPanel />
          ) : section === 'applications' ? (
            <ApplicationsPanel />
          ) : section === 'partners' ? (
            <PartnerOperations />
          ) : section === 'homepage' ? (
            <HomepageManagementPanel />
          ) : section === 'community' && isSuperAdmin ? (
            <CommunityManagementPanel />
          ) : section === 'accounts' && isSuperAdmin ? (
            <AccountsPanel currentLoginId={identity.loginId} />
          ) : section === 'audit' ? (
            <AuditPanel />
          ) : (
            <AdminContent section={section} />
          )
        ) : isPartner ? (
          <PartnerContent section={section} />
        ) : (
          <EmptyState title="当前账号没有可用权限" text="请联系管理员核对账号角色。" />
        )}
      </main>
    </div>
  )
}

function WorkspaceEditorPage({
  identity,
  contentId,
  onDirtyChange,
}: {
  identity: Identity
  contentId?: string
  onDirtyChange: (dirty: boolean) => void
}) {
  const isAdmin = identity.roles.includes('ADMIN') || identity.roles.includes('SUPER_ADMIN')
  const isPartner = identity.roles.includes('PARTNER')
  const navigate = useNavigate()
  const savedMessage = useRef('')
  const close = () =>
    navigate('/workspace?section=content', {
      replace: true,
      state: savedMessage.current ? { workspaceNotice: savedMessage.current } : null,
    })
  if (!isAdmin && !isPartner)
    return <EmptyState title="当前账号没有编辑权限" text="请联系管理员核对账号角色。" />
  if (contentId) {
    return (
      <LoadedWorkspaceEditor
        contentId={contentId}
        isAdmin={isAdmin}
        close={close}
        savedMessage={savedMessage}
        onDirtyChange={onDirtyChange}
      />
    )
  }
  return (
    <ContentEditor
      onClose={close}
      onDirtyChange={onDirtyChange}
      onSave={async (draft) => {
        const result = isAdmin ? await api.adminCreate(draft) : await api.partnerCreate(draft)
        savedMessage.current = `“${result.title}”已保存，可从列表继续处理。`
      }}
      onSaveAndSubmit={
        !isAdmin
          ? async (draft) => {
              const result = await api.partnerCreate(draft)
              await api.partnerSubmit(result.id)
              savedMessage.current = `“${result.title}”已保存并送审。`
            }
          : undefined
      }
    />
  )
}

function RecruitmentActionEditor({
  contentId,
  initialVersion,
  isAdmin,
  onVersionChange,
}: {
  contentId: string
  initialVersion: number
  isAdmin: boolean
  onVersionChange?: (version: number) => void
}) {
  const loader = useCallback(
    () => (isAdmin ? api.adminActions(contentId) : api.partnerActions(contentId)),
    [contentId, isAdmin],
  )
  const state = useResource(loader)
  const [rows, setRows] = useState<ContentActionInput[]>([])
  const [qrFiles, setQrFiles] = useState<Record<number, { file: File; preview: string }>>({})
  const qrFilesRef = useRef(qrFiles)
  qrFilesRef.current = qrFiles
  const [version, setVersion] = useState(initialVersion)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  useEffect(() => {
    if (state.data) setRows(state.data.map(fromManagedAction))
  }, [state.data])
  useEffect(() => {
    setVersion(initialVersion)
  }, [initialVersion])
  useEffect(
    () => () => {
      Object.values(qrFilesRef.current).forEach((item) => URL.revokeObjectURL(item.preview))
    },
    [],
  )
  if (state.loading)
    return (
      <section className="panel">
        <LoadingRows />
      </section>
    )
  if (state.error)
    return (
      <section className="panel">
        <ErrorState error={state.error} retry={state.reload} />
      </section>
    )
  const update = (index: number, field: keyof ContentActionInput, value: string | boolean | number) =>
    setRows((current) =>
      current.map((row, rowIndex) => (rowIndex === index ? { ...row, [field]: value } : row)),
    )
  const setQrFile = (index: number, file: File | null) => {
    setQrFiles((current) => {
      const next = { ...current }
      if (next[index]) URL.revokeObjectURL(next[index].preview)
      if (file) next[index] = { file, preview: URL.createObjectURL(file) }
      else delete next[index]
      return next
    })
  }
  return (
    <section className="panel action-editor-panel">
      <PanelHeading eyebrow="RECRUITMENT ACTIONS" title="招募行动入口" />
      <p className="panel-help">
        分别选择展示形式、联系渠道和访问级别。个人联系方式默认经邀请码验证；如需公开，保存前必须确认风险。
      </p>
      {rows.map((row, index) => (
        <fieldset className="action-editor-row" key={index} disabled={busy}>
          <label>
            展示形式
            <select
              value={row.presentationType}
              onChange={(event) => {
                const nextType = event.target.value as ContentActionInput['presentationType']
                setRows((current) =>
                  current.map((item, rowIndex) =>
                    rowIndex === index
                      ? {
                          ...item,
                          ...nextEntryShape(nextType, item.contactChannel, item.accessLevel, {
                            from: 'presentation',
                          }),
                          mediaAssetId: nextType === 'QR_CODE' ? item.mediaAssetId : undefined,
                          publicExposureConfirmed: false,
                        }
                      : item,
                  ),
                )
                if (nextType !== 'QR_CODE') setQrFile(index, null)
              }}
            >
              <option value="LINK">链接</option>
              <option value="QR_CODE">二维码</option>
              <option value="TEXT">文本</option>
            </select>
          </label>
          <label>
            联系渠道
            <select
              value={row.contactChannel}
              onChange={(event) => {
                const channel = event.target.value as ContentActionInput['contactChannel']
                setRows((current) =>
                  current.map((item, rowIndex) =>
                    rowIndex === index
                      ? {
                          ...item,
                          ...nextEntryShape(item.presentationType, channel, item.accessLevel, {
                            from: 'channel',
                            resetSensitiveAccess: true,
                          }),
                          publicExposureConfirmed: false,
                        }
                      : item,
                  ),
                )
              }}
            >
              <option value="URL">网页 / 平台链接</option>
              <option value="EMAIL">邮箱</option>
              <option value="WECHAT">微信</option>
              <option value="QQ">QQ</option>
              <option value="PHONE">电话</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            展示名称
            <input
              value={row.label}
              maxLength={120}
              onChange={(event) => update(index, 'label', event.target.value)}
            />
          </label>
          <label>
            访问级别
            <select
              value={row.accessLevel}
              onChange={(event) => {
                update(index, 'accessLevel', event.target.value)
                update(index, 'publicExposureConfirmed', false)
              }}
            >
              <option value="PUBLIC">公开</option>
              <option value="COMMUNITY_GATED">邀请码验证后</option>
            </select>
          </label>
          {row.presentationType === 'QR_CODE' ? (
            <label>
              二维码图片
              <small>
                {row.mediaAssetId
                  ? '已有图片，重新选择会在保存时替换。'
                  : '从本机选择一张不超过 5 MB 的 png / jpeg / webp。'}
              </small>
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(event) => setQrFile(index, event.target.files?.[0] ?? null)}
              />
              {qrFiles[index] ? (
                <img className="community-qr" src={qrFiles[index].preview} alt="待保存的招募二维码预览" />
              ) : null}
            </label>
          ) : (
            <label>
              入口内容
              <input
                value={row.value ?? ''}
                maxLength={2048}
                onChange={(event) => update(index, 'value', event.target.value)}
              />
            </label>
          )}
          {requiresPublicExposureConfirmation(row) ? (
            <label className="checkbox-row">
              <input
                type="checkbox"
                checked={row.publicExposureConfirmed}
                onChange={(event) => update(index, 'publicExposureConfirmed', event.target.checked)}
              />
              我确认该联系方式或二维码将对所有访客公开
            </label>
          ) : null}
          <button
            className="button ghost"
            type="button"
            onClick={() => {
              setRows((current) => current.filter((_, rowIndex) => rowIndex !== index))
              setQrFiles((current) => {
                const remapped: Record<number, { file: File; preview: string }> = {}
                Object.entries(current).forEach(([key, value]) => {
                  const from = Number(key)
                  if (from === index) URL.revokeObjectURL(value.preview)
                  else remapped[from < index ? from : from - 1] = value
                })
                return remapped
              })
            }}
          >
            移除
          </button>
        </fieldset>
      ))}
      <div className="button-row">
        <button
          className="button ghost"
          type="button"
          onClick={() =>
            setRows((current) => [
              ...current,
              {
                presentationType: 'LINK',
                contactChannel: 'URL',
                accessLevel: 'PUBLIC',
                label: '立即报名',
                value: '',
                publicExposureConfirmed: false,
                enabled: true,
                sortOrder: current.length,
              },
            ])
          }
        >
          添加入口
        </button>
        <button
          className="button primary"
          type="button"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            setNotice(null)
            try {
              const prepared = rows.map((row) => ({ ...row }))
              for (let index = 0; index < prepared.length; index += 1) {
                const row = prepared[index]
                if (row.presentationType !== 'QR_CODE') continue
                const pending = qrFiles[index]
                if (pending) {
                  if (pending.file.size > 5 * 1024 * 1024) {
                    setNotice({ tone: 'error', text: '二维码图片不能超过 5 MB。' })
                    return
                  }
                  const uploaded = await api.uploadContentActionMedia(
                    contentId,
                    pending.file,
                    row.label.trim() || '招募二维码',
                  )
                  prepared[index] = { ...row, mediaAssetId: uploaded.id, value: undefined }
                }
                if (!prepared[index].mediaAssetId) {
                  setNotice({ tone: 'error', text: '二维码行动入口请先选择一张图片。' })
                  return
                }
              }
              const result = isAdmin
                ? await api.adminReplaceActions(contentId, version, prepared)
                : await api.partnerReplaceActions(contentId, version, prepared)
              setRows(result.actions.map(fromManagedAction))
              setQrFiles((current) => {
                Object.values(current).forEach((item) => URL.revokeObjectURL(item.preview))
                return {}
              })
              setVersion(result.version)
              onVersionChange?.(result.version)
              setNotice({
                tone: 'success',
                text: result.candidateCreated
                  ? '已创建候选版本，当前线上入口尚未改变。请完成送审和发布。'
                  : '招募行动入口已保存，内容版本已同步。',
              })
            } catch (error) {
              setNotice({ tone: 'error', text: message(error) })
            } finally {
              setBusy(false)
            }
          }}
        >
          {busy ? '保存中…' : '保存行动入口'}
        </button>
      </div>
      <Notice value={notice} />
    </section>
  )
}

function requiresPublicExposureConfirmation(action: ContentActionInput) {
  return requiresEntryExposureConfirmation(action.presentationType, action.contactChannel, action.accessLevel)
}

const SENSITIVE_CONTACT_CHANNELS: ContentActionInput['contactChannel'][] = ['EMAIL', 'WECHAT', 'QQ', 'PHONE']

function nextEntryShape(
  presentationType: ContentActionInput['presentationType'],
  contactChannel: ContentActionInput['contactChannel'],
  accessLevel: ContentActionInput['accessLevel'],
  options: { from?: 'presentation' | 'channel'; resetSensitiveAccess?: boolean } = {},
) {
  let nextPresentation = presentationType
  let nextChannel = contactChannel
  let nextAccess = accessLevel
  if (options.from === 'presentation') {
    if (nextPresentation === 'LINK') nextChannel = nextChannel === 'OTHER' ? 'OTHER' : 'URL'
    else if (nextChannel === 'URL') nextChannel = 'OTHER'
  } else {
    if (nextChannel === 'URL') nextPresentation = 'LINK'
    else if (nextPresentation === 'LINK' && nextChannel !== 'OTHER') nextPresentation = 'TEXT'
    if (options.resetSensitiveAccess && SENSITIVE_CONTACT_CHANNELS.includes(nextChannel)) {
      nextAccess = 'COMMUNITY_GATED'
    }
  }
  return { presentationType: nextPresentation, contactChannel: nextChannel, accessLevel: nextAccess }
}

function fromManagedAction(action: ManagedAction): ContentActionInput {
  return {
    presentationType: action.presentationType,
    contactChannel: action.contactChannel,
    accessLevel: action.accessLevel,
    label: action.label,
    value: action.value,
    mediaAssetId: action.mediaAssetId,
    publicExposureConfirmed: false,
    enabled: action.enabled,
    sortOrder: action.sortOrder,
  }
}

function LoadedWorkspaceEditor({
  contentId,
  isAdmin,
  close,
  savedMessage,
  onDirtyChange,
}: {
  contentId: string
  isAdmin: boolean
  close: () => void
  savedMessage: React.MutableRefObject<string>
  onDirtyChange: (dirty: boolean) => void
}) {
  const loader = useCallback(
    () => (isAdmin ? api.adminContent(contentId) : api.partnerContent(contentId)),
    [contentId, isAdmin],
  )
  const state = useResource(loader)
  const [revisionOverride, setRevision] = useState<number | null>(null)
  useEffect(() => {
    setRevision(null)
  }, [contentId])
  if (state.loading)
    return (
      <section className="content-editor-page">
        <LoadingRows />
      </section>
    )
  if (state.error || !state.data)
    return (
      <section className="content-editor-page">
        <ErrorState error={state.error || '内容不存在或无法编辑。'} retry={state.reload} />
      </section>
    )
  const editing = state.data
  const revision = revisionOverride ?? editing.revision
  const recruitment =
    editing.draft.kind === 'RECRUITMENT' || editing.draft.moduleKey?.startsWith('recruitment')
  return (
    <>
      <ContentEditor
        editing={{ ...editing, revision }}
        onClose={close}
        onDirtyChange={onDirtyChange}
        onSave={async (draft) => {
          const result = isAdmin
            ? await api.adminRevise(editing.id, revision, draft)
            : await api.partnerRevise(editing.id, revision, draft)
          setRevision(result.revision)
          savedMessage.current = `“${result.title}”已保存。`
        }}
        onSaveAndSubmit={
          !isAdmin
            ? async (draft) => {
                const result = await api.partnerRevise(editing.id, revision, draft)
                await api.partnerSubmit(result.id)
                setRevision(result.revision)
                savedMessage.current = `“${result.title}”已保存并送审。`
              }
            : undefined
        }
      />
      {recruitment ? (
        <RecruitmentActionEditor
          contentId={editing.id}
          initialVersion={revision}
          isAdmin={isAdmin}
          onVersionChange={setRevision}
        />
      ) : null}
    </>
  )
}
type Action =
  | 'submit'
  | 'approve'
  | 'changes'
  | 'reject'
  | 'publish'
  | 'hide'
  | 'show'
  | 'archive'
  | 'restore'
const actionLabel: Record<Action, string> = {
  submit: '送审',
  approve: '通过审核',
  changes: '退回修改',
  reject: '拒绝',
  publish: '发布',
  hide: '隐藏',
  show: '重新公开',
  archive: '归档',
  restore: '恢复',
}
export function availableActions(item: ManagedContent): Action[] {
  if (item.recordState === 'ARCHIVED') return ['restore']
  if (['DRAFT', 'CHANGES_REQUESTED'].includes(item.workflowStatus)) return ['submit']
  if (
    item.workflowStatus === 'PENDING_REVIEW' ||
    item.workflowStatus === 'APPROVED' ||
    item.workflowStatus === 'REJECTED'
  )
    return []
  if (item.workflowStatus === 'PUBLISHED')
    return item.visibility === 'PUBLIC' ? ['hide', 'archive'] : ['show', 'archive']
  return []
}
function runAction(item: ManagedContent, action: Action, reason: string) {
  switch (action) {
    case 'submit':
      return api.adminSubmit(item.id)
    case 'approve':
      return api.adminDecision(item.id, item.revision, 'APPROVE')
    case 'changes':
      return api.adminDecision(item.id, item.revision, 'CHANGES_REQUESTED', reason)
    case 'reject':
      return api.adminDecision(item.id, item.revision, 'REJECT', reason)
    case 'publish':
      return api.adminPublish(item.id)
    case 'hide':
      return api.adminHide(item.id, reason)
    case 'show':
      return api.adminShow(item.id)
    case 'archive':
      return api.adminArchive(item.id, reason)
    case 'restore':
      return api.adminRestore(item.id, reason)
  }
}
function ConfirmationView({ value, close }: { value: Confirmation | null; close: () => void }) {
  return value ? (
    <ConfirmDialog
      title={value.title}
      description={value.description}
      reasonRequired={value.reasonRequired}
      onConfirm={value.run}
      onClose={close}
    />
  ) : null
}
function ContentRows({
  items,
  actions,
}: {
  items: ManagedContent[]
  actions: (item: ManagedContent) => ReactNode
}) {
  return (
    <div className="data-list">
      {items.length ? (
        items.map((item) => (
          <article className="data-row" key={item.id}>
            <div>
              <div className="row-meta">
                <Badge value={item.kind} />
                <Badge value={item.workflowStatus} />
                {item.publicationStatus ? <Badge value={item.publicationStatus} /> : null}
                {item.recordState === 'ARCHIVED' && <Badge value="ARCHIVED" />}
              </div>
              <h3>{item.title}</h3>
              <p>
                模块：{item.moduleKey || statusText[item.kind]} · 可见性：
                {statusText[item.visibility] ?? item.visibility} · 修订 {item.revision}
              </p>
              {item.latestReview?.reason ? (
                <p className="review-note">最近审核意见：{item.latestReview.reason}</p>
              ) : null}
            </div>
            <div className="row-actions">{actions(item)}</div>
          </article>
        ))
      ) : (
        <EmptyState title="这里还没有内容" text="从一份草稿开始，把值得的内容整理出来。" />
      )}
    </div>
  )
}
function Overview({
  name,
  items,
  quota,
}: {
  name: string
  items: ManagedContent[]
  quota?: { count: number; total: number }
}) {
  return (
    <section className="overview-grid">
      <article className="feature-card">
        <p>YOUR EDITORIAL DESK</p>
        <h2>
          {name}
          <br />
          <em>认真维护，可信发布。</em>
        </h2>
        <span>从一份草稿开始，让每一步都有清晰的方向。</span>
        <Link className="button ghost" to="/workspace?section=content">
          查看内容 <ArrowUpRight size={15} />
        </Link>
      </article>
      <article className="metric-card">
        <Gauge size={20} />
        <span>{quota ? '公开配额' : '内容记录'}</span>
        <strong>{quota ? `${quota.count} / ${quota.total}` : items.length}</strong>
      </article>
      <article className="metric-card">
        <FileText size={20} />
        <span>待审核内容</span>
        <strong>{items.filter((item) => item.workflowStatus === 'PENDING_REVIEW').length}</strong>
      </article>
      <article className="process-card">
        <p>内容的旅程</p>
        <ol>
          <li>
            <span>01</span>整理草稿
          </li>
          <li>
            <span>02</span>提交审核
          </li>
          <li>
            <span>03</span>发布与维护
          </li>
        </ol>
      </article>
      {!quota ? <OperationalTasks /> : null}
    </section>
  )
}

function OperationalTasks() {
  const state = useResource(api.adminTasks)
  if (state.loading)
    return (
      <article className="metric-card">
        <RefreshCw size={20} />
        <span>运营待办加载中</span>
      </article>
    )
  if (state.error)
    return (
      <article className="metric-card">
        <Inbox size={20} />
        <span>运营待办暂时不可用</span>
        <button className="text-link" onClick={state.reload}>
          重试
        </button>
      </article>
    )
  return (
    <>
      {state.data
        ?.filter((task) => task.count > 0)
        .map((task) => (
          <Link className={`metric-card task-card ${task.severity}`} to={task.href} key={task.code}>
            <ClipboardCheck size={20} />
            <span>{task.label}</span>
            <strong>{task.count}</strong>
          </Link>
        ))}
    </>
  )
}

function requiresEntryExposureConfirmation(
  presentation: PresentationType,
  channel: ContactChannel,
  access: EntryAccessLevel,
) {
  return (
    access === 'PUBLIC' &&
    (presentation === 'QR_CODE' || ['EMAIL', 'WECHAT', 'QQ', 'PHONE'].includes(channel))
  )
}

function CommunityManagementPanel() {
  const loader = useCallback(async () => {
    const [entries, invites, capabilities] = await Promise.all([
      api.adminCommunityEntries(),
      api.adminCommunityInvites(),
      api.capabilities(),
    ])
    return { entries, invites, capabilities }
  }, [])
  const state = useResource(loader)
  const [credential, setCredential] = useState<{
    label: string
    inviteCode: string
    expiresAt: string
  } | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [presentationType, setPresentationType] = useState<PresentationType>('LINK')
  const [contactChannel, setContactChannel] = useState<ContactChannel>('URL')
  const [accessLevel, setAccessLevel] = useState<EntryAccessLevel>('COMMUNITY_GATED')
  const [qrFile, setQrFile] = useState<File | null>(null)
  const [qrPreview, setQrPreview] = useState('')
  const [qrMediaId, setQrMediaId] = useState('')
  const [uploadingQr, setUploadingQr] = useState(false)
  if (state.loading) return <LoadingRows />
  if (state.error || !state.data)
    return <ErrorState error={state.error || '社群管理数据不可用。'} retry={state.reload} />
  const operationsEnabled = state.data.capabilities.communityGateEnabled
  return (
    <section className="workspace-stack">
      <PanelHeading eyebrow="COMMUNITY OPERATIONS" title="社群入口与邀请码" />
      {credential ? (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>邀请码批次 · {credential.label}</strong>
            <code>{credential.inviteCode}</code>
            <small>
              只在当前页面显示一次，有效至 {new Date(credential.expiresAt).toLocaleString('zh-CN')}。
            </small>
            <button
              className="button ghost"
              onClick={() => void navigator.clipboard.writeText(credential.inviteCode)}
            >
              复制邀请码
            </button>
          </div>
        </section>
      ) : null}
      <Notice value={notice} />
      {!operationsEnabled ? (
        <Notice
          value={{
            tone: 'error',
            text: '社群与受控招募尚未完整配置。请先在运行环境启用功能开关并配置邀请码 HMAC 与 AES-256 密钥。',
          }}
        />
      ) : null}
      <form
        className="panel"
        onSubmit={async (event) => {
          event.preventDefault()
          const form = event.currentTarget
          const data = new FormData(form)
          if (!operationsEnabled) return
          try {
            const created = await api.adminCreateCommunityInvite(
              String(data.get('label') ?? '').trim(),
              Number(data.get('days') || 30),
              Number(data.get('uses') || 500),
            )
            setCredential(created)
            form.reset()
            state.reload()
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          }
        }}
      >
        <h3>创建批次共享邀请码</h3>
        <label>
          批次名称
          <input name="label" required maxLength={120} />
        </label>
        <label>
          有效天数
          <input name="days" type="number" min={1} max={90} defaultValue={30} required />
        </label>
        <label>
          最大使用次数
          <input name="uses" type="number" min={1} max={10000} defaultValue={500} required />
        </label>
        <button className="button primary" disabled={!operationsEnabled}>
          创建并显示一次性邀请码
        </button>
      </form>
      <div className="data-list">
        {state.data.invites.map((invite) => (
          <article className="data-row" key={invite.id}>
            <div>
              <Badge value={invite.state} />
              <h3>{invite.label}</h3>
              <p>
                {invite.useCount} / {invite.maxUses} · 到期{' '}
                {new Date(invite.expiresAt).toLocaleDateString('zh-CN')}
              </p>
            </div>
            {invite.state === 'ACTIVE' ? (
              <button
                className="button danger"
                onClick={async () => {
                  const reason = window.prompt('请输入撤销原因')?.trim()
                  if (!reason) return
                  try {
                    await api.adminRevokeCommunityInvite(invite.id, reason)
                    state.reload()
                  } catch (error) {
                    setNotice({ tone: 'error', text: message(error) })
                  }
                }}
              >
                撤销
              </button>
            ) : null}
          </article>
        ))}
      </div>
      <form
        className="panel"
        onSubmit={async (event) => {
          event.preventDefault()
          const form = event.currentTarget
          const data = new FormData(form)
          if (!operationsEnabled) return
          try {
            let mediaAssetId = qrMediaId.trim()
            if (presentationType === 'QR_CODE') {
              if (!mediaAssetId && qrFile) {
                setUploadingQr(true)
                const uploaded = await api.adminUploadCommunityMedia(
                  qrFile,
                  String(data.get('name') ?? '').trim() || '社群二维码',
                )
                mediaAssetId = uploaded.id
                setQrMediaId(uploaded.id)
              }
              if (!mediaAssetId) {
                setNotice({ tone: 'error', text: '二维码入口请先选择一张图片。' })
                return
              }
            }
            await api.adminCreateCommunityEntry({
              name: String(data.get('name') ?? '').trim(),
              description: String(data.get('description') ?? '').trim() || undefined,
              presentationType,
              contactChannel,
              accessLevel,
              value: String(data.get('value') ?? '').trim() || undefined,
              mediaAssetId: mediaAssetId || undefined,
              publicExposureConfirmed: data.get('publicExposureConfirmed') === 'on',
              primary: data.get('primary') === 'on',
              sortOrder: Number(data.get('sortOrder') || 0),
            })
            form.reset()
            setPresentationType('LINK')
            setContactChannel('URL')
            setAccessLevel('COMMUNITY_GATED')
            setQrFile(null)
            setQrPreview('')
            setQrMediaId('')
            state.reload()
            setNotice({ tone: 'success', text: '社群入口已创建。' })
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          } finally {
            setUploadingQr(false)
          }
        }}
      >
        <h3>新增社群入口</h3>
        <label>
          名称
          <input name="name" required maxLength={120} />
        </label>
        <label>
          说明
          <input name="description" maxLength={1000} />
        </label>
        <label>
          展示形式
          <select
            name="presentationType"
            value={presentationType}
            onChange={(event) => {
              const value = event.target.value as PresentationType
              const next = nextEntryShape(value, contactChannel, accessLevel, { from: 'presentation' })
              setPresentationType(next.presentationType)
              setContactChannel(next.contactChannel)
              setAccessLevel(next.accessLevel)
            }}
          >
            <option value="LINK">链接</option>
            <option value="QR_CODE">二维码</option>
            <option value="TEXT">文本</option>
          </select>
        </label>
        <label>
          联系渠道
          <select
            name="contactChannel"
            value={contactChannel}
            onChange={(event) => {
              const value = event.target.value as ContactChannel
              const next = nextEntryShape(presentationType, value, accessLevel, {
                from: 'channel',
                resetSensitiveAccess: true,
              })
              setPresentationType(next.presentationType)
              setContactChannel(next.contactChannel)
              setAccessLevel(next.accessLevel)
            }}
          >
            <option value="URL">网页 / 平台链接</option>
            <option value="EMAIL">邮箱</option>
            <option value="WECHAT">微信</option>
            <option value="QQ">QQ</option>
            <option value="PHONE">电话</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label>
          访问级别
          <select
            name="accessLevel"
            value={accessLevel}
            onChange={(event) => setAccessLevel(event.target.value as EntryAccessLevel)}
          >
            <option value="COMMUNITY_GATED">邀请码验证后</option>
            <option value="PUBLIC">公开</option>
          </select>
        </label>
        {presentationType === 'QR_CODE' ? (
          <label>
            二维码图片
            <small>从本机选择一张不超过 5 MB 的图片，创建时会自动上传。</small>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null
                setQrFile(file)
                setQrMediaId('')
                setQrPreview((current) => {
                  if (current) URL.revokeObjectURL(current)
                  return file ? URL.createObjectURL(file) : ''
                })
              }}
            />
            {qrPreview ? <img className="community-qr" src={qrPreview} alt="待上传的社群二维码预览" /> : null}
          </label>
        ) : (
          <label>
            入口内容
            <input name="value" maxLength={2048} />
          </label>
        )}
        {requiresEntryExposureConfirmation(presentationType, contactChannel, accessLevel) ? (
          <label className="checkbox-row">
            <input name="publicExposureConfirmed" type="checkbox" required />
            我确认该联系方式或二维码将对所有访客公开
          </label>
        ) : null}
        <label>
          排序
          <input name="sortOrder" type="number" min={0} max={1000} defaultValue={0} />
        </label>
        <label className="checkbox-row">
          <input name="primary" type="checkbox" /> 设为主入口
        </label>
        <button className="button primary" disabled={uploadingQr || !operationsEnabled}>
          {uploadingQr ? '上传并创建中…' : '创建入口'}
        </button>
      </form>
      <div className="data-list">
        {state.data.entries.map((entry) => (
          <article className="data-row" key={entry.id}>
            <div>
              <div className="row-meta">
                <Badge value={entry.active ? 'ACTIVE' : 'INACTIVE'} />
                {entry.primary ? <Badge value="PRIMARY" /> : null}
              </div>
              <h3>{entry.name}</h3>
              <p>
                {entry.presentationType} · {entry.contactChannel} ·{' '}
                {entry.accessLevel === 'PUBLIC' ? '公开' : '验证后可见'} · 顺序 {entry.sortOrder}
              </p>
            </div>
            {entry.active ? (
              <CommunityEntryActions
                entry={entry}
                operationsEnabled={operationsEnabled}
                onDone={(text) => {
                  state.reload()
                  setNotice({ tone: 'success', text })
                }}
                onError={(error) => setNotice({ tone: 'error', text: message(error) })}
              />
            ) : null}
          </article>
        ))}
      </div>
    </section>
  )
}

function CommunityEntryActions({
  entry,
  operationsEnabled,
  onDone,
  onError,
}: {
  entry: CommunityEntrySummary
  operationsEnabled: boolean
  onDone: (text: string) => void
  onError: (error: unknown) => void
}) {
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState(false)
  const [editPresentation, setEditPresentation] = useState<PresentationType>(entry.presentationType)
  const [editChannel, setEditChannel] = useState<ContactChannel>(entry.contactChannel)
  const [editAccess, setEditAccess] = useState<EntryAccessLevel>(entry.accessLevel)
  async function run(work: () => Promise<void>, success: string) {
    setBusy(true)
    try {
      await work()
      onDone(success)
    } catch (error) {
      onError(error)
    } finally {
      setBusy(false)
    }
  }
  return (
    <>
      <div className="button-row">
        <button
          className="button ghost"
          type="button"
          disabled={busy || !operationsEnabled}
          onClick={() => setEditing((current) => !current)}
        >
          {editing ? '取消编辑' : '编辑'}
        </button>
        {!entry.primary ? (
          <button
            className="button ghost"
            type="button"
            disabled={busy || !operationsEnabled}
            onClick={() =>
              operationsEnabled &&
              void run(async () => {
                await api.adminUpdateCommunityEntry(entry.id, entry.version, {
                  name: entry.name,
                  description: entry.description ?? undefined,
                  presentationType: entry.presentationType,
                  contactChannel: entry.contactChannel,
                  accessLevel: entry.accessLevel,
                  mediaAssetId: entry.mediaAssetId ?? undefined,
                  publicExposureConfirmed:
                    entry.accessLevel === 'PUBLIC' &&
                    requiresEntryExposureConfirmation(
                      entry.presentationType,
                      entry.contactChannel,
                      entry.accessLevel,
                    ),
                  primary: true,
                  sortOrder: entry.sortOrder,
                })
              }, '已切换为主入口。')
            }
          >
            设为主入口
          </button>
        ) : null}
        <button
          className="button danger"
          type="button"
          disabled={busy}
          onClick={() => {
            const reason = window.prompt('请输入停用原因')?.trim()
            if (!reason) return
            void run(async () => {
              await api.adminDeactivateCommunityEntry(entry.id, entry.version, reason)
            }, '社群入口已停用。')
          }}
        >
          停用
        </button>
      </div>
      {editing ? (
        <form
          className="decision-inline"
          onSubmit={(event) => {
            event.preventDefault()
            const data = new FormData(event.currentTarget)
            void run(async () => {
              await api.adminUpdateCommunityEntry(entry.id, entry.version, {
                name: String(data.get('name') ?? '').trim(),
                description: String(data.get('description') ?? '').trim() || undefined,
                presentationType: editPresentation,
                contactChannel: editChannel,
                accessLevel: editAccess,
                value: String(data.get('value') ?? '').trim() || undefined,
                mediaAssetId: String(data.get('mediaAssetId') ?? '').trim() || undefined,
                publicExposureConfirmed: data.get('publicExposureConfirmed') === 'on',
                primary: data.get('primary') === 'on',
                sortOrder: Number(data.get('sortOrder') || 0),
              })
              setEditing(false)
            }, '社群入口已更新。')
          }}
        >
          <label>
            名称
            <input name="name" required maxLength={120} defaultValue={entry.name} />
          </label>
          <label>
            说明
            <input name="description" maxLength={1000} defaultValue={entry.description ?? ''} />
          </label>
          <label>
            展示形式
            <select
              name="presentationType"
              value={editPresentation}
              onChange={(event) => {
                const value = event.target.value as PresentationType
                const next = nextEntryShape(value, editChannel, editAccess, { from: 'presentation' })
                setEditPresentation(next.presentationType)
                setEditChannel(next.contactChannel)
                setEditAccess(next.accessLevel)
              }}
            >
              <option value="LINK">链接</option>
              <option value="QR_CODE">二维码</option>
              <option value="TEXT">文本</option>
            </select>
          </label>
          <label>
            联系渠道
            <select
              name="contactChannel"
              value={editChannel}
              onChange={(event) => {
                const value = event.target.value as ContactChannel
                const next = nextEntryShape(editPresentation, value, editAccess, {
                  from: 'channel',
                  resetSensitiveAccess: true,
                })
                setEditPresentation(next.presentationType)
                setEditChannel(next.contactChannel)
                setEditAccess(next.accessLevel)
              }}
            >
              <option value="URL">网页 / 平台链接</option>
              <option value="EMAIL">邮箱</option>
              <option value="WECHAT">微信</option>
              <option value="QQ">QQ</option>
              <option value="PHONE">电话</option>
              <option value="OTHER">其他</option>
            </select>
          </label>
          <label>
            访问级别
            <select
              name="accessLevel"
              value={editAccess}
              onChange={(event) => setEditAccess(event.target.value as EntryAccessLevel)}
            >
              <option value="COMMUNITY_GATED">邀请码验证后</option>
              <option value="PUBLIC">公开</option>
            </select>
          </label>
          {editPresentation === 'QR_CODE' ? (
            <label>
              二维码媒体 ID <small>留空保留原图片；替换图片可先新建入口后停用旧入口。</small>
              <input name="mediaAssetId" maxLength={26} defaultValue={entry.mediaAssetId ?? ''} />
            </label>
          ) : (
            <label>
              新入口内容 <small>留空保留原有受保护内容。</small>
              <input name="value" maxLength={2048} autoComplete="off" />
            </label>
          )}
          {requiresEntryExposureConfirmation(editPresentation, editChannel, editAccess) ? (
            <label className="checkbox-row">
              <input name="publicExposureConfirmed" type="checkbox" required />
              我确认该联系方式或二维码将对所有访客公开
            </label>
          ) : null}
          <label>
            排序
            <input name="sortOrder" type="number" min={0} max={1000} defaultValue={entry.sortOrder} />
          </label>
          <label className="checkbox-row">
            <input name="primary" type="checkbox" defaultChecked={entry.primary} /> 设为主入口
          </label>
          <button className="button primary" disabled={busy || !operationsEnabled}>
            保存入口
          </button>
        </form>
      ) : null}
    </>
  )
}

function HomepageManagementPanel() {
  const state = useResource(useCallback(() => api.adminHomepagePlacements(), []))
  const [text, setText] = useState('')
  const [notice, setNotice] = useState<NoticeValue>(null)
  useEffect(() => {
    if (state.data)
      setText(state.data.map((item) => `${item.slot},${item.contentItemId},${item.sortOrder}`).join('\n'))
  }, [state.data])
  if (state.loading) return <LoadingRows />
  if (state.error) return <ErrorState error={state.error} retry={state.reload} />
  return (
    <section className="workspace-stack">
      <PanelHeading eyebrow="HOMEPAGE CURATION" title="首页人工编排" />
      <p className="panel-help">
        每行填写“栏目,内容ID,顺序”。栏目可用
        FOCUS、RECRUITMENT、RESOURCE；留空保存后，未配置栏目会回退到最新公开内容。
      </p>
      {!state.data?.length && !text.trim() ? (
        <p className="field-help">当前没有人工编排。保存空清单将使用自动回退。</p>
      ) : null}
      <form
        className="panel"
        onSubmit={async (event) => {
          event.preventDefault()
          const parsed = parseHomepagePlacementText(text)
          if (parsed.errors.length) {
            setNotice({ tone: 'error', text: parsed.errors.join(' ') })
            return
          }
          try {
            await api.adminReplaceHomepagePlacements(parsed.placements)
            state.reload()
            setNotice({
              tone: 'success',
              text: parsed.placements.length
                ? `首页编排已保存，共 ${parsed.placements.length} 条。`
                : '已清空人工编排，公开首页将回退最新内容。',
            })
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          }
        }}
      >
        <label>
          编排清单
          <textarea
            value={text}
            onChange={(event) => setText(event.target.value)}
            rows={10}
            spellCheck={false}
            placeholder="FOCUS,01HZXC0V4K7N9PQRSTVWXY0001,0"
          />
        </label>
        <button className="button primary">保存首页编排</button>
        <Notice value={notice} />
      </form>
    </section>
  )
}

function PasswordChangePanel() {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const next = String(data.get('newPassword') ?? '')
    if (next !== String(data.get('confirm') ?? '')) {
      setNotice({ tone: 'error', text: '两次输入的新密码不一致。' })
      return
    }
    setBusy(true)
    try {
      await api.changePassword(String(data.get('currentPassword') ?? ''), next)
      window.location.assign('/auth/sign-in')
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
      setBusy(false)
    }
  }
  return (
    <section className="workspace-card narrow-card">
      <PanelHeading eyebrow="ACCOUNT SECURITY" title="修改密码" />
      <p className="panel-help">修改成功后，当前账号在所有设备上的会话都会失效，需要使用新密码重新登录。</p>
      <form className="paper-form" onSubmit={submit}>
        <fieldset disabled={busy}>
          <label>
            当前密码
            <input
              name="currentPassword"
              type="password"
              required
              maxLength={128}
              autoComplete="current-password"
            />
          </label>
          <label>
            新密码
            <input
              name="newPassword"
              type="password"
              required
              minLength={12}
              maxLength={128}
              autoComplete="new-password"
            />
          </label>
          <label>
            确认新密码
            <input
              name="confirm"
              type="password"
              required
              minLength={12}
              maxLength={128}
              autoComplete="new-password"
            />
          </label>
          <button className="button primary" disabled={busy}>
            {busy ? '修改中…' : '修改密码并注销全部会话'}
          </button>
        </fieldset>
        <Notice value={notice} />
      </form>
    </section>
  )
}

function AdminContent({ section }: { section: string }) {
  const [params] = useSearchParams()
  const [filters, setFilters] = useState(() => ({
    query: '',
    status: '',
    module: '',
    verification: ['attention', 'due'].includes(params.get('verification') ?? '')
      ? (params.get('verification') as 'attention' | 'due')
      : ('' as const),
  }))
  const loader = useCallback(() => api.adminContentItems(filters), [filters])
  const state = useResource(loader)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const update = (item: ManagedContent) =>
    state.setData((page) => {
      const items = itemsOf(page)
      return {
        ...(page ?? { nextCursor: null }),
        items: items.some((value) => value.id === item.id)
          ? items.map((value) => (value.id === item.id ? item : value))
          : [item, ...items],
      }
    })
  const actions = (item: ManagedContent) => (
    <>
      {['PENDING_REVIEW', 'APPROVED', 'REJECTED'].includes(item.workflowStatus) ? (
        <Link
          className="button primary"
          to={`/workspace?section=reviews&item=${encodeURIComponent(item.id)}`}
        >
          {item.workflowStatus === 'PENDING_REVIEW'
            ? '查看原文并审核'
            : item.workflowStatus === 'APPROVED'
              ? '发布检查'
              : '查看拒绝记录'}
        </Link>
      ) : null}
      {item.recordState !== 'ARCHIVED' &&
        ['DRAFT', 'CHANGES_REQUESTED', 'PUBLISHED'].includes(item.workflowStatus) && (
          <Link className="button ghost" to={`/workspace/content/${encodeURIComponent(item.id)}/edit`}>
            编辑
          </Link>
        )}
      <VerificationControl item={item} />
      {availableActions(item).map((action) => (
        <button
          className={`button ${action === 'publish' || action === 'approve' ? 'primary' : 'ghost'}`}
          key={action}
          onClick={() =>
            setConfirmation({
              title: actionLabel[action],
              description: `即将对“${item.title}”执行${actionLabel[action]}。${action === 'publish' || action === 'show' ? '完成后内容将可供公开访问。' : action === 'archive' || action === 'hide' ? '完成后内容将不再公开展示。' : '操作将由服务端校验当前状态。'}`,
              reasonRequired: ['changes', 'reject', 'hide', 'archive', 'restore'].includes(action),
              run: async (reason) => {
                const updated = await runAction(item, action, reason)
                update(updated)
                setNotice({ tone: 'success', text: `“${updated.title}”已${actionLabel[action]}。` })
              },
            })
          }
        >
          {actionLabel[action]}
        </button>
      ))}
    </>
  )
  return (
    <>
      {section === 'overview' ? (
        state.loading ? (
          <LoadingRows />
        ) : state.error ? (
          <ErrorState error={state.error} retry={state.reload} />
        ) : (
          <Overview name="内容编辑部" items={itemsOf(state.data)} />
        )
      ) : (
        <section className="panel">
          <PanelHeading
            eyebrow="内容生命周期"
            title="创建、审核与发布"
            action={
              <>
                <button className="button ghost" onClick={state.reload}>
                  <RefreshCw size={15} />
                  刷新
                </button>
                <Link className="button primary" to="/workspace/content/new">
                  <FilePlus2 size={15} />
                  新建草稿
                </Link>
              </>
            }
          />
          <p className="panel-help">先整理内容，再核对来源。公开发布前，每份内容都经过审核。</p>
          {filters.verification ? (
            <div className="inline-notice">
              当前核验筛选：{filters.verification === 'attention' ? '异常待处理' : '定期复核已到期'}
              <button
                className="button ghost"
                type="button"
                onClick={() => setFilters((current) => ({ ...current, verification: '' }))}
              >
                清除筛选
              </button>
            </div>
          ) : null}
          <form
            className="workspace-filter"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              setFilters({
                query: String(data.get('q') ?? '').trim(),
                status: String(data.get('status') ?? ''),
                module: String(data.get('module') ?? ''),
                verification: String(data.get('verification') ?? '') as 'attention' | 'due' | '',
              })
            }}
          >
            <label>
              搜索
              <input name="q" placeholder="标题或关键词" />
            </label>
            <label>
              状态
              <select name="status">
                <option value="">全部状态</option>
                <option value="DRAFT">草稿</option>
                <option value="PENDING_REVIEW">待审核</option>
                <option value="APPROVED">待发布</option>
                <option value="CHANGES_REQUESTED">待修改</option>
                <option value="REJECTED">已拒绝</option>
                <option value="PUBLISHED">已发布</option>
              </select>
            </label>
            <label>
              模块
              <select name="module">
                <option value="">全部模块</option>
                <option value="competition">竞赛</option>
                <option value="recruitment">团队招募</option>
                <option value="resource">学习资源</option>
                <option value="resource.article">文章 / 经验</option>
              </select>
            </label>
            <label>
              来源核验
              <select name="verification" defaultValue={filters.verification}>
                <option value="">全部</option>
                <option value="attention">异常待处理</option>
                <option value="due">定期复核已到期</option>
              </select>
            </label>
            <button className="button ghost">应用筛选</button>
          </form>
          <Notice value={notice} />
          {state.loading ? (
            <LoadingRows />
          ) : state.error ? (
            <ErrorState error={state.error} retry={state.reload} />
          ) : (
            <ContentRows items={itemsOf(state.data)} actions={actions} />
          )}
          {state.data?.nextCursor ? (
            <button
              className="button ghost load-more"
              onClick={async () => {
                const next = await api.adminContentItems({
                  ...filters,
                  cursor: state.data?.nextCursor ?? undefined,
                })
                state.setData((current) => ({ ...next, items: [...itemsOf(current), ...next.items] }))
              }}
            >
              加载更多
            </button>
          ) : null}
        </section>
      )}
      <ConfirmationView value={confirmation} close={() => setConfirmation(null)} />
    </>
  )
}

function VerificationControl({ item }: { item: ManagedContent }) {
  const [latest, setLatest] = useState<
    Awaited<ReturnType<typeof api.adminContentVerifications>>[number] | null
  >(null)
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <details
      className="verification-control"
      onToggle={async (event) => {
        if (!event.currentTarget.open || loaded) return
        try {
          const values = await api.adminContentVerifications(item.id)
          setLatest(values[0] ?? null)
          setLoaded(true)
        } catch (error) {
          setNotice({ tone: 'error', text: message(error) })
        }
      }}
    >
      <summary className="button ghost">来源核验</summary>
      <form
        className="verification-form"
        onSubmit={async (event) => {
          event.preventDefault()
          const form = event.currentTarget
          const data = new FormData(form)
          const outcome = String(data.get('outcome')) as 'VALID' | 'INVALID' | 'NEEDS_REVIEW'
          const detailSummary = String(data.get('detailSummary') ?? '').trim()
          const nextCheck = String(data.get('nextCheckAt') ?? '')
          setBusy(true)
          setNotice(null)
          try {
            const saved = await api.adminRecordContentVerification(item.id, {
              outcome,
              detailSummary: detailSummary || undefined,
              nextCheckAt: nextCheck ? new Date(`${nextCheck}T00:00:00`).toISOString() : undefined,
            })
            setLatest(saved)
            setNotice({ tone: 'success', text: '核验结果已保存并进入后续待办规则。' })
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          } finally {
            setBusy(false)
          }
        }}
      >
        {latest ? (
          <small>
            最近：{latest.outcome} · {new Date(latest.checkedAt).toLocaleString()}
          </small>
        ) : (
          <small>尚无核验记录</small>
        )}
        <label>
          结果
          <select name="outcome" defaultValue="VALID" disabled={busy}>
            <option value="VALID">有效</option>
            <option value="INVALID">失效</option>
            <option value="NEEDS_REVIEW">待复核</option>
          </select>
        </label>
        <label>
          核验说明
          <input name="detailSummary" maxLength={1000} placeholder="失效或待复核时必填" disabled={busy} />
        </label>
        <label>
          下次复核日期
          <input name="nextCheckAt" type="date" disabled={busy} />
        </label>
        <button className="button primary" disabled={busy}>
          {busy ? '保存中…' : '保存核验结果'}
        </button>
        <Notice value={notice} />
      </form>
    </details>
  )
}
function PartnerContent({ section }: { section: string }) {
  const [filters, setFilters] = useState({ query: '', status: '', module: '' })
  const loader = useCallback(() => api.partnerDashboard(filters), [filters])
  const state = useResource(loader)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const actions = (item: ManagedContent) => (
    <>
      {item.recordState !== 'ARCHIVED' &&
        ['DRAFT', 'CHANGES_REQUESTED', 'PUBLISHED'].includes(item.workflowStatus) && (
          <Link className="button ghost" to={`/workspace/content/${encodeURIComponent(item.id)}/edit`}>
            编辑
          </Link>
        )}
      {item.recordState !== 'ARCHIVED' && ['DRAFT', 'CHANGES_REQUESTED'].includes(item.workflowStatus) && (
        <button
          className="button primary"
          onClick={() =>
            setConfirmation({
              title: '送审',
              description: `提交“${item.title}”供管理员审核。送审后请等待审核结果。`,
              run: async () => {
                await api.partnerSubmit(item.id)
                state.reload()
                setNotice({ tone: 'success', text: '内容已送入审核队列。' })
              },
            })
          }
        >
          送审
        </button>
      )}
    </>
  )
  return (
    <>
      {section === 'overview' ? (
        state.loading ? (
          <LoadingRows />
        ) : state.error ? (
          <ErrorState error={state.error} retry={state.reload} />
        ) : (
          state.data && (
            <Overview
              name={state.data.displayName}
              items={itemsOf(state.data.contentItems)}
              quota={{ count: state.data.publicActiveCount, total: state.data.quota }}
            />
          )
        )
      ) : (
        <section className="panel">
          <PanelHeading
            eyebrow="我的内容"
            title="把想法，认真整理"
            action={
              <>
                <button className="button ghost" onClick={state.reload}>
                  <RefreshCw size={15} />
                  刷新
                </button>
                <Link className="button primary" to="/workspace/content/new">
                  <FilePlus2 size={15} />
                  新建草稿
                </Link>
              </>
            }
          />
          <p className="panel-help">
            草稿与审核中的内容不占用公开配额。
            {state.data && `当前公开配额 ${state.data.publicActiveCount} / ${state.data.quota}。`}
          </p>
          <form
            className="workspace-filter"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              setFilters({
                query: String(data.get('q') ?? '').trim(),
                status: String(data.get('status') ?? ''),
                module: String(data.get('module') ?? ''),
              })
            }}
          >
            <label>
              搜索
              <input name="q" placeholder="标题或关键词" />
            </label>
            <label>
              状态
              <select name="status">
                <option value="">全部状态</option>
                <option value="DRAFT">草稿</option>
                <option value="PENDING_REVIEW">待审核</option>
                <option value="CHANGES_REQUESTED">待修改</option>
                <option value="PUBLISHED">已发布</option>
              </select>
            </label>
            <label>
              模块
              <select name="module">
                <option value="">全部模块</option>
                <option value="competition">竞赛</option>
                <option value="recruitment">团队招募</option>
                <option value="resource">学习资源</option>
                <option value="resource.article">文章 / 经验</option>
              </select>
            </label>
            <button className="button ghost">应用筛选</button>
          </form>
          <Notice value={notice} />
          {state.loading ? (
            <LoadingRows />
          ) : state.error ? (
            <ErrorState error={state.error} retry={state.reload} />
          ) : (
            <ContentRows items={itemsOf(state.data?.contentItems)} actions={actions} />
          )}
          {state.data?.contentItems.nextCursor ? (
            <button
              className="button ghost load-more"
              onClick={async () => {
                const next = await api.partnerDashboard({
                  ...filters,
                  cursor: state.data?.contentItems.nextCursor ?? undefined,
                })
                state.setData((current) =>
                  current
                    ? {
                        ...next,
                        contentItems: {
                          ...next.contentItems,
                          items: [...current.contentItems.items, ...next.contentItems.items],
                        },
                      }
                    : next,
                )
              }}
            >
              加载更多
            </button>
          ) : null}
        </section>
      )}
      <ConfirmationView value={confirmation} close={() => setConfirmation(null)} />
    </>
  )
}
export function PartnerApplications() {
  const state = useResource(api.adminPartnerApplications)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [provisioned, setProvisioned] = useState<ProvisionedPartner | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <section className="panel">
      <PanelHeading
        eyebrow="合作方申请"
        title="欢迎新的维护者"
        action={
          <button className="button ghost" onClick={state.reload}>
            <RefreshCw size={15} />
            刷新
          </button>
        }
      />
      <p className="panel-help">批准后创建待激活账号。一次性激活链接仅在本次展示，请通过受控渠道交付。</p>
      <Notice value={notice} />
      {provisioned && (
        <section className="token-card">
          <KeyRound size={21} />
          <div>
            <strong>一次性激活链接 · {provisioned.loginId}</strong>
            <code>{`${window.location.origin}/auth/activate?token=${encodeURIComponent(provisioned.activationToken)}`}</code>
            <small>离开页面或刷新后不再显示，请勿公开分享。</small>
            <button className="button ghost" onClick={() => setProvisioned(null)}>
              已妥善记录，关闭凭据
            </button>
          </div>
        </section>
      )}
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : itemsOf(state.data).length ? (
        <div className="data-list">
          {itemsOf(state.data).map((item) => (
            <article className="application-row" key={item.id}>
              <div>
                <h3>{item.displayName}</h3>
                <p>{item.introduction}</p>
                {safeUrl(item.portfolioUrl) && (
                  <a href={safeUrl(item.portfolioUrl)} target="_blank" rel="noreferrer">
                    查看公开作品 <ArrowUpRight size={14} />
                  </a>
                )}
              </div>
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  const loginId = String(new FormData(e.currentTarget).get('loginId')).trim()
                  setProvisioned(null)
                  setConfirmation({
                    title: '批准申请',
                    description: `为“${item.displayName}”创建登录名为“${loginId}”的合作方账号，并生成一次性激活凭据。`,
                    reasonRequired: true,
                    run: async (reason) => {
                      setProvisioned(await api.adminApprovePartnerApplication(item.id, loginId, reason))
                      state.reload()
                      setNotice({ tone: 'success', text: '合作方账号已创建，请完成凭据交付。' })
                    },
                  })
                }}
              >
                <label>
                  受控登录名
                  <input
                    name="loginId"
                    required
                    minLength={4}
                    maxLength={100}
                    pattern="[A-Za-z0-9._-]{4,100}"
                    placeholder="partner-name"
                  />
                </label>
                <button className="button primary">批准并生成凭据</button>
              </form>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState title="当前没有待处理申请" text="新的合作方申请将在这里出现。" />
      )}
      <ConfirmationView value={confirmation} close={() => setConfirmation(null)} />
    </section>
  )
}
function PartnerOperations() {
  const [filters, setFilters] = useState({ query: '', status: '' })
  const loader = useCallback(() => api.adminPartnerProfiles(filters), [filters])
  const state = useResource(loader)
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  return (
    <section className="panel">
      <PanelHeading
        eyebrow="合作方运营"
        title="配额与内容归属"
        action={
          <button className="button ghost" onClick={state.reload}>
            <RefreshCw size={15} />
            刷新
          </button>
        }
      />
      <p className="panel-help">公开且活跃的内容计入配额。调整配额或转交内容时，请说明原因。</p>
      <form
        className="workspace-filter"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          setFilters({ query: String(data.get('q') ?? '').trim(), status: String(data.get('status') ?? '') })
        }}
      >
        <label>
          搜索合作方
          <input name="q" placeholder="展示名或账号" />
        </label>
        <label>
          账号资格
          <select name="status">
            <option value="">全部状态</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="REVOKED">REVOKED</option>
          </select>
        </label>
        <button className="button ghost">应用筛选</button>
      </form>
      <Notice value={notice} />
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : itemsOf(state.data).length ? (
        <div className="profiles">
          {itemsOf(state.data).map((profile) => (
            <article className="profile-card" key={profile.id}>
              <div>
                <span className="avatar">{profile.displayName.slice(0, 1)}</span>
                <h3>{profile.displayName}</h3>
                <p>
                  {profile.active ? '启用' : '停用'} · 账号资格 {profile.qualificationStatus ?? '—'} ·
                  当前公开配额 {profile.contentQuota}
                </p>
              </div>
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  const form = e.currentTarget
                  const data = new FormData(form)
                  const quota = Number(data.get('quota'))
                  const reason = String(data.get('reason')).trim()
                  if (!reason) return
                  setConfirmation({
                    title: '调整配额',
                    description: `将“${profile.displayName}”的公开配额从 ${profile.contentQuota} 调整为 ${quota}。原因：${reason}`,
                    run: async () => {
                      await api.adminSetPartnerQuota(profile.id, quota, reason)
                      state.reload()
                      setNotice({ tone: 'success', text: '公开配额已更新。' })
                    },
                  })
                }}
              >
                <label>
                  公开配额
                  <input
                    name="quota"
                    type="number"
                    min="0"
                    max="5"
                    required
                    defaultValue={profile.contentQuota}
                    key={profile.contentQuota}
                  />
                </label>
                <label>
                  调整原因
                  <input name="reason" required maxLength={1000} />
                </label>
                <button className="button ghost">保存配额</button>
              </form>
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  const form = e.currentTarget
                  const data = new FormData(form)
                  const id = String(data.get('contentItemId')).trim()
                  const reason = String(data.get('reason')).trim()
                  if (!reason) return
                  setConfirmation({
                    title: '转交内容',
                    description: `将内容 ${id} 转交给“${profile.displayName}”。原因：${reason}`,
                    run: async () => {
                      await api.adminTransferOwnership(id, profile.id, reason)
                      form.reset()
                      state.reload()
                      setNotice({ tone: 'success', text: '内容归属已更新。' })
                    },
                  })
                }}
              >
                <label>
                  待转交内容 ID
                  <input name="contentItemId" required minLength={26} maxLength={26} />
                </label>
                <label>
                  转交原因
                  <input name="reason" required maxLength={1000} />
                </label>
                <button className="button ghost">转交内容</button>
              </form>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState title="当前没有合作方档案" text="批准合作申请后，在这里维护配额和内容归属。" />
      )}
      {state.data?.nextCursor ? (
        <button
          className="button ghost load-more"
          onClick={async () => {
            const next = await api.adminPartnerProfiles({
              ...filters,
              cursor: state.data?.nextCursor ?? undefined,
            })
            state.setData((current) => ({ ...next, items: [...itemsOf(current), ...next.items] }))
          }}
        >
          加载更多
        </button>
      ) : null}
      <ConfirmationView value={confirmation} close={() => setConfirmation(null)} />
    </section>
  )
}
