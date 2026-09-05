import { useRef, useState } from 'react'
import { api } from '../lib/api'
import type { PublicMedia } from '../lib/api'

export function ContentMediaUploadForm({ contentItemId, onUploaded, beforeUpload }: { contentItemId: string; onUploaded?: (media: PublicMedia) => void; beforeUpload?: () => Promise<void> }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [altText, setAltText] = useState('')
  const fileInput = useRef<HTMLInputElement>(null)
  const idempotencyKey = useRef<string | null>(null)

  async function upload() {
    const file = fileInput.current?.files?.item(0)
    const description = altText.trim()
    if (!file || file.size === 0) { setMessage('请选择一张图片后再上传。'); return }
    if (!description) { setMessage('请填写图片说明。'); return }
    setBusy(true)
    setMessage(null)
    try {
      await beforeUpload?.()
      idempotencyKey.current ??= crypto.randomUUID()
      const uploaded = await api.uploadContentMedia(contentItemId, file, description, idempotencyKey.current)
      if (fileInput.current) fileInput.current.value = ''
      idempotencyKey.current = null
      setAltText('')
      setMessage('图片已关联到当前候选修订；送审并发布后才可能公开展示。')
      onUploaded?.(uploaded)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '图片上传未完成。')
    } finally { setBusy(false) }
  }

  return <section className="media-upload-form" aria-label="正文图片上传">
    <label>正文图片<input ref={fileInput} name="file" type="file" accept="image/jpeg,image/png,image/webp" disabled={busy} onChange={() => { idempotencyKey.current = null }} /></label>
    <label>图片说明<input name="altText" value={altText} onChange={(event) => { setAltText(event.target.value); idempotencyKey.current = null }} maxLength={240} disabled={busy} /></label>
    <button type="button" className="button ghost" disabled={busy} onClick={() => void upload()}>{busy ? '上传中…' : '上传图片'}</button>
    {message ? <p className="media-upload-message" role="status">{message}</p> : null}
  </section>
}
