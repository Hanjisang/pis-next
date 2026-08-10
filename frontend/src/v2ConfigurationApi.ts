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
