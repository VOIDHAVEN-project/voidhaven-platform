import { Link } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'
import { siteContactLinks } from '../lib/site-meta'
import { usePageTitle } from '../lib/hooks'

const handling = [
  {
    title: '公开内容与联系方式分开',
    text: '公开页面只展示内容与来源；联系方式与受控入口单独保存，不会出现在公开列表、审计明细或常规日志里。',
  },
  {
    title: '公开投稿只接受文字、链接和图片说明',
    text: '投稿不要求注册账号。除非版权或下线请求需要回访，否则我们不会要求你留下邮箱、微信或电话。',
  },
  {
    title: '受控联系方式的加密与最小留存',
    text: '需要保存的联系方式会加密存储；审计只记录对象、动作和时间等最小摘要，不记录密码、令牌或原值。',
  },
  {
    title: '邀请码与短期授权',
    text: '社群与项目组的邀请码不会写入浏览器存储；验证结果只在当前页面内存中共享，刷新或关闭页面即失效。',
  },
]

export function PrivacyPage() {
  usePageTitle('隐私说明')
  const contactLinks = siteContactLinks()
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">HOW WE HANDLE INFORMATION</p>
        <h1>
          只收集必须的，
          <br />
          <span>只保留必要的。</span>
        </h1>
        <p>本页说明 VOIDHAVEN 当前实际如何处理信息；未配置的条目不会出现在页面上。</p>
      </header>
      <div className="about-grid">
        <div className="about-prose">
          {handling.map((item) => (
            <section key={item.title}>
              <h2>{item.title}</h2>
              <p>{item.text}</p>
            </section>
          ))}
          <section>
            <h2>留存周期</h2>
            <p>
              幂等记录保留 24 小时，社群验证事件保留 7 天，非敏感异常摘要保留 14
              天；真实业务的留存周期会在正式投入生产前单独确认。
            </p>
          </section>
          <section>
            <h2>我们不做的事</h2>
            <p>
              不建立学生账号、简历、报名或站内私信；不保存社群沟通记录；不把投稿信息用于推荐算法或广告。重要事项以原始来源为准。
            </p>
          </section>
          {contactLinks.length ? (
            <section>
              <h2>联系与更正</h2>
              <p>如果发现信息有误或需要更正，可以从以下入口联系我们：</p>
              <ul>
                {contactLinks.map((link) => (
                  <li key={link.href}>
                    <a href={link.href}>{link.label}</a>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          <Link className="button primary" to="/submit">
            提交纠错或下线请求 <ArrowUpRight size={15} />
          </Link>
        </div>
        <aside className="about-note">
          <blockquote>
            Clear boundaries.
            <br />
            <em>Fewer surprises.</em>
          </blockquote>
          <p>
            信息边界写清楚，
            <br />
            才谈得上信任。
          </p>
          <span>— VOIDHAVEN</span>
        </aside>
      </div>
    </>
  )
}
