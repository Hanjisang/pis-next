import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

import P19DiagnosisReportWorkbench from './P19DiagnosisReportWorkbench.vue';

vi.mock('../api', () => ({
  approveP19Report: vi.fn(),
  createP19DiagnosisTask: vi.fn(),
  createP19Report: vi.fn(),
  generateP19ReportContent: vi.fn(),
  getP19DiagnosisQueue: vi.fn().mockResolvedValue([]),
  getP19ReportQueue: vi.fn().mockResolvedValue([]),
  saveP19DiagnosisDraft: vi.fn(),
  signP19Report: vi.fn(),
  submitP19Initial: vi.fn(),
  submitP19ReportReview: vi.fn(),
  takeoverP19DiagnosisTask: vi.fn(),
}));

describe('P19DiagnosisReportWorkbench', () => {
  it('explains versioned diagnosis and report signing boundaries', () => {
    const wrapper = mount(P19DiagnosisReportWorkbench);
    expect(wrapper.text()).toContain('诊断与报告工作台');
    expect(wrapper.text()).toContain('独立复核');
    expect(wrapper.text()).toContain('签发报告');
  });
});
