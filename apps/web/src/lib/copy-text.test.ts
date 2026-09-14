import { describe, expect, it, vi } from 'vitest'
import { copyText } from './copy-text'

describe('copyText', () => {
  it('uses the clipboard API in a secure context', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    await copyText('https://example.test/activate', {
      isSecureContext: true,
      navigator: { clipboard: { writeText } },
    })
    expect(writeText).toHaveBeenCalledWith('https://example.test/activate')
  })

  it('falls back to execCommand on HTTP pages without clipboard', async () => {
    const execCommand = vi.fn().mockReturnValue(true)
    const textarea = {
      value: '',
      style: {} as CSSStyleDeclaration,
      setAttribute: vi.fn(),
      focus: vi.fn(),
      select: vi.fn(),
    }
    const document = {
      createElement: vi.fn(() => textarea),
      execCommand,
      body: { appendChild: vi.fn(), removeChild: vi.fn() },
    } as unknown as Document
    await copyText('once-token-url', {
      isSecureContext: false,
      navigator: {},
      document,
    })
    expect(textarea.value).toBe('once-token-url')
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('falls back when the clipboard API rejects', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('NotAllowedError'))
    const execCommand = vi.fn().mockReturnValue(true)
    const textarea = {
      value: '',
      style: {} as CSSStyleDeclaration,
      setAttribute: vi.fn(),
      focus: vi.fn(),
      select: vi.fn(),
    }
    const document = {
      createElement: vi.fn(() => textarea),
      execCommand,
      body: { appendChild: vi.fn(), removeChild: vi.fn() },
    } as unknown as Document
    await copyText('fallback-url', {
      isSecureContext: true,
      navigator: { clipboard: { writeText } },
      document,
    })
    expect(execCommand).toHaveBeenCalledWith('copy')
  })
})
