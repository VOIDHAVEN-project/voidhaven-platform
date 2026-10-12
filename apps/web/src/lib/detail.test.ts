import { describe, expect, it } from 'vitest'
import type { PublicContent } from './api'
import { deadlineState, detailClosure, primaryFacts, relatedGroups, verificationSummary } from './detail'

function item(overrides: Partial<PublicContent> = {}): PublicContent {
  return {
    id: 'AAAAAAAAAAAAAAAAAAAAAAAAAA',
    kind: 'COMPETITION',
    moduleKey: 'competition',
    schemaVersion: 1,
    title: '示例竞赛',
    summary: '摘要',
    body: { version: 1, blocks: [{ type: 'paragraph', text: '正文' }] },
    languageCode: 'zh-CN',
    languageLabel: '中文',
    rightsBasis: '说明',
    reprintPermission: 'NOT_ALLOWED',
    details: {},
    revision: 1,
    ...overrides,
  } as PublicContent
}

describe('detail helpers', () => {
  it('closes actions for paused, filled, cancelled and past deadlines', () => {
    expect(detailClosure(item({ operationalStatus: 'PAUSED' })).actionsOpen).toBe(false)
    expect(detailClosure(item({ kind: 'RECRUITMENT', operationalStatus: 'FILLED' })).code).toBe('filled')
    expect(detailClosure(item({ operationalStatus: 'CANCELLED' })).code).toBe('cancelled')
    const expired = item({
      details: { deadlineDate: '2020-01-01' },
      operationalStatus: 'OPEN',
    })
    expect(detailClosure(expired, Date.parse('2026-01-01T00:00:00+08:00')).code).toBe('expired')
    expect(detailClosure(item({ kind: 'RESOURCE', details: {} })).actionsOpen).toBe(true)
  })

  it('keeps historical empty fields out of primary facts', () => {
    const facts = primaryFacts(
      item({
        details: { deadlineDate: '2030-10-15', competitionLevel: '', audience: '  ' },
        operationalStatus: 'OPEN',
      }),
      Date.parse('2030-01-01T00:00:00+08:00'),
    )
    expect(facts.map((fact) => fact.label)).toEqual(['当前状态', '截止日期'])
    expect(facts[0]?.value).toBe('进行中')
  })

  it('groups related items by the current page kind and ignores same-kind noise', () => {
    const groups = relatedGroups('COMPETITION', [
      item({ id: '1', kind: 'RECRUITMENT', title: '招募甲' }),
      item({ id: '2', kind: 'RESOURCE', title: '资源乙' }),
      item({ id: '3', kind: 'COMPETITION', title: '另一场竞赛' }),
      item({ id: '1', kind: 'RECRUITMENT', title: '招募甲重复' }),
    ])
    expect(groups.map((group) => group.title)).toEqual(['相关团队招募', '相关资源与分享'])
    expect(groups[0]?.items.map((entry) => entry.title)).toEqual(['招募甲'])
    expect(relatedGroups('RECRUITMENT', [item({ id: '9', kind: 'COMPETITION' })])[0]?.title).toBe('所属竞赛')
    expect(relatedGroups('RESOURCE', [item({ id: '9', kind: 'COMPETITION' })])[0]?.title).toBe('适用竞赛')
  })

  it('keeps deadline derivation and verification copy conservative', () => {
    const dated = item({ details: { deadlineDate: '2030-10-15' } })
    expect(deadlineState(dated, Date.parse('2030-10-14T00:00:00+08:00'))).toBe('active')
    expect(deadlineState(dated, Date.parse('2030-10-16T00:00:00+08:00'))).toBe('expired')
    expect(verificationSummary(null).tone).toBe('unknown')
    expect(verificationSummary({ outcome: 'VALID', checkedAt: '2030-01-02T00:00:00Z' }).tone).toBe('valid')
  })
})
