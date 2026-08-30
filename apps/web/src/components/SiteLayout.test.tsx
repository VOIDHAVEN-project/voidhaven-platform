import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { SiteLayout } from './SiteLayout'
describe('SiteLayout', () => {
  it('keeps controlled login out of public navigation', () => {
    render(<MemoryRouter><Routes><Route element={<SiteLayout />}><Route index element={<p>首页内容</p>} /></Route></Routes></MemoryRouter>)
    expect(screen.getByRole('navigation', { name: '主导航' })).toHaveTextContent('竞赛资源团队招募投稿/反馈加入我们关于')
    expect(screen.queryByText(/登录/)).not.toBeInTheDocument()
  })
})
