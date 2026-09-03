import type { ContentDocument, DocumentBlock, Inline } from './api'

const image = /^!\[([^\]]{1,240})]\(media:([A-Za-z0-9]{26})\)$/
const heading = /^(#{1,4})\s+(.+)$/
const ordered = /^\d+[.)]\s+(.+)$/
const unordered = /^[-*+]\s+(.+)$/
const inlinePattern = /\[([^\]]{1,500})]\((https:\/\/[^)\s]+)\)|\*\*([^*]{1,1000})\*\*|`([^`]{1,1000})`|\*([^*]{1,1000})\*/g

function inlines(text: string): Inline[] {
  const output: Inline[] = []
  let cursor = 0
  for (const match of text.matchAll(inlinePattern)) {
    const start = match.index ?? 0
    if (start > cursor) output.push({ type: 'text', text: text.slice(cursor, start) })
    if (match[1]) output.push({ type: 'link', text: match[1], href: match[2] })
    else if (match[3]) output.push({ type: 'strong', text: match[3] })
    else if (match[4]) output.push({ type: 'code', text: match[4] })
    else output.push({ type: 'emphasis', text: match[5] })
    cursor = start + match[0].length
  }
  if (cursor < text.length) output.push({ type: 'text', text: text.slice(cursor) })
  return output.length ? output : [{ type: 'text', text: ' ' }]
}

function startsBlock(value: string) { return value.startsWith('```') || value.startsWith('> ') || heading.test(value) || image.test(value.trim()) || ordered.test(value) || unordered.test(value) || value.trim() === '---' || value.trim() === '***' }

export function markdownPreview(source: string): ContentDocument {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: DocumentBlock[] = []
  for (let index = 0; index < lines.length;) {
    const line = lines[index]
    if (!line.trim()) { index++; continue }
    if (line.startsWith('```')) {
      const language = line.slice(3).trim(); const code: string[] = []; index++
      while (index < lines.length && !lines[index].startsWith('```')) code.push(lines[index++])
      if (index < lines.length) index++
      blocks.push({ type: 'code', language, text: code.join('\n') }); continue
    }
    const picture = image.exec(line.trim())
    if (picture) { blocks.push({ type: 'image', alt: picture[1], mediaId: picture[2] }); index++; continue }
    const title = heading.exec(line)
    if (title) { blocks.push({ type: 'heading', level: title[1].length, inlines: inlines(title[2]) }); index++; continue }
    if (line.trim() === '---' || line.trim() === '***') { blocks.push({ type: 'rule' }); index++; continue }
    if (line.startsWith('> ')) {
      const quote: string[] = []; while (index < lines.length && lines[index].startsWith('> ')) quote.push(lines[index++].slice(2))
      blocks.push({ type: 'quote', inlines: inlines(quote.join('\n')) }); continue
    }
    const firstOrdered = ordered.exec(line); const firstUnordered = unordered.exec(line)
    if (firstOrdered || firstUnordered) {
      const isOrdered = Boolean(firstOrdered); const items: Inline[][] = []
      while (index < lines.length) { const match = isOrdered ? ordered.exec(lines[index]) : unordered.exec(lines[index]); if (!match) break; items.push(inlines(match[1])); index++ }
      blocks.push({ type: 'list', ordered: isOrdered, items }); continue
    }
    const paragraph = [line]; index++
    while (index < lines.length && lines[index].trim() && !startsBlock(lines[index])) paragraph.push(lines[index++])
    blocks.push({ type: 'paragraph', inlines: inlines(paragraph.join('\n')) })
  }
  return { version: 2, blocks: blocks.length ? blocks : [{ type: 'paragraph', inlines: [{ type: 'text', text: '开始写作吧。' }] }] }
}
