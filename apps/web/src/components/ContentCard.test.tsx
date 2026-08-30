import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ContentCard } from './ContentCard'
import type { Content } from '../lib/api'
const sample: Content = { id: '01JLOCALFIXTURE000000000000', kind: 'COMPETITION', title: '合成竞赛', summary: '用于验收。', body: {}, languageCode: 'zh-CN', sourceName: '官方来源', sourceUrl: 'https://example.invalid', rightsBasis: '合成数据', details: { deadlineDate: '2026-10-15' }, revision: 1 }
describe('ContentCard', () => { it('links with opaque ID and shows an official source', () => { render(<MemoryRouter><ContentCard content={sample} /></MemoryRouter>); expect(screen.getByRole('link', { name: '合成竞赛' })).toHaveAttribute('href', '/competitions/01JLOCALFIXTURE000000000000'); expect(screen.getByText('官方来源')).toBeInTheDocument() }) })
