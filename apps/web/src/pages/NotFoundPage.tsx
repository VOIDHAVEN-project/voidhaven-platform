import { Link } from 'react-router-dom'
export function NotFoundPage() { return <main className="auth-page"><Link className="brand" to="/">VOIDHAVEN<span>／</span></Link><section className="state"><p className="eyebrow">404</p><h1>这里没有可见内容</h1><p>页面可能不存在、已下线，或你没有访问权限。</p><Link className="button ghost" to="/">返回首页</Link></section></main> }
