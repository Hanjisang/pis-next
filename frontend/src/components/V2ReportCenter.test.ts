import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

import V2ReportCenter from './V2ReportCenter.vue';

describe('V2ReportCenter', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows report TAT, declares delay and keeps the case actionable', async () => {
    let delayed = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/report-center') && !init?.method) {
        return new Response(
          JSON.stringify({
            items: [
              {
                diagnosisId: 'DIAGNOSIS-1',
                caseId: 'CASE-1',
                pathologyNo: 'H-2026-001',
                patientReference: 'SYNTH-PATIENT',
                businessTypeCode: 'HISTOLOGY',
                queueCode: 'WAITING_SIGN',
                reportId: null,
                reportNo: null,
                statusCode: null,
                occurredAt: '2026-08-14T00:00:00Z',
                targetLabel: '待签发',
                tatStatus: 'OVERDUE',
                elapsedMinutes: 5000,
                warningAt: '2026-08-12T00:00:00Z',
                dueAt: '2026-08-13T00:00:00Z',
                policyVersion: 1,
                delay: delayed
                  ? {
                      delayId: 'DELAY-1',
                      reasonCode: 'TECHNICAL_WORK',
                      reasonDetail: '等待合成技术结果',
                      expectedSignAt: '2026-08-16T00:00:00Z',
                      declaredAt: '2026-08-14T00:00:00Z',
                    }
                  : null,
              },
            ],
            counts: {
              waitingSign: 1,
              signed: 0,
              withdrawn: 0,
              supplemental: 0,
              recentSigned: 0,
              warning: 0,
              overdue: 1,
              delayed: delayed ? 1 : 0,
            },
            refreshedAt: '2026-08-14T00:00:00Z',
          }),
        );
      }
      if (url.endsWith('/report-center/delays') && init?.method === 'POST') {
        delayed = true;
        return new Response(JSON.stringify({ delayId: 'DELAY-1', duplicate: false }));
      }
      if (url.endsWith('/report-center/delays/DELAY-1/resolve') && init?.method === 'POST') {
        delayed = false;
        return new Response(
          JSON.stringify({ delayId: 'DELAY-1', resolvedAt: '2026-08-14T01:00:00Z' }),
        );
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ReportCenter);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('超期'))
      ?.trigger('click');
    expect(wrapper.text()).toContain('H-2026-001');
    expect(wrapper.text()).toContain('已超期');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '登记延迟')
      ?.trigger('click');
    const dialog = wrapper.get('[aria-label="登记报告延迟"]');
    await dialog.get('textarea').setValue('等待合成技术结果');
    await dialog.trigger('submit');
    await flushPromises();

    const declaration = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/report-center/delays'),
    );
    expect(JSON.parse(declaration?.[1]?.body as string)).toMatchObject({
      diagnosisId: 'DIAGNOSIS-1',
      reasonCode: 'TECHNICAL_WORK',
      reasonDetail: '等待合成技术结果',
      idempotencyKey: expect.any(String),
    });
    expect(wrapper.text()).toContain('预计');
    expect(wrapper.text()).toContain('关闭延迟');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '关闭延迟')
      ?.trigger('click');
    const resolutionDialog = wrapper.get('[aria-label="关闭报告延迟"]');
    await resolutionDialog.get('textarea').setValue('技术结果已回传并完成复核');
    await resolutionDialog.trigger('submit');
    await flushPromises();

    const resolution = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/report-center/delays/DELAY-1/resolve'),
    );
    expect(JSON.parse(resolution?.[1]?.body as string)).toMatchObject({
      resolutionNote: '技术结果已回传并完成复核',
      idempotencyKey: expect.any(String),
    });
  });

  it('queries effective reports for clinicians and verifies a patient before terminal printing', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/report-center') && !init?.method) {
        return new Response(
          JSON.stringify({
            items: [],
            counts: {
              waitingSign: 0,
              signed: 0,
              withdrawn: 0,
              supplemental: 0,
              recentSigned: 0,
              warning: 0,
              overdue: 0,
              delayed: 0,
            },
            refreshedAt: '2026-08-14T00:00:00Z',
          }),
        );
      }
      if (url.includes('/report-center/access/clinician?')) {
        return new Response(
          JSON.stringify([
            {
              reportId: 'REPORT-1',
              reportNo: 'R001',
              caseId: 'CASE-1',
              pathologyNo: 'H-2026-001',
              patientReference: 'SYNTH-PATIENT',
              reportNature: 'ORIGINAL',
              signedAt: '2026-08-14T00:00:00Z',
              pdfContentHash: 'abcdef0123456789abcdef0123456789',
            },
          ]),
        );
      }
      if (url.endsWith('/report-center/access/patient') && init?.method === 'POST') {
        return new Response(
          JSON.stringify([
            {
              reportId: 'REPORT-1',
              reportNo: 'R001',
              caseId: 'CASE-1',
              pathologyNo: 'H-2026-001',
              reportNature: 'ORIGINAL',
              signedAt: '2026-08-14T00:00:00Z',
              pdfContentHash: 'abcdef0123456789abcdef0123456789',
            },
          ]),
        );
      }
      if (url.endsWith('/operations/reports/REPORT-1/print') && init?.method === 'POST') {
        return new Response(JSON.stringify({ statusCode: 'SUCCESS', duplicate: false }));
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const wrapper = mount(V2ReportCenter);
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '临床查询')
      ?.trigger('click');
    const clinicianForm = wrapper.get('form[aria-label="临床报告查询"]');
    await clinicianForm.findAll('input')[1].setValue('H-2026-001');
    await clinicianForm.trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('SYNTH-PATIENT');
    expect(wrapper.get('a').attributes('href')).toBe('/api/v2/reports/REPORT-1/pdf');

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '患者自助终端')
      ?.trigger('click');
    const terminalForm = wrapper.get('form[aria-label="患者报告自助查询"]');
    const inputs = terminalForm.findAll('input');
    await inputs[0].setValue('R001');
    await inputs[1].setValue('H-2026-001');
    await inputs[2].setValue('SYNTH-PATIENT');
    await inputs[3].setValue('TERMINAL-01');
    await terminalForm.trigger('submit');
    await flushPromises();
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '打印报告')
      ?.trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('报告已发送至打印机');
    const printRequest = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith('/operations/reports/REPORT-1/print'),
    );
    expect(JSON.parse(printRequest?.[1]?.body as string)).toMatchObject({
      identityReference: 'SYNTH-PATIENT',
      terminalReference: 'TERMINAL-01',
      copyCount: 1,
    });
  });
});
