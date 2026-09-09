type SecureRandomSource = {
  randomUUID?: () => string
  getRandomValues?: (array: Uint8Array<ArrayBuffer>) => Uint8Array<ArrayBuffer>
}

function formatUuid(bytes: Uint8Array) {
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function createIdempotencyKey(
  source: SecureRandomSource | undefined = globalThis.crypto as unknown as SecureRandomSource,
) {
  if (typeof source?.randomUUID === 'function') return source.randomUUID.call(source)
  if (typeof source?.getRandomValues !== 'function') {
    throw new Error('当前浏览器不支持安全随机数，无法安全提交。请升级浏览器或改用 HTTPS 后重试。')
  }

  const bytes = source.getRandomValues.call(source, new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  return formatUuid(bytes)
}
