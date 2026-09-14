type ClipboardHost = {
  isSecureContext?: boolean
  navigator?: { clipboard?: { writeText?: (value: string) => Promise<void> } }
  document?: Document
}

function copyWithExecCommand(value: string, document: Document) {
  const input = document.createElement('textarea')
  input.value = value
  input.setAttribute('readonly', '')
  input.setAttribute('aria-hidden', 'true')
  input.style.position = 'fixed'
  input.style.top = '0'
  input.style.left = '-9999px'
  document.body.appendChild(input)
  input.focus()
  input.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(input)
  if (!copied) throw new Error('无法复制到剪贴板，请手动选中链接。')
}

export async function copyText(value: string, host: ClipboardHost = globalThis as ClipboardHost) {
  const clipboard = host.navigator?.clipboard
  if (host.isSecureContext !== false && typeof clipboard?.writeText === 'function') {
    try {
      await clipboard.writeText(value)
      return
    } catch {
      /* HTTP pages and denied permissions fall through to execCommand. */
    }
  }
  if (!host.document) throw new Error('无法复制到剪贴板，请手动选中链接。')
  copyWithExecCommand(value, host.document)
}
