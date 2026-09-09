export const partnerApplicationStatusOptions = [
  { value: 'PENDING_REVIEW', label: '审核中' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'ALL', label: '全部状态' },
] as const

export type PartnerApplicationState = Exclude<
  (typeof partnerApplicationStatusOptions)[number]['value'],
  'ALL'
>

export function isReviewablePartnerApplication(state: PartnerApplicationState) {
  return state === 'PENDING_REVIEW'
}
