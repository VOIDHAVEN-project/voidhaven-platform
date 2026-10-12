import { expect, test } from '@playwright/test'
import { id, mockApi } from './fixtures'

for (const theme of ['light', 'dark']) {
  test(`已通过稿件发布受阻后可退回修改（${theme}）`, async ({ page }) => {
    await page.addInitScript((value) => localStorage.setItem('voidhaven-theme', value), theme)
    const state = await mockApi(page)
    state.status = 'APPROVED'
    await page.route('**/api/v1/admin/content-items/*/publication-readiness', (route) =>
      route.fulfill({
        json: {
          data: {
            ready: false,
            blockers: [
              { code: 'RECRUITMENT_ACTION_REQUIRED', message: '招募内容至少需要一个有效行动入口。' },
            ],
          },
        },
      }),
    )
    await page.goto(`/workspace?section=reviews&item=${id}`)
    await page.getByRole('button', { name: '执行检查', exact: true }).click()
    await expect(page.getByText('招募内容至少需要一个有效行动入口。')).toBeVisible()
    await expect(page.getByRole('button', { name: '正式发布', exact: true })).toBeDisabled()
    await expect(page.getByLabel('审核决定').locator('option[value="APPROVE"]')).toHaveCount(0)
    await expect(page.getByLabel('审核决定').locator('option[value="REJECT"]')).toHaveCount(0)
    await page.getByLabel('审核决定').selectOption('CHANGES_REQUESTED')
    await page.getByRole('button', { name: '保存审核决定', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('必须填写原因')
    await page.getByLabel('审核说明').fill('补齐行动入口后重新送审')
    await page.getByRole('button', { name: '保存审核决定', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('审核决定已保存')
    await expect(page.getByRole('button', { name: '正式发布', exact: true })).toHaveCount(0)
    expect(state.status).toBe('CHANGES_REQUESTED')
    const write = state.writes.find((item) => item.path.includes('/reviews/'))
    expect(write?.headers['if-match']).toBe('7')
    expect(write?.headers['idempotency-key']).toBeTruthy()
    expect(write?.headers['x-csrf-token']).toBeTruthy()
  })
}
