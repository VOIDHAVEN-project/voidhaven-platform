import { describe, expect, it } from 'vitest'
import { isReviewablePublicSubmission, publicSubmissionStatusOptions } from './public-submissions'

describe('public submission states', () => {
  it('matches the backend filter contract', () => {
    expect(publicSubmissionStatusOptions.map(({ value }) => value)).toEqual([
      'RECEIVED',
      'IN_PROGRESS',
      'WAITING_FOR_REQUESTER',
      'RESOLVED',
      'REJECTED',
      'CLOSED',
      'ALL',
    ])
  })

  it('only exposes actions for non-terminal states', () => {
    expect(isReviewablePublicSubmission('RECEIVED')).toBe(true)
    expect(isReviewablePublicSubmission('IN_PROGRESS')).toBe(true)
    expect(isReviewablePublicSubmission('RESOLVED')).toBe(false)
    expect(isReviewablePublicSubmission('REJECTED')).toBe(false)
  })
})
