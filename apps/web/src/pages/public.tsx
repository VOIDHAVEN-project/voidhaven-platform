import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useOutletContext, useSearchParams } from 'react-router-dom'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  CalendarDays,
  Compass,
  Feather,
  LayoutGrid,
  LayoutList,
  Search,
  ShieldCheck,
  Users,
  X,
} from 'lucide-react'
import {
  api,
  categories,
  contentKinds,
  type ContentKind,
  type PublicContent,
  type SearchGroup,
} from '../lib/api'
import { usePageTitle, useResource } from '../lib/hooks'
import { siteContactLinks } from '../lib/site-meta'
import { EmptyState, ErrorState, LoadingRows, message, statusText } from '../components/ui'
import { StatusFigure } from '../components/status-figures'
import type { PublicShellContext } from '../components/shell'
import {
  catalogFacts,
  catalogFilterFields,
  catalogHasActiveFilters,
  catalogSearchParams,
  catalogSortOptions,
  catalogStatusOptions,
  resolveCatalogView,
  writeStoredCatalogView,
  type CatalogView,
} from '../lib/catalog'
import { dateLabel, deadline, deadlineState } from '../lib/detail'
import { fieldOptionLabel } from '../lib/module-field-labels'

export { deadline, deadlineState }
export type { DeadlineState } from '../lib/detail'

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
  const status = item.operationalStatus
    ? (statusText[item.operationalStatus] ?? item.operationalStatus)
    : null
  const updated = item.updatedAt ? dateLabel(item.updatedAt) : null
  return (
    <article className={`editorial-card ${featured ? 'featured-story' : ''}`}>
      <div className="story-top">
        <span>
          {item.moduleKey === 'resource.article' ? '文章 / 经验' : category.label}
          {status ? ` · ${status}` : ''}
        </span>
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
        {catalogFacts(item).length ? <p className="catalog-facts">{catalogFacts(item).join(' · ')}</p> : null}
        <div className="story-meta">
          <span>
            {date ? (
              <>
                <CalendarDays size={13} />
                {dateLabel(date)}
                {expired(item) ? ' · 已截止' : ''}
              </>
            ) : updated ? (
              <>
                <CalendarDays size={13} />
                更新 {updated}
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
          <p>为学生整理可靠的竞赛、团队招募与资源与分享，帮助你判断下一步是否值得投入。</p>
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
        <SectionTitle label="01 / IN FOCUS" title="本期值得关注" />
        {state.loading ? (
          <LoadingRows />
        ) : state.error ? (
          <ErrorState error={state.error} retry={state.reload} />
        ) : state.data?.focus?.length ? (
          <>
            <div className="focus-grid">
              <ContentCard item={state.data.focus[0]} featured />
              <aside className="editor-note">
                <span className="eyebrow">A NOTE FROM VOIDHAVEN</span>
                <span className="note-asterisk">✳</span>
                <h3>
                  先看清方向，
                  <br />
                  再认真出发。
                </h3>
                <p>
                  竞赛、招募与资源与分享会按当前有效内容混合呈现。不必追赶所有机会，只需核对自己的下一步。
                </p>
                <Link to="/about">
                  我们的编辑原则 <ArrowUpRight size={15} />
                </Link>
              </aside>
            </div>
            {state.data.focus.length > 1 ? (
              <div className="story-grid compact-story-grid">
                {state.data.focus.slice(1).map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index + 1} />
                ))}
              </div>
            ) : null}
          </>
        ) : (
          <EmptyState
            figure="shelf"
            title="下一份机会，正在路上"
            text="当前没有可供关注的公开内容。你可以先浏览分类目录，或提供可靠的线索。"
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

      {state.data && !state.error ? (
        <>
          <section className="home-section">
            <SectionTitle label="03 / COMPETITIONS" title="竞赛资源" path="/competitions" />
            {state.data.competitions.length ? (
              <div className="story-grid">
                {state.data.competitions.map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index} />
                ))}
              </div>
            ) : (
              <EmptyState figure="shelf" title="当前没有公开竞赛" text="新的竞赛会在审核后出现在这里。" />
            )}
          </section>
          <section className="home-section">
            <SectionTitle label="04 / BETTER, TOGETHER" title="团队招募" path="/recruitments" />
            {state.data.recruitments.length ? (
              <div className="story-grid recruitment-grid">
                {state.data.recruitments.map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index} />
                ))}
              </div>
            ) : (
              <EmptyState figure="shelf" title="暂时没有公开招募" text="新的团队计划会在审核后出现在这里。" />
            )}
          </section>
          <section className="home-section">
            <SectionTitle label="05 / THE READING ROOM" title="资源与分享" path="/resources" />
            {state.data.resources.length ? (
              <div className="resource-list">
                {state.data.resources.map((item, index) => (
                  <ContentCard key={item.id} item={item} index={index} />
                ))}
              </div>
            ) : (
              <EmptyState
                figure="harbor"
                title="书架正在整理中"
                text="欢迎提供有来源、值得阅读的学习资料。"
              />
            )}
          </section>
        </>
      ) : null}

      <section className="principles-section">
        <div className="principles-intro">
          <p className="eyebrow">HOW THE INDEX IS MADE</p>
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

function SectionTitle({ label, title, path }: { label: string; title: string; path?: string }) {
  return (
    <div className="section-heading">
      <div>
        <p className="eyebrow">{label}</p>
        <h2>{title}</h2>
      </div>
      {path ? (
        <Link to={path}>
          查看全部 <ArrowUpRight size={15} />
        </Link>
      ) : null}
    </div>
  )
}

function CatalogListRow({ item }: { item: PublicContent }) {
  const location = useLocation()
  const date = deadline(item)
  const facts = catalogFacts(item)
  const status = item.operationalStatus
    ? (statusText[item.operationalStatus] ?? item.operationalStatus)
    : null
  return (
    <article className="catalog-list-row">
      <div>
        <span>
          {item.moduleKey === 'resource.article' ? '文章 / 经验' : categories[item.kind].label}
          {status ? ` · ${status}` : ''}
        </span>
        {date ? (
          <span>
            {dateLabel(date)}
            {expired(item) ? ' · 已截止' : ''}
          </span>
        ) : item.updatedAt ? (
          <span>更新 {dateLabel(item.updatedAt)}</span>
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
      {facts.length ? <p className="catalog-facts">{facts.join(' · ')}</p> : null}
      <small>{item.sourceName ?? '来源详见正文'}</small>
    </article>
  )
}

export function CatalogPage({ kind }: { kind: ContentKind }) {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const moduleKey = params.get('module') ?? ''
  const filterKey = params.get('filterKey') ?? ''
  const filterValue = params.get('filterValue') ?? ''
  const sort = params.get('sort') ?? ''
  const status = params.get('status') ?? ''
  const cursor = params.get('cursor') ?? undefined
  const view = resolveCatalogView(kind, params.get('view'))
  const [input, setInput] = useState(query)
  const category = categories[kind]
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => setInput(query), [query, kind])
  usePageTitle(kind === 'COMPETITION' ? '竞赛资源' : category.label)
  const modulesState = useResource(useCallback((signal?: AbortSignal) => api.publicModules(signal), []))
  const modules = (modulesState.data?.modules ?? []).filter(
    (module) => module.active && module.legacyKind === kind,
  )
  const filterFields = catalogFilterFields(modules)
  const selectedField = filterFields.find((field) => field.key === filterKey)
  const listQuery = useMemo(
    () => ({
      query,
      cursor,
      sort: sort || undefined,
      filterKey: filterKey || undefined,
      filterValue: filterValue || undefined,
      status: status || undefined,
    }),
    [query, cursor, sort, filterKey, filterValue, status],
  )
  const loader = useCallback(
    (signal?: AbortSignal) =>
      moduleKey ? api.listModule(moduleKey, listQuery, signal) : api.catalogList(kind, listQuery, signal),
    [kind, moduleKey, listQuery],
  )
  const state = useResource(loader)
  const appliedSort = sort || state.data?.sort || 'default'
  const filtered = catalogHasActiveFilters({
    q: query,
    module: moduleKey,
    filterKey,
    filterValue,
    sort,
    view: '',
    status,
    cursor,
  })
  function updateParams(next: Record<string, string>) {
    const value = new URLSearchParams(params)
    value.delete('cursor')
    Object.entries(next).forEach(([key, entry]) => (entry ? value.set(key, entry) : value.delete(key)))
    setParams(value)
  }
  function setView(next: CatalogView) {
    writeStoredCatalogView(kind, next)
    updateParams({ view: next })
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
      <div className="catalog-toolbar">
        <label>
          排序
          <select value={appliedSort} onChange={(event) => updateParams({ sort: event.target.value })}>
            {catalogSortOptions(kind).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        {kind !== 'RESOURCE' ? (
          <label>
            当前状态
            <select value={status} onChange={(event) => updateParams({ status: event.target.value })}>
              <option value="">全部状态</option>
              {catalogStatusOptions().map((option) => (
                <option key={option} value={option}>
                  {statusText[option] ?? option}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        <div className="catalog-view-toggle" role="group" aria-label="目录视图">
          <button
            type="button"
            className={view === 'card' ? 'active' : ''}
            aria-pressed={view === 'card'}
            onClick={() => setView('card')}
          >
            <LayoutGrid size={15} /> 卡片
          </button>
          <button
            type="button"
            className={view === 'list' ? 'active' : ''}
            aria-pressed={view === 'list'}
            onClick={() => setView('list')}
          >
            <LayoutList size={15} /> 列表
          </button>
        </div>
      </div>
      {filterFields.length ? (
        <div className="catalog-field-filter">
          <label>
            筛选字段
            <select
              value={filterKey}
              onChange={(event) => updateParams({ filterKey: event.target.value, filterValue: '' })}
            >
              <option value="">不使用字段筛选</option>
              {filterFields.map((field) => (
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
                      {selectedField.optionLabels?.[option] ?? fieldOptionLabel(option)}
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
        <div
          className={view === 'list' ? 'catalog-list' : kind === 'RESOURCE' ? 'resource-list' : 'story-grid'}
        >
          {state.data.items.map((item, index) =>
            view === 'list' ? (
              <CatalogListRow key={item.id} item={item} />
            ) : (
              <ContentCard key={item.id} item={item} index={index} />
            ),
          )}
        </div>
      ) : (
        <>
          <EmptyState
            figure={filtered ? 'search' : kind === 'RESOURCE' ? 'harbor' : 'shelf'}
            title={filtered ? '没有找到匹配内容' : '这个目录还在整理中'}
            text={filtered ? '试试更短的关键词，或清空搜索、筛选和排序条件。' : '内容通过审核后会在此公开。'}
          />
          {filtered ? (
            <div className="catalog-empty-actions">
              <button
                type="button"
                className="button"
                onClick={() => {
                  setInput('')
                  setParams(catalogSearchParams({ view: params.get('view') || '' }))
                }}
              >
                清空筛选条件
              </button>
            </div>
          ) : null}
        </>
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
            to={`${category.path}?${catalogSearchParams({
              q: query,
              module: moduleKey,
              filterKey,
              filterValue,
              sort,
              view: params.get('view') || '',
              status,
              cursor: state.data.nextCursor ?? undefined,
            })}`}
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
        <p>同时检索竞赛、团队招募与资源与分享；每一条结果仍保留原始来源。</p>
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
          figure="prompt"
          title="输入一个关键词"
          text="我们会分别查询竞赛、团队招募与资源与分享，不会把当前页面筛选冒充全站结果。"
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
                  figure="search"
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

export { DetailPage } from './detail-page'

export function AboutPage() {
  usePageTitle('关于')
  const contactLinks = siteContactLinks()
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">A NOTE ABOUT THIS PLACE</p>
        <h1>
          更少的信息差。
          <br />
          <span>更清晰的下一步。</span>
        </h1>
        <p>VOIDHAVEN 是一个独立的、由学生维护的项目，不是学校或任何赛事主办方的官方平台。</p>
      </header>
      <div className="about-grid">
        <div className="about-prose">
          <section>
            <h2>名字从哪里来</h2>
            <p>
              VOIDHAVEN
              由“VOID”与“HAVEN”拼成，意思是一片供人停靠的虚空避风港；“求索袋底洞”是我们对这套整理方式的称呼——把零散线索收进一个可以反复翻找的地方。
            </p>
          </section>
          <section>
            <h2>为什么开始</h2>
            <p>
              发现竞赛、理解规则、找到团队与学习机会时，信息往往散落在群聊、海报和转发里。我们把线索整理到一处，让一次偶然发现，变成一次认真开始。
            </p>
          </section>
          <section>
            <h2>现在提供什么</h2>
            <p>
              三类公开内容：竞赛资源、团队招募和资源与分享。每条内容保留来源、内容性质与最后核验时间，重要事项以原始来源为准。
            </p>
          </section>
          <section id="sources">
            <h2>内容整理、核验和来源原则</h2>
            <p>
              我们只做整理与索引，不代替官方发布。状态变化（截止、招满、暂停、取消）会在页面显著提示，并隐藏已经不能继续使用的行动入口；发现错误可以直接提交纠错。
            </p>
          </section>
          <section>
            <h2>维护团队与当前阶段</h2>
            <p>
              项目由学生小规模维护，仍处在持续建设的早期阶段：先把公开目录、详情页和内容编辑器做扎实，再逐步扩展参与方式。
            </p>
          </section>
          {contactLinks.length ? (
            <section>
              <h2>开源代码与公开文档</h2>
              <ul>
                {contactLinks.map((link) => (
                  <li key={link.href}>
                    <a href={link.href}>{link.label}</a>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          <section>
            <h2>可以怎样参与</h2>
            <p>
              投稿或反馈线索，加入用户社群，或申请加入项目组；也可以先阅读隐私说明，了解我们如何处理信息。
            </p>
            <div className="button-row">
              <Link className="button primary" to="/submit">
                提供线索或反馈 <ArrowUpRight size={15} />
              </Link>
              <Link className="button ghost" to="/join/community">
                用户社群
              </Link>
              <Link className="button ghost" to="/join/project">
                项目组
              </Link>
            </div>
          </section>
          <section>
            <h2>我们不提供什么</h2>
            <p>
              不提供学生账号、简历、站内报名、私信、聊天、评论或个性化推荐，也不保存社群沟通记录；项目组申请在站外完成。我们相信，先把一件小事做好，就有意义。
            </p>
          </section>
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
      <div className="not-found-copy">
        <p className="eyebrow">Lost but still seeking</p>
        <span className="not-found-number">
          4
          <span className="not-found-zero">
            0
            <svg className="not-found-spark" viewBox="0 0 16 16" aria-hidden="true">
              <path d="M8 1.2 9.05 6.95 14.8 8 9.05 9.05 8 14.8 6.95 9.05 1.2 8 6.95 6.95Z" />
            </svg>
          </span>
          4
        </span>
        <h1>这条小径，暂时没有内容。</h1>
        <p>页面可能不存在或已下线。先回到目录，再找下一站。</p>
      </div>
      <figure className="not-found-plate">
        <StatusFigure kind="path" className="not-found-figure" />
      </figure>
      <Link to="/" className="button primary not-found-action">
        返回首页 <ArrowRight size={16} />
      </Link>
    </section>
  )
}
