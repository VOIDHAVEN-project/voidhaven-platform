import type { PublicContent } from '../lib/api'
import { customValues } from '../lib/resource-config'
import { safeUrl } from './document'

export function ResourceFacts({ item }: { item: PublicContent }) {
  const values = customValues(item.details)
  const fields =
    item.resourcePresentation?.fields.filter((field) => {
      const value = values[field.key]
      return value != null && (Array.isArray(value) ? value.length > 0 : String(value).trim() !== '')
    }) ?? []
  if (!fields.length) return null
  return (
    <section className="extra-facts" aria-label="资源信息">
      <h2>资源信息</h2>
      <dl>
        {fields.map((field) => {
          const value = values[field.key]
          const text = Array.isArray(value) ? value.join('、') : String(value)
          const url = field.type === 'url' ? safeUrl(text) : null
          return (
            <div key={field.key}>
              <dt>{field.label}</dt>
              <dd>
                {url ? (
                  <a href={url} target="_blank" rel="noreferrer">
                    {text}
                  </a>
                ) : (
                  text
                )}
              </dd>
            </div>
          )
        })}
      </dl>
    </section>
  )
}
