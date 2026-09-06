import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, kindMeta } from '../lib/api'
import type { Content } from '../lib/api'
import { ContentDocument } from '../components/ContentDocument'
import { ErrorState, LoadingState } from '../components/PageState'
import { useDocumentTitle } from '../lib/useDocumentTitle'

export function ContentDetailPage() {
  const { id = '' } = useParams()
  const [entry, setEntry] = useState<Content | null>(null)
  const [error, setError] = useState<string | null>(null)
  const load = () => {
    api
      .detailById(id)
      .then((result) => {
        setEntry(result)
        setError(null)
      })
      .catch((failure: Error) => setError(failure.message))
  }
  useEffect(load, [id])
  useDocumentTitle(entry?.title, entry?.summary)
  if (error)
    return (
      <section className="page">
        <ErrorState message={error} onRetry={load} />
        <Link className="back-link" to="/">
          ← 返回首页
        </Link>
      </section>
    )
  if (!entry)
    return (
      <section className="page">
        <LoadingState />
      </section>
    )
  const meta = kindMeta[entry.kind]
  const reuse = (permission: Content['reprintPermission']) => (permission === 'ATTRIBUTION_ALLOWED' ? '允许署名使用' : '不允许')
  return (
    <article className="detail">
      <Link className="back-link" to={meta.path}>
        ← 返回{meta.label}
      </Link>
      <p className="eyebrow">
        {entry.moduleKey === 'resource.article' ? '文章 / 经验' : meta.singular} · {entry.languageLabel}
      </p>
      <h1>{entry.title}</h1>
      <p className="lead">{entry.summary}</p>
      <dl className="facts">
        {entry.originalTitle ? (
          <div>
            <dt>原始标题</dt>
            <dd>{entry.originalTitle}</dd>
          </div>
        ) : null}
        <div>
          <dt>{entry.sourceUrl ? '官方或原始来源' : '来源方式'}</dt>
          <dd>
            {entry.sourceUrl ? (
              <a href={entry.sourceUrl} target="_blank" rel="noreferrer">
                {entry.sourceName} ↗
              </a>
            ) : (
              entry.sourceName
            )}
          </dd>
        </div>
        <div>
          <dt>作者展示名</dt>
          <dd>{entry.authorDisplayName}</dd>
        </div>
        <div>
          <dt>权利来源/使用说明</dt>
          <dd>{entry.rightsBasis}</dd>
        </div>
        <div>
          <dt>署名转载</dt>
          <dd>{reuse(entry.reprintPermission)}</dd>
        </div>
        <div>
          <dt>署名改编</dt>
          <dd>{reuse(entry.adaptationPermission)}</dd>
        </div>
      </dl>
      <ContentDocument document={entry.body} media={entry.media} />
      <aside className="official-note">
        <strong>请以官方规则为准</strong>
        <p>本站内容仅帮助理解和发现机会；竞赛的报名、资格、时间与规则以官方来源为准。</p>
      </aside>
    </article>
  )
}
