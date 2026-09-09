import { describe, expect, it } from 'vitest'
import { isReviewablePartnerApplication, partnerApplicationStatusOptions } from './partner-applications'

describe('partner application states', () => {
  it('matches the backend filter contract', () => {
    expect(partnerApplicationStatusOptions.map(({ value }) => value)).toEqual([
      'PENDING_REVIEW',
      'APPROVED',
      'REJECTED',
      'ALL',
    ])
  })

  it('only exposes review actions for PENDING_REVIEW', () => {
    expect(isReviewablePartnerApplication('PENDING_REVIEW')).toBe(true)
    expect(isReviewablePartnerApplication('APPROVED')).toBe(false)
    expect(isReviewablePartnerApplication('REJECTED')).toBe(false)
  })
})
