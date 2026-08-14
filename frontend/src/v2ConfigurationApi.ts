export type V2ConfigurationSnapshot = {
  businessTypes: Array<{
    id: string;
    code: string;
    displayName: string;
    modalityCode: string;
    enabled: boolean;
    configurationVersion: number;
  }>;
  applicationItemMappings: Array<{
    id: string;
    applicationItemCode: string;
    defaultSpecimenKindCode: string;
    required: boolean;
    sequenceNo: number;
    active: boolean;
    configurationVersion: number;
    businessTypeCode: string;
    businessTypeName: string;
  }>;
  pathologyNumberRules: Array<{
    id: string;
    numberKindCode: string;
    prefix: string;
    scopeCode: string;
    paddingWidth: number;
    nextSerial: number;
    active: boolean;
    configurationVersion: number;
    businessTypeCode: string;
    businessTypeName: string;
  }>;
  technicalProjects: Array<{
    id: string;
    projectCode: string;
    projectName: string;
    enabled: boolean;
    configurationVersion: number;
    businessTypeCode: string | null;
    businessTypeName: string | null;
    requiredBeforeSignOutDefault: boolean;
  }>;
  diagnosisTemplates: Array<{
    id: string;
    templateCode: string;
    templateName: string;
    enabled: boolean;
    concurrencyVersion: number;
    businessTypeCode: string | null;
    businessTypeName: string | null;
    versionCount: number;
  }>;
  reportTemplates: Array<{
    id: string;
    templateCode: string;
    templateName: string;
    enabled: boolean;
    configurationVersion: number;
    businessTypeCode: string | null;
    businessTypeName: string | null;
    versionCount: number;
  }>;
};

export type V2ReportTemplatePreset = {
  presetCode: string;
  presetName: string;
  tumorSiteCode: string;
  definition: string;
  presetVersion: number;
};

export type V2ReportTemplateCatalogRow = {
  templateId: string;
  code: string;
  name: string;
  businessTypeId: string;
  businessTypeCode: string;
  businessTypeName: string;
  enabled: boolean;
  configurationVersion: number;
  sourcePresetCode?: string;
  versionId?: string;
  versionNo?: number;
  definition?: string;
  status?: 'DRAFT' | 'PUBLISHED';
  publishedAt?: string;
  createdAt?: string;
};

async function configurationRequest<T>(path: string, init: RequestInit = {}) {
  const response = await fetch(`/api/v2/configuration${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string };
  if (!response.ok) throw new Error((body as { message?: string }).message ?? '配置暂时无法保存');
  return body as T;
}

export function getV2Configuration() {
  return configurationRequest<V2ConfigurationSnapshot>('');
}

export function updateV2Configuration(path: string, body: unknown) {
  return configurationRequest<V2ConfigurationSnapshot>(path, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

async function reportTemplateRequest<T>(path: string, init: RequestInit = {}) {
  const response = await fetch(`/api/v2${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
  });
  const body = (await response.json()) as T | { message?: string };
  if (!response.ok) throw new Error((body as { message?: string }).message ?? '报告模板操作失败');
  return body as T;
}

export function getV2ReportTemplatePresets() {
  return reportTemplateRequest<V2ReportTemplatePreset[]>('/report-template-presets');
}

export function getV2ReportTemplateCatalog() {
  return reportTemplateRequest<V2ReportTemplateCatalogRow[]>('/report-templates');
}

export function createV2ReportTemplate(input: {
  code: string;
  name: string;
  businessTypeId: string;
}) {
  return reportTemplateRequest<{ templateId: string; code: string; name: string }>(
    '/report-templates',
    {
      method: 'POST',
      body: JSON.stringify(input),
    },
  );
}

export function instantiateV2ReportTemplatePreset(
  presetCode: string,
  input: { code: string; name: string; businessTypeId: string },
) {
  return reportTemplateRequest<{
    template: { templateId: string; code: string; name: string };
    version: { versionId: string; versionNo: number; definition: string; status: 'DRAFT' };
  }>(`/report-template-presets/${encodeURIComponent(presetCode)}/instantiate`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function createV2ReportTemplateVersion(templateId: string, definition: string) {
  return reportTemplateRequest<{
    versionId: string;
    templateId: string;
    versionNo: number;
    definition: string;
    status: 'DRAFT';
  }>(`/report-templates/${templateId}/versions`, {
    method: 'POST',
    body: JSON.stringify({ definition }),
  });
}

export function publishV2ReportTemplateVersion(versionId: string) {
  return reportTemplateRequest<{ versionId: string; status: 'PUBLISHED'; publishedAt: string }>(
    `/report-template-versions/${versionId}/publish`,
    {
      method: 'POST',
      body: JSON.stringify({ idempotencyKey: `report-template-publish-${crypto.randomUUID()}` }),
    },
  );
}
