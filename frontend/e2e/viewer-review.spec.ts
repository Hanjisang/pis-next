import { expect, test } from '@playwright/test';

test('医生在真实阅片视口完成标注、两点测量和 PNG 截图', async ({ page }) => {
  const annotations: unknown[] = [];
  const measurements: unknown[] = [];
  const screenshots: unknown[] = [];
  const image =
    'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22800%22 height=%22600%22%3E%3Crect width=%22800%22 height=%22600%22 fill=%22%23d8b4a0%22/%3E%3Ccircle cx=%22400%22 cy=%22300%22 r=%22120%22 fill=%22%237c2d12%22/%3E%3C/svg%3E';
  const workspace = {
    caseSummary: {
      caseId: 'CASE-VIEWER',
      pathologyNo: 'H-2026-VIEWER',
      businessTypeCode: 'HISTOLOGY',
      lifecycle: 'ACTIVE',
    },
    application: {
      applicationItemCode: 'SYNTH-HISTOLOGY',
      sourceSystemCode: 'SYNTH-HIS',
      externalApplicationId: 'APP-VIEWER',
    },
    patient: { patientReference: 'SYNTH-PATIENT', visitReference: 'SYNTH-VISIT' },
    materialTree: {
      caseId: 'CASE-VIEWER',
      caseNo: 'H-2026-VIEWER',
      businessTypeCode: 'HISTOLOGY',
      specimens: [
        {
          specimenId: 'SPECIMEN-1',
          specimenNo: 'SP-1',
          specimenCode: 'A',
          specimenKindCode: 'TISSUE',
          blocks: [
            {
              blockId: 'BLOCK-1',
              blockCode: 'A1',
              blockType: 'ROUTINE',
              slides: [
                {
                  slideId: 'SLIDE-1',
                  slideCode: 'A1-HE',
                  slideType: 'HE',
                  sourceContextType: 'INITIAL',
                  completed: true,
                  required: true,
                  concurrencyVersion: 1,
                },
              ],
            },
          ],
          directSlides: [],
        },
      ],
      initialRequiredCount: 1,
      initialCompletedCount: 1,
      initialProductionComplete: true,
    },
    diagnosis: {
      diagnosisId: 'DIAGNOSIS-1',
      templateVersionId: 'TEMPLATE-VERSION-1',
      structuredData: '{}',
      version: 0,
      updatedAt: '2026-08-14T00:00:00Z',
    },
    templateVersion: {
      versionId: 'TEMPLATE-VERSION-1',
      templateId: 'TEMPLATE-1',
      versionNo: 1,
      schemaDefinition: '{"components":[]}',
      status: 'PUBLISHED',
    },
    responsibilityChain: [],
    actions: {
      canClaim: false,
      canAssign: false,
      canCompleteInitial: false,
      canCompleteReview: false,
      canCompleteAudit: false,
      canReassign: false,
      readyForSignOut: false,
      canCreateTechnicalOrder: false,
      canPreview: false,
      canSignOut: false,
      canWithdraw: false,
      canSupplement: false,
    },
    molecularResults: [],
    technicalOrders: [],
    blockingTechnicalOrderCount: 0,
    reports: [],
    blockingReasons: [],
    digitalSlides: [
      {
        digitalSlideId: 'DIGITAL-1',
        blockId: 'BLOCK-1',
        slideId: 'SLIDE-1',
        statusCode: 'ACTIVE',
        viewerReference: image,
        sourcePlatform: 'TEST-VIEWER',
      },
    ],
    refreshedAt: '2026-08-14T00:00:00Z',
  };

  await page.route('**/api/v2/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { required: false } });
    if (path.endsWith('/operations/notifications')) return route.fulfill({ json: [] });
    if (path.endsWith('/diagnosis-workspaces/CASE-VIEWER')) return route.fulfill({ json: workspace });
    if (path.endsWith('/case-workspaces/CASE-VIEWER')) return route.fulfill({ json: { timeline: [] } });
    if (path.endsWith('/patient-history')) return route.fulfill({ json: { items: [] } });
    if (path.endsWith('/my-workbench')) {
      return route.fulfill({ json: { myWork: [], publicPool: [], counts: {} } });
    }
    if (path.endsWith('/auth/doctors')) return route.fulfill({ json: [] });
    if (path.endsWith('/case-support/cases/CASE-VIEWER/favorite')) {
      return route.fulfill({ json: { caseId: 'CASE-VIEWER', favorite: false } });
    }
    if (path.endsWith('/follow-ups') || path.endsWith('/consultations')) {
      return route.fulfill({ json: [] });
    }
    if (path.endsWith('/digital-slides/DIGITAL-1/annotations')) {
      if (request.method() === 'POST') {
        const body = request.postDataJSON();
        annotations.push({ annotationId: 'ANNOTATION-1', ...body });
      }
      return route.fulfill({ json: annotations });
    }
    if (path.endsWith('/digital-slides/DIGITAL-1/measurements')) {
      if (request.method() === 'POST') {
        const body = request.postDataJSON();
        measurements.push({ measurementId: 'MEASUREMENT-1', ...body });
      }
      return route.fulfill({ json: measurements });
    }
    if (path.endsWith('/digital-slides/DIGITAL-1/screenshots')) {
      if (request.method() === 'POST') {
        const body = request.postDataJSON();
        screenshots.push({
          screenshotId: 'SCREENSHOT-1',
          digitalSlideId: 'DIGITAL-1',
          createdAt: '2026-08-14T00:00:00Z',
          ...body,
        });
      }
      return route.fulfill({ json: screenshots });
    }
    return route.fulfill({ json: {} });
  });

  await page.goto('/v2/diagnosis/CASE-VIEWER');
  await expect(page.getByText('WSI Viewer', { exact: true })).toBeVisible();
  const viewerImage = page.getByLabel('数字切片阅片器').locator('.viewer-regular-image');
  await expect(viewerImage).toBeVisible();
  const clickImageAt = async (xRatio: number, yRatio: number) => {
    const bounds = await viewerImage.boundingBox();
    expect(bounds).not.toBeNull();
    await page.mouse.click(
      bounds!.x + bounds!.width * xRatio,
      bounds!.y + bounds!.height * yRatio,
    );
  };
  await page.getByLabel('标注说明').fill('胃黏膜可疑区域');
  await page.getByRole('button', { name: '在图像上标注' }).click();
  await clickImageAt(0.5, 0.5);
  await expect(page.getByLabel('阅片记录历史')).toContainText('胃黏膜可疑区域');
  const annotationGeometry = JSON.parse(
    String((annotations[0] as { geometryJson?: string }).geometryJson),
  );
  expect(annotationGeometry).toMatchObject({ coordinateSystem: 'NORMALIZED_IMAGE' });
  expect(annotationGeometry.x).toBeGreaterThan(0);
  expect(annotationGeometry.x).toBeLessThan(1);

  await page.getByRole('button', { name: '在图像上测量' }).click();
  await clickImageAt(0.25, 0.5);
  await clickImageAt(0.75, 0.5);
  await expect(page.getByLabel('阅片记录历史')).toContainText('归一化视距');
  expect(measurements[0]).toMatchObject({
    unitCode: 'IMAGE_RATIO',
    measurementModeCode: 'NORMALIZED_IMAGE_COORDINATE',
  });

  await page.getByRole('button', { name: '保存当前截图' }).click();
  await expect(page.getByLabel('阅片记录历史')).toContainText('查看截图');
  expect(String((screenshots[0] as { imageDataBase64?: string }).imageDataBase64)).toMatch(/^iVBOR/);
});
