import { Link } from 'react-router-dom'
import { kindMeta } from '../lib/api'
import type { Content } from '../lib/api'
export function ContentCard({ content }: { content: Content }) { const meta = kindMeta[content.kind]; const deadline = typeof content.details.deadlineDate === 'string' ? content.details.deadlineDate : typeof content.details.deadlineAt === 'string' ? content.details.deadlineAt : null; return <article className="content-card"><div className="eyebrow"><span>{meta.singular}</span>{deadline ? <time>{deadline}</time> : null}</div><h3><Link to={`${meta.path}/${content.id}`}>{content.title}</Link></h3><p>{content.summary}</p><div className="card-meta"><span>{content.languageCode.toUpperCase()}</span><span>{content.sourceName}</span></div></article> }
