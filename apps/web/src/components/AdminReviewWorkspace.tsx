import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { EditableContent, ManagedContent, PublicationReadiness } from '../lib/api'
import { api } from '../lib/api'
import { ContentDocument } from './ContentDocument'

type Notice = { tone: 'success' | 'error'; text: string } | null

function displayTime(value?: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}

function ReviewQueue() {
  const [items, setItems] = useState<ManagedContent[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [module, setModule] = useState('')
  const [notice, setNotice] = useState<Notice>(null)
  const load = useCallback(async (search = query) => {
    setLoading(true); setNotice(null)
    try { const page = await api.adminQueue({ query: search, module }); setItems(page.items); setNextCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '审核队列加载失败。' }) }
    finally { setLoading(false) }
  }, [module, query])
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void load() }, [load])
  async function loadMore() {
    if (!nextCursor) return
    setLoadingMore(true)
    try { const page = await api.adminQueue({ query, module, cursor: nextCursor }); setItems((current) => [...current, ...page.items]); setNextCursor(page.nextCursor) }
    catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '下一页加载失败。' }) }
    finally { setLoadingMore(false) }
  }
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const values = new FormData(event.currentTarget); setQuery(String(values.get('query')).trim()); setModule(String(values.get('module')))
  }
  return <section className="admin-review-workspace">
    <div className="workspace-section-heading"><div><p className="eyebrow">审核中心</p><h2>待审核内容</h2></div><button className="button ghost" type="button" onClick={() => void load()} disabled={loading}>刷新队列</button></div>
    <p className="workspace-help">进入详情后查看候选修订的完整正文、图片、来源和版权信息，再作出审核决定。</p>
    <form className="workspace-filter" onSubmit={search}><label>搜索待审内容<input name="query" defaultValue={query} placeholder="标题或摘要" /></label><label>模块<select name="module" defaultValue={module}><option value="">全部模块</option><option value="competition">竞赛</option><option value="resource">学习资源</option><option value="resource.article">文章 / 经验</option><option value="recruitment">团队招募</option></select></label><button className="button ghost" disabled={loading}>筛选</button></form>
    {notice ? <p className={`notice notice-${notice.tone}`} role="alert">{notice.text}</p> : null}
    <div className="admin-content-list">{loading ? <p>正在加载审核队列…</p> : items.length === 0 ? <p>当前没有待审核内容。</p> : items.map((item) => <article className="admin-content-row" key={item.id}><div><p className="eyebrow"><span>{item.moduleKey}</span><span>修订版本 {item.revision}</span></p><h3>{item.title}</h3><p className="admin-state">送审时间：{displayTime(item.submittedAt)}</p></div><Link className="button primary" to={`/workspace/reviews/${item.id}`}>查看原文并审核</Link></article>)}</div>
    {nextCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? '正在加载…' : '加载更多'}</button> : null}
  </section>
}

function Readiness({ value }: { value: PublicationReadiness | null }) {
  if (!value) return null
  if (value.ready) return <p className="notice notice-success" role="status">发布检查已通过，可以正式发布。</p>
  return <section className="publication-blockers" role="alert"><strong>当前还不能发布</strong><ul>{value.blockers.map((blocker) => <li key={blocker.code}>{blocker.message}</li>)}</ul>{value.partnerProfileId ? <p>合作方配额：{value.publicActiveCount ?? '—'} / {value.quota ?? '—'}。超级管理员可在合作方与账号区域调整。</p> : null}</section>
}

function ReviewDetail({ contentId }: { contentId: string }) {
  const [detail, setDetail] = useState<EditableContent | null>(null)
  const [readiness, setReadiness] = useState<PublicationReadiness | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)

  const load = useCallback(async () => {
    setLoading(true); setNotice(null)
    try {
      const value = await api.adminContent(contentId)
      setDetail(value)
      setReadiness(value.workflowStatus === 'APPROVED' ? await api.adminPublicationReadiness(contentId) : null)
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载审核原文。' }) }
    finally { setLoading(false) }
  }, [contentId])
  // oxlint-disable-next-line react/set-state-in-effect
  useEffect(() => { void load() }, [load])

  async function review(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!detail) return
    const values = new FormData(event.currentTarget)
    const decision = String(values.get('decision')) as 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT'
    const reason = String(values.get('reason')).trim()
    if (!decision) { setNotice({ tone: 'error', text: '请先选择审核决定。' }); return }
    if (decision !== 'APPROVE' && !reason) { setNotice({ tone: 'error', text: '退回修改或拒绝时必须填写原因。' }); return }
    if (decision === 'REJECT' && !window.confirm('拒绝后只有超级管理员可以重新开放，确定拒绝这篇稿件吗？')) return
    setBusy(true); setNotice(null)
    try {
      const updated = await api.adminDecision(contentId, detail.revision, decision, reason || undefined)
      setDetail((current) => current ? { ...current, workflowStatus: updated.workflowStatus, revision: updated.revision } : current)
      if (updated.workflowStatus === 'APPROVED') setReadiness(await api.adminPublicationReadiness(contentId))
      setNotice({ tone: 'success', text: decision === 'APPROVE' ? '审核已通过，请完成发布检查后正式发布。' : '审核决定已保存，作者将在工作台看到审核意见。' })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '审核操作未完成。' }) }
    finally { setBusy(false) }
  }

  async function publish() {
    if (!detail || !readiness?.ready || !window.confirm('确定将当前审核通过的修订公开发布吗？')) return
    setBusy(true); setNotice(null)
    try {
      await api.adminPublish(contentId)
      await load()
      setNotice({ tone: 'success', text: '内容已正式发布，可从公开详情页访问。' })
    } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '发布未完成。' }) }
    finally { setBusy(false) }
  }

  return <section className="admin-review-workspace">
    <p><Link className="text-link" to="/workspace/reviews">← 返回审核队列</Link></p>
    {loading ? <p>正在加载审核原文…</p> : null}
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    {detail ? <>
      <header className="review-document-header"><p className="eyebrow">候选修订 · {detail.draft.moduleKey}</p><h2>{detail.draft.title}</h2><p>{detail.draft.summary}</p><p className="admin-state">修订版本：{detail.revision} · 送审时间：{displayTime(detail.submittedAt)} · 归属：{detail.ownerPartnerProfileId ?? '管理员代录'}</p></header>
      <section className="review-metadata" aria-label="审核信息"><h3>发布信息与来源</h3><dl><div><dt>作者</dt><dd>{detail.draft.authorDisplayName}</dd></div><div><dt>语言</dt><dd>{detail.draft.languageCode}</dd></div><div><dt>来源</dt><dd>{detail.draft.sourceUrl ? <a href={detail.draft.sourceUrl} target="_blank" rel="noopener noreferrer">{detail.draft.sourceName || detail.draft.sourceUrl} ↗</a> : detail.draft.sourceName || '原创内容'}</dd></div><div><dt>权利说明</dt><dd>{detail.draft.rightsBasis}</dd></div>{Object.entries(detail.draft.details).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{Array.isArray(value) ? value.join('、') : String(value ?? '—')}</dd></div>)}</dl></section>
      <article className="review-document"><ContentDocument document={detail.draft.body} media={detail.media} label="待审核正文" /></article>
      <section className="review-history"><h3>审核记录</h3>{detail.reviews.length === 0 ? <p>当前没有历史审核决定。</p> : <ol>{detail.reviews.map((record) => <li key={`${record.revisionId}:${record.createdAt}`}><strong>{record.decision}</strong> · {displayTime(record.createdAt)}{record.reason ? <p>{record.reason}</p> : null}</li>)}</ol>}</section>
      {detail.workflowStatus === 'PENDING_REVIEW' ? <form className="review-decision-form" onSubmit={(event) => void review(event)}><h3>作出审核决定</h3><label>审核决定<select name="decision" defaultValue="" required><option value="" disabled>请选择</option><option value="APPROVE">审核通过</option><option value="CHANGES_REQUESTED">退回修改</option><option value="REJECT">拒绝</option></select></label><label>审核说明<textarea name="reason" maxLength={1000} rows={4} placeholder="退回修改或拒绝时必须填写；通过时可选。" /></label><button className="button primary" disabled={busy}>{busy ? '处理中…' : '保存审核决定'}</button></form> : null}
      {detail.workflowStatus === 'APPROVED' ? <section className="review-publish"><h3>发布检查</h3><Readiness value={readiness} /><button className="button primary" type="button" disabled={busy || !readiness?.ready} onClick={() => void publish()}>{busy ? '处理中…' : '正式发布'}</button></section> : null}
    </> : null}
  </section>
}

export function AdminReviewWorkspace({ contentId }: { contentId?: string }) {
  return contentId ? <ReviewDetail contentId={contentId} /> : <ReviewQueue />
}
