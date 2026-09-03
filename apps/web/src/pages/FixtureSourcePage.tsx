import { Link } from 'react-router-dom'
export function FixtureSourcePage() {
  return <section className="page prose"><p className="eyebrow">仅本地验收</p><h1>本地合成来源说明</h1><p>此页面只用于本机演示数据的来源链接验证。它不是竞赛、资源或团队招募的真实官方规则页。</p><p>正式发布前，管理员必须将每条内容替换为可核验的 HTTPS 官方或原始来源；请以实际来源的规则、时间和资格说明为准。</p><Link className="button ghost" to="/">返回首页</Link></section>
}
