import { describe, expect, it } from 'vitest'
import { parseHomepagePlacementText } from './homepage-placements'

describe('parseHomepagePlacementText', () => {
  it('accepts valid rows and reports empty state separately', () => {
    const parsed = parseHomepagePlacementText(
      'FOCUS,01HZXC0V4K7N9PQRSTVWXY0001,0\n\nRECRUITMENT,01HZXC0V4K7N9PQRSTVWXY0002,1',
    )
    expect(parsed.errors).toEqual([])
    expect(parsed.placements).toHaveLength(2)
  })

  it('locates invalid slots, ids and duplicates by line number', () => {
    const parsed = parseHomepagePlacementText(
      'HOME,01HZXC0V4K7N9PQRSTVWXY0001,0\nFOCUS,not-a-ulid,0\nFOCUS,01HZXC0V4K7N9PQRSTVWXY0001,0\nFOCUS,01HZXC0V4K7N9PQRSTVWXY0001,1',
    )
    expect(parsed.errors[0]).toContain('第 1 行')
    expect(parsed.errors.some((error) => error.includes('第 2 行') && error.includes('ULID'))).toBe(true)
    expect(parsed.errors.some((error) => error.includes('重复'))).toBe(true)
  })
})
