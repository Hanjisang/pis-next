export type ViewerViewport = {
  zoom: number;
  centerX: number;
  centerY: number;
};

export type ViewerOpenRequest = {
  source: string;
  viewport?: ViewerViewport;
};

export type ViewerCapture = {
  mediaType: 'image/png';
  dataUrl: string;
};

export interface ImageViewerAdapter {
  mount(element: HTMLElement): Promise<void>;
  open(request: ViewerOpenRequest): Promise<void> | void;
  zoomBy(delta: number): void;
  reset(): void;
  setFullScreen(enabled: boolean): void;
  getViewport(): ViewerViewport | null;
  captureCurrentView(): Promise<ViewerCapture | null>;
  destroy(): void;
}

export function isTiledViewerSource(source: string | null | undefined): boolean {
  const normalized = source?.trim().toLowerCase() ?? '';
  return (
    normalized.endsWith('.dzi') || normalized.includes('.dzi?') || normalized.startsWith('iiif:')
  );
}

export function isRegularImageSource(source: string | null | undefined): boolean {
  const normalized = source?.trim() ?? '';
  return /^(https?:|data:image|blob:|\/)/i.test(normalized) && !isTiledViewerSource(normalized);
}
