import { NavLink, Outlet } from 'react-router-dom'
const links = [['/competitions', '竞赛'], ['/resources', '资源'], ['/recruitments', '团队招募'], ['/feedback', '投稿/反馈'], ['/join', '加入我们'], ['/about', '关于']] as const
export function SiteLayout() { return <><header className="site-header"><NavLink to="/" className="brand" aria-label="VOIDHAVEN 首页">VOIDHAVEN<span>／</span></NavLink><nav aria-label="主导航">{links.map(([to, label]) => <NavLink key={to} to={to}>{label}</NavLink>)}</nav></header><main><Outlet /></main><footer><span>VOIDHAVEN V0 · 仅作信息发现与资源沉淀</span><span>官方规则以来源链接为准</span></footer></> }
