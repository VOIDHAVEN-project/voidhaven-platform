import { describe, expect, it } from 'vitest'
import { isReviewablePartnerApplication, partnerApplicationStatusOptions } from './partner-applications'

describe('partner application states', () => {
  it('matches the backend filter contract', () => {
    expect(partnerApplicationStatusOptions.map(({ value }) => value)).toEqual([
      'RECEIVED',
      'IN_REVIEW',
      'WAITING_FOR_APPLICANT',
      'APPROVED',
      'ACTIVATION_PENDING',
      'ACTIVATED',
      'REJECTED',
      'CLOSED',
      'ALL',
    ])
  })

  it('only exposes review actions for active application states', () => {
    expect(isReviewablePartnerApplication('RECEIVED')).toBe(true)
    expect(isReviewablePartnerApplication('IN_REVIEW')).toBe(true)
    expect(isReviewablePartnerApplication('APPROVED')).toBe(false)
    expect(isReviewablePartnerApplication('REJECTED')).toBe(false)
  })
})
