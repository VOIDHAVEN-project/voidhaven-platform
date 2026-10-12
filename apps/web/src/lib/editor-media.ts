const MEDIA_TOKEN = /media:([A-Za-z0-9]{26})/g
const MEDIA_IMAGE_LINE = /^!\[[^\]]{0,240}]\(media:([A-Za-z0-9]{26})\)$/

export type MediaUploadState = 'ready' | 'needs-new-draft' | 'needs-candidate' | 'blocked'

export function mediaUploadState(editing?: { workflowStatus?: string } | null): MediaUploadState {
  if (!editing) return 'needs-new-draft'
  if (editing.workflowStatus === 'DRAFT' || editing.workflowStatus === 'CHANGES_REQUESTED') return 'ready'
  if (editing.workflowStatus === 'PUBLISHED') return 'needs-candidate'
  return 'blocked'
}

export function referencedMediaIds(source: string) {
  const ids = new Set<string>()
  for (const match of source.matchAll(MEDIA_TOKEN)) ids.add(match[1])
  return ids
}

const MAX_ALT_LENGTH = 240

export function normalizeMediaAltText(value: string) {
  const cleaned = value
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/]/g, '')
    .trim()
  return cleaned.slice(0, MAX_ALT_LENGTH) || '图片'
}

function mediaImagePattern(id: string) {
  return new RegExp(`!\\[[^\\]]{0,${MAX_ALT_LENGTH}}]\\(media:${id}\\)`, 'g')
}

export function mediaAltInBody(source: string, id: string): string | null {
  const match = new RegExp(`!\\[([^\\]]{0,${MAX_ALT_LENGTH}})]\\(media:${id}\\)`).exec(source)
  return match ? match[1] : null
}

export function replaceMediaAltText(source: string, id: string, altText: string) {
  return source.replace(mediaImagePattern(id), `![${normalizeMediaAltText(altText)}](media:${id})`)
}

export function mediaInsertToken(id: string, altText: string) {
  const alt = altText.trim() || '图片'
  return `\n\n![${alt}](media:${id})\n\n`
}

export function stripMediaReferences(source: string, id: string) {
  return source
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .filter((line) => {
      const match = MEDIA_IMAGE_LINE.exec(line.trim())
      return !match || match[1] !== id
    })
    .join('\n')
    .replaceAll(`media:${id}`, '')
    .replace(/\n{3,}/g, '\n\n')
}
