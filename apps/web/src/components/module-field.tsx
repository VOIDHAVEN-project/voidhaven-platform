import { memo } from 'react'
import type { ModuleField } from '../lib/api'
import { editorFieldId } from '../lib/editor-validation'
import { fieldOptionLabel } from '../lib/module-field-labels'

export const DynamicField = memo(function DynamicField({
  field,
  value,
  invalid,
  onChange,
}: {
  field: ModuleField
  value: unknown
  invalid?: boolean
  onChange: (key: string, value: unknown) => void
}) {
  const text = value == null ? '' : String(value)
  const controlId = editorFieldId(field.key)
  return (
    <label className={field.type === 'url' || field.type === 'textarea' ? 'wide' : ''}>
      {field.label}
      {field.required ? '（必填）' : ''}
      {field.type === 'select' ? (
        <select
          id={controlId}
          value={text}
          required={field.required}
          aria-invalid={invalid}
          onChange={(event) => onChange(field.key, event.target.value)}
        >
          {!field.required ? <option value="">未设置</option> : null}
          {field.options?.map((option) => (
            <option key={option} value={option}>
              {field.optionLabels?.[option] ?? fieldOptionLabel(option)}
            </option>
          ))}
        </select>
      ) : field.type === 'boolean' ? (
        <select
          id={controlId}
          value={text}
          required={field.required}
          aria-invalid={invalid}
          onChange={(event) => onChange(field.key, event.target.value === 'true')}
        >
          <option value="">未设置</option>
          <option value="true">是</option>
          <option value="false">否</option>
        </select>
      ) : field.type === 'textarea' || field.type === 'tags' ? (
        <textarea
          id={controlId}
          value={Array.isArray(value) ? value.join('，') : text}
          required={field.required}
          aria-invalid={invalid}
          rows={2}
          onChange={(event) =>
            onChange(
              field.key,
              field.type === 'tags'
                ? event.target.value
                    .split(/[，,]/)
                    .map((item) => item.trim())
                    .filter(Boolean)
                : event.target.value,
            )
          }
        />
      ) : (
        <input
          id={controlId}
          value={text}
          required={field.required}
          aria-invalid={invalid}
          type={['date', 'url', 'number'].includes(field.type) ? field.type : 'text'}
          onChange={(event) =>
            onChange(
              field.key,
              field.type === 'number' && event.target.value ? Number(event.target.value) : event.target.value,
            )
          }
        />
      )}
      {field.help ? <small className="field-help">{field.help}</small> : null}
    </label>
  )
})
