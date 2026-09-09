import { useCallback, useEffect, useRef, useState, type MouseEvent, type ReactNode } from 'react'
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
import { api, type Identity, type ManagedContent, type ProvisionedPartner } from '../lib/api'
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
        ...(isSuperAdmin ? [{ id: 'accounts', label: '账号治理', icon: UserCog }] : []),
        { id: 'audit', label: '审计记录', icon: ScrollText },
      ]
    : [
        { id: 'overview', label: '概览', icon: LayoutDashboard },
        { id: 'content', label: '我的内容', icon: FileText },
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
        {editorRoute ? (
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
  return (
    <ContentEditor
      editing={editing}
      onClose={close}
      onDirtyChange={onDirtyChange}
      onSave={async (draft) => {
        const result = isAdmin
          ? await api.adminRevise(editing.id, editing.revision, draft)
          : await api.partnerRevise(editing.id, editing.revision, draft)
        savedMessage.current = `“${result.title}”已保存。`
      }}
      onSaveAndSubmit={
        !isAdmin
          ? async (draft) => {
              const result = await api.partnerRevise(editing.id, editing.revision, draft)
              await api.partnerSubmit(result.id)
              savedMessage.current = `“${result.title}”已保存并送审。`
            }
          : undefined
      }
    />
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
    </section>
  )
}
function AdminContent({ section }: { section: string }) {
  const [filters, setFilters] = useState({ query: '', status: '', module: '' })
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
