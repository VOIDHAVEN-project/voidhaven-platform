import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AuditLogEntry } from '../lib/api'
import { api } from '../lib/api'

const shanghaiDateFormatter = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'Asia/Shanghai' })

function displayTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? '时间不可用' : shanghaiDateFormatter.format(parsed)
}

export function AdminAuditPanel() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [action, setAction] = useState('')
  const [actorAccountId, setActorAccountId] = useState('')
  const [objectType, setObjectType] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try { const page = await api.adminAuditLogs({ query, action, actorAccountId, objectType, from, to }); setEntries(page.items); setNextCursor(page.nextCursor) } catch (reason) { setError(reason instanceof Error ? reason.message : '无法加载审计记录。') } finally { setLoading(false) }
  }, [action, actorAccountId, from, objectType, query, to])

  useEffect(() => {
    let active = true
    void api.adminAuditLogs({ query, action, actorAccountId, objectType, from, to })
      .then((result) => { if (active) { setEntries(result.items); setNextCursor(result.nextCursor) } })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '无法加载审计记录。') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [action, actorAccountId, from, objectType, query, to])

  async function loadMore() {
    if (!nextCursor) return
    setLoadingMore(true); setError(null)
    try { const page = await api.adminAuditLogs({ query, action, actorAccountId, objectType, from, to, cursor: nextCursor }); setEntries((current) => [...current, ...page.items]); setNextCursor(page.nextCursor) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '下一页加载失败。') }
    finally { setLoadingMore(false) }
  }

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const values = new FormData(event.currentTarget)
    const localFrom = String(values.get('from')).trim()
    const localTo = String(values.get('to')).trim()
    const normalizedFrom = localFrom ? new Date(localFrom).toISOString() : ''
    const normalizedTo = localTo ? new Date(localTo).toISOString() : ''
    if (normalizedFrom && normalizedTo && normalizedFrom > normalizedTo) { setError('审计起始时间不能晚于结束时间。'); return }
    setQuery(String(values.get('query')).trim()); setAction(String(values.get('action')).trim())
    setActorAccountId(String(values.get('actorAccountId')).trim()); setObjectType(String(values.get('objectType')).trim())
    setFrom(normalizedFrom); setTo(normalizedTo)
  }

  return <section className="admin-audit" aria-label="管理员审计记录">
    <div className="workspace-section-heading"><div><p className="eyebrow">最小审计查询</p><h2>最近受控操作</h2></div><button className="button ghost" type="button" onClick={() => void load()} disabled={loading}>{loading ? '加载中…' : '刷新记录'}</button></div>
    <p className="workspace-help">仅展示操作角色、对象、不透明标识、原因和请求标识；不展示密码、令牌、私密联系方式或私密正文。</p>
    <form className="workspace-filter" onSubmit={filter}><label>关键词<input name="query" defaultValue={query} placeholder="对象、动作或角色" /></label><label>动作<input name="action" defaultValue={action} placeholder="例如 CONTENT_PUBLISHED" /></label><label>操作人账号 ID<input name="actorAccountId" defaultValue={actorAccountId} minLength={26} maxLength={26} /></label><label>对象类型<input name="objectType" defaultValue={objectType} placeholder="例如 CONTENT_ITEM" /></label><label>开始时间<input name="from" type="datetime-local" /></label><label>结束时间<input name="to" type="datetime-local" /></label><button className="button ghost" disabled={loading}>筛选</button></form>
    {error ? <p className="notice notice-error" role="alert">{error}</p> : null}
    <div className="admin-content-list" aria-live="polite">
      {loading ? <p>正在加载审计记录…</p> : entries.length === 0 ? <p>当前没有可展示的审计记录。</p> : entries.map((entry) => <article className="admin-content-row" key={entry.id}>
        <div><p className="eyebrow"><span>{entry.actorRole}</span><span>{entry.action}</span></p><h3>{entry.objectType} · {entry.objectId}</h3><p className="admin-state"><time dateTime={entry.createdAt}>{displayTime(entry.createdAt)}</time>{entry.actorAccountId ? ` · 操作人：${entry.actorAccountId}` : ''} · 请求：{entry.requestId}</p>{entry.reason ? <p className="workspace-help">原因：{entry.reason}</p> : null}</div>
      </article>)}
    </div>
    {nextCursor ? <button className="button ghost load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? '正在加载…' : '加载更多'}</button> : null}
  </section>
}
