import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { ArrowLeft, ArrowRight, ArrowUpRight, Check, Copy, Share2, ShieldCheck } from 'lucide-react'
import { api, categories, type ContentModule, type PublicAction, type PublicContent } from '../lib/api'
import { copyText } from '../lib/copy-text'
import { usePageTitle, useResource } from '../lib/hooks'
import { ControlledCommunityImage } from '../components/controlled-community-image'
import { ErrorState, LoadingRows, Notice, message, type NoticeValue } from '../components/ui'
import { DocumentView, extractDocumentHeadings, safeUrl, type DocumentHeading } from '../components/document'
import type { PublicShellContext } from '../components/shell'
import {
  dateLabel,
  deadline,
  detailClosure,
  primaryFacts,
  relatedGroups,
  verificationSummary,
  type DetailClosure,
} from '../lib/detail'
import { ResourceFacts } from '../components/resource-facts'
import { fieldOptionLabel } from '../lib/module-field-labels'

const EXTRA_FACT_SKIP = new Set([
  'officialRulesUrl',
  'deadlineDate',
  'deadlineAt',
  'followUpUrl',
  'relatedCompetitionId',
  'relatedCompetitionIds',
  'resourceType',
  'audience',
  'startDate',
  'competitionLevel',
  'participationForm',
  'roles',
  'headcount',
  'stage',
])

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
    () => related.data?.items?.filter((entry) => entry.id !== item.id) ?? [],
    [related.data, item.id],
  )
  const closure = detailClosure(item)
  const trust = verificationSummary(item.verification)
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
  const date = deadline(item)
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
        <p className={`detail-trust ${trust.tone}`}>
          <span>{trust.label}</span>
          {item.updatedAt ? <span>最近更新 {dateLabel(item.updatedAt)}</span> : null}
        </p>
        <div className="detail-meta">
          <span>
            {item.authorDisplayName || 'VOIDHAVEN'}
            {date ? ` · ${dateLabel(date)}${closure.code === 'expired' ? ' · 已截止' : ''}` : ''}
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
      <div className="detail-document-grid">
        <section className="article-reading" aria-label="正文">
          {closure.message ? (
            <aside className="detail-closed" role="status">
              <strong>{closure.label}</strong>
              <p>{closure.message}</p>
            </aside>
          ) : null}
          <PrimaryFacts item={item} closure={closure} />
          <DetailPrimaryActions item={item} closure={closure} />
          {headings.length >= 3 ? (
            <div className="detail-context-mobile">
              <Outline headings={headings} active={activeHeading} mobile />
            </div>
          ) : null}
          <DocumentView body={item.body} media={item.media} />
          <ExtraFacts item={item} module={module} />
          <ResourceFacts item={item} />
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
            纠错或反馈这条信息 <ArrowUpRight size={13} />
          </Link>
          <RelatedGroups
            kind={item.kind}
            items={relatedItems}
            loading={related.loading}
            error={related.error}
            onRetry={related.reload}
          />
        </section>
        <aside className="detail-context-desktop">
          <SidebarActions item={item} closure={closure} />
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

function PrimaryFacts({ item, closure }: { item: PublicContent; closure: DetailClosure }) {
  const facts = primaryFacts(item)
  return (
    <section className="primary-facts" aria-labelledby="primary-facts-title">
      <p className="eyebrow">AT A GLANCE</p>
      <h2 id="primary-facts-title">关键信息</h2>
      <p className="primary-facts-lead">先看它是什么、是否仍有效、是否适合你，再决定要不要读正文。</p>
      <dl>
        {facts.map((fact) => (
          <div key={fact.label} className="primary-fact">
            <dt>{fact.label}</dt>
            <dd>
              {fact.label === '当前状态' ? (
                <span className={`deadline-status ${closure.actionsOpen ? 'active' : 'expired'}`}>
                  {fact.value}
                </span>
              ) : (
                fact.value
              )}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function ExtraFacts({ item, module }: { item: PublicContent; module?: ContentModule }) {
  const extras =
    module?.fields.filter(
      (field) => !EXTRA_FACT_SKIP.has(field.key) && presentDetail(item.details[field.key]),
    ) ?? []
  if (!extras.length) return null
  return (
    <section className="extra-facts" aria-labelledby="extra-facts-title">
      <h2 id="extra-facts-title">补充信息</h2>
      <dl>
        {extras.map((field) => (
          <div key={field.key} className="primary-fact">
            <dt>{field.label}</dt>
            <dd>
              {typeof item.details[field.key] === 'boolean'
                ? item.details[field.key]
                  ? '是'
                  : '否'
                : Array.isArray(item.details[field.key])
                  ? (item.details[field.key] as unknown[]).join('、')
                  : fieldOptionLabel(String(item.details[field.key]))}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function DetailPrimaryActions({ item, closure }: { item: PublicContent; closure: DetailClosure }) {
  const outbound = outboundActions(item, closure)
  const recruitmentOpen = item.kind === 'RECRUITMENT' && closure.actionsOpen
  if (!outbound.length && !recruitmentOpen) return null
  return (
    <section className="detail-primary-actions" aria-labelledby="detail-actions-title">
      <p className="eyebrow">TAKE THE NEXT STEP</p>
      <h2 id="detail-actions-title">现在可以做什么</h2>
      {outbound.length ? (
        <div className="button-row">
          {outbound.map((action) => (
            <a
              key={action.href}
              className={action.primary ? 'button primary' : 'button'}
              href={action.href}
              target="_blank"
              rel="noreferrer"
            >
              {action.label} <ArrowUpRight size={15} />
            </a>
          ))}
        </div>
      ) : null}
      <RecruitmentActions item={item} closure={closure} />
    </section>
  )
}

function SidebarActions({ item, closure }: { item: PublicContent; closure: DetailClosure }) {
  const outbound = outboundActions(item, closure)
  if (!outbound.length) return null
  return (
    <section className="detail-sidebar-actions" aria-labelledby="sidebar-actions-title">
      <p className="eyebrow">ACTIONS</p>
      <h2 id="sidebar-actions-title">行动入口</h2>
      {outbound.map((action) => (
        <a
          key={action.href}
          className={action.primary ? 'button' : 'text-link'}
          href={action.href}
          target="_blank"
          rel="noreferrer"
        >
          {action.label} <ArrowUpRight size={13} />
        </a>
      ))}
    </section>
  )
}

function outboundActions(item: PublicContent, closure: DetailClosure) {
  const rules = safeUrl(item.details.officialRulesUrl)
  const follow = safeUrl(item.details.followUpUrl)
  const source = safeUrl(item.sourceUrl)
  const actions: { href: string; label: string; primary: boolean }[] = []
  if (rules) actions.push({ href: rules, label: '阅读官方规则', primary: true })
  if (follow && closure.actionsOpen && !(item.actions && item.actions.length)) {
    actions.push({ href: follow, label: '了解后续入口', primary: !rules })
  }
  if (source && !rules)
    actions.push({ href: source, label: '查看原始来源', primary: item.kind === 'RESOURCE' })
  return actions
}

function RecruitmentActions({ item, closure }: { item: PublicContent; closure: DetailClosure }) {
  const [inviteCode, setInviteCode] = useState('')
  const [grantToken, setGrantToken] = useState('')
  const [gated, setGated] = useState<PublicAction[]>([])
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<NoticeValue>(null)
  if (item.kind !== 'RECRUITMENT' || !closure.actionsOpen) return null
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
    <div className="recruitment-actions">
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
    </div>
  )
}

function RecruitmentAction({ action, grant }: { action: PublicAction; grant?: string }) {
  if (action.mediaUrl && action.accessLevel === 'PUBLIC') {
    return (
      <figure className="recruitment-qr">
        <figcaption>{action.label}</figcaption>
        <img src={action.mediaUrl} alt={`${action.label}二维码`} />
      </figure>
    )
  }
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
  const href =
    action.contactChannel === 'EMAIL'
      ? `mailto:${action.value}`
      : action.contactChannel === 'PHONE'
        ? `tel:${action.value}`
        : action.presentationType === 'LINK'
          ? safeUrl(action.value)
          : null
  const label =
    action.contactChannel === 'EMAIL'
      ? '发送邮件'
      : action.contactChannel === 'PHONE'
        ? '拨打电话'
        : action.presentationType === 'LINK'
          ? '打开入口'
          : `复制${action.label}`
  return href ? (
    <a
      className="button primary"
      href={href}
      target={href.startsWith('http') ? '_blank' : undefined}
      rel="noreferrer"
    >
      {label} <ArrowUpRight size={14} />
    </a>
  ) : (
    <button className="button ghost" type="button" onClick={() => void copyText(action.value ?? '')}>
      {label}：{action.value}
    </button>
  )
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
      {items.map((item) => (
        <Link
          key={item.id}
          to={`/content/${item.id}`}
          state={{ from: location.pathname + location.search, kind: item.kind }}
        >
          <span>{categories[item.kind].label}</span>
          <strong>{item.title}</strong>
          <ArrowRight size={14} />
        </Link>
      ))}
    </div>
  )
}

function RelatedGroups({
  kind,
  items,
  loading,
  error,
  onRetry,
}: {
  kind: PublicContent['kind']
  items: PublicContent[]
  loading: boolean
  error: string
  onRetry: () => void
}) {
  const groups = relatedGroups(kind, items)
  if (loading) return null
  if (error) {
    return (
      <div className="related-error">
        <span>关联内容暂时不可用。</span>
        <button className="text-link" onClick={onRetry}>
          重试
        </button>
      </div>
    )
  }
  if (!groups.length) return null
  return (
    <div className="related-groups">
      {groups.map((group) => (
        <section key={group.key} className="related-at-end">
          <p className="eyebrow">KEEP EXPLORING</p>
          <h2>{group.title}</h2>
          <RelatedLinks items={group.items} />
        </section>
      ))}
    </div>
  )
}

function Provenance({ item }: { item: PublicContent }) {
  const source = safeUrl(item.sourceUrl)
  const trust = verificationSummary(item.verification)
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
        <dd>{trust.label}</dd>
        <dt>最近更新</dt>
        <dd>{item.updatedAt ? dateLabel(item.updatedAt) : '未提供'}</dd>
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

function presentDetail(value: unknown) {
  if (value == null) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.length > 0
  return true
}

function locationOrigin() {
  return window.location.origin + window.location.pathname
}
