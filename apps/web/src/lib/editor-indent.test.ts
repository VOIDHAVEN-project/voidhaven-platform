import { describe, expect, it } from 'vitest'
import { applyMarkdownIndent } from './editor-indent'

describe('applyMarkdownIndent', () => {
  it('inserts two spaces at the caret when nothing is selected', () => {
    expect(applyMarkdownIndent('ab', 1, 1, 1)).toEqual({ source: 'a  b', start: 3, end: 3 })
  })

  it('indents every selected line and keeps the block selected', () => {
    expect(applyMarkdownIndent('one\ntwo\nthree', 0, 7, 1)).toEqual({
      source: '  one\n  two\nthree',
      start: 0,
      end: 11,
    })
  })

  it('removes two leading spaces with Shift+Tab', () => {
    expect(applyMarkdownIndent('  one\n  two', 0, 11, -1)).toEqual({
      source: 'one\ntwo',
      start: 0,
      end: 7,
    })
  })

  it('reduces a single leading space and ignores unindented lines', () => {
    expect(applyMarkdownIndent(' one\nplain', 0, 10, -1)).toEqual({
      source: 'one\nplain',
      start: 0,
      end: 9,
    })
  })
})
