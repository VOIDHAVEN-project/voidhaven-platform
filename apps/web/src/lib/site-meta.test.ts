import { describe, expect, it } from 'vitest'
import { siteBeian, siteContactLinks } from './site-meta'

describe('site meta visibility', () => {
  it('未配置时整条隐藏，不渲染占位内容', () => {
    expect(siteContactLinks({})).toEqual([])
    expect(siteBeian({})).toBeNull()
    expect(siteBeian({ beian: '   ' })).toBeNull()
  })

  it('配置后按固定顺序输出真实入口', () => {
    expect(
      siteContactLinks({
        contactEmail: 'hello@example.org',
        githubUrl: 'https://github.com/example',
        docsUrl: 'https://example.org/docs',
      }),
    ).toEqual([
      { label: '联系邮箱', href: 'mailto:hello@example.org' },
      { label: '开源仓库', href: 'https://github.com/example' },
      { label: '公开文档与决策记录', href: 'https://example.org/docs' },
    ])
    expect(siteBeian({ beian: '备案号示例' })).toBe('备案号示例')
  })
})
