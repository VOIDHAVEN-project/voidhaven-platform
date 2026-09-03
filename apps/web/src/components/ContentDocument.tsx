import type { ContentDocument as Document, DocumentBlock, Inline, PublicMedia } from '../lib/api'

function InlineContent({ values }: { values: Inline[] }) {
  return <>{values.map((value, index) => {
    const key = `${value.type}-${index}-${value.text}`
    if (value.type === 'strong') return <strong key={key}>{value.text}</strong>
    if (value.type === 'emphasis') return <em key={key}>{value.text}</em>
    if (value.type === 'code') return <code key={key}>{value.text}</code>
    if (value.type === 'link' && value.href) return <a key={key} href={value.href} target="_blank" rel="noreferrer">{value.text}</a>
    return <span key={key}>{value.text}</span>
  })}</>
}

function legacyText(block: DocumentBlock) { return 'text' in block ? block.text : '' }

function DocumentBlockView({ block, index, media }: { block: DocumentBlock; index: number; media: Map<string, PublicMedia> }) {
  const key = `${block.type}-${index}`
  if (block.type === 'heading') {
    const content = <InlineContent values={block.inlines} />
    if (block.level === 1) return <h2 key={key}>{content}</h2>
    if (block.level === 2) return <h3 key={key}>{content}</h3>
    if (block.level === 3) return <h4 key={key}>{content}</h4>
    return <h5 key={key}>{content}</h5>
  }
  if (block.type === 'paragraph') return <p key={key}>{'inlines' in block ? <InlineContent values={block.inlines} /> : legacyText(block)}</p>
  if (block.type === 'quote') return <blockquote key={key}><InlineContent values={block.inlines} /></blockquote>
  if (block.type === 'code') return <pre key={key}><code data-language={block.language}>{block.text}</code></pre>
  if (block.type === 'rule') return <hr key={key} />
  if (block.type === 'list') {
    const items = block.items.map((item, itemIndex) => <li key={`${key}-${itemIndex}`}><InlineContent values={item} /></li>)
    return block.ordered ? <ol key={key}>{items}</ol> : <ul key={key}>{items}</ul>
  }
  if (block.type === 'image') {
    const item = media.get(block.mediaId)
    return item ? <figure className="content-image" key={key}><img src={item.url} alt={block.alt} width={item.width} height={item.height} loading="lazy" /><figcaption>{block.alt}</figcaption></figure> : <p className="document-missing-media" key={key}>图片暂不可显示。</p>
  }
  return null
}

export function ContentDocument({ document, media = [], label = '正文' }: { document: Document; media?: PublicMedia[]; label?: string }) {
  const mediaMap = new Map(media.map((item) => [item.id, item]))
  return <section className="rich-text" aria-label={label}>{document.blocks.map((block, index) => <DocumentBlockView key={`${block.type}-${index}`} block={block} index={index} media={mediaMap} />)}</section>
}
