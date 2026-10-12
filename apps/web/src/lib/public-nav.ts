import type { ContentKind } from './api'

export type PublicNavLink = { path: string; label: string; kind?: ContentKind }
export type PublicNavGroup = { label: string; children: PublicNavNode[] }
export type PublicNavNode = PublicNavLink | PublicNavGroup

export function isNavGroup(node: PublicNavNode): node is PublicNavGroup {
  return 'children' in node
}

/** 顶层直达链接：父级不承担展开职责，只有叶子节点触发跳转。 */
export const publicNavLinks: PublicNavLink[] = [
  { path: '/', label: '首页' },
  { path: '/competitions', label: '竞赛资源', kind: 'COMPETITION' },
  { path: '/recruitments', label: '团队招募', kind: 'RECRUITMENT' },
  { path: '/resources', label: '资源与分享', kind: 'RESOURCE' },
]

/** 三层参与菜单：顶层展开面板，面板内“加入我们”再展开为两个三级叶子。 */
export const participationMenu: PublicNavGroup = {
  label: '参与 VOIDHAVEN',
  children: [
    { path: '/submit', label: '投稿 / 反馈' },
    { path: '/partners/apply', label: '合作申请' },
    { path: '/status', label: '进度查询' },
    {
      label: '加入我们',
      children: [
        { path: '/join/community', label: '加入 VOIDHAVEN 用户社群' },
        { path: '/join/project', label: '加入 VOIDHAVEN 项目组' },
      ],
    },
  ],
}

export const publicNavTrailingLink: PublicNavLink = { path: '/about', label: '关于' }

export function navLinks(nodes: PublicNavNode[]): PublicNavLink[] {
  return nodes.filter((node): node is PublicNavLink => !isNavGroup(node))
}

export function navGroups(nodes: PublicNavNode[]): PublicNavGroup[] {
  return nodes.filter(isNavGroup)
}

/** 展开三层结构中的所有可跳转叶子，用于导航契约测试与可访问性检查。 */
export function navLeafLinks(nodes: PublicNavNode[]): PublicNavLink[] {
  return nodes.flatMap((node) => (isNavGroup(node) ? navLeafLinks(node.children) : [node]))
}
