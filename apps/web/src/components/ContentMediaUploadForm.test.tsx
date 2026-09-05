import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ContentMediaUploadForm } from './ContentMediaUploadForm'

const mocks = vi.hoisted(() => ({ uploadContentMedia: vi.fn() }))
vi.mock('../lib/api', () => ({ api: mocks }))

describe('受控图片上传表单', () => {
  it('uploads without nesting or submitting another form and keeps editor state intact', async () => {
    mocks.uploadContentMedia.mockResolvedValueOnce({ id: '01JLOCALMEDIA000000000000', url: '/api/v1/media/01JLOCALMEDIA000000000000/preview' })
    const beforeUpload = vi.fn(async () => undefined)
    const user = userEvent.setup()
    const { container } = render(<form data-testid="outer"><ContentMediaUploadForm contentItemId="01JLOCALCONTENT00000000000" beforeUpload={beforeUpload} /></form>)
    expect(container.querySelectorAll('form')).toHaveLength(1)
    const file = new File(['local-image'], 'evidence.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText('正文图片'), file)
    expect((screen.getByLabelText('正文图片') as HTMLInputElement).files).toHaveLength(1)
    await user.type(screen.getByLabelText('图片说明'), '合成验证图片')
    await user.click(screen.getByRole('button', { name: '上传图片' }))

    expect(await screen.findByText('图片已关联到当前候选修订；送审并发布后才可能公开展示。')).toBeInTheDocument()
    expect(mocks.uploadContentMedia).toHaveBeenCalledWith('01JLOCALCONTENT00000000000', file, '合成验证图片', expect.any(String))
    expect(beforeUpload).toHaveBeenCalledOnce()
    expect(screen.getByLabelText('图片说明')).toHaveValue('')
  })
})
