import { useEffect, useRef, useState } from 'react'
import { api, type ResourceConfig, type ResourceField } from '../lib/api'
import { useResource } from '../lib/hooks'
import { createIdempotencyKey } from '../lib/secure-id'
import { restoreResourceConfig } from '../lib/resource-config'
import { ErrorState, LoadingRows, Notice, message, type NoticeValue } from './ui'

const types: { value: ResourceField['type']; label: string }[] = [
  { value: 'text', label: '短文本' },
  { value: 'textarea', label: '长文本' },
  { value: 'date', label: '日期' },
  { value: 'url', label: '链接' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '单选' },
  { value: 'tags', label: '标签列表' },
]
type Section = 'categories' | 'fields' | 'templates'

export function ResourceConfigPanel() {
  const state = useResource(api.adminResourceConfig)
  if (state.loading) return <LoadingRows />
  if (state.error || !state.data)
    return <ErrorState error={state.error || '无法读取配置。'} retry={state.reload} />
  return <ConfigEditor initial={state.data} />
}

function ConfigEditor({ initial }: { initial: ResourceConfig }) {
  const [saved, setSaved] = useState(initial)
  const [draft, setDraft] = useState(() => structuredClone(initial))
  const [section, setSection] = useState<Section>('categories')
  const [notice, setNotice] = useState<NoticeValue>(null)
  const [busy, setBusy] = useState(false)
  const [history, setHistory] = useState<ResourceConfig[]>([])
  const [more, setMore] = useState(false)
  const [latest, setLatest] = useState<ResourceConfig | null>(null)
  const attempt = useRef<{ body: string; key: string } | null>(null)
  const dirty = JSON.stringify(saved) !== JSON.stringify(draft)
  useEffect(() => {
    if (!dirty) return
    const block = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    const navigate = (event: MouseEvent) => {
      const target = event.target instanceof Element ? event.target.closest('a[href]') : null
      if (target && !window.confirm('配置尚未保存，确定离开吗？')) {
        event.preventDefault()
        event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', block)
    document.addEventListener('click', navigate, true)
    return () => {
      window.removeEventListener('beforeunload', block)
      document.removeEventListener('click', navigate, true)
    }
  }, [dirty])

  function patch(index: number, changes: Record<string, unknown>) {
    setDraft((current) => ({
      ...current,
      [section]: current[section].map((item, i) => (i === index ? { ...item, ...changes } : item)),
    }))
  }
  function move(index: number, offset: number) {
    const items = [...draft[section]]
    const next = index + offset
    if (next < 0 || next >= items.length) return
    ;[items[index], items[next]] = [items[next], items[index]]
    setDraft({ ...draft, [section]: items })
  }
  function add() {
    const key = `custom_${crypto.randomUUID().replaceAll('-', '').slice(0, 12)}`
    const base = { key, label: '', enabled: true }
    const item =
      section === 'fields'
        ? { ...base, type: 'text', help: '', options: [] }
        : section === 'templates'
          ? { ...base, markdown: '', fieldKeys: [] }
          : base
    setDraft({ ...draft, [section]: [...draft[section], item] })
  }
  async function loadHistory(before?: number) {
    setBusy(true)
    try {
      const values = await api.adminResourceConfigHistory(before)
      setHistory(before ? [...history, ...values] : values)
      setMore(values.length === 20)
    } catch (error) {
      setNotice({ tone: 'error', text: message(error) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="resource-config-panel">
      <h1>资源与分享设置</h1>
      <p>维护公共分类、可选信息项和写作模板。保存后供作者使用，已发布内容保留原展示。</p>
      <div className="resource-config-tabs" aria-label="配置分组">
        {(['categories', 'fields', 'templates'] as const).map((value, i) => (
          <button
            key={value}
            type="button"
            className={`button ${section === value ? 'primary' : 'ghost'}`}
            aria-pressed={section === value}
            onClick={() => setSection(value)}
          >
            {['内容分类', '公共字段', '写作模板'][i]}
          </button>
        ))}
      </div>
      <form
        onSubmit={async (event) => {
          event.preventDefault()
          if (busy) return
          setBusy(true)
          setNotice(null)
          const body = JSON.stringify(draft)
          if (attempt.current?.body !== body) attempt.current = { body, key: createIdempotencyKey() }
          try {
            const next = await api.adminSaveResourceConfig(draft, attempt.current!.key)
            setSaved(next)
            setDraft(next)
            setLatest(null)
            attempt.current = null
            setNotice({ tone: 'success', text: '配置已保存并生效。' })
          } catch (error) {
            setNotice({ tone: 'error', text: message(error) })
          } finally {
            setBusy(false)
          }
        }}
      >
        <fieldset disabled={busy}>
          {draft[section].map((item, index) => {
            const existing = saved[section].some((value) => value.key === item.key)
            const field = section === 'fields' ? draft.fields[index] : null
            const template = section === 'templates' ? draft.templates[index] : null
            return (
              <div className="resource-config-item" key={item.key}>
                <div className="form-grid">
                  <label>
                    名称
                    <input
                      required
                      maxLength={80}
                      value={item.label}
                      onChange={(e) => patch(index, { label: e.target.value })}
                    />
                  </label>
                  <label>
                    状态
                    <select
                      value={item.enabled ? 'enabled' : 'disabled'}
                      disabled={item.key === 'GENERAL'}
                      onChange={(e) => patch(index, { enabled: e.target.value === 'enabled' })}
                    >
                      <option value="enabled">启用</option>
                      <option value="disabled">停用</option>
                    </select>
                  </label>
                  {field ? (
                    <>
                      <label>
                        信息类型
                        <select
                          value={field.type}
                          disabled={existing}
                          onChange={(e) => patch(index, { type: e.target.value })}
                        >
                          {types.map((t) => (
                            <option key={t.value} value={t.value}>
                              {t.label}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label>
                        填写说明
                        <input
                          maxLength={500}
                          value={field.help}
                          onChange={(e) => patch(index, { help: e.target.value })}
                        />
                      </label>
                      {field.type === 'select' ? (
                        <label className="wide">
                          选项（每行一个；已有选项请保留）
                          <textarea
                            value={field.options.join('\n')}
                            rows={4}
                            onChange={(e) => patch(index, { options: e.target.value.split('\n') })}
                          />
                        </label>
                      ) : null}
                    </>
                  ) : null}
                  {template ? (
                    <>
                      <label className="wide">
                        模板正文
                        <textarea
                          value={template.markdown}
                          maxLength={262144}
                          rows={8}
                          onChange={(e) => patch(index, { markdown: e.target.value })}
                        />
                      </label>
                      <fieldset className="wide">
                        <legend>推荐信息项（作者可移除）</legend>
                        {draft.fields
                          .filter((f) => f.enabled || template.fieldKeys.includes(f.key))
                          .map((f) => (
                            <label className="resource-checkbox" key={f.key}>
                              <input
                                type="checkbox"
                                checked={template.fieldKeys.includes(f.key)}
                                onChange={(e) =>
                                  patch(index, {
                                    fieldKeys: e.target.checked
                                      ? [...template.fieldKeys, f.key]
                                      : template.fieldKeys.filter((k) => k !== f.key),
                                  })
                                }
                              />
                              {f.label || '未命名字段'}
                              {!f.enabled ? '（已停用）' : ''}
                            </label>
                          ))}
                      </fieldset>
                    </>
                  ) : null}
                </div>
                <div className="resource-config-actions">
                  <button
                    type="button"
                    className="button ghost"
                    disabled={index === 0}
                    onClick={() => move(index, -1)}
                    aria-label={`上移${item.label}`}
                  >
                    上移
                  </button>
                  <button
                    type="button"
                    className="button ghost"
                    disabled={index === draft[section].length - 1}
                    onClick={() => move(index, 1)}
                    aria-label={`下移${item.label}`}
                  >
                    下移
                  </button>
                  {!existing ? (
                    <button
                      type="button"
                      className="button ghost"
                      onClick={() =>
                        setDraft({ ...draft, [section]: draft[section].filter((_, i) => i !== index) })
                      }
                    >
                      移除未保存项
                    </button>
                  ) : null}
                </div>
              </div>
            )
          })}
          <div className="resource-config-actions">
            <button type="button" className="button ghost" onClick={add}>
              新增{section === 'categories' ? '分类' : section === 'fields' ? '公共字段' : '模板'}
            </button>
            <button className="button primary" disabled={!dirty}>
              保存并生效
            </button>
            <button
              type="button"
              className="button ghost"
              onClick={async () => {
                setBusy(true)
                try {
                  setLatest(await api.adminResourceConfig())
                } catch (error) {
                  setNotice({ tone: 'error', text: message(error) })
                } finally {
                  setBusy(false)
                }
              }}
            >
              核对最新版本
            </button>
            <button type="button" className="button ghost" onClick={() => void loadHistory()}>
              查看历史配置
            </button>
          </div>
        </fieldset>
      </form>
      <Notice value={notice} />
      {latest ? (
        <section className="resource-config-item">
          <h2>服务器配置 · 版本 {latest.version}</h2>
          <p>你的输入仍保留在上方。核对下面的最新配置后，可继续编辑。</p>
          {(['categories', 'fields', 'templates'] as const).map((group, i) => (
            <details key={group}>
              <summary>{['分类', '公共字段', '模板'][i]}</summary>
              {latest[group].map((item) => (
                <div key={item.key}>
                  <strong>
                    {item.label}（{item.enabled ? '启用' : '停用'}）
                  </strong>
                  {'help' in item ? (
                    <p>
                      {item.help} {'options' in item ? item.options.join('、') : ''}
                    </p>
                  ) : null}
                  {'markdown' in item ? <pre>{item.markdown}</pre> : null}
                </div>
              ))}
            </details>
          ))}
          <button
            type="button"
            className="button ghost"
            onClick={() => {
              setDraft(restoreResourceConfig(latest, draft))
              setSaved(latest)
              setLatest(null)
              attempt.current = null
            }}
          >
            已核对，保留输入继续编辑
          </button>
        </section>
      ) : null}
      {history.length ? (
        <section className="resource-config-item">
          <h2>历史配置</h2>
          <p>恢复会载入待保存配置，并保留后续新增标识为停用状态；点击保存后生成新版本。</p>
          {history.map((value) => (
            <div className="resource-config-actions" key={value.version}>
              <span>
                版本 {value.version} · {value.categories.length} 个分类 · {value.fields.length} 个字段 ·{' '}
                {value.templates.length} 个模板
              </span>
              <button
                type="button"
                className="button ghost"
                disabled={busy}
                onClick={() => {
                  if (dirty && !window.confirm('载入历史配置将替换当前未保存的配置，是否继续？')) return
                  setDraft(restoreResourceConfig(saved, value))
                  setNotice({ tone: 'success', text: '历史配置已载入，请检查各分组后保存并生效。' })
                }}
              >
                载入此版本
              </button>
            </div>
          ))}
          {more ? (
            <button
              type="button"
              className="button ghost"
              disabled={busy}
              onClick={() => void loadHistory(history.at(-1)?.version)}
            >
              加载更早版本
            </button>
          ) : null}
        </section>
      ) : null}
    </section>
  )
}
