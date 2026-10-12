import { describe, expect, it } from 'vitest'
import {
  catalogOrderFingerprint,
  dropCatalogEntry,
  moveCatalogEntry,
  type CatalogOrderEntry,
} from './catalog-order'

function row(id: string, order: number): CatalogOrderEntry {
  return { contentItemId: id, sortOrder: order }
}

describe('catalog order helpers', () => {
  it('moves and drops entries while rewriting sort order', () => {
    const entries = [row('a', 0), row('b', 1), row('c', 2)]
    expect(moveCatalogEntry(entries, 0, 1).map((item) => item.contentItemId)).toEqual(['b', 'a', 'c'])
    expect(dropCatalogEntry(entries, 2, 0).map((item) => item.sortOrder)).toEqual([0, 1, 2])
    expect(dropCatalogEntry(entries, 2, 0).map((item) => item.contentItemId)).toEqual(['c', 'a', 'b'])
  })

  it('treats rewritten sort order as saved', () => {
    const live = [row('01HZXC0V4K7N9PQRSTVWXY0001', 0)]
    const draft = [row('01HZXC0V4K7N9PQRSTVWXY0001', 0)]
    expect(catalogOrderFingerprint('default', live)).toBe(catalogOrderFingerprint('default', draft))
    expect(catalogOrderFingerprint('deadline', live)).not.toBe(catalogOrderFingerprint('default', draft))
  })
})
