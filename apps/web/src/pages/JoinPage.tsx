import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
export function JoinPage() {
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    setBusy(true); setNotice(null)
    try {
      await api.partnerApply({ displayName: String(values.get('displayName')), introduction: String(values.get('introduction')), portfolioUrl: String(values.get('portfolioUrl') || '') || undefined })
      form.reset()
      setNotice('申请已进入审核队列。通过后，管理员会以受控方式创建合作方账号。')
    } catch (error) { setNotice(error instanceof Error ? error.message : '提交未完成，请稍后重试。') } finally { setBusy(false) }
  }
  return <section className="page join-page"><div className="page-intro"><p className="eyebrow">共同维护信息</p><h1>加入我们</h1><p>VOIDHAVEN 把校园里的机会与学习路径整理为可发现的信息。合作方负责受控投稿和维护，后续沟通与组队仍在社群中完成。</p></div><div className="join-grid"><section className="community-card"><p className="eyebrow">校内社群</p><h2>在受控入口继续沟通</h2><p>首版不在站内保存社群成员、聊天、报名或筛选记录。真实入口仅在取得单独授权后由受控验证流程提供。</p><button className="button ghost" disabled>社群入口尚未配置</button></section><form onSubmit={submit}><h2>申请成为合作方</h2><label>展示名称<input name="displayName" required maxLength={120} placeholder="团队或项目名称" /></label><label>你想维护什么内容？<textarea name="introduction" required maxLength={5000} rows={6} placeholder="说明内容领域、更新方式和公开来源。" /></label><label>公开作品链接（可选）<input name="portfolioUrl" type="url" placeholder="https://" /></label><p className="form-note">请勿填写不必要的私人联系方式。获批后的账号与首次激活由管理员受控交付。</p><button className="button primary" disabled={busy}>{busy ? '提交中…' : '提交合作方申请'}</button>{notice ? <p className="notice" role="status">{notice}</p> : null}</form></div></section>
}
