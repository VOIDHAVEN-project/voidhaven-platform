export type ContentKind = 'COMPETITION' | 'RESOURCE' | 'RECRUITMENT'
export type Content = { id: string; kind: ContentKind; title: string; summary: string; body: { blocks?: { type: string; text: string }[] }; languageCode: string; sourceName: string; sourceUrl: string; rightsBasis: string; details: Record<string, unknown>; revision: number }
type Envelope<T> = { data: T; requestId: string }
export class ApiFailure extends Error { readonly code: string; constructor(code: string, message: string) { super(message); this.code = code } }
const base = '/api/v1'
const segment: Record<ContentKind, string> = { COMPETITION: 'competitions', RESOURCE: 'resources', RECRUITMENT: 'recruitments' }
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, { credentials: 'include', headers: { Accept: 'application/json', ...init?.headers }, ...init })
  const body = await response.json().catch(() => null) as Envelope<T> | { code?: string; message?: string } | null
  if (!response.ok) throw new ApiFailure((body as { code?: string } | null)?.code ?? 'NETWORK_ERROR', (body as { message?: string } | null)?.message ?? '请求未完成，请稍后重试。')
  return (body as Envelope<T>).data
}
export const api = {
  home: () => request<{ competitions: Content[]; resources: Content[]; recruitments: Content[] }>('/public/home'),
  list: (kind: ContentKind) => request<Content[]>(`/public/${segment[kind]}?pageSize=50`),
  detail: (kind: ContentKind, id: string) => request<Content>(`/public/${segment[kind]}/${encodeURIComponent(id)}`),
  submit: (payload: { submissionType: 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'; title: string; message: string; linkUrl?: string; imageDescription?: string }) => request<{ id: string }>('/public/submissions', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) }),
  partnerApply: (payload: { displayName: string; introduction: string; portfolioUrl?: string }) => request<{ id: string }>('/public/partner-applications', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify(payload) }),
  signIn: (loginId: string, password: string) => request<{ loginId: string; roles: string[] }>('/auth/session', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ loginId, password }) }),
  me: () => request<{ loginId: string; roles: string[] }>('/auth/me'),
  signOut: () => request<void>('/auth/session', { method: 'DELETE' }),
}
export const kindMeta: Record<ContentKind, { label: string; path: string; singular: string }> = {
  COMPETITION: { label: '竞赛', path: '/competitions', singular: '竞赛' }, RESOURCE: { label: '学习资源', path: '/resources', singular: '资源' }, RECRUITMENT: { label: '团队招募', path: '/recruitments', singular: '团队招募' },
}
