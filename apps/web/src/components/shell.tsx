import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
} from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useNavigationType } from 'react-router-dom'
import {
  ArrowUpRight,
  BookOpen,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  Compass,
  Feather,
  Handshake,
  Home,
  Info,
  Menu,
  MessageSquare,
  Search,
  UserPlus,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react'
import type { ContentKind } from '../lib/api'
import { useProductCapabilities } from '../lib/hooks'
import { siteBeian } from '../lib/site-meta'
import {
  isNavGroup,
  navLeafLinks,
  participationMenu,
  publicNavLinks,
  publicNavTrailingLink,
  type PublicNavLink,
} from '../lib/public-nav'
import { ThemeButton } from './theme'

export const navLinkIcons: Record<string, LucideIcon> = {
  '/': Home,
  '/competitions': Compass,
  '/recruitments': Users,
  '/resources': BookOpen,
  '/submit': MessageSquare,
  '/partners/apply': Handshake,
  '/status': ClipboardCheck,
  '/join/community': Feather,
  '/join/project': UserPlus,
  '/about': Info,
}

export type PublicShellContext = {
  openSearch: () => void
  setDetailKind: (kind: ContentKind | null) => void
}

export function Brand() {
  return (
    <Link className="brand-lockup" to="/" aria-label="VOIDHAVEN 首页">
      <span className="brand-monogram">
        V<span>h.</span>
      </span>
      <span className="brand-name">
        VOIDHAVEN<small>A HAVEN FOR SEEKERS</small>
      </span>
    </Link>
  )
}

export function MobileDrawer({
  open,
  onClose,
  children,
  closeAt = 1024,
}: {
  open: boolean
  onClose: () => void
  children: ReactNode
  closeAt?: number
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    const overflow = document.body.style.overflow
    dialog?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog?.close()
      document.body.style.overflow = overflow
      previous?.focus()
    }
  }, [open])
  useEffect(() => {
    const mq = matchMedia(`(min-width: ${closeAt}px)`)
    const close = () => {
      if (mq.matches) onClose()
    }
    mq.addEventListener('change', close)
    return () => mq.removeEventListener('change', close)
  }, [closeAt, onClose])
  if (!open) return null
  return (
    <dialog
      ref={ref}
      className="navigation-drawer"
      aria-label="导航菜单"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="drawer-inner">
        <button className="icon-button drawer-close" aria-label="关闭菜单" onClick={onClose}>
          <X size={19} />
        </button>
        {children}
      </div>
    </dialog>
  )
}

function NavLinkItem({
  link,
  detailKind,
  onNavigate,
}: {
  link: PublicNavLink
  detailKind: ContentKind | null
  onNavigate?: () => void
}) {
  const Icon = navLinkIcons[link.path] ?? Info
  const categoryActive = link.kind !== undefined && link.kind === detailKind
  return (
    <NavLink
      to={link.path}
      end={link.path === '/'}
      className={({ isActive }) => (isActive || categoryActive ? 'active' : undefined)}
      aria-label={link.label}
      onClick={onNavigate}
    >
      <Icon size={16} />
      <span>{link.label}</span>
    </NavLink>
  )
}

/**
 * 三层参与菜单：顶层展开面板，面板内“加入我们”再展开子项。
 * 桌面悬停只是增强，点击与键盘始终可用；父级按钮只负责展开，不触发跳转。
 */
function ParticipationMenu({ projectJoinEnabled }: { projectJoinEnabled: boolean }) {
  const [open, setOpen] = useState(false)
  const [subOpen, setSubOpen] = useState(false)
  const wrap = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const subTrigger = useRef<HTMLButtonElement>(null)
  const closeTimer = useRef<number | null>(null)
  const location = useLocation()

  const close = useCallback(() => {
    setOpen(false)
    setSubOpen(false)
  }, [])

  useEffect(() => {
    close()
  }, [location.pathname, location.search, close])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!wrap.current?.contains(event.target as Node)) close()
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open, close])

  useEffect(
    () => () => {
      if (closeTimer.current !== null) window.clearTimeout(closeTimer.current)
    },
    [],
  )

  const hoverCapable = () => window.matchMedia('(hover: hover) and (pointer: fine)').matches
  const openForHover = () => {
    if (!hoverCapable()) return
    if (closeTimer.current !== null) window.clearTimeout(closeTimer.current)
    setOpen(true)
  }
  const closeForHover = () => {
    if (!hoverCapable()) return
    if (closeTimer.current !== null) window.clearTimeout(closeTimer.current)
    closeTimer.current = window.setTimeout(close, 160)
  }

  const panelItems = () =>
    Array.from(
      wrap.current?.querySelectorAll<HTMLElement>(
        '.participation-panel a:not([hidden]), .participation-panel button:not([hidden])',
      ) ?? [],
    ).filter((element) => element.offsetParent !== null)

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
      trigger.current?.focus()
      return
    }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
    event.preventDefault()
    if (!open) {
      setOpen(true)
      window.requestAnimationFrame(() => panelItems()[0]?.focus())
      return
    }
    const items = panelItems()
    if (!items.length) return
    const current = items.indexOf(document.activeElement as HTMLElement)
    if (current === -1) {
      items[0].focus()
      return
    }
    const delta = event.key === 'ArrowDown' ? 1 : -1
    items[(current + delta + items.length) % items.length].focus()
  }

  return (
    <div
      className="participation-menu"
      ref={wrap}
      onMouseEnter={openForHover}
      onMouseLeave={closeForHover}
      onKeyDown={onKeyDown}
    >
      <button
        ref={trigger}
        type="button"
        className="participation-trigger"
        aria-expanded={open}
        aria-controls="participation-panel"
        onClick={() => (open ? close() : setOpen(true))}
      >
        <Users size={16} />
        <span>{participationMenu.label}</span>
      </button>
      <div
        id="participation-panel"
        className="participation-panel"
        role="group"
        aria-label={participationMenu.label}
        hidden={!open}
      >
        {participationMenu.children.map((node) =>
          isNavGroup(node) ? (
            <div key={node.label} className="participation-subgroup">
              <button
                ref={subTrigger}
                type="button"
                className="participation-subtrigger"
                aria-expanded={subOpen}
                aria-controls="participation-submenu"
                onClick={() => setSubOpen((value) => !value)}
                onKeyDown={(event) => {
                  if (event.key === 'ArrowRight') {
                    event.preventDefault()
                    setSubOpen(true)
                    window.requestAnimationFrame(() =>
                      wrap.current?.querySelector<HTMLElement>('#participation-submenu a')?.focus(),
                    )
                  }
                  if (event.key === 'ArrowLeft' && subOpen) {
                    event.preventDefault()
                    setSubOpen(false)
                  }
                }}
              >
                <span>{node.label}</span>
                <ChevronRight size={14} />
              </button>
              <div
                id="participation-submenu"
                className="participation-submenu"
                role="group"
                aria-label={node.label}
                hidden={!subOpen}
                onKeyDown={(event) => {
                  if (event.key !== 'ArrowLeft') return
                  event.preventDefault()
                  setSubOpen(false)
                  subTrigger.current?.focus()
                }}
              >
                {navLeafLinks(node.children)
                  .filter((leaf) => leaf.path !== '/join/project' || projectJoinEnabled)
                  .map((leaf) => (
                    <Link key={leaf.path} to={leaf.path} onClick={close}>
                      {leaf.label}
                    </Link>
                  ))}
              </div>
            </div>
          ) : (
            <Link key={node.path} to={node.path} onClick={close} aria-label={node.label}>
              {node.label}
            </Link>
          ),
        )}
      </div>
    </div>
  )
}

function DesktopNav({
  detailKind,
  projectJoinEnabled,
}: {
  detailKind: ContentKind | null
  projectJoinEnabled: boolean
}) {
  return (
    <nav aria-label="主导航">
      {publicNavLinks.map((link) => (
        <NavLinkItem key={link.path} link={link} detailKind={detailKind} />
      ))}
      <ParticipationMenu projectJoinEnabled={projectJoinEnabled} />
      <NavLinkItem link={publicNavTrailingLink} detailKind={detailKind} />
    </nav>
  )
}

function DrawerNav({
  detailKind,
  onNavigate,
  projectJoinEnabled,
}: {
  detailKind: ContentKind | null
  onNavigate: () => void
  projectJoinEnabled: boolean
}) {
  const [open, setOpen] = useState(false)
  const [subOpen, setSubOpen] = useState(false)
  return (
    <nav aria-label="主导航">
      {publicNavLinks.map((link) => (
        <NavLinkItem key={link.path} link={link} detailKind={detailKind} onNavigate={onNavigate} />
      ))}
      <div className="drawer-nav-group">
        <button
          type="button"
          className="drawer-nav-toggle"
          aria-expanded={open}
          aria-controls="drawer-participation"
          onClick={() => setOpen((value) => !value)}
        >
          <Users size={16} />
          <span>{participationMenu.label}</span>
          <ChevronDown size={15} />
        </button>
        <div id="drawer-participation" className="drawer-nav-panel" hidden={!open}>
          {participationMenu.children.map((node) =>
            isNavGroup(node) ? (
              <div key={node.label} className="drawer-nav-subgroup">
                <button
                  type="button"
                  className="drawer-nav-toggle sub"
                  aria-expanded={subOpen}
                  aria-controls="drawer-join-us"
                  onClick={() => setSubOpen((value) => !value)}
                >
                  <span>{node.label}</span>
                  <ChevronDown size={14} />
                </button>
                <div id="drawer-join-us" className="drawer-nav-panel sub" hidden={!subOpen}>
                  {navLeafLinks(node.children)
                    .filter((leaf) => leaf.path !== '/join/project' || projectJoinEnabled)
                    .map((leaf) => (
                      <NavLinkItem
                        key={leaf.path}
                        link={leaf}
                        detailKind={detailKind}
                        onNavigate={onNavigate}
                      />
                    ))}
                </div>
              </div>
            ) : (
              <NavLinkItem key={node.path} link={node} detailKind={detailKind} onNavigate={onNavigate} />
            ),
          )}
        </div>
      </div>
      <NavLinkItem link={publicNavTrailingLink} detailKind={detailKind} onNavigate={onNavigate} />
    </nav>
  )
}

function SearchDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const location = useLocation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    const overflow = document.body.style.overflow
    setQuery(new URLSearchParams(location.search).get('q') ?? '')
    dialog?.showModal()
    document.body.style.overflow = 'hidden'
    requestAnimationFrame(() => input.current?.focus())
    return () => {
      dialog?.close()
      document.body.style.overflow = overflow
      previous?.focus()
    }
  }, [open, location.search])
  if (!open) return null
  return (
    <dialog
      ref={ref}
      className="search-dialog"
      aria-labelledby="search-dialog-title"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="search-dialog-card">
        <div className="search-dialog-heading">
          <div>
            <p className="eyebrow">FIND YOUR NEXT STEP</p>
            <h2 id="search-dialog-title">搜索公开内容</h2>
          </div>
          <button className="icon-button" aria-label="关闭搜索" onClick={onClose}>
            <X size={18} />
          </button>
        </div>
        <form
          className="global-search-form"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            const value = query.trim()
            if (!value) return input.current?.focus()
            onClose()
            navigate(`/search?${new URLSearchParams({ q: value })}`)
          }}
        >
          <Search size={20} aria-hidden="true" />
          <label className="sr-only" htmlFor="global-search-query">
            搜索竞赛、团队招募与资源与分享
          </label>
          <input
            ref={input}
            id="global-search-query"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="输入标题、来源或关键词"
          />
          <button className="button primary">搜索</button>
        </form>
        <div className="search-shortcuts" aria-label="直接浏览分类">
          <Link to="/competitions" onClick={onClose}>
            竞赛
          </Link>
          <Link to="/recruitments" onClick={onClose}>
            团队招募
          </Link>
          <Link to="/resources" onClick={onClose}>
            资源与分享
          </Link>
          <span>Esc 关闭</span>
        </div>
      </div>
    </dialog>
  )
}

const positions = new Map<string, number>()
export function ScrollManager() {
  const location = useLocation()
  const action = useNavigationType()
  const key = location.pathname + location.search + location.hash
  useLayoutEffect(() => {
    history.scrollRestoration = 'manual'
    const anchorId = location.hash.replace(/^#/, '')
    const anchor = anchorId ? document.getElementById(anchorId) : null
    const target = anchor
      ? anchor.getBoundingClientRect().top + window.scrollY - 96
      : action === 'POP'
        ? (positions.get(key) ?? 0)
        : 0
    let stopped = false
    const restore = () => {
      if (!stopped) window.scrollTo({ top: target, behavior: 'instant' })
    }
    restore()
    const observer = new ResizeObserver(restore)
    observer.observe(document.body)
    const stop = () => {
      stopped = true
      observer.disconnect()
    }
    window.addEventListener('wheel', stop, { passive: true })
    window.addEventListener('touchstart', stop, { passive: true })
    window.addEventListener('keydown', stop)
    const timeout = window.setTimeout(stop, 4000)
    const save = () => positions.set(key, window.scrollY)
    window.addEventListener('scroll', save, { passive: true })
    return () => {
      clearTimeout(timeout)
      observer.disconnect()
      window.removeEventListener('wheel', stop)
      window.removeEventListener('touchstart', stop)
      window.removeEventListener('keydown', stop)
      window.removeEventListener('scroll', save)
    }
  }, [key, action, location.hash])
  return null
}

function FooterColumn({ title, links }: { title: string; links: { label: string; path: string }[] }) {
  return (
    <div className="footer-column">
      <p>{title}</p>
      {links.map((link) => (
        <Link key={link.path} to={link.path}>
          {link.label} <ArrowUpRight size={12} />
        </Link>
      ))}
    </div>
  )
}

export function PublicShell() {
  const [menu, setMenu] = useState(false)
  const [search, setSearch] = useState(false)
  const [detailKind, setDetailKind] = useState<ContentKind | null>(null)
  const { capabilities } = useProductCapabilities()
  const projectJoinEnabled = capabilities?.projectJoinEnabled === true
  const beian = siteBeian()
  const location = useLocation()
  const openSearch = useCallback(() => setSearch(true), [])
  const closeMenu = useCallback(() => setMenu(false), [])
  const closeSearch = useCallback(() => setSearch(false), [])
  useEffect(() => {
    if (!location.pathname.startsWith('/content/')) setDetailKind(null)
    setMenu(false)
    setSearch(false)
  }, [location.pathname])
  useEffect(() => {
    const open = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setSearch(true)
      }
    }
    window.addEventListener('keydown', open)
    return () => window.removeEventListener('keydown', open)
  }, [])
  const context = useMemo(() => ({ openSearch, setDetailKind }), [openSearch])
  return (
    <div className="public-shell public-site-shell">
      <a className="skip" href="#main-content">
        跳至正文
      </a>
      <header className="public-header">
        <div className="public-header-inner">
          <Brand />
          <div className="desktop-public-nav">
            <DesktopNav detailKind={detailKind} projectJoinEnabled={projectJoinEnabled} />
          </div>
          <div className="public-header-actions">
            <button className="header-search" onClick={openSearch} aria-label="搜索公开内容">
              <Search size={16} />
              <span>搜索竞赛、招募与资源</span>
              <kbd>Ctrl K</kbd>
            </button>
            <div className="header-theme">
              <ThemeButton />
            </div>
            <Link className="workspace-entry" to="/auth/sign-in">
              受控工作台 <ArrowUpRight size={14} />
            </Link>
            <button
              className="icon-button public-menu-button"
              aria-label="打开菜单"
              aria-expanded={menu}
              onClick={() => setMenu(true)}
            >
              <Menu size={19} />
            </button>
          </div>
        </div>
      </header>
      <MobileDrawer open={menu} onClose={closeMenu} closeAt={1180}>
        <Brand />
        <button
          className="drawer-search"
          onClick={() => {
            closeMenu()
            openSearch()
          }}
        >
          <Search size={17} /> 搜索公开内容
        </button>
        <DrawerNav detailKind={detailKind} onNavigate={closeMenu} projectJoinEnabled={projectJoinEnabled} />
        <div className="drawer-public-bottom">
          <ThemeButton />
          <Link className="workspace-entry" to="/auth/sign-in" onClick={closeMenu}>
            受控工作台 <ArrowUpRight size={14} />
          </Link>
        </div>
      </MobileDrawer>
      <SearchDialog open={search} onClose={closeSearch} />
      <div className="public-main">
        <main id="main-content" tabIndex={-1}>
          <Outlet context={context} />
        </main>
        <footer className="public-footer expanded-footer">
          <div className="footer-statement">
            <strong>
              Small discoveries.
              <br />
              <em>Meaningful beginnings.</em>
            </strong>
            <p>为求索者整理可靠的竞赛、招募与资源与分享。官方规则以来源链接为准。</p>
          </div>
          <nav className="footer-links" aria-label="页脚导航">
            <FooterColumn
              title="浏览"
              links={[
                { label: '竞赛资源', path: '/competitions' },
                { label: '团队招募', path: '/recruitments' },
                { label: '资源与分享', path: '/resources' },
              ]}
            />
            <FooterColumn
              title="参与"
              links={[
                { label: '投稿 / 反馈', path: '/submit' },
                { label: '进度查询', path: '/status' },
                { label: '加入社群', path: '/join/community' },
                { label: '合作申请', path: '/partners/apply' },
              ]}
            />
            <FooterColumn
              title="了解"
              links={[
                { label: '关于 VOIDHAVEN', path: '/about' },
                { label: '受控工作台', path: '/auth/sign-in' },
              ]}
            />
            <FooterColumn
              title="规则与联系"
              links={[
                { label: '隐私说明', path: '/privacy' },
                { label: '来源与核验', path: '/about#sources' },
                { label: '纠错与反馈', path: '/submit' },
                { label: '版权与下线', path: '/submit?type=COPYRIGHT' },
              ]}
            />
          </nav>
          <div className="footer-bottom">
            <span>© {new Date().getFullYear()} VOIDHAVEN</span>
            <span>A HAVEN FOR SEEKERS</span>
            {beian ? <span>{beian}</span> : null}
          </div>
        </footer>
      </div>
    </div>
  )
}
