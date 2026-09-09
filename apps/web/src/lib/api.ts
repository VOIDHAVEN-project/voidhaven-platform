import { createIdempotencyKey } from './secure-id'
import type { PartnerApplicationState } from './partner-applications'

export type ContentKind = 'COMPETITION' | 'RESOURCE' | 'RECRUITMENT'
export type ReusePermission = 'NOT_ALLOWED' | 'ATTRIBUTION_ALLOWED'
export type Inline = { type: string; text: string; href?: string; [key: string]: unknown }
export type ContentBlock = {
  type: string
  text?: string
  inlines?: Inline[]
  level?: number
  ordered?: boolean
  items?: Inline[][]
  mediaId?: string
  alt?: string
  language?: string
  [key: string]: unknown
}
export type DocumentBody = { version: number; blocks: ContentBlock[]; [key: string]: unknown }
export type Media = {
  id: string
  url: string
  mediaType: string
  width: number
  height: number
  altText: string
}
export type AdminDraft = {
  kind?: ContentKind
  moduleKey?: string
  schemaVersion?: number
  title: string
  summary: string
  body: DocumentBody
  markdownSource?: string
  languageCode: string
  originalTitle?: string
  sourceUrl?: string
  sourceName?: string
  rightsBasis: string
  authorDisplayName?: string
  reprintPermission: ReusePermission
  adaptationPermission?: ReusePermission
  details: Record<string, unknown>
  [key: string]: unknown
}
export type PublicContent = AdminDraft & {
  id: string
  kind: ContentKind
  languageLabel?: string
  revision: number
  media?: Media[]
}
export type ReviewRecord = { decision: string; reason: string | null; createdAt: string; revisionId: string }
export type ManagedContent = {
  id: string
  kind: ContentKind
  moduleKey: string
  workflowStatus: string
  publicationStatus: string
  visibility: string
  recordState: string
  title: string
  revision: number
  ownerPartnerProfileId?: string | null
  submittedAt?: string | null
  latestReview?: ReviewRecord | null
}
export type ManagementPage<T> = { items: T[]; nextCursor?: string | null }
export type ManagedPage = ManagementPage<ManagedContent>
export type EditablePartnerContent = {
  id: string
  draft: AdminDraft
  revision: number
  workflowStatus: string
  visibility: string
  recordState: string
  publicationStatus: string
  ownerPartnerProfileId?: string | null
  submittedAt?: string | null
  media: Media[]
  reviews: ReviewRecord[]
}
export type ManagementQuery = {
  cursor?: string
  query?: string
  status?: string
  module?: string
  action?: string
  actorAccountId?: string
  objectType?: string
  from?: string
  to?: string
  pageSize?: number
}
export type PartnerDashboard = {
  displayName: string
  quota: number
  publicActiveCount: number
  contentItems: ManagedPage
}
export type PendingPartnerApplication = {
  id: string
  displayName: string
  introduction: string
  portfolioUrl: string | null
  state: PartnerApplicationState
}
export type PublicSubmission = {
  id: string
  submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'
  title: string
  message: string
  linkUrl: string | null
  imageDescription: string | null
  state: string
}
export type ReviewDecisionResult = { id: string; state: string }
export type ProvisionedPartner = { partnerProfileId: string; loginId: string; activationToken: string }
export type PartnerProfile = {
  id: string
  accountId: string
  displayName: string
  contentQuota: number
  active: boolean
  qualificationStatus: string
}
export type AccountStatus = { id: string; loginId: string; qualificationStatus: string; roles: string[] }
export type PublicationReadiness = {
  ready: boolean
  blockers: { code: string; message: string }[]
  partnerProfileId?: string | null
  quota?: number | null
  publicActiveCount?: number | null
}
export type AuditLogEntry = {
  id: string
  createdAt: string
  actorAccountId: string | null
  actorRole: string
  action: string
  objectType: string
  objectId: string
  reason: string | null
  requestId: string
}
export type ModuleField = {
  key: string
  label: string
  type:
    | 'text'
    | 'textarea'
    | 'url'
    | 'date'
    | 'datetime'
    | 'relation'
    | 'select'
    | 'boolean'
    | 'number'
    | 'tags'
  required: boolean
  filterable: boolean
  options?: string[]
  help?: string
}
export type ContentModule = {
  key: string
  label: string
  legacyKind: ContentKind
  active: boolean
  allowsOriginalSource: boolean
  requiresOfficialRules?: boolean
  deadlinePolicy?: string
  template?: string
  fields: ModuleField[]
}
export type ModuleSet = { version: number; modules: ContentModule[] }
export type PublicPage = ManagementPage<PublicContent>
export type SearchGroup = {
  kind: ContentKind
  items: PublicContent[]
  status: 'loading' | 'success' | 'empty' | 'error'
  error?: string
}
export type HomeContent = {
  competitions: PublicContent[]
  resources: PublicContent[]
  recruitments: PublicContent[]
}
export type Submission = {
  submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'
  title: string
  message: string
  linkUrl?: string
  imageDescription?: string
}
export type Identity = { loginId: string; roles: string[] }

type Envelope<T> = { data: T; requestId: string }
type CsrfPayload = { headerName: string; token: string }
type ErrorPayload = {
  code?: string
  message?: string
  requestId?: string
  fieldErrors?: Record<string, string>
  retryable?: boolean
}

export class ApiFailure extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status = 0,
    public readonly requestId = '',
    public readonly fieldErrors: Record<string, string> = {},
    public readonly retryable = status === 0 || status === 429 || status >= 500,
    public readonly retryAfterSeconds?: number,
  ) {
    super(requestId ? `${message}（请求编号：${requestId}）` : message)
  }
}

const base = '/api/v1'
export const contentKinds: ContentKind[] = ['COMPETITION', 'RECRUITMENT', 'RESOURCE']
const segment: Record<ContentKind, string> = {
  COMPETITION: 'competitions',
  RESOURCE: 'resources',
  RECRUITMENT: 'recruitments',
}
const failures: Record<number, string> = {
  401: '登录已失效，请重新登录后继续。',
  403: '当前账号没有执行此操作的权限。',
  404: '内容不存在或已不再公开。',
  409: '内容状态已变化。你的输入仍被保留，请先加载服务器上的最新版本。',
  412: '这份内容已有新版本。你的输入仍被保留，请先核对最新版本。',
  429: '提交过于频繁，请稍后再试。',
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(base + path, {
      ...init,
      credentials: 'include',
      signal: init.signal ?? AbortSignal.timeout(15000),
      headers: { Accept: 'application/json', ...init.headers },
    })
  } catch (error) {
    if (init.signal?.aborted) throw error
    throw new ApiFailure(
      'CONNECTION_ERROR',
      init.method && init.method !== 'GET'
        ? '连接中断，暂时无法确认操作结果。请先刷新相关列表核对，再决定是否重试。'
        : '暂时无法连接内容服务，请确认本地服务已启动后重试。',
    )
  }
  if (response.status === 204) return undefined as T
  const payload = (await response.json().catch(() => null)) as Envelope<T> | ErrorPayload | null
  if (!response.ok) {
    const failure = payload as ErrorPayload | null
    const retry = response.headers.get('Retry-After')
    throw new ApiFailure(
      failure?.code ?? 'REQUEST_FAILED',
      failure?.message ?? failures[response.status] ?? '请求未完成，请稍后重试。',
      response.status,
      failure?.requestId ?? response.headers.get('X-Request-Id') ?? '',
      failure?.fieldErrors ?? {},
      failure?.retryable,
      retry && /^\d+$/.test(retry) ? Number(retry) : undefined,
    )
  }
  if (!payload || !Object.prototype.hasOwnProperty.call(payload, 'data'))
    throw new ApiFailure('INVALID_RESPONSE', '内容服务返回了非预期页面，请检查本地代理地址。')
  return (payload as Envelope<T>).data
}

async function protectedRequest<T>(path: string, init: RequestInit) {
  const csrf = await request<CsrfPayload>('/auth/csrf')
  return request<T>(path, { ...init, headers: { ...init.headers, [csrf.headerName]: csrf.token } })
}
const jsonWrite = (body?: unknown, idempotencyKey?: string): RequestInit => ({
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Idempotency-Key': idempotencyKey ?? createIdempotencyKey(),
  },
  body: body === undefined ? undefined : JSON.stringify(body),
})
const write = <T>(path: string, body?: unknown, key?: string) =>
  protectedRequest<T>(path, jsonWrite(body, key))
const itemPath = (role: string, id: string) => `/${role}/content-items/${encodeURIComponent(id)}`
const revise = (role: string, id: string, revision: number, draft: AdminDraft) => {
  const init = jsonWrite(draft)
  return protectedRequest<ManagedContent>(itemPath(role, id), {
    ...init,
    method: 'PATCH',
    headers: { ...init.headers, 'If-Match': String(revision) },
  })
}
function queryString(options: ManagementQuery = {}) {
  const params = new URLSearchParams({ pageSize: String(options.pageSize ?? 20) })
  if (options.cursor) params.set('cursor', options.cursor)
  if (options.query?.trim()) params.set('q', options.query.trim())
  if (options.status) params.set('status', options.status)
  if (options.module) params.set('module', options.module)
  if (options.action) params.set('action', options.action)
  if (options.actorAccountId) params.set('actorAccountId', options.actorAccountId)
  if (options.objectType) params.set('objectType', options.objectType)
  if (options.from) params.set('from', options.from)
  if (options.to) params.set('to', options.to)
  return params.toString()
}
function queryOptions(value: ManagementQuery | AbortSignal | undefined): ManagementQuery {
  return value instanceof AbortSignal ? {} : (value ?? {})
}
function publicList(kind: ContentKind, query = '', cursor?: string, pageSize = 20, signal?: AbortSignal) {
  const params = new URLSearchParams({ pageSize: String(pageSize) })
  if (query.trim()) params.set('q', query.trim())
  if (cursor) params.set('cursor', cursor)
  return request<PublicPage>(`/public/${segment[kind]}?${params}`, { signal })
}

export const categories = {
  COMPETITION: {
    label: '竞赛',
    path: '/competitions',
    caption: 'OPPORTUNITIES',
    description: '从一份可靠的信息开始，找到下一次值得投入的机会。',
  },
  RESOURCE: {
    label: '学习资源',
    path: '/resources',
    caption: 'THE READING ROOM',
    description: '收藏值得反复阅读的路径，让好奇心有一个可以落脚的地方。',
  },
  RECRUITMENT: {
    label: '团队招募',
    path: '/recruitments',
    caption: 'BETTER, TOGETHER',
    description: '让有共同方向的人相遇，从了解一项具体的计划开始。',
  },
}

export const api = {
  home: (signal?: AbortSignal) => request<HomeContent>('/public/home', { signal }),
  publicModules: (signal?: AbortSignal) => request<ModuleSet>('/public/content-modules', { signal }),
  modules: (signal?: AbortSignal) => request<ModuleSet>('/content-modules', { signal }),
  list: (kind: ContentKind, query = '', cursor?: string, signal?: AbortSignal) =>
    publicList(kind, query, cursor, 20, signal),
  listModule: (
    moduleKey: string,
    options: { query?: string; cursor?: string; filterKey?: string; filterValue?: string } = {},
    signal?: AbortSignal,
  ) => {
    const params = new URLSearchParams({ pageSize: '20', module: moduleKey })
    if (options.query?.trim()) params.set('q', options.query.trim())
    if (options.cursor) params.set('cursor', options.cursor)
    if (options.filterKey && options.filterValue) {
      params.set('filterKey', options.filterKey)
      params.set('filterValue', options.filterValue)
    }
    return request<PublicPage>(`/public/content?${params}`, { signal })
  },
  searchKind: (kind: ContentKind, query: string, signal?: AbortSignal) =>
    publicList(kind, query, undefined, 6, signal),
  searchAll: async (query: string, signal?: AbortSignal): Promise<SearchGroup[]> => {
    const results = await Promise.allSettled(
      contentKinds.map((kind) => publicList(kind, query, undefined, 6, signal)),
    )
    return results.map((result, index) =>
      result.status === 'fulfilled'
        ? {
            kind: contentKinds[index],
            items: result.value.items,
            status: result.value.items.length ? 'success' : 'empty',
          }
        : {
            kind: contentKinds[index],
            items: [],
            status: 'error',
            error: result.reason instanceof Error ? result.reason.message : '搜索暂时不可用。',
          },
    )
  },
  detail: (id: string, signal?: AbortSignal) =>
    request<PublicContent>('/public/content/' + encodeURIComponent(id), { signal }),
  submit: (body: Submission, key: string = createIdempotencyKey()) =>
    request<{ id: string }>('/public/submissions', jsonWrite(body, key)),
  partnerApply: (
    body: { displayName: string; introduction: string; portfolioUrl?: string },
    key: string = createIdempotencyKey(),
  ) => request<{ id: string }>('/public/partner-applications', jsonWrite(body, key)),
  signIn: (loginId: string, password: string) =>
    request<Identity>('/auth/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginId, password }),
    }),
  consumeActivation: (token: string, password: string) =>
    request<{ loginId: string; message: string }>('/auth/activation/consume', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, password }),
    }),
  me: () => request<Identity>('/auth/me'),
  signOut: () => protectedRequest<void>('/auth/session', { method: 'DELETE' }),
  adminQueue: (options: ManagementQuery = {}) =>
    request<ManagedPage>(`/admin/review-queue?${queryString(options)}`),
  adminContentItems: (options: ManagementQuery | AbortSignal = {}) =>
    request<ManagedPage>(
      `/admin/content-items?${queryString(queryOptions(options))}`,
      options instanceof AbortSignal ? { signal: options } : {},
    ),
  adminContent: (id: string) => request<EditablePartnerContent>(itemPath('admin', id)),
  adminPublicationReadiness: (id: string) =>
    request<PublicationReadiness>(itemPath('admin', id) + '/publication-readiness'),
  adminCreate: (draft: AdminDraft) => write<ManagedContent>('/admin/content-items', draft),
  adminRevise: (id: string, revision: number, draft: AdminDraft) => revise('admin', id, revision, draft),
  adminSubmit: (id: string) => write<ManagedContent>(itemPath('admin', id) + '/submit-review'),
  adminDecision: (
    id: string,
    revision: number,
    decision: 'APPROVE' | 'CHANGES_REQUESTED' | 'REJECT',
    reason?: string,
  ) => {
    const init = jsonWrite({ decision, reason })
    return protectedRequest<ManagedContent>(`/admin/reviews/${encodeURIComponent(id)}/decision`, {
      ...init,
      headers: { ...init.headers, 'If-Match': String(revision) },
    })
  },
  adminReopen: (id: string, reason: string) =>
    write<ManagedContent>(itemPath('admin', id) + '/reopen', { reason }),
  adminPublish: (id: string) => write<ManagedContent>(itemPath('admin', id) + '/publish'),
  adminHide: (id: string, reason: string) =>
    write<ManagedContent>(itemPath('admin', id) + '/hide', { reason }),
  adminShow: (id: string) => write<ManagedContent>(itemPath('admin', id) + '/show'),
  adminRestore: (id: string, reason: string) =>
    write<ManagedContent>(itemPath('admin', id) + '/restore', { reason }),
  adminArchive: (id: string, reason: string) =>
    write<ManagedContent>(itemPath('admin', id) + '/archive', { reason }),
  adminPartnerApplications: (options: ManagementQuery | AbortSignal = {}) =>
    request<ManagementPage<PendingPartnerApplication>>(
      `/admin/partner-applications?${queryString(queryOptions(options))}`,
      options instanceof AbortSignal ? { signal: options } : {},
    ),
  adminApprovePartnerApplication: (id: string, loginId: string, reason: string) =>
    write<ProvisionedPartner>(`/admin/partner-applications/${encodeURIComponent(id)}/approve`, {
      loginId,
      reason,
    }),
  adminDecidePartnerApplication: (id: string, reason: string) =>
    write<ReviewDecisionResult>(`/admin/partner-applications/${encodeURIComponent(id)}/decision`, {
      decision: 'REJECT',
      reason,
    }),
  adminSubmissions: (options: ManagementQuery = {}) =>
    request<ManagementPage<PublicSubmission>>(`/admin/submissions?${queryString(options)}`),
  adminDecideSubmission: (id: string, decision: 'APPROVE' | 'REJECT', reason: string) =>
    write<ReviewDecisionResult>(`/admin/submissions/${encodeURIComponent(id)}/decision`, {
      decision,
      reason,
    }),
  adminPartnerProfiles: (options: ManagementQuery | AbortSignal = {}) =>
    request<ManagementPage<PartnerProfile>>(
      `/admin/partner-profiles?${queryString(queryOptions(options))}`,
      options instanceof AbortSignal ? { signal: options } : {},
    ),
  adminSetPartnerQuota: (id: string, quota: number, reason: string) =>
    protectedRequest<PartnerProfile>(`/admin/partner-profiles/${encodeURIComponent(id)}/content-quota`, {
      ...jsonWrite({ quota, reason }),
      method: 'PATCH',
    }),
  adminTransferOwnership: (contentItemId: string, toPartnerProfileId: string, reason: string) =>
    write<{ contentItemId: string; toPartnerProfileId: string }>('/admin/content-ownership-transfers', {
      contentItemId,
      toPartnerProfileId,
      reason,
    }),
  adminAccounts: (options: ManagementQuery = {}) =>
    request<ManagementPage<AccountStatus>>(`/admin/accounts?${queryString(options)}`),
  adminSuspendAccount: (id: string, reason: string) =>
    write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/suspend`, { reason }),
  adminReactivateAccount: (id: string, reason: string) =>
    write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/reactivate`, { reason }),
  adminRevokeAccount: (id: string, reason: string) =>
    write<AccountStatus>(`/admin/accounts/${encodeURIComponent(id)}/revoke`, { reason }),
  adminAuditLogs: (options: ManagementQuery = {}) =>
    request<ManagementPage<AuditLogEntry>>(`/admin/audit-logs?${queryString(options)}`),
  uploadContentMedia: (
    contentItemId: string,
    file: File,
    altText: string,
    key: string = createIdempotencyKey(),
  ) => {
    const body = new FormData()
    body.set('contentItemId', contentItemId)
    body.set('altText', altText)
    body.set('file', file)
    return protectedRequest<Media>('/media/uploads', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body,
    })
  },
  partnerDashboard: (options: ManagementQuery | AbortSignal = {}) =>
    request<PartnerDashboard>(
      `/partner/dashboard?${queryString(queryOptions(options))}`,
      options instanceof AbortSignal ? { signal: options } : {},
    ),
  partnerContent: (id: string) => request<EditablePartnerContent>(itemPath('partner', id)),
  partnerCreate: (draft: AdminDraft) => write<ManagedContent>('/partner/content-items', draft),
  partnerSubmit: (id: string) => write<ManagedContent>(itemPath('partner', id) + '/submit-review'),
  partnerRevise: (id: string, revision: number, draft: AdminDraft) => revise('partner', id, revision, draft),
}
