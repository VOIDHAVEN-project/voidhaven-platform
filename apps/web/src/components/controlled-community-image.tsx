import { useEffect, useState } from 'react'
import { api } from '../lib/api'

export function ControlledCommunityImage({ path, grant, alt }: { path: string; grant: string; alt: string }) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    let active = true
    let objectUrl = ''
    api
      .communityMediaBlob(path, grant)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => setUrl(''))
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [path, grant])
  return url ? (
    <img className="community-qr" src={url} alt={alt} />
  ) : (
    <p className="field-help">二维码加载中或当前不可用。</p>
  )
}
