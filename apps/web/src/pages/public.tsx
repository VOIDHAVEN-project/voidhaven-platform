import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Link,
  useLocation,
  useNavigate,
  useOutletContext,
  useParams,
  useSearchParams,
} from 'react-router-dom'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  CalendarDays,
  Check,
  Compass,
  Copy,
  Feather,
  Search,
  Share2,
  ShieldCheck,
  Users,
  X,
} from 'lucide-react'
import {
  api,
  categories,
  contentKinds,
  type ContentKind,
  type ContentModule,
  type PublicContent,
  type PublicAction,
  type SearchGroup,
} from '../lib/api'
import { copyText } from '../lib/copy-text'
import { usePageTitle, useResource } from '../lib/hooks'
import { ControlledCommunityImage } from '../components/controlled-community-image'
import { EmptyState, ErrorState, LoadingRows, Notice, message, type NoticeValue } from '../components/ui'
import { DocumentView, extractDocumentHeadings, safeUrl, type DocumentHeading } from '../components/document'
import type { PublicShellContext } from '../components/shell'

export function deadline(item: PublicContent) {
  const value = item.details.deadlineDate ?? item.details.deadlineAt
  return typeof value === 'string' ? value : null
}

function dateLabel(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
}

export type DeadlineState = 'active' | 'expired' | 'unknown'
export function deadlineState(item: PublicContent, now = Date.now()): DeadlineState {
  const value = deadline(item)
  if (!value || item.kind === 'RESOURCE') return 'unknown'
  const time = new Date(value.length === 10 ? value + 'T23:59:59+08:00' : value).getTime()
  if (Number.isNaN(time)) return 'unknown'
  return time < now ? 'expired' : 'active'
}

function expired(item: PublicContent) {
  return deadlineState(item) === 'expired'
}

export function ContentCard({
  item,
  featured = false,
  index = 0,
}: {
  item: PublicContent
  featured?: boolean
  index?: number
}) {
  const location = useLocation()
  const category = categories[item.kind]
  const date = deadline(item)
  return (
    <article className={`editorial-card ${featured ? 'featured-story' : ''}`}>
      <div className="story-top">
        <span>{item.moduleKey === 'resource.article' ? '文章 / 经验' : category.label}</span>
        <span>{featured ? 'IN FOCUS' : String(index + 1).padStart(2, '0')}</span>
      </div>
      {featured ? (
        <div className="orbit-art" aria-hidden="true">
          <i />
          <i />
          <i />
          <span>
            Find your
            <br />
            <em>next beginning.</em>
          </span>
        </div>
      ) : null}
      <div className="story-copy">
        <h3>
          <Link
            to={`/content/${item.id}`}
            state={{ from: location.pathname + location.search, kind: item.kind }}
          >
            {item.title}
          </Link>
        </h3>
        <p>{item.summary}</p>
        <div className="story-meta">
          <span>
            {date ? (
              <>
                <CalendarDays size={13} />
                {dateLabel(date)}
                {expired(item) ? ' · 已截止' : ''}
              </>
            ) : (
              <>
                <BookOpen size={13} />
                {item.languageLabel ?? '中文导读'}
              </>
            )}
          </span>
          <ArrowUpRight size={20} />
        </div>
        <small>{item.sourceName ?? '来源详见正文'}</small>
      </div>
    </article>
  )
}

const principles = [
  { number: '01', title: '发现线索', text: '从竞赛、资源和真实的团队计划中，找到值得继续核对的方向。' },
  { number: '02', title: '核对来源', text: '保留官方或原始入口，明确截止日期、作者和信息边界。' },
  { number: '03', title: '审核公开', text: '内容经过受控流程后公开，也为更正和下线保留清晰入口。' },
]

export function HomePage() {
  usePageTitle('求索者的公开目录')
  const state = useResource(api.home)
  const { openSearch } = useOutletContext<PublicShellContext>()
  return (
    <>
      <section className="home-intro product-hero">
        <p className="eyebrow">A LITTLE CURIOSITY GOES A LONG WAY</p>
        <h1>
          让好奇心，
          <br />
          找到<span>下一站。</span>
          <Feather size={37} strokeWidth={1} />
        </h1>
        <div className="hero-explainer">
          <p>为学生整理可靠的竞赛、团队招募与学习资源，帮助你判断下一步是否值得投入。</p>
          <div className="hero-actions">
            <Link className="button primary" to="/competitions">
              浏览近期机会 <ArrowRight size={16} />
            </Link>
            <button className="button ghost" onClick={openSearch}>
              <Search size={16} /> 搜索公开内容
            </button>
          </div>
        </div>
        <div className="intro-bottom hero-assurance">
          <span>来源可核对</span>
          <span>内容经审核</span>
          <span>支持反馈更正</span>
          <a className="round-link" href="#discover" aria-label="浏览本期内容">
            <ArrowDown size={20} />
          </a>
        </div>
      </section>

      <section id="discover" className="home-section home-focus-section">
        <SectionTitle label="01 / IN FOCUS" title="本期值得关注" path="/competitions" />
        {state.loading ? (
          <LoadingRows />
        ) : state.error ? (
          <ErrorState error={state.error} retry={state.reload} />
        ) : state.data?.competitions?.length ? (
          <>
            <div className="focus-grid">
              <ContentCard item={state.data.competitions[0]} featured />
              <aside className="editor-note">
                <span className="eyebrow">A NOTE FROM VOIDHAVEN</span>
                <span className="note-asterisk">✳</span>
                <h3>
                  先看清方向，
                  <br />
                  再认真出发。
                </h3>
                <p>核对时间，阅读规则，了解投入。不必追赶所有机会，只需找到适合自己的那一个。</p>
                <Link to="/about">
                  我们的编辑原则 <ArrowUpRight size={15} />
                </Link>
              </aside>
            </div>
            {state.data.competitions.length > 1 ? (
              <div className="story-grid compact-story-grid">
                {state.data.competitions.slice(1).map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index + 1} />
                ))}
              </div>
            ) : null}
          </>
        ) : (
          <EmptyState
            title="下一份机会，正在路上"
            text="当前没有公开竞赛。你可以先浏览学习资源，或提供可靠的线索。"
          />
        )}
      </section>

      <section className="home-section content-pillars-section">
        <SectionTitle label="02 / THREE WAYS FORWARD" title="从哪里开始" path="/about" />
        <div className="content-pillars">
          {(
            [
              { kind: 'COMPETITION', icon: Compass, title: '发现值得投入的机会', number: '01' },
              { kind: 'RECRUITMENT', icon: Users, title: '找到方向一致的同路人', number: '02' },
              { kind: 'RESOURCE', icon: BookOpen, title: '为下一步补齐方法与知识', number: '03' },
            ] as const
          ).map(({ kind, icon: Icon, title, number }) => (
            <Link key={kind} className="pillar-card" to={categories[kind].path}>
              <span>{number}</span>
              <Icon size={22} strokeWidth={1.25} />
              <p>{categories[kind].label}</p>
              <h3>{title}</h3>
              <ArrowUpRight size={17} />
            </Link>
          ))}
        </div>
      </section>

      <section className="principles-section">
        <div className="principles-intro">
          <p className="eyebrow">03 / HOW THE INDEX IS MADE</p>
          <h2>
            让每一条信息，
            <br />
            都保留可以核对的来处。
          </h2>
          <p>不替代官方规则，不制造虚假的热度。我们只把散落的线索，整理成更清晰的下一步。</p>
          <Link className="text-link" to="/about">
            了解平台边界 <ArrowRight size={15} />
          </Link>
        </div>
        <ol className="principle-steps">
          {principles.map((item) => (
            <li key={item.number}>
              <span>{item.number}</span>
              <div>
                <h3>{item.title}</h3>
                <p>{item.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {state.data && !state.error ? (
        <>
          <section className="home-section">
            <SectionTitle label="04 / BETTER, TOGETHER" title="最新团队招募" path="/recruitments" />
            {state.data.recruitments.length ? (
              <div className="story-grid recruitment-grid">
                {state.data.recruitments.map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index} />
                ))}
              </div>
            ) : (
              <EmptyState title="暂时没有公开招募" text="新的团队计划会在审核后出现在这里。" />
            )}
          </section>
          <section className="home-section">
            <SectionTitle label="05 / THE READING ROOM" title="给下一步，留一点准备" path="/resources" />
            {state.data.resources.length ? (
              <div className="resource-list">
                {state.data.resources.map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index} />
                ))}
              </div>
            ) : (
              <EmptyState title="书架正在整理中" text="欢迎提供有来源、值得阅读的学习资料。" />
            )}
          </section>
        </>
      ) : null}

      <section className="contribute-panel">
        <div>
          <p className="eyebrow">GOOD THINGS ARE BETTER SHARED</p>
          <h2>
            你的一条线索，
            <br />
            可能是别人的<span>起点。</span>
          </h2>
          <p>网站负责审核与展示，后续沟通由对应的官方或社群入口承接。</p>
        </div>
        <div>
          <Link className="button primary" to="/feedback">
            投稿 / 反馈 <ArrowUpRight size={16} />
          </Link>
          <Link className="text-link" to="/partners/apply">
            申请成为合作方 <ArrowRight size={16} />
          </Link>
        </div>
      </section>
    </>
  )
}

function SectionTitle({ label, title, path }: { label: string; title: string; path: string }) {
  return (
    <div className="section-heading">
      <div>
        <p className="eyebrow">{label}</p>
        <h2>{title}</h2>
      </div>
      <Link to={path}>
        查看全部 <ArrowUpRight size={15} />
      </Link>
    </div>
  )
}

export function CatalogPage({ kind }: { kind: ContentKind }) {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const moduleKey = params.get('module') ?? ''
  const filterKey = params.get('filterKey') ?? ''
  const filterValue = params.get('filterValue') ?? ''
  const cursor = params.get('cursor') ?? undefined
  const [input, setInput] = useState(query)
  const category = categories[kind]
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => setInput(query), [query, kind])
  usePageTitle(category.label)
  const modulesState = useResource(useCallback((signal?: AbortSignal) => api.publicModules(signal), []))
  const modules = (modulesState.data?.modules ?? []).filter(
    (module) => module.active && module.legacyKind === kind,
  )
  const selectedModule = modules.find((module) => module.key === moduleKey)
  const selectedField = selectedModule?.fields.find((field) => field.filterable && field.key === filterKey)
  const loader = useCallback(
    (signal?: AbortSignal) =>
      moduleKey
        ? api.listModule(moduleKey, { query, cursor, filterKey, filterValue }, signal)
        : api.list(kind, query, cursor, signal),
    [kind, query, cursor, moduleKey, filterKey, filterValue],
  )
  const state = useResource(loader)
  function updateParams(next: Record<string, string>) {
    const value = new URLSearchParams(params)
    value.delete('cursor')
    Object.entries(next).forEach(([key, entry]) => (entry ? value.set(key, entry) : value.delete(key)))
    setParams(value)
  }
  return (
    <>
      <header className="page-heading catalog-page-heading">
        <p className="eyebrow">{category.caption}</p>
        <h1>
          {category.label}
          <span className="heading-period">.</span>
        </h1>
        <p>{category.description}</p>
      </header>
      <form
        className="catalog-search"
        role="search"
        onSubmit={(event) => {
          event.preventDefault()
          updateParams({ q: input.trim() })
        }}
      >
        <Search size={19} />
        <label className="sr-only" htmlFor={`catalog-query-${kind}`}>
          搜索{category.label}
        </label>
        <input
          id={`catalog-query-${kind}`}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder={`搜索${category.label}、来源或关键词`}
        />
        {input || query ? (
          <button
            type="button"
            className="icon-button"
            aria-label="清空搜索"
            onClick={() => {
              setInput('')
              updateParams({ q: '' })
            }}
          >
            <X size={16} />
          </button>
        ) : null}
        <button className="button primary">搜索</button>
      </form>
      {modules.length > 1 ? (
        <div className="module-filter" aria-label={`${category.label}内容模块`}>
          <button
            className={!moduleKey ? 'active' : ''}
            onClick={() => updateParams({ module: '', filterKey: '', filterValue: '' })}
          >
            全部
          </button>
          {modules.map((module) => (
            <button
              key={module.key}
              className={moduleKey === module.key ? 'active' : ''}
              onClick={() => updateParams({ module: module.key, filterKey: '', filterValue: '' })}
            >
              {module.label}
            </button>
          ))}
        </div>
      ) : null}
      {selectedModule?.fields.some((field) => field.filterable && field.type !== 'tags') ? (
        <div className="catalog-field-filter">
          <label>
            筛选字段
            <select
              value={filterKey}
              onChange={(event) => updateParams({ filterKey: event.target.value, filterValue: '' })}
            >
              <option value="">不使用字段筛选</option>
              {selectedModule.fields
                .filter((field) => field.filterable && field.type !== 'tags')
                .map((field) => (
                  <option key={field.key} value={field.key}>
                    {field.label}
                  </option>
                ))}
            </select>
          </label>
          {selectedField ? (
            <label>
              精确值
              {selectedField.type === 'select' ? (
                <select
                  value={filterValue}
                  onChange={(event) => updateParams({ filterValue: event.target.value })}
                >
                  <option value="">请选择</option>
                  {selectedField.options?.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              ) : selectedField.type === 'boolean' ? (
                <select
                  value={filterValue}
                  onChange={(event) => updateParams({ filterValue: event.target.value })}
                >
                  <option value="">请选择</option>
                  <option value="true">是</option>
                  <option value="false">否</option>
                </select>
              ) : (
                <input
                  type={
                    selectedField.type === 'date'
                      ? 'date'
                      : selectedField.type === 'number'
                        ? 'number'
                        : 'text'
                  }
                  value={filterValue}
                  onChange={(event) => updateParams({ filterValue: event.target.value })}
                />
              )}
            </label>
          ) : null}
        </div>
      ) : null}
      <div className="catalog-caption">
        <span>{query ? `“${query}”的搜索结果` : '公开内容目录'}</span>
        <span>
          {state.loading
            ? '正在整理…'
            : state.error
              ? '连接待恢复'
              : `本页 ${state.data?.items.length ?? 0} 条`}
        </span>
      </div>
      {state.loading ? (
        <LoadingRows />
      ) : state.error ? (
        <ErrorState error={state.error} retry={state.reload} />
      ) : state.data?.items.length ? (
        <div className={kind === 'RESOURCE' ? 'resource-list' : 'story-grid'}>
          {state.data.items.map((item, index) => (
            <ContentCard key={item.id} item={item} index={index} />
          ))}
        </div>
      ) : (
        <EmptyState
          title={query ? '没有找到匹配内容' : '这个目录还在整理中'}
          text={query ? '试试更短的关键词，或清空搜索条件。' : '内容通过审核后会在此公开。'}
        />
      )}
      <div className="pagination">
        {cursor ? (
          <button
            className="button ghost"
            onClick={() => {
              if (location.state?.previousPage) navigate(location.state.previousPage)
              else updateParams({ cursor: '' })
            }}
          >
            <ArrowLeft size={15} />
            上一页
          </button>
        ) : null}
        {!state.loading && !state.error && state.data?.nextCursor ? (
          <Link
            className="button"
            to={`${category.path}?${new URLSearchParams({ ...(query ? { q: query } : {}), ...(moduleKey ? { module: moduleKey } : {}), ...(filterKey && filterValue ? { filterKey, filterValue } : {}), cursor: state.data.nextCursor })}`}
            state={{ previousPage: location.pathname + location.search }}
          >
            下一页 <ArrowRight size={15} />
          </Link>
        ) : null}
      </div>
      <p className="public-disclaimer">
        <ShieldCheck size={15} />
        保留原始来源。截止日期、资格和报名要求请以官方页面为准。
      </p>
    </>
  )
}

function emptySearchGroups(status: SearchGroup['status']): SearchGroup[] {
  return contentKinds.map((kind) => ({ kind, items: [], status }))
}

function useGroupedSearch(query: string) {
  const [groups, setGroups] = useState<SearchGroup[]>(() => emptySearchGroups('empty'))
  const [attempt, setAttempt] = useState(0)
  const retries = useRef(new Map<ContentKind, AbortController>())
  useEffect(() => {
    retries.current.forEach((controller) => controller.abort())
    retries.current.clear()
    if (!query) {
      setGroups(emptySearchGroups('empty'))
      return
    }
    let active = true
    const controller = new AbortController()
    setGroups(emptySearchGroups('loading'))
    api.searchAll(query, controller.signal).then((results) => {
      if (active) setGroups(results)
    })
    return () => {
      active = false
      controller.abort()
      retries.current.forEach((retryController) => retryController.abort())
      retries.current.clear()
    }
  }, [query, attempt])
  const retry = useCallback(
    (kind: ContentKind) => {
      if (!query) return
      retries.current.get(kind)?.abort()
      const controller = new AbortController()
      retries.current.set(kind, controller)
      setGroups((current) =>
        current.map((group) =>
          group.kind === kind ? { ...group, status: 'loading', error: undefined } : group,
        ),
      )
      api
        .searchKind(kind, query, controller.signal)
        .then((result) =>
          setGroups((current) =>
            current.map((group) =>
              group.kind === kind
                ? {
                    kind,
                    items: result.items,
                    status: result.items.length ? 'success' : 'empty',
                  }
                : group,
            ),
          ),
        )
        .catch((error) => {
          if (error instanceof DOMException && error.name === 'AbortError') return
          setGroups((current) =>
            current.map((group) =>
              group.kind === kind
                ? {
                    kind,
                    items: [],
                    status: 'error',
                    error: message(error),
                  }
                : group,
            ),
          )
        })
    },
    [query],
  )
  return { groups, retry, reload: () => setAttempt((value) => value + 1) }
}

function SearchResultRow({ item }: { item: PublicContent }) {
  const location = useLocation()
  const date = deadline(item)
  return (
    <article className="search-result-row">
      <div>
        <span>{item.moduleKey === 'resource.article' ? '文章 / 经验' : categories[item.kind].label}</span>
        {date ? (
          <span>
            {dateLabel(date)}
            {expired(item) ? ' · 已截止' : ''}
          </span>
        ) : null}
      </div>
      <h3>
        <Link
          to={`/content/${item.id}`}
          state={{ from: location.pathname + location.search, kind: item.kind }}
        >
          {item.title}
        </Link>
      </h3>
      <p>{item.summary}</p>
      <small>{item.sourceName ?? '来源详见正文'}</small>
    </article>
  )
}

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q')?.trim() ?? ''
  const [input, setInput] = useState(query)
  const { groups, retry, reload } = useGroupedSearch(query)
  useEffect(() => setInput(query), [query])
  usePageTitle(query ? `搜索：${query}` : '搜索公开内容')
  const allFailed = query && groups.every((group) => group.status === 'error')
  return (
    <>
      <header className="page-heading search-page-heading">
        <p className="eyebrow">SEARCH THE PUBLIC INDEX</p>
        <h1>
          找到更清晰的<span>下一步。</span>
        </h1>
        <p>同时检索竞赛、团队招募与学习资源；每一条结果仍保留原始来源。</p>
      </header>
      <form
        className="catalog-search search-page-form"
        role="search"
        onSubmit={(event) => {
          event.preventDefault()
          const value = input.trim()
          setParams(value ? { q: value } : {})
        }}
      >
        <Search size={19} />
        <label className="sr-only" htmlFor="search-page-query">
          搜索全部公开内容
        </label>
        <input
          id="search-page-query"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="输入标题、来源或关键词"
        />
        {input || query ? (
          <button
            type="button"
            className="icon-button"
            aria-label="清空搜索"
            onClick={() => {
              setInput('')
              setParams({})
            }}
          >
            <X size={16} />
          </button>
        ) : null}
        <button className="button primary">搜索</button>
      </form>
      {!query ? (
        <EmptyState
          title="输入一个关键词"
          text="我们会分别查询竞赛、团队招募与学习资源，不会把当前页面筛选冒充全站结果。"
        />
      ) : allFailed ? (
        <ErrorState error="三类公开内容暂时都无法检索，请确认本地内容服务已启动。" retry={reload} />
      ) : (
        <div className="grouped-search-results" aria-live="polite">
          <p className="search-result-summary">“{query}”的当前返回结果</p>
          {groups.map((group) => (
            <section
              key={group.kind}
              className="search-result-group"
              aria-labelledby={`search-${group.kind}`}
            >
              <div className="search-group-heading">
                <div>
                  <p className="eyebrow">{categories[group.kind].caption}</p>
                  <h2 id={`search-${group.kind}`}>{categories[group.kind].label}</h2>
                </div>
                <Link to={`${categories[group.kind].path}?${new URLSearchParams({ q: query })}`}>
                  查看该类全部结果 <ArrowUpRight size={14} />
                </Link>
              </div>
              {group.status === 'loading' ? (
                <LoadingRows />
              ) : group.status === 'error' ? (
                <ErrorState error={group.error ?? '该分类暂时无法检索。'} retry={() => retry(group.kind)} />
              ) : group.status === 'empty' ? (
                <EmptyState
                  title={`没有匹配的${categories[group.kind].label}`}
                  text="其他分类仍可能有相关内容。"
                />
              ) : (
                <div className="search-result-list">
                  {group.items.map((item) => (
                    <SearchResultRow key={item.id} item={item} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}
    </>
  )
}

function valueAsString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

const resourceTypes: Record<string, string> = {
  ARTICLE: '文章 / 经验',
  GUIDE: '指南',
  COURSE: '课程',
  TOOL: '工具',
  VIDEO: '视频',
  OTHER: '其他资源',
}

function Outline({
  headings,
  active,
  mobile = false,
}: {
  headings: DocumentHeading[]
  active: string
  mobile?: boolean
}) {
  const links = (
    <nav className="article-outline" aria-label="本文目录">
      {headings.map((heading) => (
        <a
          key={heading.id}
          href={`#${heading.id}`}
          className={`${heading.level > 1 ? 'nested' : ''} ${active === heading.id ? 'active' : ''}`}
        >
          {heading.label}
        </a>
      ))}
    </nav>
  )
  return mobile ? (
    <details className="mobile-outline">
      <summary>本文目录</summary>
      {links}
    </details>
  ) : (
    links
  )
}

function RelatedLinks({ items }: { items: PublicContent[] }) {
  const location = useLocation()
  return (
    <div className="related-links">
      {items.map((item, index) => (
        <Link
          key={item.id}
          to={`/content/${item.id}`}
          state={{ from: location.pathname + location.search, kind: item.kind }}
        >
          <span>{String(index + 1).padStart(2, '0')}</span>
          <strong>{item.title}</strong>
          <ArrowRight size={14} />
        </Link>
      ))}
    </div>
  )
}

function RecruitmentActions({ item }: { item: PublicContent }) {
  const [inviteCode, setInviteCode] = useState('')
  const [grantToken, setGrantToken] = useState('')
  const [gated, setGated] = useState<PublicAction[]>([])
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  if (item.kind !== 'RECRUITMENT') return null
  const open = (item.operationalStatus ?? 'OPEN') === 'OPEN' && !expired(item)
  const actions = [...(item.actions ?? []), ...gated]
  const verify = async () => {
    if (!inviteCode.trim()) return
    setBusy(true)
    setNotice(null)
    try {
      const grant = await api.verifyCommunityInvite(inviteCode.trim())
      setGrantToken(grant.grant)
      setGated(await api.gatedActions(item.id, grant.grant))
      setInviteCode('')
      setNotice({ tone: 'success', text: '验证成功，受控联系方式已在当前页面临时显示。' })
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }
  return (
    <section className="recruitment-actions" aria-labelledby="recruitment-actions-title">
      <p className="eyebrow">TAKE THE NEXT STEP</p>
      <h2 id="recruitment-actions-title">下一步怎么做</h2>
      {!open ? (
        <p className="muted">本次招募已暂停、截止或结束，行动入口不再开放；页面保留为历史记录。</p>
      ) : (
        <>
          <div className="button-row">
            {actions.map((action) => (
              <RecruitmentAction key={action.id} action={action} grant={grantToken} />
            ))}
          </div>
          <div className="community-unlock">
            <label>
              查看微信、群号或二维码等受控入口
              <input
                value={inviteCode}
                onChange={(event) => setInviteCode(event.target.value)}
                maxLength={256}
                autoComplete="off"
                placeholder="输入邀请码"
              />
            </label>
            <button
              className="button ghost"
              type="button"
              disabled={busy || !inviteCode.trim()}
              onClick={() => void verify()}
            >
              {busy ? '验证中…' : '验证并显示'}
            </button>
          </div>
          <Notice value={notice} />
        </>
      )}
    </section>
  )
}

function RecruitmentAction({ action, grant }: { action: PublicAction; grant?: string }) {
  if (action.mediaUrl && grant) {
    return (
      <figure className="recruitment-qr">
        <figcaption>{action.label}</figcaption>
        <ControlledCommunityImage path={action.mediaUrl} grant={grant} alt={`${action.label}二维码`} />
      </figure>
    )
  }
  if (action.mediaUrl) return <span className="button ghost">{action.label}（验证邀请码后显示二维码）</span>
  if (!action.value) return null
  const href = action.actionType === 'PUBLIC_EMAIL' ? `mailto:${action.value}` : safeUrl(action.value)
  return href ? (
    <a
      className="button primary"
      href={href}
      target={href.startsWith('http') ? '_blank' : undefined}
      rel="noreferrer"
    >
      {action.label} <ArrowUpRight size={14} />
    </a>
  ) : (
    <button className="button ghost" type="button" onClick={() => void copyText(action.value ?? '')}>
      {action.label}：{action.value}
    </button>
  )
}

function DetailQuickFacts({
  item,
  module,
  placement,
}: {
  item: PublicContent
  module?: ContentModule
  placement: 'mobile' | 'desktop'
}) {
  const date = deadline(item)
  const status = deadlineState(item)
  const source = safeUrl(item.sourceUrl)
  const rules = safeUrl(item.details.officialRulesUrl)
  const follow = safeUrl(item.details.followUpUrl)
  const relatedCompetition = valueAsString(item.details.relatedCompetitionId)
  const resourceType = valueAsString(item.details.resourceType)
  const audience = valueAsString(item.details.audience)
  return (
    <section className="quick-facts" aria-labelledby={`quick-facts-title-${placement}`}>
      <p className="eyebrow">AT A GLANCE</p>
      <h2 id={`quick-facts-title-${placement}`}>关键信息</h2>
      <dl>
        {item.kind !== 'RESOURCE' ? (
          <>
            <dt>当前状态</dt>
            <dd>
              <span className={`deadline-status ${status}`}>
                {status === 'active' ? '进行中' : status === 'expired' ? '已截止' : '以来源为准'}
              </span>
            </dd>
          </>
        ) : null}
        {date ? (
          <>
            <dt>截止日期</dt>
            <dd>{dateLabel(date)}</dd>
          </>
        ) : null}
        {item.kind === 'RESOURCE' ? (
          <>
            <dt>资源类型</dt>
            <dd>{resourceType ? (resourceTypes[resourceType] ?? resourceType) : '未标注'}</dd>
            <dt>适用人群</dt>
            <dd>{audience ?? '未标注'}</dd>
            <dt>内容语言</dt>
            <dd>{item.languageLabel ?? item.languageCode}</dd>
          </>
        ) : null}
        {module?.fields
          .filter(
            (field) =>
              ![
                'officialRulesUrl',
                'deadlineDate',
                'followUpUrl',
                'relatedCompetitionId',
                'resourceType',
                'audience',
              ].includes(field.key) && item.details[field.key] !== undefined,
          )
          .map((field) => (
            <Fragment key={field.key}>
              <dt>{field.label}</dt>
              <dd>
                {typeof item.details[field.key] === 'boolean'
                  ? item.details[field.key]
                    ? '是'
                    : '否'
                  : Array.isArray(item.details[field.key])
                    ? (item.details[field.key] as unknown[]).join('、')
                    : String(item.details[field.key])}
              </dd>
            </Fragment>
          ))}
      </dl>
      {rules ? (
        <a className="button primary" href={rules} target="_blank" rel="noreferrer">
          阅读官方规则 <ArrowUpRight size={15} />
        </a>
      ) : null}
      {follow && !(item.actions && item.actions.length) ? (
        <a className="button primary" href={follow} target="_blank" rel="noreferrer">
          了解后续入口 <ArrowUpRight size={15} />
        </a>
      ) : null}
      {relatedCompetition ? (
        <Link className="button" to={`/content/${encodeURIComponent(relatedCompetition)}`}>
          查看关联竞赛 <ArrowRight size={15} />
        </Link>
      ) : null}
      {source ? (
        <a className="text-link" href={source} target="_blank" rel="noreferrer">
          查看原始来源 <ArrowUpRight size={13} />
        </a>
      ) : null}
      <p className="quick-facts-note">
        {item.kind === 'COMPETITION'
          ? '资格、时间和提交要求以官方规则为准。'
          : item.kind === 'RECRUITMENT'
            ? '本站负责展示信息，后续沟通由对应社群或来源入口承接。'
            : '资源信息保留原始来源，便于继续核对与学习。'}
      </p>
    </section>
  )
}

function Provenance({ item }: { item: PublicContent }) {
  const source = safeUrl(item.sourceUrl)
  return (
    <section className="provenance-panel" aria-labelledby="provenance-title">
      <div>
        <p className="eyebrow">THE SOURCE NOTES</p>
        <h2 id="provenance-title">来源与说明</h2>
      </div>
      <dl>
        {item.originalTitle ? (
          <>
            <dt>原始标题</dt>
            <dd>{item.originalTitle}</dd>
          </>
        ) : null}
        <dt>官方或原始来源</dt>
        <dd>
          {source ? (
            <a href={source} target="_blank" rel="noreferrer">
              {item.sourceName || '查看来源'} <ArrowUpRight size={13} />
            </a>
          ) : (
            item.sourceName || '原创内容'
          )}
        </dd>
        <dt>最近来源核验</dt>
        <dd>
          {item.verification
            ? `${item.verification.outcome === 'VALID' ? '有效' : item.verification.outcome === 'INVALID' ? '已标记失效' : '待复核'} · ${new Date(item.verification.checkedAt).toLocaleDateString()}`
            : '尚无独立核验记录，请以原始来源为准'}
        </dd>
        <dt>作者展示名</dt>
        <dd>{item.authorDisplayName || '未提供'}</dd>
        <dt>权利来源 / 使用说明</dt>
        <dd>{item.rightsBasis}</dd>
        <dt>署名转载</dt>
        <dd>{item.reprintPermission === 'ATTRIBUTION_ALLOWED' ? '允许署名使用' : '不允许'}</dd>
        <dt>署名改编</dt>
        <dd>{item.adaptationPermission === 'ATTRIBUTION_ALLOWED' ? '允许署名使用' : '不允许'}</dd>
      </dl>
    </section>
  )
}

export function DetailPage() {
  const { publicId = '' } = useParams()
  const loader = useCallback((signal?: AbortSignal) => api.detail(publicId, signal), [publicId])
  const state = useResource(loader)
  usePageTitle(state.data?.title ?? '内容详情', state.data?.summary)
  if (state.loading)
    return (
      <div className="detail-loading">
        <LoadingRows />
      </div>
    )
  if (state.error || !state.data)
    return <ErrorState error={state.error || '内容不存在。'} retry={state.reload} />
  return <DetailContent item={state.data} />
}

function DetailContent({ item }: { item: PublicContent }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { setDetailKind } = useOutletContext<PublicShellContext>()
  const [notice, setNotice] = useState<NoticeValue>(null)
  const article = useRef<HTMLElement>(null)
  const [progress, setProgress] = useState(0)
  const modulesState = useResource(useCallback((signal?: AbortSignal) => api.publicModules(signal), []))
  const module = modulesState.data?.modules.find((value) => value.key === item.moduleKey)
  const headings = useMemo(() => extractDocumentHeadings(item.body), [item.body])
  const [activeHeading, setActiveHeading] = useState(headings[0]?.id ?? '')
  const relatedLoader = useCallback((signal?: AbortSignal) => api.related(item.id, signal), [item.id])
  const related = useResource(relatedLoader)
  const relatedItems = useMemo(
    () => related.data?.items?.filter((entry) => entry.id !== item.id).slice(0, 4) ?? [],
    [related.data, item.id],
  )
  const isLong = headings.length >= 3 || item.body.blocks.length >= 10
  const showRelatedRail = isLong && relatedItems.length >= 2
  useEffect(() => {
    setDetailKind(item.kind)
    return () => setDetailKind(null)
  }, [item.kind, setDetailKind])
  useEffect(() => {
    const update = () => {
      const rect = article.current?.getBoundingClientRect()
      if (rect) setProgress(Math.max(0, Math.min(1, -rect.top / Math.max(1, rect.height - innerHeight))))
    }
    const resize = new ResizeObserver(update)
    if (article.current) resize.observe(article.current)
    window.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    update()
    return () => {
      resize.disconnect()
      window.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
    }
  }, [item])
  useEffect(() => {
    if (headings.length < 3) return
    const nodes = headings
      .map((heading) => document.getElementById(heading.id))
      .filter((node): node is HTMLElement => Boolean(node))
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
        if (visible[0]?.target.id) setActiveHeading(visible[0].target.id)
      },
      { rootMargin: '-18% 0px -68% 0px' },
    )
    nodes.forEach((node) => observer.observe(node))
    return () => observer.disconnect()
  }, [headings])
  const back = location.state?.from
  const fallback = categories[item.kind].path
  const copy = async () => {
    try {
      const shareText = [item.title, item.summary, date ? `截止：${dateLabel(date)}` : '', locationOrigin()]
        .filter(Boolean)
        .join('\n')
      await copyText(shareText)
      setNotice({ tone: 'success', text: '标题、导读、截止信息和链接已复制，可直接粘贴分享。' })
    } catch {
      setNotice({ tone: 'error', text: '未能自动复制，请从浏览器地址栏复制链接。' })
    }
  }
  const share = async () => {
    if (!navigator.share) return copy()
    try {
      await navigator.share({ title: item.title, text: item.summary, url: locationOrigin() })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      await copy()
    }
  }
  const date = deadline(item)
  return (
    <article ref={article} className="detail-article document-detail">
      <div className="reading-progress" aria-hidden="true">
        <span style={{ transform: `scaleX(${progress})` }} />
      </div>
      <nav className="breadcrumbs" aria-label="面包屑">
        <Link to="/">首页</Link>
        <span>/</span>
        <Link to={fallback}>{categories[item.kind].label}</Link>
        <span>/</span>
        <span aria-current="page">{item.title}</span>
      </nav>
      {back ? (
        <button className="text-link back-link" onClick={() => navigate(-1)}>
          <ArrowLeft size={15} />
          返回浏览目录
        </button>
      ) : (
        <Link className="text-link back-link" to={fallback}>
          <ArrowLeft size={15} />
          返回{categories[item.kind].label}
        </Link>
      )}
      <header className="detail-heading">
        <p className="detail-kicker">
          <span className="eyebrow">{module?.label ?? categories[item.kind].caption}</span>
          <span aria-hidden="true">·</span>
          <span>{item.languageLabel ?? item.languageCode}</span>
        </p>
        <h1>{item.title}</h1>
        <p className="detail-lead">{item.summary}</p>
        <div className="detail-meta">
          <span>
            {item.authorDisplayName || 'VOIDHAVEN'}{' '}
            {date ? ` · ${dateLabel(date)}${expired(item) ? ' · 已截止' : ''}` : ''}
          </span>
          <div>
            <button
              className="icon-button"
              title="复制链接"
              aria-label="复制链接"
              onClick={() => void copy()}
            >
              <Copy size={16} />
            </button>
            <button
              className="icon-button"
              title="分享内容"
              aria-label="分享内容"
              onClick={() => void share()}
            >
              <Share2 size={16} />
            </button>
          </div>
        </div>
        <Notice value={notice} />
      </header>
      <RecruitmentActions item={item} />
      <div className={`detail-document-grid ${showRelatedRail ? 'has-related-rail' : ''}`}>
        {showRelatedRail ? (
          <aside className="related-rail">
            <p className="eyebrow">MORE IN THIS INDEX</p>
            <h2>同类内容</h2>
            <RelatedLinks items={relatedItems} />
          </aside>
        ) : null}
        <section className="article-reading" aria-label="正文">
          <div className="detail-context-mobile">
            <DetailQuickFacts item={item} module={module} placement="mobile" />
            {headings.length >= 3 ? <Outline headings={headings} active={activeHeading} mobile /> : null}
          </div>
          <DocumentView body={item.body} media={item.media} />
          <aside className="official-note">
            <ShieldCheck size={20} />
            <div>
              <strong>
                {item.kind === 'RECRUITMENT' ? '信息展示，止于可靠的入口' : '阅读导读，也请核对原始来源'}
              </strong>
              <p>
                {item.kind === 'RECRUITMENT'
                  ? '后续沟通、筛选和组队在对应社群或来源中完成，本站不保存这些过程记录。'
                  : '本站说明帮助理解和发现机会；资格、时间与规则以官方或原始来源为准。'}
              </p>
            </div>
          </aside>
          <Provenance item={item} />
          <Link
            className="text-link feedback-content-link"
            to={`/feedback?link=${encodeURIComponent(locationOrigin())}`}
          >
            反馈这条信息 <ArrowUpRight size={13} />
          </Link>
          {related.loading ? null : related.error ? (
            <div className="related-error">
              <span>同类内容暂时不可用。</span>
              <button className="text-link" onClick={related.reload}>
                重试
              </button>
            </div>
          ) : relatedItems.length ? (
            <section className="related-at-end">
              <p className="eyebrow">KEEP EXPLORING</p>
              <h2>继续发现</h2>
              <RelatedLinks items={relatedItems.slice(0, 3)} />
            </section>
          ) : null}
        </section>
        <aside className="detail-context-desktop">
          <DetailQuickFacts item={item} module={module} placement="desktop" />
          {headings.length >= 3 ? (
            <section className="outline-panel">
              <p className="eyebrow">ON THIS PAGE</p>
              <h2>本文目录</h2>
              <Outline headings={headings} active={activeHeading} />
            </section>
          ) : null}
        </aside>
      </div>
      <div className="article-end">
        <Check size={16} />
        <span>感谢认真读到这里。</span>
        <Link to={fallback}>
          继续发现 <ArrowRight size={15} />
        </Link>
      </div>
    </article>
  )
}

function locationOrigin() {
  return window.location.origin + window.location.pathname
}

export function AboutPage() {
  usePageTitle('关于')
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">A NOTE ABOUT THIS PLACE</p>
        <h1>
          更少的信息差。
          <br />
          <span>更清晰的下一步。</span>
        </h1>
        <p>VOIDHAVEN，为仍然保持好奇的求索者而建。</p>
      </header>
      <div className="about-grid">
        <div className="about-prose">
          <h2>让值得的事，被看见。</h2>
          <p>
            我们服务本校学生，重点解决发现竞赛、理解规则、找到团队与学习机会时的信息分散问题。把散落的线索整理在一起，让一次偶然发现，变成一次认真开始。
          </p>
          <h2>我们如何整理信息</h2>
          <p>
            网站负责展示、审核和沉淀信息；竞赛页面保留官方来源，站内说明只作辅助理解。社群负责后续沟通、筛选和组队。
          </p>
          <h2>清楚地知道边界</h2>
          <p>
            V0
            不建立学生账户、简历、报名流程、私信、聊天、评论或复杂推荐，也不会保存社群沟通记录。我们相信，先把一件小事做好，就有意义。
          </p>
          <Link className="button primary" to="/feedback">
            提供线索或反馈 <ArrowUpRight size={15} />
          </Link>
        </div>
        <aside className="about-note">
          <Feather size={36} strokeWidth={1} />
          <blockquote>
            Stay curious.
            <br />
            <em>Keep going.</em>
          </blockquote>
          <p>
            愿每一份探索，
            <br />
            都有一个温柔而可靠的起点。
          </p>
          <span>— VOIDHAVEN</span>
        </aside>
      </div>
    </>
  )
}

export function NotFoundPage() {
  usePageTitle('页面未找到')
  return (
    <section className="not-found">
      <p className="eyebrow">A SMALL DETOUR · 404</p>
      <span className="not-found-number">404</span>
      <h1>这条小径，暂时没有内容。</h1>
      <p>页面可能不存在或已下线，回到目录继续发现吧。</p>
      <Link to="/" className="button primary">
        返回首页 <ArrowRight size={16} />
      </Link>
    </section>
  )
}
