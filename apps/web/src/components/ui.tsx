import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { AlertCircle, Archive, Check, LoaderCircle, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiFailure } from '../lib/api'
export type NoticeValue = { tone: 'success' | 'error'; text: string } | null
export const message = (error: unknown) => error instanceof ApiFailure && error.retryAfterSeconds
  ? `${error.message} 建议等待 ${Math.ceil(error.retryAfterSeconds / 60)} 分钟后重试。`
  : error instanceof Error ? error.message : '操作未完成，请稍后重试。'
export function Notice({ value }: { value: NoticeValue }) {
  return value ? (
    <p className={`notice ${value.tone}`} role={value.tone === 'error' ? 'alert' : 'status'}>
      {value.tone === 'success' ? <Check size={17} /> : <AlertCircle size={17} />}
      <span>{value.text}</span>
    </p>
  ) : null
}
export function EmptyState({ title, text }: { title: string; text: string }) {
  return (
    <section className="empty-state">
      <Archive size={24} />
      <strong>{title}</strong>
      <p>{text}</p>
    </section>
  )
}
export function ErrorState({ error, retry }: { error: string; retry?: () => void }) {
  return (
    <section className="error-state" role="alert">
      <AlertCircle size={23} />
      <h2>暂时无法完成请求</h2>
      <p>{error}</p>
      <div className="heading-actions">
        {retry && (
          <button className="button" onClick={retry}>
            重新尝试
          </button>
        )}
        <Link to="/" className="button ghost">
          返回首页
        </Link>
      </div>
    </section>
  )
}
export function LoadingRows() {
  return (
    <div className="loading-rows" role="status" aria-label="正在加载">
      <span className="sr-only">正在加载内容</span>
      <i />
      <i />
      <i />
    </div>
  )
}
export function BusyLabel({ busy, children }: { busy: boolean; children: ReactNode }) {
  return busy ? (
    <>
      <LoaderCircle size={16} className="spin" />
      处理中…
    </>
  ) : (
    children
  )
}
export function PanelHeading({
  eyebrow,
  title,
  action,
}: {
  eyebrow: string
  title: string
  action?: ReactNode
}) {
  return (
    <div className="panel-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      <div className="heading-actions">{action}</div>
    </div>
  )
}
export const statusText: Record<string, string> = {
  COMPETITION: '竞赛',
  RESOURCE: '学习资源',
  RECRUITMENT: '团队招募',
  DRAFT: '草稿',
  CHANGES_REQUESTED: '待修改',
  PENDING_REVIEW: '审核中',
  APPROVED: '待发布',
  PUBLISHED: '已发布',
  REJECTED: '已拒绝',
  PUBLIC: '公开',
  HIDDEN: '隐藏',
  ACTIVE: '活跃',
  ARCHIVED: '已归档',
  SUSPENDED: '已停用',
  REVOKED: '已撤销',
  SUPER_ADMIN: '超级管理员',
  ADMIN: '管理员',
  PARTNER: '合作方',
  CONTENT: '内容线索',
  FEEDBACK: '产品反馈',
  COPYRIGHT: '版权请求',
  PENDING: '待处理',
}
export function Badge({ value }: { value: string }) {
  return <span className={`badge badge-${value.toLowerCase()}`}>{statusText[value] ?? value}</span>
}
export function Dialog({
  title,
  children,
  onClose,
  busy = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const dialog = ref.current
    dialog?.showModal()
    return () => {
      dialog?.close()
      previous?.focus()
    }
  }, [])
  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      onCancel={(e) => {
        e.preventDefault()
        if (!busy) onClose()
      }}
      className="memoir-dialog"
    >
      <div className="dialog-heading">
        <h2 id={id}>{title}</h2>
        <button className="icon-button" aria-label="关闭对话框" disabled={busy} onClick={onClose}>
          <X size={18} />
        </button>
      </div>
      {children}
    </dialog>
  )
}
export function ConfirmDialog({
  title,
  description,
  reasonRequired = false,
  onConfirm,
  onClose,
}: {
  title: string
  description: string
  reasonRequired?: boolean
  onConfirm: (reason: string) => Promise<void>
  onClose: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<NoticeValue>(null)
  return (
    <Dialog title={title} onClose={onClose} busy={busy}>
      <form
        onSubmit={async (e) => {
          e.preventDefault()
          if (busy) return
          const reason = String(new FormData(e.currentTarget).get('reason') ?? '').trim()
          if (reasonRequired && !reason) {
            setError({ tone: 'error', text: '请填写操作原因，不能只输入空格。' })
            return
          }
          setBusy(true)
          setError(null)
          try {
            await onConfirm(reason)
            onClose()
          } catch (err) {
            setError({ tone: 'error', text: message(err) })
          } finally {
            setBusy(false)
          }
        }}
      >
        <p>{description}</p>
        {reasonRequired && (
          <label>
            操作原因
            <textarea name="reason" required maxLength={1000} rows={3} disabled={busy} />
          </label>
        )}
        <Notice value={error} />
        <div className="dialog-actions">
          <button type="button" className="button ghost" onClick={onClose} disabled={busy}>
            取消
          </button>
          <button className="button primary" disabled={busy}>
            <BusyLabel busy={busy}>确认{title}</BusyLabel>
          </button>
        </div>
      </form>
    </Dialog>
  )
}
