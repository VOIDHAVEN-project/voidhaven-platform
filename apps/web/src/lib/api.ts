export type ContentKind = 'COMPETITION' | 'RESOURCE' | 'RECRUITMENT'
export type ReusePermission = 'NOT_ALLOWED' | 'ATTRIBUTION_ALLOWED'
export type PublicMedia = { id: string; url: string; mediaType: string; width: number; height: number; altText: string }
export type Inline = { type: 'text' | 'strong' | 'emphasis' | 'code' | 'link'; text: string; href?: string }
export type DocumentBlock =
  | { type: 'paragraph' | 'quote'; inlines: Inline[] }
  | { type: 'heading'; level: number; inlines: Inline[] }
  | { type: 'list'; ordered: boolean; items: Inline[][] }
  | { type: 'code'; language: string; text: string }
  | { type: 'image'; mediaId: string; alt: string }
  | { type: 'rule' }
  | { type: 'paragraph'; text: string }
export type ContentDocument = { version: number; blocks: DocumentBlock[] }
export type ContentField = { key: string; label: string; type: 'text' | 'textarea' | 'url' | 'date' | 'datetime' | 'relation' | 'select' | 'boolean' | 'number' | 'tags'; required: boolean; filterable: boolean; options?: string[]; help?: string }
export type ContentModule = { key: string; label: string; legacyKind: ContentKind; active: boolean; allowsOriginalSource: boolean; requiresOfficialRules: boolean; deadlinePolicy: string; template: string; fields: ContentField[] }
export type ContentModuleSet = { version: number; modules: ContentModule[] }
export type Content = { id: string; kind: ContentKind; moduleKey: string; schemaVersion: number; title: string; summary: string; body: ContentDocument; languageCode: string; languageLabel: string; originalTitle?: string | null; sourceName: string; sourceUrl?: string | null; rightsBasis: string; authorDisplayName: string; reprintPermission: ReusePermission; adaptationPermission: ReusePermission; details: Record<string, unknown>; revision: number; media?: PublicMedia[] }
export type PublicContentPage = { items: Content[]; nextCursor: string | null }
export type ReviewRecord = { decision: string; reason: string | null; createdAt: string; revisionId: string }
export type ManagedContent = { id: string; kind: ContentKind; moduleKey: string; workflowStatus: string; publicationStatus: string; visibility: string; recordState: string; title: string; revision: number; ownerPartnerProfileId?: string | null; submittedAt?: string | null; latestReview?: ReviewRecord | null }
export type AdminDraft = { kind?: ContentKind; moduleKey: string; schemaVersion: number; title: string; summary: string; body: ContentDocument; markdownSource: string; languageCode: string; originalTitle?: string; sourceUrl?: string; sourceName?: string; rightsBasis: string; authorDisplayName: string; reprintPermission: ReusePermission; adaptationPermission: ReusePermission; details: Record<string, unknown> }
export type EditableContent = { id: string; draft: AdminDraft; revision: number; workflowStatus: string; visibility: string; recordState: string; publicationStatus: string; ownerPartnerProfileId?: string | null; submittedAt?: string | null; media: PublicMedia[]; reviews: ReviewRecord[] }
export type ManagementPage<T> = { items: T[]; nextCursor: string | null }
export type ManagementQuery = { cursor?: string; query?: string; status?: string; module?: string; action?: string; actorAccountId?: string; objectType?: string; from?: string; to?: string; pageSize?: number }
export type PartnerDashboard = { displayName: string; quota: number; publicActiveCount: number; contentItems: ManagementPage<ManagedContent> }
export type PendingPartnerApplication = { id: string; displayName: string; introduction: string; portfolioUrl: string | null; state: string }
export type PublicSubmission = { id: string; submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'; title: string; message: string; linkUrl: string | null; imageDescription: string | null; state: string }
export type ReviewDecisionResult = { id: string; state: string }
export type ProvisionedPartner = { partnerProfileId: string; loginId: string; activationToken: string }
export type PartnerProfile = { id: string; accountId: string; displayName: string; contentQuota: number; active: boolean; qualificationStatus: string }
export type AccountStatus = { id: string; loginId: string; qualificationStatus: string; roles: string[] }
export type PublicationReadiness = { ready: boolean; blockers: { code: string; message: string }[]; partnerProfileId?: string | null; quota?: number | null; publicActiveCount?: number | null }
export type AuditLogEntry = { id: string; createdAt: string; actorAccountId: string | null; actorRole: string; action: string; objectType: string; objectId: string; reason: string | null; requestId: string }
type Envelope<T> = { data: T; requestId: string }
type CsrfPayload = { headerName: string; token: string }
type ErrorPayload = { code?: string; message?: string; requestId?: string; fieldErrors?: Record<string, string>; retryable?: boolean }
export class ApiFailure extends Error {
  readonly code: string
  readonly status: number
  readonly requestId: string
  readonly retryable: boolean
  readonly fieldErrors: Record<string, string>
  constructor(code: string, message: string, status = 0, requestId = '', fieldErrors: Record<string, string> = {}, retryable?: boolean) {
    super(requestId ? `${message}（请求编号：${requestId}）` : message)
    this.code = code; this.status = status; this.requestId = requestId; this.fieldErrors = fieldErrors
    this.retryable = retryable ?? (status === 0 || status === 429 || status >= 500)
  }
}
const base = '/api/v1'
const segment: Record<ContentKind, string> = { COMPETITION: 'competitions', RESOURCE: 'resources', RECRUITMENT: 'recruitments' }

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    const response = await fetch(`${base}${path}`, { credentials: 'include', headers: { Accept: 'application/json', ...init?.headers }, ...init })
    const body = await response.json().catch(() => null) as Envelope<T> | ErrorPayload | null
    if (!response.ok) {
      const failure = body as ErrorPayload | null
      throw new ApiFailure(failure?.code ?? 'REQUEST_FAILED', failure?.message ?? '请求未完成，请稍后重试。', response.status,
        failure?.requestId ?? response.headers.get('X-Request-Id') ?? '', failure?.fieldErrors ?? {}, failure?.retryable)
    }
    if (response.status === 204) return undefined as T
    return (body as Envelope<T>).data
  } catch (error) {
    if (error instanceof ApiFailure) throw error
    throw new ApiFailure('NETWORK_ERROR', '无法连接到服务，请检查网络或稍后重试。')
  }
}
async function protectedRequest<T>(path: string, init: RequestInit): Promise<T> {
  const csrf = await request<CsrfPayload>('/auth/csrf')
  return request<T>(path, { ...init, headers: { ...init.headers, [csrf.headerName]: csrf.token } })
}
function write<T>(path: string, body?: unknown): Promise<T> { return protectedRequest<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: body === undefined ? undefined : JSON.stringify(body) }) }
function writePatch<T>(path: string, body: unknown): Promise<T> { return protectedRequest<T>(path, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(body) }) }
function writeForm<T>(path: string, body: FormData, idempotencyKey: string = crypto.randomUUID()): Promise<T> { return protectedRequest<T>(path, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body }) }
function managementQuery(options: ManagementQuery = {}) {
  const search = new URLSearchParams({ pageSize: String(options.pageSize ?? 20) })
  if (options.cursor) search.set('cursor', options.cursor)
  if (options.query?.trim()) search.set('q', options.query.trim())
  if (options.status) search.set('status', options.status)
  if (options.module) search.set('module', options.module)
  if (options.action) search.set('action', options.action)
  if (options.actorAccountId) search.set('actorAccountId', options.actorAccountId)
  if (options.objectType) search.set('objectType', options.objectType)
  if (options.from) search.set('from', options.from)
  if (options.to) search.set('to', options.to)
  return search.toString()
}

export const api = {
  home: () => request<{ competitions: Content[]; resources: Content[]; recruitments: Content[] }>('/public/home'),
  publicModules: () => request<ContentModuleSet>('/public/content-modules'),
  modules: () => request<ContentModuleSet>('/content-modules'),
  list: (kind: ContentKind, options: { query?: string; cursor?: string } = {}) => {
    const search = new URLSearchParams({ pageSize: '20' }); if (options.query?.trim()) search.set('q', options.query.trim()); if (options.cursor) search.set('cursor', options.cursor)
    return request<PublicContentPage>(`/public/${segment[kind]}?${search.toString()}`)
  },
  listModule: (moduleKey: string, options: { query?: string; cursor?: string; filterKey?: string; filterValue?: string } = {}) => {
    const search = new URLSearchParams({ pageSize: '20', module: moduleKey }); if (options.query?.trim()) search.set('q', options.query.trim()); if (options.cursor) search.set('cursor', options.cursor)
    if (options.filterKey && options.filterValue) { search.set('filterKey', options.filterKey); search.set('filterValue', options.filterValue) }
    return request<PublicContentPage>(`/public/content?${search.toString()}`)
  },
  detail: (kind: ContentKind, id: string) => request<Content>(`/public/${segment[kind]}/${encodeURIComponent(id)}`),
  detailById: (id: string) => request<Content>(`/public/content/${encodeURIComponent(id)}`),
  submit: (payload: { submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'; title: string; message: string; linkUrl?: string; imageDescription?: string }, idempotencyKey: string = crypto.randomUUID()) => request<{ id: string }>('/public/submissions', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(payload) }),
  partnerApply: (payload: { displayName: string; introduction: string; portfolioUrl?: string }, idempotencyKey: string = crypto.randomUUID()) => request<{ id: string }>('/public/partner-applications', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(payload) }),
  signIn: (loginId: string, password: string) => request<{ loginId: string; roles: string[] }>('/auth/session', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ loginId, password }) }),
  consumeActivation: (token: string, password: string) => request<{ loginId: string; message: string }>('/auth/activation/consume', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, password }) }),
  me: () => request<{ loginId: string; roles: string[] }>('/auth/me'), signOut: () => protectedRequest<void>('/auth/session', { method: 'DELETE' }),
  adminQueue: (options: ManagementQuery = {}) => request<ManagementPage<ManagedContent>>(`/admin/review-queue?${managementQuery(options)}`), adminContentItems: (options: ManagementQuery = {}) => request<ManagementPage<ManagedContent>>(`/admin/content-items?${managementQuery(options)}`), adminContent: (id: string) => request<EditableContent>(`/admin/content-items/${encodeURIComponent(id)}`), adminPublicationReadiness: (id: string) => request<PublicationReadiness>(`/admin/content-items/${encodeURIComponent(id)}/publication-readiness`),
  adminCreate: (draft: AdminDraft) => write<ManagedContent>('/admin/content-items', draft), adminRevise: (id: string, revision: number, draft: AdminDraft) => protectedRequest<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(revision) }, body: JSON.stringify(draft) }),
  adminSubmit: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/submit-review`), adminDecision: (id: string, revision: number, decision: 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT', reason?: string) => protectedRequest<ManagedContent>(`/admin/reviews/${encodeURIComponent(id)}/decision`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(revision) }, body: JSON.stringify({ decision, reason }) }), adminReopen: (id: string, reason: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/reopen`, { reason }), adminPublish: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/publish`), adminHide: (id: string, reason = '本地验收操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/hide`, { reason }), adminShow: (id: string) => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/show`), adminRestore: (id: string, reason = '本地验收恢复操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/restore`, { reason }), adminArchive: (id: string, reason = '本地验收操作') => write<ManagedContent>(`/admin/content-items/${encodeURIComponent(id)}/archive`, { reason }),
  adminPartnerApplications: (options: ManagementQuery = {}) => request<ManagementPage<PendingPartnerApplication>>(`/admin/partner-applications?${managementQuery(options)}`), adminApprovePartnerApplication: (id: string, loginId: string, reason: string) => write<ProvisionedPartner>(`/admin/partner-applications/${encodeURIComponent(id)}/approve`, { loginId, reason }), adminDecidePartnerApplication: (id: string, reason: string) => write<ReviewDecisionResult>(`/admin/partner-applications/${encodeURIComponent(id)}/decision`, { decision: 'REJECT', reason }), adminSubmissions: (options: ManagementQuery = {}) => request<ManagementPage<PublicSubmission>>(`/admin/submissions?${managementQuery(options)}`), adminDecideSubmission: (id: string, decision: 'APPROVE' | 'REJECT', reason: string) => write<ReviewDecisionResult>(`/admin/submissions/${encodeURIComponent(id)}/decision`, { decision, reason }), adminAuditLogs: (options: ManagementQuery = {}) => request<ManagementPage<AuditLogEntry>>(`/admin/audit-logs?${managementQuery(options)}`), adminPartnerProfiles: (options: ManagementQuery = {}) => request<ManagementPage<PartnerProfile>>(`/admin/partner-profiles?${managementQuery(options)}`), adminSetPartnerQuota: (id: string, quota: number, reason: string) => writePatch<PartnerProfile>(`/admin/partner-profiles/${encodeURIComponent(id)}/content-quota`, { quota, reason }), adminTransferOwnership: (contentItemId: string, toPartnerProfileId: string, reason: string) => write<{ contentItemId: string; toPartnerProfileId: string }>('/admin/content-ownership-transfers', { contentItemId, toPartnerProfileId, reason }),
  adminAccounts: (options: ManagementQuery = {}) => request<ManagementPage<AccountStatus>>(`/admin/accounts?${managementQuery(options)}`), adminSuspendAccount: (id: string, reason: string) => write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/suspend`, { reason }), adminReactivateAccount: (id: string, reason: string) => write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/reactivate`, { reason }), adminRevokeAccount: (id: string, reason: string) => write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/revoke`, { reason }),
  uploadContentMedia: (contentItemId: string, file: File, altText: string, idempotencyKey?: string) => { const body = new FormData(); body.set('contentItemId', contentItemId); body.set('altText', altText); body.set('file', file); return writeForm<PublicMedia>('/media/uploads', body, idempotencyKey) },
  partnerDashboard: (options: ManagementQuery = {}) => request<PartnerDashboard>(`/partner/dashboard?${managementQuery(options)}`), partnerContent: (id: string) => request<EditableContent>(`/partner/content-items/${encodeURIComponent(id)}`), partnerCreate: (draft: AdminDraft) => write<ManagedContent>('/partner/content-items', draft), partnerSubmit: (id: string) => write<ManagedContent>(`/partner/content-items/${encodeURIComponent(id)}/submit-review`), partnerRevise: (id: string, revision: number, draft: AdminDraft) => protectedRequest<ManagedContent>(`/partner/content-items/${encodeURIComponent(id)}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), 'If-Match': String(revision) }, body: JSON.stringify(draft) }),
}

export const kindMeta: Record<ContentKind, { label: string; path: string; singular: string }> = { COMPETITION: { label: '竞赛', path: '/competitions', singular: '竞赛' }, RESOURCE: { label: '学习资源', path: '/resources', singular: '资源' }, RECRUITMENT: { label: '团队招募', path: '/recruitments', singular: '团队招募' } }
