import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  me: vi.fn(),
  signOut: vi.fn(),
}))

vi.mock('../lib/api', () => ({ api: mocks }))
vi.mock('../components/AdminLifecyclePanel', () => ({
  AdminLifecyclePanel: ({ onDirtyChange }: { onDirtyChange?: (dirty: boolean) => void }) =>
    <button type="button" onClick={() => onDirtyChange?.(true)}>制造未保存修改</button>,
}))

import { WorkspacePage } from './WorkspacePage'

describe('WorkspacePage draft protection', () => {
  beforeEach(() => {
    mocks.me.mockReset().mockResolvedValue({ loginId: 'synthetic-admin', roles: ['SUPER_ADMIN'] })
    mocks.signOut.mockReset()
    vi.restoreAllMocks()
  })

  it('cancels logout when the author refuses to discard an unsaved draft', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    render(<MemoryRouter initialEntries={['/workspace']}><WorkspacePage /></MemoryRouter>)
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: '你好，synthetic-admin' })
    await user.click(screen.getByRole('button', { name: '制造未保存修改' }))
    await user.click(screen.getByRole('button', { name: '退出' }))

    expect(confirm).toHaveBeenCalledWith('当前还有未保存修改，确定放弃并离开吗？')
    expect(mocks.signOut).not.toHaveBeenCalled()
  })

  it('keeps the workspace open and reports a server logout failure', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    mocks.signOut.mockRejectedValue(new Error('服务暂时不可用'))
    render(<MemoryRouter initialEntries={['/workspace']}><WorkspacePage /></MemoryRouter>)
    const user = userEvent.setup()

    await screen.findByRole('heading', { name: '你好，synthetic-admin' })
    await user.click(screen.getByRole('button', { name: '制造未保存修改' }))
    await user.click(screen.getByRole('button', { name: '退出' }))

    await screen.findByRole('alert')
    expect(screen.getByText('服务暂时不可用')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: '退出' })).toBeEnabled())
  })
})
