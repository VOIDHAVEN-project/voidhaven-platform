export const partnerApplicationStatusOptions = [
  { value: 'RECEIVED', label: '已接收' },
  { value: 'IN_REVIEW', label: '审核中' },
  { value: 'WAITING_FOR_APPLICANT', label: '等待申请人补充' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'ACTIVATION_PENDING', label: '待交付激活凭据' },
  { value: 'ACTIVATED', label: '已激活' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'ALL', label: '全部状态' },
] as const

export type PartnerApplicationState = Exclude<
  (typeof partnerApplicationStatusOptions)[number]['value'],
  'ALL'
>

export function isReviewablePartnerApplication(state: PartnerApplicationState) {
  return ['RECEIVED', 'IN_REVIEW', 'WAITING_FOR_APPLICANT'].includes(state)
}

/** 兼容期：approve/decision 仍是快捷批准与拒绝；transition 只用于非待审状态。 */
