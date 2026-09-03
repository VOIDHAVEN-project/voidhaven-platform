export type ContentKind = 'COMPETITION' | 'RESOURCE' | 'RECRUITMENT'
export type PublicMedia = { id: string; url: string; mediaType: string; width: number; height: number; altText: string }
export type Content = { id: string; kind: ContentKind; title: string; summary: string; body: { version?: number; blocks?: { type: string; text: string }[] }; languageCode: string; languageLabel: string; originalTitle?: string | null; sourceName: string; sourceUrl: string; rightsBasis: string; authorDisplayName: string; reprintPermission: ReusePermission; adaptationPermission: ReusePermission; details: Record<string, unknown>; revision: number; media?: PublicMedia[] }
export type PublicContentPage = { items: Content[]; nextCursor: string | null }
export type ManagedContent = { id: string; kind: ContentKind; workflowStatus: string; publicationStatus: string; visibility: string; recordState: string; title: string; revision: number }
export type ReusePermission = 'NOT_ALLOWED' | 'ATTRIBUTION_ALLOWED'
export type AdminDraft = { kind: ContentKind; title: string; summary: string; body: { version: number; blocks: { type: string; text: string }[] }; languageCode: string; originalTitle?: string; sourceUrl: string; sourceName: string; rightsBasis: string; authorDisplayName: string; reprintPermission: ReusePermission; adaptationPermission: ReusePermission; details: Record<string, unknown> }
export type PartnerDashboard = { displayName: string; quota: number; publicActiveCount: number; contentItems: ManagedContent[] }
export type EditablePartnerContent = { id: string; draft: AdminDraft; revision: number; workflowStatus: string; visibility: string; recordState: string }
export type PendingPartnerApplication = { id: string; displayName: string; introduction: string; portfolioUrl: string | null }
export type PublicSubmission = { id: string; submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'; title: string; message: string; linkUrl: string | null; imageDescription: string | null; state: string }
export type ReviewDecisionResult = { id: string; state: string }
export type ProvisionedPartner = { partnerProfileId: string; loginId: string; activationToken: string }
export type PartnerProfile = { id: string; displayName: string; contentQuota: number; active: boolean }
export type AuditLogEntry = { createdAt: string; actorRole: string; action: string; objectType: string; objectId: string; reason: string | null; requestId: string }
type Envelope<T> = { data: T; requestId: string }
type CsrfPayload = { headerName: string; token: string }
export class ApiFailure extends Error { readonly code: string; constructor(code: string, message: string) { super(message); this.code = code } }
const base = '/api/v1'
const segment: Record<ContentKind, string> = { COMPETITION: 'competitions', RESOURCE: 'resources', RECRUITMENT: 'recruitments' }
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, { credentials: 'include', headers: { Accept: 'application/json', ...init?.headers }, ...init })
  const body = await response.json().catch(() => null) as Envelope<T> | { code?: string; message?: string } | null
  if (!response.ok) throw new ApiFailure((body as { code?: string } | null)?.code ?? 'NETWORK_ERROR', (body as { message?: string } | null)?.message ?? '请求未完成，请稍后重试。')
  if (response.status === 204) return undefined as T
  return (body as Envelope<T>).data
}
async function protectedRequest<T>(path: string, init: RequestInit): Promise<T> {
  const csrf = await request<CsrfPayload>('/auth/csrf')
  return request<T>(path, { ...init, headers: { ...init.headers, [csrf.headerName]: csrf.token } })
}
function write<T>(path: string, body?: unknown): Promise<T> {
  return protectedRequest<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: body === undefined ? undefined : JSON.stringify(body) })
}
function writePatch<T>(path: string, body: unknown): Promise<T> {
  return protectedRequest<T>(path, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(body) })
}
function writeForm<T>(path: string, body: FormData): Promise<T> {
  return protectedRequest<T>(path, { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body })
}
export const api = {
  home: () => request<{ competitions: Content[]; resources: Content[]; recruitments: Content[] }>('/public/home'),
  list: (kind: ContentKind, options: { query?: string; cursor?: string } = {}) => {
    const search = new URLSearchParams({ pageSize: '20' })
    if (options.query?.trim()) search.set('q', options.query.trim())
    if (options.cursor) search.set('cursor', options.cursor)
    return request<PublicContentPage>(`/public/${segment[kind]}?${search.toString()}`)
  },
  detail: (kind: ContentKind, id: string) => request<Content>(`/public/${segment[kind]}/${encodeURIComponent(id)}`),
  submit: (payload: { submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'; title: string; message: string; linkUrl?: string; imageDescription?: string }) => request<{ id: string }>('/public/submissions', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) }),
  partnerApply: (payload: { displayName: string; introduction: string; portfolioUrl?: string }) => request<{ id: string }>('/public/partner-applications', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) }),
  signIn: (loginId: string, password: string) => request<{ loginId: string; roles: string[] }>('/auth/session', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ loginId, password }) }),
  consumeActivation: (token: string, password: string) => request<{ loginId: string; message: string }>('/auth/activation/consume', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }) }),
  me: () => request<{ loginId: string; roles: string[] }>('/auth/me'),
  signOut: () => protectedRequest<void>('/auth/session', { method: 'DELETE' }),
  adminQueue: () => request<ManagedContent[]>('/admin/review-queue'),
  adminContentItems: () => request<ManagedContent[]>('/admin/content-items'),
  adminCreate: (draft: AdminDraft) => write<ManagedContent>('/admin/content-items', draft),
  adminSubmit: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/submit-review`),
  adminDecision: (id: string, decision: 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT', reason?: string) => write<ManagedContent>(`/admin/reviews/${encodeURIComponent(id)}/decision`, { decision, reason }),
  adminPublish: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/publish`),
  adminHide: (id: string, reason = '本地验收操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/hide`, { reason }),
  adminShow: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/show`),
  adminRestore: (id: string, reason = '本地验收恢复操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/restore`, { reason }),
  adminArchive: (id: string, reason = '本地验收操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/archive`, { reason }),
  adminPartnerApplications: () => request<PendingPartnerApplication[]>('/admin/partner-applications'),
  adminApprovePartnerApplication: (id: string, loginId: string) => write<ProvisionedPartner>(`/admin/partner-applications/${encodeURIComponent(id)}/approve`, { loginId }),
  adminDecidePartnerApplication: (id: string, decision: 'CHANGES_REQUESTED' | 'REJECT', reason: string) => write<ReviewDecisionResult>(`/admin/partner-applications/${encodeURIComponent(id)}/decision`, { decision, reason }),
  adminSubmissions: () => request<PublicSubmission[]>('/admin/submissions'),
  adminDecideSubmission: (id: string, decision: 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT', reason: string) => write<ReviewDecisionResult>(`/admin/submissions/${encodeURIComponent(id)}/decision`, { decision, reason }),
  adminAuditLogs: () => request<AuditLogEntry[]>('/admin/audit-logs?limit=30'),
  adminPartnerProfiles: () => request<PartnerProfile[]>('/admin/partner-profiles'),
  adminSetPartnerQuota: (id: string, quota: number, reason: string) => writePatch<PartnerProfile>(`/admin/partner-profiles/${encodeURIComponent(id)}/content-quota`, { quota, reason }),
  adminTransferOwnership: (contentItemId: string, toPartnerProfileId: string, reason: string) => write<{ contentItemId: string; toPartnerProfileId: string }>('/admin/content-ownership-transfers', { contentItemId, toPartnerProfileId, reason }),
  uploadContentMedia: (contentItemId: string, file: File, altText: string) => { const body = new FormData(); body.set('contentItemId', contentItemId); body.set('altText', altText); body.set('file', file); return writeForm<{ id: string; url: string; mediaType: string; width: number; height: number; altText: string }>('/media/uploads', body) },
  partnerDashboard: () => request<PartnerDashboard>('/partner/dashboard'),
  partnerContent: (id: string) => request<EditablePartnerContent>(`/partner/content-items/${encodeURIComponent(id)}`),
  partnerCreate: (draft: AdminDraft) => write<ManagedContent>('/partner/content-items', draft),
  partnerSubmit: (id: string) => write<ManagedContent>(`/partner/content-items/${encodeURIComponent(id)}/submit-review`),
  partnerRevise: (id: string, revision: number, draft: AdminDraft) => protectedRequest<ManagedContent>(`/partner/content-items/${encodeURIComponent(id)}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(revision) }, body: JSON.stringify(draft) }),
}
export const kindMeta: Record<ContentKind, { label: string; path: string; singular: string }> = {
  COMPETITION: { label: '竞赛', path: '/competitions', singular: '竞赛' }, RESOURCE: { label: '学习资源', path: '/resources', singular: '资源' }, RECRUITMENT: { label: '团队招募', path: '/recruitments', singular: '团队招募' },
}
