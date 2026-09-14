import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useNavigationType } from 'react-router-dom'
import {
  ArrowUpRight,
  BookOpen,
  Compass,
  ClipboardCheck,
  Feather,
  Home,
  Info,
  Menu,
  MessageSquare,
  Search,
  Users,
  X,
} from 'lucide-react'
import type { ContentKind } from '../lib/api'
import { ThemeButton } from './theme'

export const publicNav = [
  { path: '/', label: '首页', icon: Home },
  { path: '/competitions', label: '竞赛', icon: Compass, kind: 'COMPETITION' as const },
  { path: '/recruitments', label: '团队招募', icon: Users, kind: 'RECRUITMENT' as const },
  { path: '/resources', label: '学习资源', icon: BookOpen, kind: 'RESOURCE' as const },
  { path: '/submit', label: '投稿 / 反馈', icon: MessageSquare },
  { path: '/status', label: '进度查询', icon: ClipboardCheck },
  { path: '/join', label: '加入社群', icon: Feather },
  { path: '/about', label: '关于', icon: Info },
]

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
            搜索竞赛、团队招募与学习资源
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
            学习资源
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
  const key = location.pathname + location.search
  useLayoutEffect(() => {
    history.scrollRestoration = 'manual'
    const target = action === 'POP' ? (positions.get(key) ?? 0) : 0
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
  }, [key, action])
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
  const nav = (onNavigate?: () => void) => (
    <nav aria-label="主导航">
      {publicNav.map(({ path, label, icon: Icon, ...item }) => {
        const categoryActive = 'kind' in item && item.kind === detailKind
        return (
          <NavLink
            key={path}
            to={path}
            end={path === '/'}
            className={({ isActive }) => (isActive || categoryActive ? 'active' : undefined)}
            aria-label={label}
            onClick={onNavigate}
          >
            <Icon size={16} />
            <span>{label}</span>
          </NavLink>
        )
      })}
    </nav>
  )
  return (
    <div className="public-shell public-site-shell">
      <a className="skip" href="#main-content">
        跳至正文
      </a>
      <header className="public-header">
        <div className="public-header-inner">
          <Brand />
          <div className="desktop-public-nav">{nav()}</div>
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
        {nav(closeMenu)}
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
            <p>为求索者整理可靠的竞赛、招募与学习资源。官方规则以来源链接为准。</p>
          </div>
          <nav className="footer-links" aria-label="页脚导航">
            <FooterColumn
              title="浏览"
              links={[
                { label: '竞赛', path: '/competitions' },
                { label: '团队招募', path: '/recruitments' },
                { label: '学习资源', path: '/resources' },
              ]}
            />
            <FooterColumn
              title="参与"
              links={[
                { label: '投稿 / 反馈', path: '/submit' },
                { label: '进度查询', path: '/status' },
                { label: '加入社群', path: '/join' },
                { label: '合作方申请', path: '/partners/apply' },
              ]}
            />
            <FooterColumn
              title="了解"
              links={[
                { label: '关于 VOIDHAVEN', path: '/about' },
                { label: '受控工作台', path: '/auth/sign-in' },
              ]}
            />
          </nav>
          <div className="footer-bottom">
            <span>© {new Date().getFullYear()} VOIDHAVEN</span>
            <span>A HAVEN FOR SEEKERS</span>
          </div>
        </footer>
      </div>
    </div>
  )
}
