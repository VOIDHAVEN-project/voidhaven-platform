import { describe, expect, it } from 'vitest'
import type { ResourceConfig } from './api'
import { customValues, resourceEditorConfig, restoreResourceConfig } from './resource-config'

const initial: ResourceConfig = {
  version: 1,
  categories: [{ key: 'GENERAL', label: '分享', enabled: true }],
  fields: [{ key: 'when', label: '原日期', type: 'date', help: '', options: [], enabled: true }],
  templates: [],
}
describe('resource configuration compatibility', () => {
  it('retains the original definition of a disabled field already used by the content', () => {
    const current: ResourceConfig = {
      ...initial,
      version: 2,
      fields: [{ ...initial.fields[0], label: '新名称', enabled: false }],
    }
    expect(
      resourceEditorConfig(current, initial, { customFields: { when: '2030-01-01' } })?.fields[0],
    ).toMatchObject({ label: '原日期', enabled: false })
    expect(resourceEditorConfig(current, initial, {})?.fields[0].label).toBe('新名称')
  })
  it('restores old configuration without deleting subsequently introduced identities', () => {
    const current = {
      ...initial,
      version: 3,
      categories: [...initial.categories, { key: 'NOTE', label: '笔记', enabled: true }],
    }
    const restored = restoreResourceConfig(current, initial)
    expect(restored.version).toBe(3)
    expect(restored.categories[1]).toMatchObject({ key: 'NOTE', enabled: false })
    expect(current.categories[1].enabled).toBe(true)
  })
  it('ignores malformed custom metadata', () => {
    expect(customValues({ customFields: ['wrong'] })).toEqual({})
    expect(customValues({ customFields: null })).toEqual({})
  })
})
