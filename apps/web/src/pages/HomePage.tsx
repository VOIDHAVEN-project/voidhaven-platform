import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import type { Content } from '../lib/api'
import { ContentCard } from '../components/ContentCard'
import { ErrorState, LoadingState, EmptyState } from '../components/PageState'
import { useDocumentTitle } from '../lib/useDocumentTitle'
type HomeData = { competitions: Content[]; resources: Content[]; recruitments: Content[] }
function Section({ title, to, entries, wide = false }: { title: string; to: string; entries: Content[]; wide?: boolean }) {
  return (
    <section className={wide ? 'home-section section-wide' : 'home-section'}>
      <div className="section-heading">
        <h2>{title}</h2>
        <Link to={to}>
          查看全部 <span aria-hidden>→</span>
        </Link>
      </div>
      {entries.length ? (
        <div className="card-list">
          {entries.map((entry) => (
            <ContentCard key={entry.id} content={entry} />
          ))}
        </div>
      ) : (
        <EmptyState label={title} />
      )}
    </section>
  )
}
export function HomePage() {
  useDocumentTitle()
  const [data, setData] = useState<HomeData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const load = () => {
    api
      .home()
      .then((result) => {
        setData(result)
        setError(null)
      })
      .catch((e: Error) => setError(e.message))
  }
  useEffect(load, [])
  return (
    <>
      <section className="hero">
        <p className="eyebrow">校内信息发现平台</p>
        <h1>
          把机会放在
          <br />
          <em>看得见的地方。</em>
        </h1>
        <p>竞赛、学习资源与团队招募，一处浏览。我们整理线索，不替代官方规则。</p>
        <div className="hero-actions">
          <Link className="button primary" to="/competitions">
            浏览近期竞赛
          </Link>
          <Link className="button ghost" to="/feedback">
            投稿或反馈
          </Link>
        </div>
      </section>
      {error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !data ? (
        <LoadingState />
      ) : (
        <div className="home-grid">
          <Section title="近期竞赛" to="/competitions" entries={data.competitions} />
          <Section title="团队招募" to="/recruitments" entries={data.recruitments} />
          <Section title="学习资源" to="/resources" entries={data.resources} wide />
        </div>
      )}
    </>
  )
}
