import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  acknowledge: vi.fn(),
  addPackageEvent: vi.fn(),
  createAddress: vi.fn(),
  createCritical: vi.fn(),
  createPackage: vi.fn(),
  distribute: vi.fn(),
  feedback: vi.fn(),
  getAddresses: vi.fn(async () => []),
  getCritical: vi.fn(async () => []),
  getDistributions: vi.fn(async () => []),
  getPrinterStatus: vi.fn(async () => ({
    printerReference: 'MOCK://REPORT-PRINTER',
    statusCode: 'READY',
    detail: 'Simulator ready',
  })),
  getPrints: vi.fn(async () => []),
  notify: vi.fn(),
  print: vi.fn(async () => ({ id: 'PRINT-1', statusCode: 'SUCCESS', duplicate: false })),
  updateDistribution: vi.fn(),
}));

vi.mock('../v2BusinessOperationsApi', () => ({
  acknowledgeOperationsCriticalValue: api.acknowledge,
  addOperationsPackageEvent: api.addPackageEvent,
  createOperationsAddress: api.createAddress,
  createOperationsCriticalValue: api.createCritical,
  createOperationsPackage: api.createPackage,
  distributeOperationsReport: api.distribute,
  feedbackOperationsCriticalValue: api.feedback,
  getOperationsAddresses: api.getAddresses,
  getOperationsCriticalValues: api.getCritical,
  getOperationsReportDistributions: api.getDistributions,
  getOperationsReportPrinterStatus: api.getPrinterStatus,
  getOperationsReportPrints: api.getPrints,
  notifyOperationsCriticalValue: api.notify,
  printOperationsReport: api.print,
  updateOperationsDistribution: api.updateDistribution,
}));

import V2ClinicalOperations from './V2ClinicalOperations.vue';

describe('V2ClinicalOperations report output', () => {
  afterEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('executes report printing through a server-generated result and exposes history', async () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'SYNTHETIC-KEY' });
    const wrapper = mount(V2ClinicalOperations, { props: { overview: { distributions: [] } } });
    await flushPromises();
    await wrapper.findAll('button').find((button) => button.text().includes('报告发放'))!.trigger('click');

    expect(wrapper.text()).toContain('报告自助打印');
    expect(wrapper.text()).toContain('产品内患者服务模拟通道');
    await wrapper.get('input[placeholder="已签发报告记录标识"]').setValue('REPORT-1');
    await wrapper.get('input[placeholder="身份核验凭据引用"]').setValue('SYNTHETIC-IDENTITY');
    await wrapper.get('form[aria-label="报告自助打印"]').trigger('submit');
    await flushPromises();

    expect(api.print).toHaveBeenCalledWith('REPORT-1', {
      identityReference: 'SYNTHETIC-IDENTITY',
      terminalReference: 'SELF-SERVICE-01',
      printerReference: 'MOCK://REPORT-PRINTER',
      copyCount: 1,
      idempotencyKey: 'report-print-SYNTHETIC-KEY',
    });
    expect(api.getPrints).toHaveBeenCalledWith('REPORT-1');
    expect(wrapper.text()).toContain('报告打印结果已记录');
  });
});
