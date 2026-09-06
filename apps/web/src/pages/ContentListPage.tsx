import { useEffect, useRef, useState } from 'react'
import { api, kindMeta } from '../lib/api'
import type { Content, ContentKind, PublicContentPage } from '../lib/api'
import { ContentCard } from '../components/ContentCard'
import { EmptyState, ErrorState, LoadingState } from '../components/PageState'
import { useDocumentTitle } from '../lib/useDocumentTitle'
export function ContentListPage({ kind }: { kind: ContentKind }) {
  const [loadedPage, setLoadedPage] = useState<(PublicContentPage & { criteria: string }) | null>(null)
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [reload, setReload] = useState(0)
  const requestSequence = useRef(0)
  const meta = kindMeta[kind]
  useDocumentTitle(meta.label, `浏览本校${meta.label}，所有信息均保留来源链接。`)
  const criteria = `${kind}:${query.trim()}`
  useEffect(() => {
    let active = true
    const sequence = ++requestSequence.current
    void api
      .list(kind, { query })
      .then((result) => {
        if (!active || sequence !== requestSequence.current) return
        setLoadedPage({ criteria, ...result })
        setError(null)
      })
      .catch((failure: unknown) => {
        if (active && sequence === requestSequence.current) setError(failure instanceof Error ? failure.message : '内容暂时无法加载。')
      })
    return () => {
      active = false
    }
  }, [criteria, kind, query, reload])
  const page = loadedPage?.criteria === criteria ? loadedPage : null
  async function loadMore() {
    if (!page?.nextCursor) return
    setLoadingMore(true)
    const sequence = ++requestSequence.current
    try {
      const result = await api.list(kind, { query, cursor: page.nextCursor })
      if (sequence === requestSequence.current)
        setLoadedPage((current) =>
          current?.criteria === criteria
            ? { criteria, items: [...current.items, ...result.items], nextCursor: result.nextCursor }
            : current,
        )
    } catch (failure) {
      if (sequence === requestSequence.current) setError(failure instanceof Error ? failure.message : '内容暂时无法加载。')
    } finally {
      if (sequence === requestSequence.current) setLoadingMore(false)
    }
  }
  const entries: Content[] = page?.items ?? []
  return (
    <section className="page">
      <div className="page-intro">
        <p className="eyebrow">公开浏览</p>
        <h1>{meta.label}</h1>
        <p>所有信息均保留来源链接。截止、资格和报名要求请以官方页面为准。</p>
      </div>
      <label className="search">
        <span className="sr-only">搜索{meta.label}</span>
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`搜索${meta.label}、来源或关键词`} />
      </label>
      {error ? (
        <ErrorState message={error} onRetry={() => setReload((value) => value + 1)} />
      ) : !page ? (
        <LoadingState />
      ) : entries.length ? (
        <>
          <div className="listing">
            {entries.map((entry) => (
              <ContentCard key={entry.id} content={entry} />
            ))}
          </div>
          {page.nextCursor ? (
            <div className="load-more">
              <button className="button ghost" type="button" onClick={() => void loadMore()} disabled={loadingMore}>
                {loadingMore ? '加载中…' : '加载更多'}
              </button>
            </div>
          ) : null}
        </>
      ) : (
        <EmptyState label={query ? '符合条件的内容' : meta.label} />
      )}
    </section>
  )
}
