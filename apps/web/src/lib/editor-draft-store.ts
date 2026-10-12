import type { AdminDraft } from './api'

export const EDITOR_DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000
export const EDITOR_DRAFT_CHANNEL = 'voidhaven-editor-drafts'
const SENSITIVE_KEY = /password|token|secret|credential|csrf|activation/i

export type EditorDraftRecord = {
  version: 1
  savedAt: number
  tabId: string
  markdownSource: string
  draft: Record<string, unknown>
}

export type EditorDraftBackend = {
  get(key: string): Promise<EditorDraftRecord | null>
  set(key: string, value: EditorDraftRecord): Promise<void>
  delete(key: string): Promise<void>
}

export function editorDraftKey(contentId?: string) {
  return contentId ? `item:${contentId}` : 'new'
}

export function sanitizeEditorDraft(draft: AdminDraft): Record<string, unknown> {
  const details = Object.fromEntries(
    Object.entries(draft.details ?? {}).filter(
      ([key, value]) => !SENSITIVE_KEY.test(key) && !(value instanceof File) && typeof value !== 'function',
    ),
  )
  return {
    kind: draft.kind,
    moduleKey: draft.moduleKey,
    schemaVersion: draft.schemaVersion,
    resourceConfigVersion: draft.resourceConfigVersion,
    title: draft.title,
    summary: draft.summary,
    languageCode: draft.languageCode,
    originalTitle: draft.originalTitle ?? '',
    sourceUrl: draft.sourceUrl ?? '',
    sourceName: draft.sourceName ?? '',
    rightsBasis: draft.rightsBasis,
    authorDisplayName: draft.authorDisplayName ?? '',
    reprintPermission: draft.reprintPermission,
    adaptationPermission: draft.adaptationPermission,
    details,
  }
}

export function isExpiredEditorDraft(record: EditorDraftRecord, now = Date.now()) {
  return now - record.savedAt > EDITOR_DRAFT_TTL_MS
}

export function editorDraftsDiffer(record: EditorDraftRecord, draft: AdminDraft, markdownSource: string) {
  return (
    JSON.stringify({ draft: record.draft, markdownSource: record.markdownSource }) !==
    JSON.stringify({ draft: sanitizeEditorDraft(draft), markdownSource })
  )
}

export function applyStoredEditorDraft(current: AdminDraft, stored: EditorDraftRecord): AdminDraft {
  const next = stored.draft
  const details = {
    ...current.details,
    ...(next.details && typeof next.details === 'object' && !Array.isArray(next.details)
      ? (next.details as Record<string, unknown>)
      : {}),
  }
  return {
    ...current,
    kind: typeof next.kind === 'string' ? (next.kind as AdminDraft['kind']) : current.kind,
    moduleKey: typeof next.moduleKey === 'string' ? next.moduleKey : current.moduleKey,
    schemaVersion: typeof next.schemaVersion === 'number' ? next.schemaVersion : current.schemaVersion,
    title: typeof next.title === 'string' ? next.title : current.title,
    summary: typeof next.summary === 'string' ? next.summary : current.summary,
    languageCode: typeof next.languageCode === 'string' ? next.languageCode : current.languageCode,
    originalTitle: typeof next.originalTitle === 'string' ? next.originalTitle : current.originalTitle,
    sourceUrl: typeof next.sourceUrl === 'string' ? next.sourceUrl : current.sourceUrl,
    sourceName: typeof next.sourceName === 'string' ? next.sourceName : current.sourceName,
    rightsBasis: typeof next.rightsBasis === 'string' ? next.rightsBasis : current.rightsBasis,
    authorDisplayName:
      typeof next.authorDisplayName === 'string' ? next.authorDisplayName : current.authorDisplayName,
    reprintPermission:
      next.reprintPermission === 'ATTRIBUTION_ALLOWED' || next.reprintPermission === 'NOT_ALLOWED'
        ? next.reprintPermission
        : current.reprintPermission,
    adaptationPermission:
      next.adaptationPermission === 'ATTRIBUTION_ALLOWED' || next.adaptationPermission === 'NOT_ALLOWED'
        ? next.adaptationPermission
        : current.adaptationPermission,
    details,
    markdownSource: stored.markdownSource,
  }
}

export function formatDraftSavedAt(savedAt: number) {
  return new Date(savedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export function createMemoryDraftBackend(
  initial: Iterable<[string, EditorDraftRecord]> = [],
): EditorDraftBackend {
  const records = new Map(initial)
  return {
    async get(key) {
      return records.get(key) ?? null
    },
    async set(key, value) {
      records.set(key, value)
    },
    async delete(key) {
      records.delete(key)
    },
  }
}

function indexedDbBackend(): EditorDraftBackend | null {
  if (typeof indexedDB === 'undefined') return null
  const open = () =>
    new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open('voidhaven-editor-drafts', 1)
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains('drafts')) request.result.createObjectStore('drafts')
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  const withStore = async <T>(mode: IDBTransactionMode, run: (store: IDBObjectStore) => IDBRequest<T>) => {
    const db = await open()
    try {
      return await new Promise<T>((resolve, reject) => {
        const transaction = db.transaction('drafts', mode)
        const request = run(transaction.objectStore('drafts'))
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
      })
    } finally {
      db.close()
    }
  }
  return {
    async get(key) {
      const value = await withStore('readonly', (store) => store.get(key))
      return value && typeof value === 'object' ? (value as EditorDraftRecord) : null
    },
    async set(key, value) {
      await withStore('readwrite', (store) => store.put(value, key))
    },
    async delete(key) {
      await withStore('readwrite', (store) => store.delete(key))
    },
  }
}

let sharedBackend: EditorDraftBackend | null = null

export function editorDraftBackend() {
  if (!sharedBackend) sharedBackend = indexedDbBackend() ?? createMemoryDraftBackend()
  return sharedBackend
}

export async function readEditorDraft(key: string, backend: EditorDraftBackend = editorDraftBackend()) {
  try {
    const record = await backend.get(key)
    if (!record || record.version !== 1 || isExpiredEditorDraft(record)) {
      if (record) await backend.delete(key)
      return null
    }
    return record
  } catch {
    return null
  }
}

export async function writeEditorDraft(
  key: string,
  record: EditorDraftRecord,
  backend: EditorDraftBackend = editorDraftBackend(),
) {
  await backend.set(key, record)
}

export async function clearEditorDraft(key: string, backend: EditorDraftBackend = editorDraftBackend()) {
  try {
    await backend.delete(key)
  } catch {
    /* local draft cleanup must not block official save */
  }
}
