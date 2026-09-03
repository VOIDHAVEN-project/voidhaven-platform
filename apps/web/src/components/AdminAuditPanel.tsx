import { useCallback, useEffect, useState } from 'react'
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
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try { setEntries(await api.adminAuditLogs()) } catch (reason) { setError(reason instanceof Error ? reason.message : '无法加载审计记录。') } finally { setLoading(false) }
  }, [])

  useEffect(() => {
    let active = true
    void api.adminAuditLogs()
      .then((result) => { if (active) setEntries(result) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : '无法加载审计记录。') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  return <section className="admin-audit" aria-label="管理员审计记录">
    <div className="workspace-section-heading"><div><p className="eyebrow">最小审计查询</p><h2>最近受控操作</h2></div><button className="button ghost" type="button" onClick={() => void load()} disabled={loading}>{loading ? '加载中…' : '刷新记录'}</button></div>
    <p className="workspace-help">仅展示操作角色、对象、不透明标识、原因和请求标识；不展示密码、令牌、私密联系方式或私密正文。</p>
    {error ? <p className="notice notice-error" role="alert">{error}</p> : null}
    <div className="admin-content-list" aria-live="polite">
      {loading ? <p>正在加载审计记录…</p> : entries.length === 0 ? <p>当前没有可展示的审计记录。</p> : entries.map((entry) => <article className="admin-content-row" key={`${entry.requestId}:${entry.objectId}:${entry.action}`}>
        <div><p className="eyebrow"><span>{entry.actorRole}</span><span>{entry.action}</span></p><h3>{entry.objectType} · {entry.objectId}</h3><p className="admin-state"><time dateTime={entry.createdAt}>{displayTime(entry.createdAt)}</time> · 请求：{entry.requestId}</p>{entry.reason ? <p className="workspace-help">原因：{entry.reason}</p> : null}</div>
      </article>)}
    </div>
  </section>
}
