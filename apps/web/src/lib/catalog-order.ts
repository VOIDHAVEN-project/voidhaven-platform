import type { ContentKind } from './api'

export const CATALOG_KINDS: ContentKind[] = ['COMPETITION', 'RECRUITMENT', 'RESOURCE']

export const CATALOG_KIND_LABELS: Record<ContentKind, string> = {
  COMPETITION: '竞赛目录',
  RECRUITMENT: '团队招募目录',
  RESOURCE: '资源与分享目录',
}

export type CatalogOrderEntry = {
  id?: string
  contentItemId: string
  sortOrder: number
  title?: string | null
  kind?: ContentKind | null
  operationalStatus?: string | null
  updatedAt?: string | null
  deadline?: string | null
  publicEligible?: boolean
  issue?: string | null
}

export function moveCatalogEntry(entries: CatalogOrderEntry[], index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= entries.length) return entries
  const next = entries.slice()
  const [row] = next.splice(index, 1)
  next.splice(target, 0, row)
  return next.map((item, order) => ({ ...item, sortOrder: order }))
}

export function dropCatalogEntry(entries: CatalogOrderEntry[], fromIndex: number, toIndex: number) {
  if (
    fromIndex === toIndex ||
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= entries.length ||
    toIndex >= entries.length
  ) {
    return entries
  }
  const next = entries.slice()
  const [row] = next.splice(fromIndex, 1)
  next.splice(toIndex, 0, row)
  return next.map((item, order) => ({ ...item, sortOrder: order }))
}

export function catalogOrderFingerprint(defaultSort: string, entries: CatalogOrderEntry[]) {
  return JSON.stringify({
    defaultSort,
    entries: entries.map((item) => [item.contentItemId, item.sortOrder]),
  })
}
