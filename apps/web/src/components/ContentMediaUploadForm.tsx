import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../lib/api'
import type { PublicMedia } from '../lib/api'

export function ContentMediaUploadForm({ contentItemId, onUploaded }: { contentItemId: string; onUploaded?: (media: PublicMedia) => void }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const values = new FormData(form)
    const fileInput = form.elements.namedItem('file')
    const file = fileInput instanceof HTMLInputElement ? fileInput.files?.item(0) : null
    const altText = String(values.get('altText')).trim()
    if (!file || file.size === 0) { setMessage('请选择一张图片后再上传。'); return }
    setBusy(true)
    setMessage(null)
    try {
      const uploaded = await api.uploadContentMedia(contentItemId, file, altText)
      form.reset()
      setMessage('图片已关联到当前候选修订；送审并发布后才可能公开展示。')
      onUploaded?.(uploaded)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '图片上传未完成。')
    } finally { setBusy(false) }
  }

  return <form className="media-upload-form" onSubmit={upload}>
    <label>正文图片<input name="file" type="file" accept="image/jpeg,image/png,image/webp" required disabled={busy} /></label>
    <label>图片说明<input name="altText" required maxLength={240} disabled={busy} /></label>
    <button className="button ghost" disabled={busy}>{busy ? '上传中…' : '上传图片'}</button>
    {message ? <p className="media-upload-message" role="status">{message}</p> : null}
  </form>
}
