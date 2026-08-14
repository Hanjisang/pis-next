import type {
  ImageViewerAdapter,
  ViewerCapture,
  ViewerOpenRequest,
  ViewerViewport,
} from './ImageViewerAdapter';

export class RegularImageViewerAdapter implements ImageViewerAdapter {
  private image: HTMLImageElement | null = null;
  private viewport: ViewerViewport = { zoom: 1, centerX: 0.5, centerY: 0.5 };
  private element: HTMLElement | null = null;

  async mount(element: HTMLElement): Promise<void> {
    this.element = element;
  }

  open(request: ViewerOpenRequest): void {
    if (!this.element) return;
    this.image?.remove();
    this.image = document.createElement('img');
    this.image.src = request.source;
    this.image.alt = '数字切片图像';
    this.image.draggable = false;
    this.image.className = 'viewer-regular-image';
    this.element.appendChild(this.image);
    this.viewport = request.viewport ?? { zoom: 1, centerX: 0.5, centerY: 0.5 };
    this.render();
  }

  zoomBy(delta: number): void {
    this.viewport.zoom = Math.min(
      8,
      Math.max(0.25, Number((this.viewport.zoom + delta).toFixed(2))),
    );
    this.render();
  }

  reset(): void {
    this.viewport = { zoom: 1, centerX: 0.5, centerY: 0.5 };
    this.render();
  }

  setFullScreen(enabled: boolean): void {
    if (!this.element) return;
    if (enabled) void this.element.requestFullscreen?.();
    else void document.exitFullscreen?.();
  }

  getViewport(): ViewerViewport {
    return { ...this.viewport };
  }

  async captureCurrentView(): Promise<ViewerCapture | null> {
    if (!this.image || !this.element || !this.image.complete || !this.image.naturalWidth)
      return null;
    const width = Math.max(1, this.element.clientWidth || this.image.naturalWidth);
    const height = Math.max(1, this.element.clientHeight || this.image.naturalHeight);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return null;
    const fit = Math.min(width / this.image.naturalWidth, height / this.image.naturalHeight);
    const drawWidth = this.image.naturalWidth * fit * this.viewport.zoom;
    const drawHeight = this.image.naturalHeight * fit * this.viewport.zoom;
    context.drawImage(
      this.image,
      (width - drawWidth) / 2,
      (height - drawHeight) / 2,
      drawWidth,
      drawHeight,
    );
    try {
      return { mediaType: 'image/png', dataUrl: canvas.toDataURL('image/png') };
    } catch {
      return null;
    }
  }

  destroy(): void {
    this.image?.remove();
    this.image = null;
    this.element = null;
  }

  private render(): void {
    if (!this.image) return;
    this.image.style.transform = `translate(-50%, -50%) scale(${this.viewport.zoom})`;
  }
}
