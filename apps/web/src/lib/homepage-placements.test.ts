import { describe, expect, it } from 'vitest'
import {
  curationFingerprint,
  dropPlacement,
  movePlacement,
  placementsInSlot,
  replaceSlot,
} from './homepage-placements'
import type { HomepagePlacement } from './api'

function row(slot: HomepagePlacement['slot'], id: string, order: number): HomepagePlacement {
  return { slot, contentItemId: id, sortOrder: order }
}

describe('homepage placement helpers', () => {
  it('moves a row up and down within one slot', () => {
    const placements = [
      row('FOCUS', '01HZXC0V4K7N9PQRSTVWXY0001', 0),
      row('FOCUS', '01HZXC0V4K7N9PQRSTVWXY0002', 1),
      row('RECRUITMENT', '01HZXC0V4K7N9PQRSTVWXY0003', 0),
    ]
    const down = movePlacement(placements, 'FOCUS', 0, 1)
    expect(placementsInSlot(down, 'FOCUS').map((item) => item.contentItemId)).toEqual([
      '01HZXC0V4K7N9PQRSTVWXY0002',
      '01HZXC0V4K7N9PQRSTVWXY0001',
    ])
    expect(placementsInSlot(down, 'RECRUITMENT')).toHaveLength(1)
    expect(movePlacement(placements, 'FOCUS', 0, -1)).toEqual(placements)
  })

  it('drops by index and rewrites sort order', () => {
    const placements = [
      row('COMPETITION', '01HZXC0V4K7N9PQRSTVWXY0001', 0),
      row('COMPETITION', '01HZXC0V4K7N9PQRSTVWXY0002', 1),
      row('COMPETITION', '01HZXC0V4K7N9PQRSTVWXY0003', 2),
    ]
    const next = dropPlacement(placements, 'COMPETITION', 2, 0)
    expect(next.map((item) => item.contentItemId)).toEqual([
      '01HZXC0V4K7N9PQRSTVWXY0003',
      '01HZXC0V4K7N9PQRSTVWXY0001',
      '01HZXC0V4K7N9PQRSTVWXY0002',
    ])
    expect(next.map((item) => item.sortOrder)).toEqual([0, 1, 2])
  })

  it('treats equal fingerprints as saved', () => {
    const live = replaceSlot([], 'FOCUS', [row('FOCUS', '01HZXC0V4K7N9PQRSTVWXY0001', 9)])
    const draft = replaceSlot([], 'FOCUS', [row('FOCUS', '01HZXC0V4K7N9PQRSTVWXY0001', 0)])
    expect(curationFingerprint(live)).toBe(curationFingerprint(draft))
  })
})
