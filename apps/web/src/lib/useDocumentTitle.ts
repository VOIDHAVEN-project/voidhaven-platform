import { useEffect } from 'react'

const SITE_NAME = 'VOIDHAVEN'
const DEFAULT_DESCRIPTION = 'VOIDHAVEN 是面向本校学生的竞赛、学习资源与团队招募发现平台，集中呈现带来源、可核验的信息。'

/**
 * Sets document.title and the meta description for the current route, and restores the
 * previous values on unmount. This is a stopgap for a pure SPA with no SSR/prerendering:
 * it does not make content crawlable by itself, but it fixes the visible symptom (every
 * page showing the same generic title) and gives crawlers that do execute JS a per-page
 * description to index.
 */
export function useDocumentTitle(title?: string, description?: string) {
  useEffect(() => {
    const previousTitle = document.title
    document.title = title ? `${title} · ${SITE_NAME}` : `${SITE_NAME} · 校内竞赛与学习资源发现平台`

    let tag = document.querySelector('meta[name="description"]')
    let created = false
    if (!tag) {
      tag = document.createElement('meta')
      tag.setAttribute('name', 'description')
      document.head.appendChild(tag)
      created = true
    }
    const previousDescription = tag.getAttribute('content')
    tag.setAttribute('content', description ?? DEFAULT_DESCRIPTION)

    return () => {
      document.title = previousTitle
      if (created) tag?.remove()
      else if (previousDescription != null) tag?.setAttribute('content', previousDescription)
    }
  }, [title, description])
}
