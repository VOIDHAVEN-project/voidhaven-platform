import type { ResourceConfig } from './api'

export function customValues(details: Record<string, unknown>): Record<string, unknown> {
  const value = details.customFields
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}
}

export function resourceEditorConfig(
  current: ResourceConfig | undefined | null,
  saved: ResourceConfig | undefined | null,
  details: Record<string, unknown>,
) {
  if (!current) return saved ?? undefined
  const values = customValues(details)
  return {
    ...current,
    fields: current.fields.map((field) => {
      const historical = saved?.fields.find((f) => f.key === field.key)
      return !field.enabled && historical && Object.hasOwn(values, field.key)
        ? { ...historical, enabled: false }
        : field
    }),
  }
}

/** Restore as a new revision, retaining identities introduced after the historical version. */
export function restoreResourceConfig(current: ResourceConfig, historical: ResourceConfig): ResourceConfig {
  const merge = <T extends { key: string; enabled: boolean }>(latest: T[], old: T[]) => [
    ...old,
    ...latest
      .filter((item) => !old.some((value) => value.key === item.key))
      .map((item) => ({ ...item, enabled: false })),
  ]
  return {
    version: current.version,
    categories: merge(current.categories, historical.categories),
    templates: merge(current.templates, historical.templates),
    fields: merge(current.fields, historical.fields).map((field) => ({
      ...field,
      options: [
        ...new Set([...field.options, ...(current.fields.find((f) => f.key === field.key)?.options ?? [])]),
      ],
    })),
  }
}
