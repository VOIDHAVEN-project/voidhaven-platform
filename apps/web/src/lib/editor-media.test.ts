import { describe, expect, it } from 'vitest'
import {
  mediaAltInBody,
  mediaInsertToken,
  mediaUploadState,
  referencedMediaIds,
  replaceMediaAltText,
  stripMediaReferences,
} from './editor-media'

const id = '01HZX9Z8NQ2J3K4M5P6R7S8T9U'

describe('editor media helpers', () => {
  it('detects media tokens referenced in markdown', () => {
    expect([...referencedMediaIds(`![说明](media:${id})\n\nplain`)]).toEqual([id])
  })

  it('builds an insert token that the preview parser can read', () => {
    expect(mediaInsertToken(id, '示意图')).toContain(`![示意图](media:${id})`)
  })

  it('removes image lines and leftover tokens for a media id', () => {
    const source = `前言\n\n![示意图](media:${id})\n\n后记 media:${id}`
    expect(stripMediaReferences(source, id)).toBe('前言\n\n后记 ')
  })

  it('gates uploads behind an editable draft', () => {
    expect(mediaUploadState()).toBe('needs-new-draft')
    expect(mediaUploadState(null)).toBe('needs-new-draft')
    expect(mediaUploadState({ workflowStatus: 'DRAFT' })).toBe('ready')
    expect(mediaUploadState({ workflowStatus: 'CHANGES_REQUESTED' })).toBe('ready')
    expect(mediaUploadState({ workflowStatus: 'PUBLISHED' })).toBe('needs-candidate')
    expect(mediaUploadState({ workflowStatus: 'PENDING_REVIEW' })).toBe('blocked')
    expect(mediaUploadState({ workflowStatus: 'APPROVED' })).toBe('blocked')
  })

  it('reads the alt text the body currently uses', () => {
    expect(mediaAltInBody(`前言\n\n![正文说明](media:${id})\n`, id)).toBe('正文说明')
    expect(mediaAltInBody('没有任何引用', id)).toBeNull()
  })

  it('rewrites every occurrence of the same media id', () => {
    const source = `![旧说明](media:${id})\n\n正文 media:${id}\n\n![旧说明](media:${id})`
    const next = replaceMediaAltText(source, id, ' 新说明 ')
    expect(next.match(new RegExp(`!\\[新说明]\\(media:${id}\\)`, 'g'))).toHaveLength(2)
    expect(next).not.toContain('旧说明')
  })

  it('keeps other media references and normalizes the rewritten alt text', () => {
    const other = '01HZX9Z8NQ2J3K4M5P6R7S8T9V'
    const source = `![保留](media:${other})\n\n![旧](media:${id})`
    expect(replaceMediaAltText(source, id, '带]括号\n换行的说明')).toBe(
      `![保留](media:${other})\n\n![带括号 换行的说明](media:${id})`,
    )
    expect(mediaAltInBody(replaceMediaAltText(source, id, ''), id)).toBe('图片')
    expect(mediaAltInBody(replaceMediaAltText(source, id, 'x'.repeat(400)), id)).toHaveLength(240)
  })
})
