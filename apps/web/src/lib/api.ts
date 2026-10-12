import { createIdempotencyKey } from './secure-id'
import type { PartnerApplicationState } from './partner-applications'
import type { PublicSubmissionState } from './public-submissions'

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
  resourceConfigVersion?: number
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
  resourcePresentation?: { category?: string | null; fields: ResourceField[] } | null
  id: string
  kind: ContentKind
  languageLabel?: string
  revision: number
  media?: Media[]
  operationalStatus?: 'OPEN' | 'PAUSED' | 'FILLED' | 'EXPIRED' | 'CANCELLED' | null
  actions?: PublicAction[]
  verification?: { outcome: 'VALID' | 'INVALID' | 'NEEDS_REVIEW'; checkedAt: string } | null
  updatedAt?: string | null
}
export type PresentationType = 'LINK' | 'QR_CODE' | 'TEXT'
export type ContactChannel = 'URL' | 'EMAIL' | 'WECHAT' | 'QQ' | 'PHONE' | 'OTHER'
export type EntryAccessLevel = 'PUBLIC' | 'COMMUNITY_GATED'
export type PublicAction = {
  id: string
  presentationType: PresentationType
  contactChannel: ContactChannel
  label: string
  value?: string | null
  mediaUrl?: string | null
  accessLevel: EntryAccessLevel
}
export type ContentActionInput = Omit<PublicAction, 'id' | 'mediaUrl'> & {
  mediaAssetId?: string | null
  publicExposureConfirmed: boolean
  enabled: boolean
  sortOrder: number
}
export type ManagedAction = Omit<ContentActionInput, 'publicExposureConfirmed'> & { id: string }
export type ContentActionSet = {
  contentId: string
  version: number
  workflowStatus: string
  candidateCreated: boolean
  actions: ManagedAction[]
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
  operationalStatus?: string | null
}
export type ManagementPage<T> = { items: T[]; nextCursor?: string | null }
export type ManagedPage = ManagementPage<ManagedContent>
export type EditablePartnerContent = {
  resourceConfig?: ResourceConfig | null
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
  operationalStatus?: string | null
}
export type ManagementQuery = {
  cursor?: string
  query?: string
  status?: string
  module?: string
  verification?: 'attention' | 'due' | ''
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
  organization?: OrganizationIdentity
}
export type PendingPartnerApplication = {
  id: string
  displayName: string
  introduction: string
  portfolioUrl: string | null
  state: PartnerApplicationState
  publicResult?: string | null
  dueAt?: string | null
  version: number
  contactAvailable: boolean
}
export type PublicSubmission = {
  id: string
  submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'
  title: string
  message: string
  linkUrl: string | null
  imageDescription: string | null
  state: PublicSubmissionState
  publicResult?: string | null
  dueAt?: string | null
  version: number
  contactAvailable: boolean
}
export type CreatedCase = { id: string; queryCode: string }
export type CaseStatus = {
  id: string
  status: string
  publicResult?: string | null
  dueAt?: string | null
  createdAt: string
  updatedAt: string
}
export type ProductCapabilities = {
  privateContactEnabled: boolean
  communityGateEnabled: boolean
  gatedRecruitmentEnabled: boolean
  statusQueryEnabled: boolean
  projectJoinEnabled: boolean
}
export type ContentVerification = {
  id: string
  contentItemId: string
  outcome: 'VALID' | 'INVALID' | 'NEEDS_REVIEW'
  detailSummary?: string | null
  checkedByAccountId?: string | null
  checkedAt: string
  nextCheckAt?: string | null
}
export type CommunityGrant = { grant: string; expiresAt: string }
export type CommunityEntry = {
  id: string
  name: string
  description?: string | null
  presentationType: PresentationType
  contactChannel: ContactChannel
  accessLevel: EntryAccessLevel
  value?: string | null
  mediaUrl?: string | null
  primary: boolean
  sortOrder: number
}
export type OperationalTask = { code: string; label: string; count: number; href: string; severity: string }
export type CommunityEntrySummary = {
  id: string
  name: string
  description?: string | null
  presentationType: PresentationType
  contactChannel: ContactChannel
  accessLevel: EntryAccessLevel
  mediaAssetId?: string | null
  primary: boolean
  active: boolean
  sortOrder: number
  version: number
}
export type ProjectJoinEntry = {
  id: string
  name: string
  description?: string | null
  presentationType: PresentationType
  contactChannel: ContactChannel
  accessLevel: EntryAccessLevel
  value?: string | null
  mediaUrl?: string | null
  sortOrder: number
}
export type ProjectJoinEntrySummary = {
  id: string
  name: string
  description?: string | null
  presentationType: PresentationType
  contactChannel: ContactChannel
  accessLevel: EntryAccessLevel
  mediaAssetId?: string | null
  active: boolean
  sortOrder: number
  version: number
}
export type ProjectJoinEntryInput = {
  name: string
  description?: string
  presentationType: PresentationType
  contactChannel: ContactChannel
  accessLevel: EntryAccessLevel
  value?: string
  mediaAssetId?: string
  publicExposureConfirmed: boolean
  sortOrder: number
}
export type CommunityInviteBatch = {
  id: string
  label: string
  validFrom: string
  expiresAt: string
  maxUses: number
  useCount: number
  state: string
  createdAt: string
}
export type HomepageSlot = 'FOCUS' | 'COMPETITION' | 'RECRUITMENT' | 'RESOURCE'
export type HomepagePlacement = {
  id?: string
  slot: HomepageSlot
  contentItemId: string
  sortOrder: number
  startsAt?: string | null
  endsAt?: string | null
  version?: number
  title?: string | null
  kind?: ContentKind | null
  operationalStatus?: string | null
  updatedAt?: string | null
  deadline?: string | null
  publicEligible?: boolean
  issue?: string | null
}
export type HomepageCuration = {
  version: number
  placements: HomepagePlacement[]
}
export type HomepageCandidate = {
  id: string
  title: string
  kind: ContentKind
  operationalStatus?: string | null
  updatedAt?: string | null
  deadline?: string | null
  publicEligible: boolean
  issue?: string | null
}
export type CatalogOrderEntry = {
  id?: string
  contentItemId: string
  sortOrder: number
  title?: string | null
  kind?: ContentKind | null
  operationalStatus?: string | null
  updatedAt?: string | null
  deadline?: string | null
  publicEligible?: boolean
  issue?: string | null
}
export type CatalogOrderCuration = {
  version: number
  kind: ContentKind
  defaultSort: string
  entries: CatalogOrderEntry[]
}
export type CatalogOrderCandidate = HomepageCandidate
export type ReviewDecisionResult = { id: string; state: string }
export type ProvisionedPartner = {
  partnerProfileId: string
  accountId: string
  loginId: string
  qualificationStatus: string
}
export type OrganizationIdentity = {
  id: string
  displayName: string
  accountKind: 'MAIN' | 'MEMBER'
  memberQuota: number
  memberCount: number
  version: number
}
export type PartnerMember = { id: string; loginId: string; qualificationStatus: string }
export type OrganizationState = {
  id: string
  active: boolean
  memberQuota: number
  memberCount: number
  version: number
}
export type PartnerProfile = {
  id: string
  accountId: string
  displayName: string
  contentQuota: number
  memberQuota: number
  memberCount: number
  version: number
  active: boolean
  qualificationStatus: string
}
export type AccountStatus = { id: string; loginId: string; qualificationStatus: string; roles: string[] }
export type CreatedAdminAccount = AccountStatus & { activationToken: string; activationExpiresAt: string }
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
  deprecated?: boolean
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
  optionLabels?: Record<string, string>
  options?: string[]
  help?: string
}
export type ResourceCategory = { key: string; label: string; enabled: boolean }
export type ResourceField = ResourceCategory & {
  type: 'text' | 'textarea' | 'date' | 'url' | 'number' | 'select' | 'tags'
  help: string
  options: string[]
}
export type ResourceTemplate = ResourceCategory & { markdown: string; fieldKeys: string[] }
export type ResourceConfig = {
  version: number
  categories: ResourceCategory[]
  fields: ResourceField[]
  templates: ResourceTemplate[]
}
export type ContentModule = {
  resourceConfig?: ResourceConfig
  createVisible?: boolean
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
export type PublicPage = ManagementPage<PublicContent> & { sort?: string | null }
export type CatalogListQuery = {
  query?: string
  cursor?: string
  sort?: string
  filterKey?: string
  filterValue?: string
  status?: string
}
export type SearchGroup = {
  kind: ContentKind
  items: PublicContent[]
  status: 'loading' | 'success' | 'empty' | 'error'
  error?: string
}
export type HomeContent = {
  focus: PublicContent[]
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
  contact?: string
}
export type AdminGrant = {
  permission: string
  scopeType: 'GLOBAL' | 'MODULES' | 'ORGANIZATIONS'
  scopeIds: string[]
}
export type AdminAuthorization = {
  grants: AdminGrant[]
  longTerm: boolean
  expiresAt?: string | null
  version: number
}
export type AdminAuthorizationInput = Omit<AdminAuthorization, 'version'> & { reason: string }
export type AuthorizationPreview = {
  before: AdminAuthorization
  proposed: AdminAuthorization
  added: string[]
  removed: string[]
  impacts: string[]
}
export type PermissionDefinition = { code: string; label: string; scopeTypes: AdminGrant['scopeType'][] }
export type AuthorizationHistory = {
  id: string
  createdAt: string
  actorAccountId: string
  before: AdminAuthorization
  after: AdminAuthorization
  reason: string
}
export type Identity = {
  loginId: string
  roles: string[]
  accountId?: string
  permissions?: AdminGrant[]
  authorizationExpiresAt?: string | null
  organization?: OrganizationIdentity | null
}

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
    if (response.status === 401 && path !== '/auth/me' && path !== '/auth/session')
      window.dispatchEvent(new Event('voidhaven:session-expired'))
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
function toHomepageWrite(placement: HomepagePlacement) {
  return {
    slot: placement.slot,
    contentItemId: placement.contentItemId,
    sortOrder: placement.sortOrder,
    startsAt: placement.startsAt ?? null,
    endsAt: placement.endsAt ?? null,
  }
}
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
  if (options.verification) params.set('verification', options.verification)
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
function publicList(
  kind: ContentKind,
  query = '',
  cursor?: string,
  pageSize = 20,
  signal?: AbortSignal,
  extra: CatalogListQuery = {},
) {
  const params = new URLSearchParams({ pageSize: String(pageSize) })
  const q = extra.query ?? query
  const nextCursor = extra.cursor ?? cursor
  if (q.trim()) params.set('q', q.trim())
  if (nextCursor) params.set('cursor', nextCursor)
  if (extra.sort) params.set('sort', extra.sort)
  if (extra.filterKey && extra.filterValue) {
    params.set('filterKey', extra.filterKey)
    params.set('filterValue', extra.filterValue)
  }
  if (extra.status) params.set('status', extra.status)
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
    label: '资源与分享',
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
  permissionCatalog: () => request<PermissionDefinition[]>('/admin/permission-catalog'),
  adminAuthorization: (id: string) =>
    request<AdminAuthorization>(`/admin/accounts/${encodeURIComponent(id)}/authorization`),
  adminAuthorizationHistory: (id: string) =>
    request<AuthorizationHistory[]>(`/admin/accounts/${encodeURIComponent(id)}/authorization/history`),
  adminAuthorizationPreview: (id: string | undefined, input: AdminAuthorizationInput) =>
    protectedRequest<AuthorizationPreview>(
      id
        ? `/admin/accounts/${encodeURIComponent(id)}/authorization/preview`
        : '/admin/accounts/authorization-preview',
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) },
    ),
  adminSaveAuthorization: (id: string, version: number, input: AdminAuthorizationInput) => {
    const init = jsonWrite(input)
    return protectedRequest<AdminAuthorization>(`/admin/accounts/${encodeURIComponent(id)}/authorization`, {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminRevokeAuthorization: (id: string, version: number, reason: string) => {
    const init = jsonWrite({ reason })
    return protectedRequest<AdminAuthorization>(
      `/admin/accounts/${encodeURIComponent(id)}/authorization/revoke`,
      { ...init, headers: { ...init.headers, 'If-Match': String(version) } },
    )
  },
  capabilities: (signal?: AbortSignal) => request<ProductCapabilities>('/public/capabilities', { signal }),
  home: (signal?: AbortSignal) => request<HomeContent>('/public/home', { signal }),
  publicModules: (signal?: AbortSignal) => request<ModuleSet>('/public/content-modules', { signal }),
  adminResourceConfig: (signal?: AbortSignal) =>
    request<ResourceConfig>('/admin/resource-config', { signal }),
  adminResourceConfigHistory: (before?: number) =>
    request<ResourceConfig[]>(`/admin/resource-config/versions${before ? `?before=${before}` : ''}`),
  adminSaveResourceConfig: (config: ResourceConfig, key: string) => {
    const init = jsonWrite(config, key)
    return protectedRequest<ResourceConfig>('/admin/resource-config', {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(config.version) },
    })
  },
  modules: (signal?: AbortSignal) => request<ModuleSet>('/content-modules', { signal }),
  list: (kind: ContentKind, query = '', cursor?: string, signal?: AbortSignal) =>
    publicList(kind, query, cursor, 20, signal),
  catalogList: (kind: ContentKind, options: CatalogListQuery = {}, signal?: AbortSignal) =>
    publicList(kind, options.query ?? '', options.cursor, 20, signal, options),
  listModule: (moduleKey: string, options: CatalogListQuery = {}, signal?: AbortSignal) => {
    const params = new URLSearchParams({ pageSize: '20', module: moduleKey })
    if (options.query?.trim()) params.set('q', options.query.trim())
    if (options.cursor) params.set('cursor', options.cursor)
    if (options.sort) params.set('sort', options.sort)
    if (options.filterKey && options.filterValue) {
      params.set('filterKey', options.filterKey)
      params.set('filterValue', options.filterValue)
    }
    if (options.status) params.set('status', options.status)
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
  related: (id: string, signal?: AbortSignal) =>
    request<PublicPage>(`/public/content/${encodeURIComponent(id)}/related`, { signal }),
  gatedActions: (id: string, grant: string) =>
    request<PublicAction[]>(`/public/content/${encodeURIComponent(id)}/gated-actions`, {
      headers: { 'X-Community-Grant': grant },
    }),
  submit: (body: Submission, key: string = createIdempotencyKey()) =>
    request<CreatedCase>('/public/submissions', jsonWrite(body, key)),
  submissionStatus: (id: string, queryCode: string) =>
    request<CaseStatus>('/public/submissions/status-query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, queryCode }),
    }),
  partnerApply: (
    body: { displayName: string; introduction: string; portfolioUrl?: string; contact: string },
    key: string = createIdempotencyKey(),
  ) => request<CreatedCase>('/public/partner-applications', jsonWrite(body, key)),
  partnerApplicationStatus: (id: string, queryCode: string) =>
    request<CaseStatus>('/public/partner-applications/status-query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, queryCode }),
    }),
  verifyCommunityInvite: (inviteCode: string) =>
    request<CommunityGrant>('/public/community-gate/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inviteCode }),
    }),
  communityEntries: (grant?: string) =>
    request<CommunityEntry[]>('/public/community/entries', {
      headers: grant ? { 'X-Community-Grant': grant } : undefined,
    }),
  projectJoinEntries: (grant?: string) =>
    request<ProjectJoinEntry[]>('/public/participation/project-join', {
      headers: grant ? { 'X-Community-Grant': grant } : undefined,
    }),
  communityMediaBlob: async (path: string, grant: string) => {
    const response = await fetch(path, {
      credentials: 'include',
      headers: { 'X-Community-Grant': grant, Accept: 'image/*' },
      signal: AbortSignal.timeout(15000),
    })
    if (!response.ok)
      throw new ApiFailure('COMMUNITY_MEDIA_UNAVAILABLE', '受控二维码当前无法读取。', response.status)
    return response.blob()
  },
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
  changePassword: (currentPassword: string, newPassword: string) =>
    protectedRequest<void>('/auth/password/change', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  consumePasswordReset: (token: string, newPassword: string) =>
    request<{ loginId: string; message: string }>('/auth/password-reset/consume', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword }),
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
  adminTasks: () => request<OperationalTask[]>('/admin/tasks'),
  adminContentVerifications: (id: string) =>
    request<ContentVerification[]>(`/admin/content-items/${encodeURIComponent(id)}/verifications`),
  adminRecordContentVerification: (
    id: string,
    body: { outcome: ContentVerification['outcome']; detailSummary?: string; nextCheckAt?: string },
  ) => write<ContentVerification>(`/admin/content-items/${encodeURIComponent(id)}/verifications`, body),
  adminCommunityEntries: () => request<CommunityEntrySummary[]>('/admin/community/entries'),
  adminCreateCommunityEntry: (body: {
    name: string
    description?: string
    presentationType: PresentationType
    contactChannel: ContactChannel
    accessLevel: EntryAccessLevel
    value?: string
    mediaAssetId?: string
    publicExposureConfirmed: boolean
    primary: boolean
    sortOrder: number
  }) => write<CommunityEntrySummary>('/admin/community/entries', body),
  adminUploadCommunityMedia: (file: File, altText: string, key: string = createIdempotencyKey()) => {
    const body = new FormData()
    body.set('altText', altText)
    body.set('file', file)
    return protectedRequest<Media>('/admin/community/media', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body,
    })
  },
  adminUpdateCommunityEntry: (
    id: string,
    version: number,
    body: {
      name: string
      description?: string
      presentationType: PresentationType
      contactChannel: ContactChannel
      accessLevel: EntryAccessLevel
      value?: string
      mediaAssetId?: string
      publicExposureConfirmed: boolean
      primary: boolean
      sortOrder: number
    },
  ) => {
    const init = jsonWrite(body)
    return protectedRequest<CommunityEntrySummary>(`/admin/community/entries/${encodeURIComponent(id)}`, {
      ...init,
      method: 'PATCH',
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminDeactivateCommunityEntry: (id: string, version: number, reason: string) => {
    const init = jsonWrite({ reason })
    return protectedRequest<CommunityEntrySummary>(
      `/admin/community/entries/${encodeURIComponent(id)}/deactivate`,
      {
        ...init,
        headers: { ...init.headers, 'If-Match': String(version) },
      },
    )
  },
  adminProjectJoinEntries: () => request<ProjectJoinEntrySummary[]>('/admin/participation/project-join'),
  adminCreateProjectJoinEntry: (body: ProjectJoinEntryInput) =>
    write<ProjectJoinEntrySummary>('/admin/participation/project-join', body),
  adminUpdateProjectJoinEntry: (id: string, version: number, body: ProjectJoinEntryInput) => {
    const init = jsonWrite(body)
    return protectedRequest<ProjectJoinEntrySummary>(
      `/admin/participation/project-join/${encodeURIComponent(id)}`,
      {
        ...init,
        method: 'PATCH',
        headers: { ...init.headers, 'If-Match': String(version) },
      },
    )
  },
  adminDeactivateProjectJoinEntry: (id: string, version: number, reason: string) => {
    const init = jsonWrite({ reason })
    return protectedRequest<ProjectJoinEntrySummary>(
      `/admin/participation/project-join/${encodeURIComponent(id)}/deactivate`,
      {
        ...init,
        headers: { ...init.headers, 'If-Match': String(version) },
      },
    )
  },
  adminRecordManualDelivery: (body: {
    subjectType: 'ACCOUNT' | 'PARTNER_APPLICATION' | 'SUBMISSION'
    subjectId: string
    deliveryType: 'ACTIVATION' | 'PASSWORD_RESET' | 'STATUS_RESULT'
    note?: string
  }) =>
    write<{
      id: string
      subjectType: string
      subjectId: string
      deliveryType: string
      deliveredAt: string
      note?: string | null
    }>('/admin/manual-deliveries', body),
  adminManualDeliveries: (subjectType?: string, subjectId?: string) => {
    const params = new URLSearchParams()
    if (subjectType) params.set('subjectType', subjectType)
    if (subjectId) params.set('subjectId', subjectId)
    return request<
      {
        id: string
        subjectType: string
        subjectId: string
        deliveryType: string
        deliveredAt: string
        note?: string | null
      }[]
    >(`/admin/manual-deliveries${params.size ? `?${params}` : ''}`)
  },
  adminCommunityInvites: () => request<CommunityInviteBatch[]>('/admin/community/invite-batches'),
  adminCreateCommunityInvite: (label: string, expiresInDays = 30, maxUses = 500) =>
    write<{ id: string; label: string; inviteCode: string; expiresAt: string; maxUses: number }>(
      '/admin/community/invite-batches',
      { label, expiresInDays, maxUses },
    ),
  adminRevokeCommunityInvite: (id: string, reason: string) =>
    write<CommunityInviteBatch>(`/admin/community/invite-batches/${encodeURIComponent(id)}/revoke`, {
      reason,
    }),
  adminHomepagePlacements: (signal?: AbortSignal) =>
    request<HomepageCuration>('/admin/homepage-placements', { signal }),
  adminHomepageCandidates: (slot: HomepageSlot, query = '', cursor?: string) => {
    const params = new URLSearchParams()
    params.set('slot', slot)
    if (query.trim()) params.set('q', query.trim())
    if (cursor) params.set('cursor', cursor)
    return request<{ items: HomepageCandidate[]; nextCursor?: string | null }>(
      `/admin/homepage-placements/candidates?${params}`,
    )
  },
  adminPreviewHomepagePlacements: (placements: HomepagePlacement[]) =>
    write<HomeContent>('/admin/homepage-placements/preview', {
      placements: placements.map(toHomepageWrite),
    }),
  adminReplaceHomepagePlacements: async (version: number, placements: HomepagePlacement[]) => {
    const init = jsonWrite({ placements: placements.map(toHomepageWrite) })
    return protectedRequest<HomepageCuration>('/admin/homepage-placements', {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminCatalogOrder: (kind: ContentKind, signal?: AbortSignal) =>
    request<CatalogOrderCuration>(`/admin/catalog-order/${kind}`, { signal }),
  adminCatalogCandidates: (kind: ContentKind, query = '', cursor?: string) => {
    const params = new URLSearchParams()
    if (query.trim()) params.set('q', query.trim())
    if (cursor) params.set('cursor', cursor)
    const suffix = params.toString() ? `?${params}` : ''
    return request<{ items: CatalogOrderCandidate[]; nextCursor?: string | null }>(
      `/admin/catalog-order/${kind}/candidates${suffix}`,
    )
  },
  adminReplaceCatalogOrder: async (
    kind: ContentKind,
    version: number,
    defaultSort: string,
    entries: CatalogOrderEntry[],
  ) => {
    const init = jsonWrite({
      defaultSort,
      entries: entries.map((item) => ({ contentItemId: item.contentItemId, sortOrder: item.sortOrder })),
    })
    return protectedRequest<CatalogOrderCuration>(`/admin/catalog-order/${kind}`, {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminReplaceActions: (id: string, revision: number, actions: ContentActionInput[]) => {
    const init = jsonWrite({ actions })
    return protectedRequest<ContentActionSet>(itemPath('admin', id) + '/actions', {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(revision) },
    })
  },
  adminActions: (id: string) => request<ManagedAction[]>(itemPath('admin', id) + '/actions'),
  adminOperationalStatus: (id: string, revision: number, status: string, reason: string) => {
    const init = jsonWrite({ status, reason })
    return protectedRequest<ManagedContent>(itemPath('admin', id) + '/operational-status', {
      ...init,
      headers: { ...init.headers, 'If-Match': String(revision) },
    })
  },
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
  adminTransitionPartnerApplication: (
    id: string,
    version: number,
    status: string,
    publicResult: string,
    dueAt: string | undefined,
    clearDueAt: boolean,
    reason: string,
  ) => {
    const init = jsonWrite({
      status,
      publicResult,
      ...(dueAt ? { dueAt } : {}),
      clearDueAt,
      reason,
    })
    return protectedRequest(`/admin/partner-applications/${encodeURIComponent(id)}/transition`, {
      ...init,
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminRevealPartnerContact: (id: string, reason: string) =>
    write<{ id: string; contact: string }>(`/admin/partner-applications/${encodeURIComponent(id)}/contact`, {
      reason,
    }),
  adminSubmissions: (options: ManagementQuery = {}) =>
    request<ManagementPage<PublicSubmission>>(`/admin/submissions?${queryString(options)}`),
  adminDecideSubmission: (id: string, decision: 'APPROVE' | 'REJECT', reason: string) =>
    write<ReviewDecisionResult>(`/admin/submissions/${encodeURIComponent(id)}/decision`, {
      decision,
      reason,
    }),
  adminTransitionSubmission: (
    id: string,
    version: number,
    status: string,
    publicResult: string,
    dueAt: string | undefined,
    clearDueAt: boolean,
    reason: string,
  ) => {
    const init = jsonWrite({
      status,
      publicResult,
      ...(dueAt ? { dueAt } : {}),
      clearDueAt,
      reason,
    })
    return protectedRequest(`/admin/submissions/${encodeURIComponent(id)}/transition`, {
      ...init,
      headers: { ...init.headers, 'If-Match': String(version) },
    })
  },
  adminRevealSubmissionContact: (id: string, reason: string) =>
    write<{ id: string; contact: string }>(`/admin/submissions/${encodeURIComponent(id)}/contact`, {
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
  adminCreateAdmin: (loginId: string, reason: string, authorization?: AdminAuthorizationInput) =>
    write<CreatedAdminAccount>('/admin/accounts', { loginId, reason, authorization }),
  adminReissueActivation: (id: string, reason: string) =>
    write<CreatedAdminAccount>(`/admin/accounts/${encodeURIComponent(id)}/activation-links`, { reason }),
  adminIssuePasswordReset: (id: string, reason: string) =>
    write<{ accountId: string; loginId: string; resetToken: string; expiresAt: string }>(
      `/admin/accounts/${encodeURIComponent(id)}/password-reset-links`,
      { reason },
    ),
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
  uploadContentActionMedia: (
    contentItemId: string,
    file: File,
    altText: string,
    key: string = createIdempotencyKey(),
  ) => {
    const body = new FormData()
    body.set('contentItemId', contentItemId)
    body.set('altText', altText)
    body.set('file', file)
    return protectedRequest<Media>('/media/action-uploads', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body,
    })
  },
  adminSetMemberQuota: (id: string, quota: number, version: number, reason: string) => {
    const init = jsonWrite({ quota, reason })
    return protectedRequest<OrganizationState>(
      `/admin/partner-profiles/${encodeURIComponent(id)}/member-quota`,
      { ...init, method: 'PATCH', headers: { ...init.headers, 'If-Match': String(version) } },
    )
  },
  adminOrganizationStatus: (id: string, active: boolean, version: number, reason: string) => {
    const init = jsonWrite({ reason })
    return protectedRequest<OrganizationState>(
      `/admin/partner-profiles/${encodeURIComponent(id)}/${active ? 'reactivate' : 'suspend'}`,
      { ...init, headers: { ...init.headers, 'If-Match': String(version) } },
    )
  },
  partnerMembers: () =>
    request<{ organization: OrganizationIdentity; items: PartnerMember[] }>('/partner/members'),
  partnerCreateMember: (loginId: string, reason: string) =>
    write<PartnerMember>('/partner/members', { loginId, reason }),
  partnerMemberActivation: (id: string, reason: string) =>
    write<CreatedAdminAccount>(`/partner/members/${encodeURIComponent(id)}/activation-links`, { reason }),
  partnerMemberReset: (id: string, reason: string) =>
    write<{ accountId: string; loginId: string; resetToken: string; expiresAt: string }>(
      `/partner/members/${encodeURIComponent(id)}/password-reset-links`,
      { reason },
    ),
  partnerMemberStatus: (id: string, action: 'suspend' | 'reactivate' | 'revoke', reason: string) =>
    write<AccountStatus>(`/partner/members/${encodeURIComponent(id)}/${action}`, { reason }),
  partnerDashboard: (options: ManagementQuery | AbortSignal = {}) =>
    request<PartnerDashboard>(
      `/partner/dashboard?${queryString(queryOptions(options))}`,
      options instanceof AbortSignal ? { signal: options } : {},
    ),
  partnerContent: (id: string) => request<EditablePartnerContent>(itemPath('partner', id)),
  partnerCreate: (draft: AdminDraft) => write<ManagedContent>('/partner/content-items', draft),
  partnerSubmit: (id: string) => write<ManagedContent>(itemPath('partner', id) + '/submit-review'),
  partnerRevise: (id: string, revision: number, draft: AdminDraft) => revise('partner', id, revision, draft),
  partnerReplaceActions: (id: string, revision: number, actions: ContentActionInput[]) => {
    const init = jsonWrite({ actions })
    return protectedRequest<ContentActionSet>(itemPath('partner', id) + '/actions', {
      ...init,
      method: 'PUT',
      headers: { ...init.headers, 'If-Match': String(revision) },
    })
  },
  partnerActions: (id: string) => request<ManagedAction[]>(itemPath('partner', id) + '/actions'),
}
