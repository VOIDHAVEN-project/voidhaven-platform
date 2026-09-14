export const publicSubmissionStatusOptions = [
  { value: 'RECEIVED', label: '已接收' },
  { value: 'IN_PROGRESS', label: '处理中' },
  { value: 'WAITING_FOR_REQUESTER', label: '等待补充' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'ALL', label: '全部状态' },
] as const

export type PublicSubmissionState = Exclude<(typeof publicSubmissionStatusOptions)[number]['value'], 'ALL'>

export function isReviewablePublicSubmission(state: PublicSubmissionState) {
  return ['RECEIVED', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER'].includes(state)
}
