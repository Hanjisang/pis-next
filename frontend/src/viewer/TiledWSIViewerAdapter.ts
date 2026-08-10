import type { ImageViewerAdapter, ViewerOpenRequest, ViewerViewport } from './ImageViewerAdapter';

type OpenSeadragonViewer = {
  open(source: string | object): void;
  destroy(): void;
  viewport: {
    zoomBy(factor: number): void;
    goHome(): void;
    getZoom(): number;
    getCenter(): { x: number; y: number };
    zoomTo(zoom: number): void;
    panTo(center: { x: number; y: number }): void;
  };
  setFullScreen(enabled: boolean): void;
};

type OpenSeadragonFactory = (options: Record<string, unknown>) => OpenSeadragonViewer;

export class TiledWSIViewerAdapter implements ImageViewerAdapter {
  private viewer: OpenSeadragonViewer | null = null;
  private element: HTMLElement | null = null;

  async mount(element: HTMLElement): Promise<void> {
    this.element = element;
    const module = await import('openseadragon');
    const factory = ((module as unknown as { default?: OpenSeadragonFactory }).default ??
      module) as OpenSeadragonFactory;
    this.viewer = factory({
      element,
      prefixUrl: '/openseadragon/images/',
      showNavigator: true,
      navigatorPosition: 'BOTTOM_RIGHT',
      showFullPageControl: false,
      showHomeControl: false,
      showZoomControl: false,
      animationTime: 0.25,
      blendTime: 0,
      maxZoomPixelRatio: 2,
      visibilityRatio: 1,
      constrainDuringPan: true,
    });
  }

  open(request: ViewerOpenRequest): void {
    if (!this.viewer) return;
    this.viewer.open(request.source);
    if (request.viewport) {
      this.viewer.viewport.zoomTo(request.viewport.zoom);
      this.viewer.viewport.panTo({ x: request.viewport.centerX, y: request.viewport.centerY });
    }
  }

  zoomBy(delta: number): void {
    this.viewer?.viewport.zoomBy(1 + delta);
  }

  reset(): void {
    this.viewer?.viewport.goHome();
  }

  setFullScreen(enabled: boolean): void {
    this.viewer?.setFullScreen(enabled);
  }

  getViewport(): ViewerViewport | null {
    if (!this.viewer) return null;
    const center = this.viewer.viewport.getCenter();
    return { zoom: this.viewer.viewport.getZoom(), centerX: center.x, centerY: center.y };
  }

  destroy(): void {
    this.viewer?.destroy();
    this.viewer = null;
    this.element = null;
  }
}
