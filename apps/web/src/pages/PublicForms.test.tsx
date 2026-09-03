import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { FeedbackPage } from './FeedbackPage'
import { JoinPage } from './JoinPage'

const mocks = vi.hoisted(() => ({ submit: vi.fn(), partnerApply: vi.fn() }))
vi.mock('../lib/api', () => ({ api: mocks }))

describe('公开表单', () => {
  it('submits feedback and safely resets the form after the asynchronous request', async () => {
    mocks.submit.mockResolvedValueOnce({ id: '01JLOCALSUBMISSION000000000' })
    const user = userEvent.setup()
    render(<FeedbackPage />)
    await user.type(screen.getByLabelText('标题'), '需要修正的截止日期')
    await user.type(screen.getByLabelText('内容'), '请以官方规则为准。')
    await user.click(screen.getByRole('button', { name: '提交审核' }))
    expect(await screen.findByText('已收到。你的内容会进入审核流程，我们不会把它直接公开。')).toBeInTheDocument()
    expect(screen.getByLabelText('标题')).toHaveValue('')
    expect(mocks.submit).toHaveBeenCalledTimes(1)
  })

  it('submits a partner application and safely resets the form after the asynchronous request', async () => {
    mocks.partnerApply.mockResolvedValueOnce({ id: '01JLOCALAPPLICATION000000000' })
    const user = userEvent.setup()
    render(<JoinPage />)
    await user.type(screen.getByLabelText('展示名称'), '本地合成团队')
    await user.type(screen.getByLabelText('你想维护什么内容？'), '维护可核验的公开竞赛来源。')
    await user.click(screen.getByRole('button', { name: '提交合作方申请' }))
    expect(await screen.findByText('申请已进入审核队列。通过后，管理员会以受控方式创建合作方账号。')).toBeInTheDocument()
    expect(screen.getByLabelText('展示名称')).toHaveValue('')
    expect(mocks.partnerApply).toHaveBeenCalledTimes(1)
  })
})
