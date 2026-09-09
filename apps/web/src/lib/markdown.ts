import type { ContentBlock, DocumentBody, Inline } from './api'

const imagePattern = /^!\[([^\]]{1,240})]\(media:([A-Za-z0-9]{26})\)$/
const headingPattern = /^(#{1,4})\s+(.+)$/
const orderedPattern = /^\d+[.)]\s+(.+)$/
const unorderedPattern = /^[-*+]\s+(.+)$/
const inlinePattern = /\[([^\]]{1,500})]\((https:\/\/[^)\s]+)\)|\*\*([^*]{1,1000})\*\*|`([^`]{1,1000})`|\*([^*]{1,1000})\*/g

function inline(text: string): Inline[] {
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

function startsBlock(value: string) {
  return value.startsWith('```') || value.startsWith('> ') || headingPattern.test(value) ||
    imagePattern.test(value.trim()) || orderedPattern.test(value) || unorderedPattern.test(value) ||
    value.trim() === '---' || value.trim() === '***'
}

export function markdownPreview(source: string): DocumentBody {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: ContentBlock[] = []
  for (let index = 0; index < lines.length;) {
    const line = lines[index]
    if (!line.trim()) { index += 1; continue }
    if (line.startsWith('```')) {
      const language = line.slice(3).trim(); const code: string[] = []; index += 1
      while (index < lines.length && !lines[index].startsWith('```')) code.push(lines[index++])
      if (index < lines.length) index += 1
      blocks.push({ type: 'code', language, text: code.join('\n') }); continue
    }
    const picture = imagePattern.exec(line.trim())
    if (picture) { blocks.push({ type: 'image', alt: picture[1], mediaId: picture[2] }); index += 1; continue }
    const title = headingPattern.exec(line)
    if (title) { blocks.push({ type: 'heading', level: title[1].length, inlines: inline(title[2]) }); index += 1; continue }
    if (line.trim() === '---' || line.trim() === '***') { blocks.push({ type: 'rule' }); index += 1; continue }
    if (line.startsWith('> ')) {
      const quote: string[] = []
      while (index < lines.length && lines[index].startsWith('> ')) quote.push(lines[index++].slice(2))
      blocks.push({ type: 'quote', inlines: inline(quote.join('\n')) }); continue
    }
    const isOrdered = Boolean(orderedPattern.exec(line)); const isUnordered = Boolean(unorderedPattern.exec(line))
    if (isOrdered || isUnordered) {
      const items: Inline[][] = []
      while (index < lines.length) {
        const match = (isOrdered ? orderedPattern : unorderedPattern).exec(lines[index])
        if (!match) break
        items.push(inline(match[1])); index += 1
      }
      blocks.push({ type: 'list', ordered: isOrdered, items }); continue
    }
    const paragraph = [line]; index += 1
    while (index < lines.length && lines[index].trim() && !startsBlock(lines[index])) paragraph.push(lines[index++])
    blocks.push({ type: 'paragraph', inlines: inline(paragraph.join('\n')) })
  }
  return { version: 2, blocks: blocks.length ? blocks : [{ type: 'paragraph', inlines: [{ type: 'text', text: '开始写作吧。' }] }] }
}
