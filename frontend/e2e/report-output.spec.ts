import { expect, test } from '@playwright/test';

test('报告打印与发放在真实业务入口显示执行结果和历史', async ({ page }) => {
  const prints: unknown[] = [];
  const distributions: unknown[] = [];

  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: true } });
    if (path.endsWith('/auth/me')) {
      return route.fulfill({
        json: {
          userId: 'SYNTH-ADMIN',
          username: 'synthetic-admin',
          displayName: '合成管理员',
          roleCode: 'ADMIN',
          permissions: ['P14-PERM-001', 'P14-PERM-048', 'P14-PERM-055'],
        },
      });
    }
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/operations/overview')) {
      return route.fulfill({ json: { distributions: [], packages: [] } });
    }
    if (
      path.endsWith('/operations/critical-values') ||
      path.endsWith('/operations/logistics/addresses')
    ) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/operations/reports/00000000-0000-0000-0000-000000000001/prints')) {
      return route.fulfill({ json: prints });
    }
    if (path.endsWith('/operations/reports/00000000-0000-0000-0000-000000000001/distributions')) {
      return route.fulfill({ json: distributions });
    }
    if (path.endsWith('/operations/reports/00000000-0000-0000-0000-000000000001/print')) {
      const body = request.postDataJSON();
      prints.push({
        id: 'PRINT-1',
        reportId: '00000000-0000-0000-0000-000000000001',
        printedAt: '2026-08-14T00:00:00Z',
        resultCode: 'SUCCESS',
        deviceJobReference: 'SIM-PRINT-1',
        ...body,
      });
      return route.fulfill({ json: { id: 'PRINT-1', statusCode: 'SUCCESS', duplicate: false } });
    }
    if (path.endsWith('/operations/reports/00000000-0000-0000-0000-000000000001/distribution')) {
      const body = request.postDataJSON();
      distributions.push({
        id: 'DELIVERY-1',
        reportId: '00000000-0000-0000-0000-000000000001',
        requestedAt: '2026-08-14T00:00:00Z',
        statusCode: 'SENT',
        retryCount: 0,
        deliveryReference: 'SIM-DELIVERY-1',
        ...body,
      });
      return route.fulfill({ json: { id: 'DELIVERY-1', statusCode: 'SENT', duplicate: false } });
    }
    if (path.endsWith('/operations/report-printer-status')) {
      return route.fulfill({
        json: {
          printerReference: 'MOCK://REPORT-PRINTER',
          statusCode: 'READY',
          detail: '产品内报告打印 Simulator 可用',
        },
      });
    }
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/business-operations?module=business');
  await expect(page.getByRole('heading', { name: '业务管理' })).toBeVisible();
  await page.getByRole('button', { name: '报告发放', exact: true }).click();
  await expect(page.getByRole('heading', { name: '报告自助打印' })).toBeVisible();

  await page.getByPlaceholder('已签发报告记录标识').fill('00000000-0000-0000-0000-000000000001');
  await page.getByPlaceholder('身份核验凭据引用').fill('SYNTHETIC-IDENTITY');
  await page.getByRole('button', { name: '打印报告' }).click();
  await expect(page.getByText('打印 · SUCCESS')).toBeVisible();

  await page.getByRole('button', { name: '执行发放' }).click();
  await expect(page.getByText('发放 · SIMULATOR_PATIENT_PORTAL')).toBeVisible();
  await page.getByRole('button', { name: '检查打印机' }).click();
  await expect(page.getByText(/打印机：READY/)).toBeVisible();
});
