export type IndentResult = { source: string; start: number; end: number }

function lineStart(source: string, index: number) {
  return source.lastIndexOf('\n', Math.max(0, index) - 1) + 1
}

function lineEnd(source: string, index: number) {
  const next = source.indexOf('\n', index)
  return next < 0 ? source.length : next
}

export function applyMarkdownIndent(
  source: string,
  start: number,
  end: number,
  direction: 1 | -1,
): IndentResult {
  const from = Math.max(0, Math.min(start, end))
  const to = Math.max(0, Math.max(start, end))
  if (direction === 1 && from === to) {
    const next = source.slice(0, from) + '  ' + source.slice(from)
    return { source: next, start: from + 2, end: from + 2 }
  }
  const rangeStart = lineStart(source, from)
  const rangeEnd =
    to === from ? lineEnd(source, from) : lineEnd(source, Math.max(0, to - (source[to - 1] === '\n' ? 1 : 0)))
  const block = source.slice(rangeStart, rangeEnd)
  const lines = block.split('\n')
  let leading = 0
  const nextLines = lines.map((line) => {
    if (direction === 1) {
      leading += 2
      return `  ${line}`
    }
    if (line.startsWith('  ')) {
      leading -= 2
      return line.slice(2)
    }
    if (line.startsWith(' ') || line.startsWith('\t')) {
      leading -= 1
      return line.slice(1)
    }
    return line
  })
  const nextBlock = nextLines.join('\n')
  const next = source.slice(0, rangeStart) + nextBlock + source.slice(rangeEnd)
  if (from === to) {
    const cursor = Math.max(rangeStart, from + leading)
    return { source: next, start: cursor, end: cursor }
  }
  return {
    source: next,
    start: rangeStart,
    end: rangeStart + nextBlock.length,
  }
}
