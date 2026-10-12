/**
 * 站点级真实信息的唯一来源。
 * 未配置的条目必须整条不渲染：不虚构运营主体、部门、联系方式或备案信息。
 * 待负责人补充：联系邮箱、GitHub / 公开文档地址、备案信息。
 */
export type SiteMeta = {
  contactEmail?: string
  githubUrl?: string
  docsUrl?: string
  beian?: string
}

export const siteMeta: SiteMeta = {}

export function siteContactLinks(meta: SiteMeta = siteMeta): { label: string; href: string }[] {
  const links: { label: string; href: string }[] = []
  if (meta.contactEmail) links.push({ label: '联系邮箱', href: `mailto:${meta.contactEmail}` })
  if (meta.githubUrl) links.push({ label: '开源仓库', href: meta.githubUrl })
  if (meta.docsUrl) links.push({ label: '公开文档与决策记录', href: meta.docsUrl })
  return links
}

export function siteBeian(meta: SiteMeta = siteMeta): string | null {
  return meta.beian?.trim() || null
}
