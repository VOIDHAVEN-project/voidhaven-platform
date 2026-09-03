import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import type { AdminDraft, ContentKind, EditablePartnerContent, PartnerDashboard } from '../lib/api'
import { api } from '../lib/api'
import { ContentMediaUploadForm } from './ContentMediaUploadForm'

type Notice = { tone: 'success' | 'error'; text: string } | null

function draftFrom(form: HTMLFormElement): AdminDraft {
  const values = new FormData(form)
  const kind = String(values.get('kind')) as ContentKind
  const sourceUrl = String(values.get('sourceUrl')).trim()
  const deadlineDate = String(values.get('deadlineDate')).trim()
  return {
    kind,
    title: String(values.get('title')).trim(),
    summary: String(values.get('summary')).trim(),
    body: { version: 1, blocks: [{ type: 'paragraph', text: String(values.get('body')).trim() }] },
    languageCode: 'zh-CN',
    sourceUrl,
    sourceName: String(values.get('sourceName')).trim(),
    rightsBasis: '已核验公开来源；站内仅作辅助导读。',
    details: kind === 'COMPETITION'
      ? { officialRulesUrl: String(values.get('officialRulesUrl')).trim() || sourceUrl, deadlineDate: deadlineDate || undefined }
      : kind === 'RESOURCE'
        ? { resourceType: String(values.get('resourceType')).trim(), deadlineDate: deadlineDate || undefined }
        : { deadlineDate: deadlineDate || undefined, followUpUrl: String(values.get('followUpUrl')).trim() || undefined, relatedCompetitionId: String(values.get('relatedCompetitionId')).trim() || undefined },
  }
}

function statusText(status: string) {
  return ({ DRAFT: '草稿', CHANGES_REQUESTED: '待修改', PENDING_REVIEW: '审核中', APPROVED: '已通过，待发布', PUBLISHED: '已发布', REJECTED: '已拒绝' } as Record<string, string>)[status] ?? status
}

export function PartnerLifecyclePanel() {
  const [dashboard, setDashboard] = useState<PartnerDashboard | null>(null)
  const [notice, setNotice] = useState<Notice>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [editing, setEditing] = useState<EditablePartnerContent | null>(null)
  const loadDashboard = useCallback(() => api.partnerDashboard(), [])

  useEffect(() => {
    let active = true
    void loadDashboard()
      .then((value) => { if (active) setDashboard(value) })
      .catch((error: unknown) => { if (active) setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载合作方工作台。' }) })
    return () => { active = false }
  }, [loadDashboard])

  async function refresh() {
    try { setDashboard(await loadDashboard()) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '刷新失败，请稍后重试。' }) }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    setBusyId('create')
    setNotice(null)
    try {
      const draft = draftFrom(form)
      const created = editing ? await api.partnerRevise(editing.id, editing.revision, draft) : await api.partnerCreate(draft)
      form.reset()
      setEditing(null)
      await refresh()
      setNotice({ tone: 'success', text: editing ? `“${created.title}”已创建候选修订。确认后可重新送审。` : `“${created.title}”已保存为草稿。确认来源与说明后即可送审。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '草稿未保存。' })
    } finally {
      setBusyId(null)
    }
  }

  async function submit(id: string) {
    setBusyId(id)
    setNotice(null)
    try {
      const updated = await api.partnerSubmit(id)
      await refresh()
      setNotice({ tone: 'success', text: `“${updated.title}”已送入审核队列。` })
    } catch (error) {
      setNotice({ tone: 'error', text: error instanceof Error ? error.message : '送审未完成。' })
    } finally {
      setBusyId(null)
    }
  }

  async function startEdit(id: string) {
    setBusyId(id)
    setNotice(null)
    try { setEditing(await api.partnerContent(id)) } catch (error) { setNotice({ tone: 'error', text: error instanceof Error ? error.message : '无法加载该内容进行编辑。' }) } finally { setBusyId(null) }
  }

  const editDraft = editing?.draft
  const bodyText = editDraft?.body.blocks?.find((block) => block.type === 'paragraph')?.text ?? ''

  return <section className="partner-lifecycle" aria-label="合作方内容工作台">
    <div className="workspace-section-heading">
      <div><p className="eyebrow">合作方内容工作台</p><h2>{dashboard?.displayName ?? '正在加载…'}</h2></div>
      <button className="button ghost" type="button" onClick={() => void refresh()}>刷新列表</button>
    </div>
    <p className="workspace-help">公开活跃内容：{dashboard?.publicActiveCount ?? '—'} / {dashboard?.quota ?? '—'}。草稿与审核中的内容不占用公开配额；发布前仍由服务端重新核验。</p>
    <form className="partner-draft-form" onSubmit={create} key={editing?.id ?? 'new'}>
      <label>内容类型<select name="kind" defaultValue={editDraft?.kind ?? 'COMPETITION'}><option value="COMPETITION">竞赛</option><option value="RESOURCE">学习资源</option><option value="RECRUITMENT">团队招募</option></select></label>
      <label>标题<input name="title" required maxLength={240} defaultValue={editDraft?.title} /></label>
      <label className="full">中文导读<textarea name="summary" required maxLength={1000} rows={3} defaultValue={editDraft?.summary} /></label>
      <label className="full">正文（安全段落）<textarea name="body" required rows={5} defaultValue={bodyText} /></label>
      <label>截止日期（可选）<input name="deadlineDate" type="date" defaultValue={typeof editDraft?.details.deadlineDate === 'string' ? editDraft.details.deadlineDate : ''} /></label>
      <label>来源名称<input name="sourceName" required maxLength={240} defaultValue={editDraft?.sourceName} /></label>
      <label className="full">官方或原始来源 URL<input name="sourceUrl" type="url" placeholder="https://" required defaultValue={editDraft?.sourceUrl} /></label>
      <label className="full">竞赛官方规则 URL（竞赛必填）<input name="officialRulesUrl" type="url" placeholder="竞赛类型可与来源 URL 相同" defaultValue={typeof editDraft?.details.officialRulesUrl === 'string' ? editDraft.details.officialRulesUrl : ''} /></label>
      <label>资源类型（资源必填）<input name="resourceType" placeholder="课程、文档、工具等" defaultValue={typeof editDraft?.details.resourceType === 'string' ? editDraft.details.resourceType : ''} /></label>
      <label className="full">后续入口 URL（团队招募可选）<input name="followUpUrl" type="url" placeholder="https://" defaultValue={typeof editDraft?.details.followUpUrl === 'string' ? editDraft.details.followUpUrl : ''} /></label>
      <label>关联竞赛 ID（团队招募可选）<input name="relatedCompetitionId" maxLength={26} defaultValue={typeof editDraft?.details.relatedCompetitionId === 'string' ? editDraft.details.relatedCompetitionId : ''} /></label>
      <div className="full partner-form-actions"><button className="button primary" disabled={busyId === 'create'}>{busyId === 'create' ? '保存中…' : editing ? '保存候选修订' : '保存合作方草稿'}</button>{editing ? <button className="button ghost" type="button" onClick={() => setEditing(null)}>取消编辑</button> : null}</div>
    </form>
    {notice ? <p className={`notice notice-${notice.tone}`} role={notice.tone === 'error' ? 'alert' : 'status'}>{notice.text}</p> : null}
    <div className="partner-content-list" aria-live="polite">
      {!dashboard ? <p>正在加载自己的内容…</p> : dashboard.contentItems.length === 0 ? <p>尚无内容。创建草稿后，它只会在你的受控工作台中可见。</p> : dashboard.contentItems.map((item) => <article className="partner-content-row" key={item.id}>
        <div><p className="eyebrow"><span>{item.kind}</span><span>{statusText(item.workflowStatus)}</span></p><h3>{item.title}</h3><p className="admin-state">可见性：{item.visibility} · 存放：{item.recordState}</p>{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <ContentMediaUploadForm contentItemId={item.id} /> : null}</div>
        <div className="partner-row-actions">{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED' || item.workflowStatus === 'PUBLISHED') ? <button className="button ghost" type="button" onClick={() => void startEdit(item.id)} disabled={busyId === item.id}>{busyId === item.id ? '加载中…' : '编辑'}</button> : null}{(item.workflowStatus === 'DRAFT' || item.workflowStatus === 'CHANGES_REQUESTED') ? <button className="button primary" type="button" onClick={() => void submit(item.id)} disabled={busyId === item.id}>{busyId === item.id ? '送审中…' : '送审'}</button> : null}</div>
      </article>)}
    </div>
  </section>
}
