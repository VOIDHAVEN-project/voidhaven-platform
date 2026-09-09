import type { ContentBlock, DocumentBody, Inline, Media } from '../lib/api'
export type DocumentHeading = {
  id: string
  level: number
  label: string
  blockIndex: number
}
export function documentSectionId(blockIndex: number) {
  return `section-${blockIndex}`
}
function inlineText(values?: Inline[]) {
  return values?.map((item) => item.text).join('') ?? ''
}
export function extractDocumentHeadings(body: DocumentBody): DocumentHeading[] {
  return body.blocks.flatMap((block, blockIndex) => {
    if (block.type !== 'heading') return []
    const label = (block.text ?? inlineText(block.inlines)).trim()
    if (!label) return []
    return [
      {
        id: documentSectionId(blockIndex),
        level: typeof block.level === 'number' ? block.level : 1,
        label,
        blockIndex,
      },
    ]
  })
}
export function safeUrl(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value.trim()) return
  try {
    const url = new URL(value, window.location.origin)
    if (url.protocol === 'https:' || url.protocol === 'http:') return url.href
  } catch {
    /* Invalid links remain plain text. */
  }
}
function Inlines({ values }: { values: Inline[] }) {
  return values.map((item, i) =>
    item.type === 'strong' ? (
      <strong key={i}>{item.text}</strong>
    ) : item.type === 'emphasis' ? (
      <em key={i}>{item.text}</em>
    ) : item.type === 'code' ? (
      <code key={i}>{item.text}</code>
    ) : item.type === 'link' && safeUrl(item.href) ? (
      <a key={i} href={safeUrl(item.href)} target="_blank" rel="noreferrer">
        {item.text}
      </a>
    ) : (
      <span key={i}>{item.text}</span>
    ),
  )
}
function Block({ block, media, index }: { block: ContentBlock; media: Media[]; index: number }) {
  const content = block.inlines ? <Inlines values={block.inlines} /> : block.text
  switch (block.type) {
    case 'heading':
      return block.level && block.level > 1 ? (
        <h3 id={documentSectionId(index)}>{content}</h3>
      ) : (
        <h2 id={documentSectionId(index)}>{content}</h2>
      )
    case 'paragraph':
      return <p>{content}</p>
    case 'quote':
      return <blockquote>{content}</blockquote>
    case 'code':
      return (
        <pre>
          <code>{block.text}</code>
        </pre>
      )
    case 'rule':
      return <hr />
    case 'list': {
      const items = block.items?.map((item, i) => (
        <li key={i}>
          <Inlines values={item} />
        </li>
      ))
      return block.ordered ? <ol>{items}</ol> : <ul>{items}</ul>
    }
    case 'image': {
      const image = media.find((item) => item.id === block.mediaId)
      return image && safeUrl(image.url) ? (
        <figure className="document-image">
          <img
            src={safeUrl(image.url)}
            alt={block.alt ?? image.altText ?? ''}
            width={image.width}
            height={image.height}
            loading="lazy"
            onError={(e) => {
              e.currentTarget.classList.add('image-unavailable')
            }}
          />
          <figcaption>{block.alt}</figcaption>
        </figure>
      ) : (
        <p className="image-fallback">图片暂不可用{block.alt ? `：${block.alt}` : ''}</p>
      )
    }
    default:
      return <p>{block.text ?? '此内容块暂不支持展示，请查看原始来源。'}</p>
  }
}
export function DocumentView({ body, media = [] }: { body: DocumentBody; media?: Media[] }) {
  return (
    <div className="document-body">
      {body.blocks.map((block, index) => (
        <Block key={index} block={block} media={media} index={index} />
      ))}
    </div>
  )
}
