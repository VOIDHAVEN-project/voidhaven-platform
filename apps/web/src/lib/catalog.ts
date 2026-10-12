import type { ContentKind, ContentModule, ModuleField, PublicContent } from './api'

export type CatalogView = 'card' | 'list'
export type CatalogSortMode = 'default' | 'deadline' | 'updated'
export type CatalogQueryState = {
  q: string
  module: string
  filterKey: string
  filterValue: string
  sort: string
  view: string
  status: string
  cursor?: string
}

const VIEW_KEY = (kind: ContentKind) => `voidhaven-catalog-view-${kind}`
const STATUS_OPTIONS = ['OPEN', 'PAUSED', 'FILLED', 'EXPIRED', 'CANCELLED'] as const

export function defaultCatalogView(kind: ContentKind): CatalogView {
  return kind === 'RESOURCE' ? 'list' : 'card'
}

export function readStoredCatalogView(kind: ContentKind): CatalogView | null {
  try {
    const value = localStorage.getItem(VIEW_KEY(kind))
    return value === 'card' || value === 'list' ? value : null
  } catch {
    return null
  }
}

export function writeStoredCatalogView(kind: ContentKind, view: CatalogView) {
  try {
    localStorage.setItem(VIEW_KEY(kind), view)
  } catch {
    /* theme-style preference is best-effort */
  }
}

export function resolveCatalogView(kind: ContentKind, urlView: string | null): CatalogView {
  if (urlView === 'card' || urlView === 'list') return urlView
  return readStoredCatalogView(kind) ?? defaultCatalogView(kind)
}

export function catalogSortOptions(kind: ContentKind): { value: CatalogSortMode; label: string }[] {
  const options: { value: CatalogSortMode; label: string }[] = [
    { value: 'default', label: '默认顺序' },
    { value: 'updated', label: '最近更新' },
  ]
  if (kind !== 'RESOURCE') options.splice(1, 0, { value: 'deadline', label: '截止日期' })
  return options
}

export function parseCatalogSort(value: string | null | undefined): CatalogSortMode | '' {
  if (value === 'default' || value === 'deadline' || value === 'updated') return value
  return ''
}

export function catalogFilterFields(modules: ContentModule[]): ModuleField[] {
  const fields = new Map<string, ModuleField>()
  const conflicting = new Set<string>()
  for (const module of modules) {
    for (const field of module.fields) {
      if (!field.filterable || field.type === 'tags' || conflicting.has(field.key)) continue
      const existing = fields.get(field.key)
      if (!existing) fields.set(field.key, field)
      else if (existing.type !== field.type) {
        fields.delete(field.key)
        conflicting.add(field.key)
      }
    }
  }
  return [...fields.values()]
}

export function catalogHasActiveFilters(state: CatalogQueryState) {
  return Boolean(state.q || state.module || state.filterKey || state.sort || state.status || state.cursor)
}

export function catalogSearchParams(state: Partial<CatalogQueryState>) {
  const params = new URLSearchParams()
  if (state.q?.trim()) params.set('q', state.q.trim())
  if (state.module) params.set('module', state.module)
  if (state.filterKey && state.filterValue) {
    params.set('filterKey', state.filterKey)
    params.set('filterValue', state.filterValue)
  }
  if (state.sort) params.set('sort', state.sort)
  if (state.view) params.set('view', state.view)
  if (state.status) params.set('status', state.status)
  if (state.cursor) params.set('cursor', state.cursor)
  return params
}

export function catalogFacts(item: PublicContent): string[] {
  const details = item.details ?? {}
  const facts: string[] = []
  const level = textValue(details.competitionLevel)
  const form = textValue(details.participationForm)
  const audience = textValue(details.audience)
  const stage = textValue(details.stage)
  const roles = arrayValue(details.roles)
  const headcount = numberValue(details.headcount)
  if (level) facts.push(level)
  if (form) facts.push(form)
  if (stage) facts.push(stage)
  if (audience) facts.push(audience)
  if (roles.length) facts.push(roles.join('、'))
  if (headcount != null) facts.push(`${headcount} 人`)
  return facts
}

export function relatedCompetitionIds(details: Record<string, unknown> | undefined): string[] {
  if (!details) return []
  const ids = new Set<string>()
  const single = textValue(details.relatedCompetitionId)
  if (single) ids.add(single)
  for (const value of arrayValue(details.relatedCompetitionIds)) ids.add(value)
  return [...ids]
}

export function catalogStatusOptions() {
  return STATUS_OPTIONS
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function arrayValue(value: unknown) {
  return Array.isArray(value) ? value.map((item) => String(item).trim()).filter(Boolean) : []
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}
