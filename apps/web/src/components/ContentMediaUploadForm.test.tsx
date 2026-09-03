import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ContentMediaUploadForm } from './ContentMediaUploadForm'

const mocks = vi.hoisted(() => ({ uploadContentMedia: vi.fn() }))
vi.mock('../lib/api', () => ({ api: mocks }))

describe('受控图片上传表单', () => {
  it('waits for the protected API result, then safely resets the captured form', async () => {
    mocks.uploadContentMedia.mockResolvedValueOnce({ id: '01JLOCALMEDIA000000000000', url: '/api/v1/public/media/01JLOCALMEDIA000000000000' })
    const user = userEvent.setup()
    render(<ContentMediaUploadForm contentItemId="01JLOCALCONTENT00000000000" />)
    const file = new File(['local-image'], 'evidence.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText('正文图片'), file)
    expect((screen.getByLabelText('正文图片') as HTMLInputElement).files).toHaveLength(1)
    await user.type(screen.getByLabelText('图片说明'), '合成验证图片')
    fireEvent.submit(screen.getByRole('button', { name: '上传图片' }).closest('form')!)

    expect(await screen.findByText('图片已关联到当前候选修订；送审并发布后才可能公开展示。')).toBeInTheDocument()
    expect(mocks.uploadContentMedia).toHaveBeenCalledWith('01JLOCALCONTENT00000000000', file, '合成验证图片')
    expect(screen.getByLabelText('图片说明')).toHaveValue('')
  })
})
