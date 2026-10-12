import { useState } from 'react'
import { createPortal } from 'react-dom'
import type { ResourceConfig, ResourceTemplate } from '../lib/api'
import { customValues } from '../lib/resource-config'
import { DynamicField } from './module-field'
import { ConfirmDialog, Notice, message, type NoticeValue } from './ui'

export function ResourceFields({
  config,
  details,
  onDetails,
  onTemplate,
  hasBody,
  onRefresh,
}: {
  config: ResourceConfig
  details: Record<string, unknown>
  onDetails: (details: Record<string, unknown>) => void
  onTemplate: (source: string) => void
  hasBody: () => boolean
  onRefresh: () => Promise<void>
}) {
  const values = customValues(details)
  const [template, setTemplate] = useState<ResourceTemplate | null>(null)
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [refreshing, setRefreshing] = useState(false)
  function apply(item: ResourceTemplate) {
    onTemplate(item.markdown)
    const next = { ...values }
    for (const key of item.fieldKeys) {
      const field = config.fields.find((f) => f.key === key && f.enabled)
      if (field && !Object.hasOwn(next, key) && Object.keys(next).length < 30)
        next[key] = field.type === 'tags' ? [] : ''
    }
    onDetails({ ...details, customFields: next })
  }
  return (
    <div className="resource-fields">
      <div className="form-grid">
        <label>
          写作模板
          <select
            value=""
            onChange={(event) => {
              const item = config.templates.find((t) => t.key === event.target.value)
              if (item) {
                if (hasBody()) setTemplate(item)
                else apply(item)
              }
            }}
          >
            <option value="">选择模板（可选）</option>
            {config.templates
              .filter((t) => t.enabled)
              .map((t) => (
                <option key={t.key} value={t.key}>
                  {t.label}
                </option>
              ))}
          </select>
        </label>
        <label>
          添加信息项
          <select
            value=""
            disabled={Object.keys(values).length >= 30}
            onChange={(event) => {
              const field = config.fields.find((f) => f.key === event.target.value)
              if (field)
                onDetails({
                  ...details,
                  customFields: { ...values, [field.key]: field.type === 'tags' ? [] : '' },
                })
            }}
          >
            <option value="">选择公共字段（可选）</option>
            {config.fields
              .filter((f) => f.enabled && !Object.hasOwn(values, f.key))
              .map((f) => (
                <option key={f.key} value={f.key}>
                  {f.label}
                </option>
              ))}
          </select>
        </label>
      </div>
      {config.fields
        .filter((f) => Object.hasOwn(values, f.key))
        .map((field) => (
          <div className="resource-field-row" key={field.key}>
            <DynamicField
              field={{ ...field, key: `custom-${field.key}`, required: false, filterable: false }}
              value={values[field.key]}
              onChange={(_, value) =>
                onDetails({ ...details, customFields: { ...values, [field.key]: value } })
              }
            />
            {!field.enabled ? <small>已停用：仅保留本篇已有信息</small> : null}
            <button
              type="button"
              className="button ghost"
              onClick={() => {
                const next = { ...values }
                delete next[field.key]
                onDetails({ ...details, customFields: next })
              }}
            >
              移除{field.label}
            </button>
          </div>
        ))}
      <p className="field-help">信息项仅用于展示，可留空；活动日期不会自动触发下架。最多添加 30 项。</p>
      <button
        type="button"
        className="button ghost"
        disabled={refreshing}
        onClick={async () => {
          setRefreshing(true)
          setNotice(null)
          try {
            await onRefresh()
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          } finally {
            setRefreshing(false)
          }
        }}
      >
        刷新字段与分类配置
      </button>
      <Notice value={notice} />
      {template
        ? createPortal(
            <div onSubmit={(event) => event.stopPropagation()}>
              <ConfirmDialog
                title="应用写作模板"
                description="应用后将替换当前正文。已填写的信息项会保留。"
                onClose={() => setTemplate(null)}
                onConfirm={async () => apply(template)}
              />
            </div>,
            document.body,
          )
        : null}
    </div>
  )
}
