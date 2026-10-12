import { describe, expect, it, vi } from 'vitest'
import {
  catalogFacts,
  catalogFilterFields,
  catalogHasActiveFilters,
  catalogSearchParams,
  catalogSortOptions,
  defaultCatalogView,
  resolveCatalogView,
} from './catalog'
import type { ContentModule, PublicContent } from './api'

const article: ContentModule = {
  key: 'resource.article',
  label: '文章',
  legacyKind: 'RESOURCE',
  active: true,
  allowsOriginalSource: true,
  fields: [
    {
      key: 'sourceMode',
      label: '来源',
      type: 'select',
      required: true,
      filterable: true,
      options: ['ORIGINAL'],
    },
    { key: 'audience', label: '对象', type: 'text', required: false, filterable: true },
  ],
}

const resource: ContentModule = {
  key: 'resource',
  label: '资源',
  legacyKind: 'RESOURCE',
  active: true,
  allowsOriginalSource: false,
  fields: [
    {
      key: 'resourceType',
      label: '类型',
      type: 'select',
      required: true,
      filterable: true,
      options: ['GUIDE'],
    },
    { key: 'audience', label: '对象', type: 'text', required: false, filterable: true },
    { key: 'roles', label: '角色', type: 'tags', required: false, filterable: true },
  ],
}

describe('catalog helpers', () => {
  it('defaults resource to list and competitions to card', () => {
    expect(defaultCatalogView('RESOURCE')).toBe('list')
    expect(defaultCatalogView('COMPETITION')).toBe('card')
    expect(defaultCatalogView('RECRUITMENT')).toBe('card')
  })

  it('lets the URL override a stored view preference', () => {
    const store = new Map<string, string>([['voidhaven-catalog-view-RESOURCE', 'card']])
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
    })
    expect(resolveCatalogView('RESOURCE', 'list')).toBe('list')
    expect(resolveCatalogView('RESOURCE', null)).toBe('card')
    vi.unstubAllGlobals()
  })

  it('omits deadline sort for resources and keeps URL params stable', () => {
    expect(catalogSortOptions('RESOURCE').map((item) => item.value)).toEqual(['default', 'updated'])
    expect(catalogSortOptions('COMPETITION').map((item) => item.value)).toEqual([
      'default',
      'deadline',
      'updated',
    ])
    const params = catalogSearchParams({
      q: ' 数学 ',
      sort: 'deadline',
      view: 'list',
      cursor: 'c1.next',
    })
    expect(params.get('q')).toBe('数学')
    expect(params.get('sort')).toBe('deadline')
    expect(params.get('view')).toBe('list')
    expect(params.get('cursor')).toBe('c1.next')
  })

  it('unions filterable fields and skips empty historical details', () => {
    expect(catalogFilterFields([resource, article]).map((field) => field.key)).toEqual([
      'resourceType',
      'audience',
      'sourceMode',
    ])
    expect(
      catalogFacts({
        id: 'x',
        kind: 'COMPETITION',
        title: '旧竞赛',
        summary: '',
        details: {},
      } as unknown as PublicContent),
    ).toEqual([])
    expect(
      catalogFacts({
        id: 'y',
        kind: 'RECRUITMENT',
        title: '招募',
        summary: '',
        details: { roles: ['设计', '开发'], headcount: 3, audience: '  ' },
      } as unknown as PublicContent),
    ).toEqual(['设计、开发', '3 人'])
    expect(
      catalogHasActiveFilters({
        q: '',
        module: '',
        filterKey: 'stage',
        filterValue: '',
        sort: '',
        view: '',
        status: '',
      }),
    ).toBe(true)
  })
})
