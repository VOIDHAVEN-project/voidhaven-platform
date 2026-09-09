import { useCallback, useEffect, useState } from 'react'
import { message } from '../components/ui'
export function useResource<T>(loader: (signal?: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true)
    setError('')
    loader(controller.signal)
      .then((value) => {
        if (active) setData(value)
      })
      .catch((err) => {
        if (active && !(err instanceof DOMException && err.name === 'AbortError')) setError(message(err))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [loader, attempt])
  const reload = useCallback(() => setAttempt((value) => value + 1), [])
  return { data, error, loading, reload, setData }
}
export function usePageTitle(title: string, description?: string) {
  useEffect(() => {
    document.title = `${title} — VOIDHAVEN`
    document
      .querySelector('meta[name="description"]')
      ?.setAttribute(
        'content',
        description ?? '竞赛、学习资源与团队招募，一处浏览。我们整理线索，不替代官方规则。',
      )
  }, [title, description])
}
export function useCollapsed() {
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem('voidhaven-sidebar') === 'collapsed'
    } catch {
      return false
    }
  })
  return [
    collapsed,
    () =>
      setCollapsed((value) => {
        try {
          localStorage.setItem('voidhaven-sidebar', value ? 'expanded' : 'collapsed')
        } catch {
          /* Optional preference. */
        }
        return !value
      }),
  ] as const
}
