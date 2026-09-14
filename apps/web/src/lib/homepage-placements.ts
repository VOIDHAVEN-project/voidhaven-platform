import type { HomepagePlacement } from './api'

const SLOTS = new Set<HomepagePlacement['slot']>(['FOCUS', 'RECRUITMENT', 'RESOURCE'])
const CONTENT_ID = /^[0-9A-HJKMNP-TV-Z]{26}$/

export function parseHomepagePlacementText(text: string): {
  placements: Omit<HomepagePlacement, 'id' | 'version'>[]
  errors: string[]
} {
  const errors: string[] = []
  const placements: Omit<HomepagePlacement, 'id' | 'version'>[] = []
  const seen = new Set<string>()
  text.split(/\r?\n/).forEach((line, index) => {
    const raw = line.trim()
    if (!raw) return
    const parts = raw.split(',').map((part) => part.trim())
    const lineNo = index + 1
    if (parts.length < 2 || parts.length > 3) {
      errors.push(`第 ${lineNo} 行：请使用“栏目,内容ID,顺序”。`)
      return
    }
    const [slot, contentItemId, order] = parts
    if (!SLOTS.has(slot as HomepagePlacement['slot'])) {
      errors.push(`第 ${lineNo} 行：栏目只能是 FOCUS、RECRUITMENT 或 RESOURCE。`)
      return
    }
    if (!CONTENT_ID.test(contentItemId ?? '')) {
      errors.push(`第 ${lineNo} 行：内容 ID 必须是 26 位 ULID。`)
      return
    }
    const sortOrder = order ? Number(order) : 0
    if (!Number.isInteger(sortOrder) || sortOrder < 0 || sortOrder > 1000) {
      errors.push(`第 ${lineNo} 行：顺序必须是 0 到 1000 的整数。`)
      return
    }
    const key = `${slot}:${contentItemId}`
    if (seen.has(key)) {
      errors.push(`第 ${lineNo} 行：同一栏目下内容 ID 重复。`)
      return
    }
    seen.add(key)
    placements.push({
      slot: slot as HomepagePlacement['slot'],
      contentItemId,
      sortOrder,
      startsAt: null,
      endsAt: null,
    })
  })
  return { placements, errors }
}
