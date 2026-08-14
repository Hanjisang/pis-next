import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import V2MolecularWorkbench from './V2MolecularWorkbench.vue';

describe('V2MolecularWorkbench', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows bound projects, instruments, reagents and drives the simulator queue', async () => {
    const fetch = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/start'))
        return new Response(JSON.stringify({ id: 'test-1', statusCode: 'RUNNING' }));
      return new Response(
        JSON.stringify({
          refreshedAt: '2026-08-14T00:00:00Z',
          projects: [
            {
              id: 'project-1',
              projectCode: 'PCR',
              projectName: '合成PCR',
              projectTypeCode: 'PCR',
              enabled: true,
            },
          ],
          instruments: [
            {
              id: 'instrument-1',
              instrumentCode: 'SIM-PCR',
              name: '合成PCR仪',
              adapterCode: 'SIMULATOR',
              enabled: true,
            },
          ],
          reagents: [
            {
              id: 'reagent-1',
              kitCode: 'PCR-KIT',
              manufacturer: 'SYNTH',
              batchNo: 'B-001',
              expiryDate: '2027-12-31',
              enabled: true,
            },
          ],
          tests: [
            {
              id: 'test-1',
              caseId: 'case-1',
              specimenId: 'specimen-1',
              projectId: 'project-1',
              projectCode: 'PCR',
              detectionNo: 'M-001',
              instrumentId: 'instrument-1',
              instrumentCode: 'SIM-PCR',
              adapterCode: 'SIMULATOR',
              reagentKitId: 'reagent-1',
              rawDataReference: 'fixture://raw',
              structuredResult: null,
              analysisResult: null,
              statusCode: 'REQUESTED',
              resultId: null,
              createdAt: '2026-08-14T00:00:00Z',
              startedAt: null,
              completedAt: null,
              concurrencyVersion: 0,
            },
          ],
          attempts: [
            {
              id: 'attempt-1',
              testId: 'test-1',
              instrumentId: 'instrument-1',
              adapterCode: 'SIMULATOR',
              attemptNo: 1,
              requestReference: 'request-1',
              statusCode: 'ACCEPTED',
              responseReference: 'SIM-RUN-test-1',
              errorCode: null,
              errorMessage: null,
              requestedAt: '2026-08-14T00:00:00Z',
              completedAt: '2026-08-14T00:00:00Z',
              requestedBy: 'operator',
            },
          ],
          attachments: [
            {
              id: 'attachment-1',
              testId: 'test-1',
              digitalSlideId: null,
              attachmentReference: 'fixture://support.pdf',
              description: '合成附件',
              createdAt: '2026-08-14T00:00:00Z',
              createdBy: 'operator',
            },
          ],
        }),
      );
    });
    vi.stubGlobal('fetch', fetch);
    const wrapper = mount(V2MolecularWorkbench);
    await flushPromises();
    expect(wrapper.text()).toContain('分子病理工作台');
    expect(wrapper.text()).toContain('PCR · 合成PCR');
    expect(wrapper.text()).toContain('SIM-PCR · 合成PCR仪 · SIMULATOR');
    expect(wrapper.text()).toContain('PCR-KIT · B-001');
    expect(wrapper.text()).toContain('M-001 · PCR');
    expect(wrapper.text()).toContain('SIM-RUN-test-1');
    expect(wrapper.text()).toContain('fixture://support.pdf');
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '启动')
      ?.trigger('click');
    await flushPromises();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v2/molecular/tests/test-1/start'),
      expect.objectContaining({ method: 'POST' }),
    );
  });
});
