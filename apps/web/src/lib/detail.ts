import type { ContentKind, PublicContent } from './api'
import { fieldOptionLabel } from './module-field-labels'

export type DeadlineState = 'active' | 'expired' | 'unknown'
export type DetailClosureCode = 'open' | 'paused' | 'filled' | 'expired' | 'cancelled'
export type DetailClosure = {
  code: DetailClosureCode
  label: string
  message: string | null
  actionsOpen: boolean
}
export type PrimaryFact = { label: string; value: string }
export type RelatedGroup = { key: string; title: string; items: PublicContent[] }

const CLOSURE_LABEL: Record<DetailClosureCode, string> = {
  open: '进行中',
  paused: '已暂停',
  filled: '已招满',
  expired: '已截止',
  cancelled: '已取消',
}

export function deadline(item: PublicContent) {
  const value = item.details?.deadlineDate ?? item.details?.deadlineAt
  return typeof value === 'string' ? value : null
}

export function deadlineState(item: PublicContent, now = Date.now()): DeadlineState {
  const value = deadline(item)
  if (!value || item.kind === 'RESOURCE') return 'unknown'
  const time = new Date(value.length === 10 ? value + 'T23:59:59+08:00' : value).getTime()
  if (Number.isNaN(time)) return 'unknown'
  return time < now ? 'expired' : 'active'
}

export function dateLabel(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
}

export function detailClosure(item: PublicContent, now = Date.now()): DetailClosure {
  if (item.kind === 'RESOURCE') {
    return { code: 'open', label: '可阅读', message: null, actionsOpen: true }
  }
  const status = item.operationalStatus ?? 'OPEN'
  if (status === 'PAUSED') {
    return {
      code: 'paused',
      label: CLOSURE_LABEL.paused,
      message:
        item.kind === 'RECRUITMENT'
          ? '本次招募已暂停，联系与社群入口暂不可用。'
          : '该竞赛当前已暂停，不能继续报名或联系。',
      actionsOpen: false,
    }
  }
  if (status === 'FILLED') {
    return {
      code: 'filled',
      label: CLOSURE_LABEL.filled,
      message: '本次招募已招满，不再接受新的联系。',
      actionsOpen: false,
    }
  }
  if (status === 'CANCELLED') {
    return {
      code: 'cancelled',
      label: CLOSURE_LABEL.cancelled,
      message:
        item.kind === 'RECRUITMENT'
          ? '本次招募已取消，行动入口不再开放。'
          : '该竞赛已取消，不能继续报名或联系。',
      actionsOpen: false,
    }
  }
  if (status === 'EXPIRED' || deadlineState(item, now) === 'expired') {
    return {
      code: 'expired',
      label: CLOSURE_LABEL.expired,
      message:
        item.kind === 'RECRUITMENT'
          ? '本次招募已截止，不能继续报名或联系。页面保留为历史记录。'
          : '该竞赛已截止，不能继续报名或联系。请以官方页面确认是否仍有补报名通道。',
      actionsOpen: false,
    }
  }
  return { code: 'open', label: CLOSURE_LABEL.open, message: null, actionsOpen: true }
}

export function primaryFacts(item: PublicContent, now = Date.now()): PrimaryFact[] {
  const details = item.details ?? {}
  const facts: PrimaryFact[] = []
  const closure = detailClosure(item, now)
  if (item.kind !== 'RESOURCE') {
    facts.push({ label: '当前状态', value: closure.label })
  }
  const start = textValue(details.startDate)
  const end = deadline(item)
  if (start) facts.push({ label: '开始日期', value: dateLabel(start) })
  if (end) facts.push({ label: '截止日期', value: dateLabel(end) })
  const level = textValue(details.competitionLevel)
  const form = textValue(details.participationForm)
  const audience = textValue(details.audience)
  if (level) facts.push({ label: '赛事级别', value: level })
  if (form) facts.push({ label: '参赛形式', value: form })
  const roles = arrayValue(details.roles)
  const headcount = numberValue(details.headcount)
  if (roles.length) facts.push({ label: '招募角色', value: roles.join('、') })
  if (headcount != null) facts.push({ label: '人数', value: `${headcount} 人` })
  const stage = textValue(details.stage)
  if (stage) facts.push({ label: '学习阶段', value: stage })
  if (item.kind === 'RESOURCE') {
    const resourceType = textValue(details.resourceType)
    const category =
      item.resourcePresentation?.category ??
      (resourceType && resourceType !== 'GENERAL' ? fieldOptionLabel(resourceType) : '')
    if (category) facts.push({ label: '内容分类', value: category })
    if (audience) facts.push({ label: '适用人群', value: audience })
    facts.push({ label: '内容语言', value: item.languageLabel ?? item.languageCode })
  } else if (audience) {
    facts.push({ label: '适合对象', value: audience })
  }
  return facts
}

export function relatedGroups(kind: ContentKind, items: PublicContent[]): RelatedGroup[] {
  const unique = new Map<string, PublicContent>()
  for (const item of items) {
    if (!unique.has(item.id)) unique.set(item.id, item)
  }
  const values = [...unique.values()]
  const competitions = values.filter((item) => item.kind === 'COMPETITION')
  const recruitments = values.filter((item) => item.kind === 'RECRUITMENT')
  const resources = values.filter((item) => item.kind === 'RESOURCE')
  const groups: RelatedGroup[] =
    kind === 'COMPETITION'
      ? [
          { key: 'recruitments', title: '相关团队招募', items: recruitments },
          { key: 'resources', title: '相关资源与分享', items: resources },
        ]
      : kind === 'RECRUITMENT'
        ? [{ key: 'competitions', title: '所属竞赛', items: competitions }]
        : [{ key: 'competitions', title: '适用竞赛', items: competitions }]
  return groups
    .map((group) => ({ ...group, items: group.items.slice(0, 3) }))
    .filter((group) => group.items.length > 0)
}

export function verificationSummary(verification: PublicContent['verification']): {
  label: string
  tone: 'valid' | 'attention' | 'unknown'
} {
  if (!verification) return { label: '尚无独立核验记录', tone: 'unknown' }
  const checked = dateLabel(verification.checkedAt)
  if (verification.outcome === 'VALID') return { label: `来源核验有效 · ${checked}`, tone: 'valid' }
  if (verification.outcome === 'INVALID') return { label: `来源已标记失效 · ${checked}`, tone: 'attention' }
  return { label: `来源待复核 · ${checked}`, tone: 'attention' }
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function arrayValue(value: unknown) {
  return Array.isArray(value) ? value.map((item) => String(item).trim()).filter(Boolean) : []
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}
