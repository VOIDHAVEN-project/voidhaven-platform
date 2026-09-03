import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
type FormKind = 'CONTENT' | 'FEEDBACK' | 'COPYRIGHT'
export function FeedbackPage() {
  const [kind, setKind] = useState<FormKind>('CONTENT')
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    setBusy(true); setNotice(null)
    try {
      await api.submit({ submissionType: kind, title: String(values.get('title')), message: String(values.get('message')), linkUrl: String(values.get('linkUrl') || '') || undefined, imageDescription: String(values.get('imageDescription') || '') || undefined })
      form.reset()
      setNotice('已收到。你的内容会进入审核流程，我们不会把它直接公开。')
    } catch (error) { setNotice(error instanceof Error ? error.message : '提交未完成，请稍后重试。') } finally { setBusy(false) }
  }
  return <section className="page form-page"><div className="page-intro"><p className="eyebrow">公开入口</p><h1>投稿 / 反馈</h1><p>任何公开访客都可提供线索或反馈。请不要在正文中填写微信、手机号、私人邮箱等不必要的个人信息。</p></div><form onSubmit={submit}><fieldset><legend>提交类型</legend><div className="choice-row">{([['CONTENT', '内容线索'], ['FEEDBACK', '产品反馈'], ['COPYRIGHT', '版权/下线反馈']] as const).map(([value, label]) => <label key={value}><input type="radio" name="kind" checked={kind === value} onChange={() => setKind(value)} /> {label}</label>)}</div></fieldset><label>标题<input name="title" required maxLength={240} placeholder="用一句话说明你要提交什么" /></label><label>内容<textarea name="message" required maxLength={5000} rows={8} placeholder="描述信息来源、需要更正的内容或建议。" /></label><label>相关链接（可选）<input name="linkUrl" type="url" placeholder="https://" /></label><label>图片说明（可选，不上传图片）<input name="imageDescription" maxLength={1000} placeholder="如有图片线索，请用文字说明其内容与来源" /></label><p className="form-note">公开投稿不接受正文图片上传；如后续需要联系，将由受控工作人员按隐私流程处理。</p><button className="button primary" disabled={busy}>{busy ? '提交中…' : '提交审核'}</button>{notice ? <p className="notice" role="status">{notice}</p> : null}</form></section>
}
