import { describe, expect, it, vi } from 'vitest'
import { createIdempotencyKey } from './secure-id'

describe('createIdempotencyKey', () => {
  it('uses the native UUID implementation when available', () => {
    const randomUUID = vi.fn(() => 'native-uuid')
    expect(createIdempotencyKey({ randomUUID })).toBe('native-uuid')
    expect(randomUUID).toHaveBeenCalledOnce()
  })

  it('creates an RFC 4122 version 4 UUID from secure random bytes', () => {
    const getRandomValues = vi.fn((bytes: Uint8Array<ArrayBuffer>) => {
      bytes.set(Array.from({ length: 16 }, (_, index) => index))
      return bytes
    })
    const value = createIdempotencyKey({ getRandomValues })
    expect(value).toBe('00010203-0405-4607-8809-0a0b0c0d0e0f')
    expect(value).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(getRandomValues).toHaveBeenCalledOnce()
  })

  it('refuses to create a weak key when secure randomness is unavailable', () => {
    expect(() => createIdempotencyKey({})).toThrow('不支持安全随机数')
  })
})
