import { describe, expect, it } from 'vitest'
import {
  isNavGroup,
  navLeafLinks,
  navLinks,
  participationMenu,
  publicNavLinks,
  publicNavTrailingLink,
} from './public-nav'

describe('public navigation model', () => {
  it('只有叶子节点带跳转地址，父级只负责展开', () => {
    expect('path' in participationMenu).toBe(false)
    expect('path' in participationMenu.children[3]).toBe(false)
    expect(navLinks(publicNavLinks)).toHaveLength(publicNavLinks.length)
    expect(navLinks(participationMenu.children).map((link) => link.path)).toEqual([
      '/submit',
      '/partners/apply',
      '/status',
    ])
  })

  it('三层结构包含两个站内参与入口', () => {
    const leaves = navLeafLinks(participationMenu.children)
    expect(leaves.map((link) => link.path)).toEqual([
      '/submit',
      '/partners/apply',
      '/status',
      '/join/community',
      '/join/project',
    ])
    expect(isNavGroup(participationMenu.children[3])).toBe(true)
  })

  it('顶层链接保留内容类型高亮信息', () => {
    expect(publicNavLinks.filter((link) => link.kind).map((link) => link.kind)).toEqual([
      'COMPETITION',
      'RECRUITMENT',
      'RESOURCE',
    ])
    expect(publicNavTrailingLink.path).toBe('/about')
  })
})
