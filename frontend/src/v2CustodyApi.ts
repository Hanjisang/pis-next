export type V2ArchiveLocation = {
  locationId: string;
  parentId?: string | null;
  locationCode: string;
  locationName: string;
  locationKindCode: string;
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
