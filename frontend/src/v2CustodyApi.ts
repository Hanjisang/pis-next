export type V2ArchiveLocation = {
  locationId: string;
  parentId?: string | null;
  locationCode: string;
  locationName: string;
  locationKindCode: string;
};

export type V2LoanMaterial = {
  materialKind: 'BLOCK' | 'SLIDE' | string;
  materialId: string;
  materialCode: string;
  caseId: string;
  pathologyNo: string;
  returnedAt: string | null;
};

export type V2Loan = {
  loanId: string;
  borrowerReference: string;
  borrowerDepartment: string | null;
  purpose: string;
  borrowedAt: string;
  expectedReturnAt: string | null;
  returnedAt: string | null;
  returnedByRef: string | null;
  statusCode: 'BORROWED' | 'DUE_SOON' | 'OVERDUE' | 'RETURNED' | string;
  items: V2LoanMaterial[];
};

export async function getV2ArchiveLocations(): Promise<V2ArchiveLocation[]> {
  const response = await fetch('/api/v2/custody/locations');
  const body = (await response.json()) as V2ArchiveLocation[] | { message?: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '归档库位暂时无法加载');
  return body as V2ArchiveLocation[];
}

export async function createV2ArchiveLocation(input: {
  parentId?: string;
  locationCode: string;
  locationName: string;
  locationKindCode: string;
}): Promise<V2ArchiveLocation> {
  const response = await fetch('/api/v2/custody/locations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  const body = (await response.json()) as V2ArchiveLocation & { message?: string };
  if (!response.ok) throw new Error(body.message ?? '归档库位保存失败');
  return body;
}

export async function getV2Loans(
  filters: {
    status?: string;
    borrower?: string;
    department?: string;
    query?: string;
  } = {},
): Promise<V2Loan[]> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const response = await fetch(`/api/v2/custody/loans${query.size ? `?${query}` : ''}`);
  const body = (await response.json()) as V2Loan[] | { message?: string };
  if (!response.ok)
    throw new Error((body as { message?: string }).message ?? '借阅记录暂时无法加载');
  return body as V2Loan[];
}
