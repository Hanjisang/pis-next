import type { ImageViewerAdapter, ViewerViewport } from './ImageViewerAdapter';

/** Contract boundary for a hospital viewer; vendor SDKs must remain outside PIS domain code. */
export class ExternalVendorViewerAdapter implements ImageViewerAdapter {
  async mount(): Promise<void> {}

  open(): void {}

  zoomBy(): void {}

  reset(): void {}

  setFullScreen(): void {}

  getViewport(): ViewerViewport | null {
    return null;
  }

  async captureCurrentView(): Promise<null> {
    return null;
  }

  destroy(): void {}
}
