import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  HostListener,
  inject,
  input,
  OnDestroy,
  signal,
} from '@angular/core';

@Component({
  selector: 'hlm-tooltip-content',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'tooltip',
    class:
      'z-50 max-w-xs overflow-hidden break-all rounded-md border border-border bg-popover px-3 py-1.5 text-xs font-mono text-popover-foreground shadow-md pointer-events-none',
  },
  template: `{{ text() }}`,
})
export class HlmTooltipContentComponent {
  public readonly text = signal<string>('');
}

@Directive({
  selector: '[hlmTooltip]',
  standalone: true,
})
export class HlmTooltipDirective implements OnDestroy {
  private readonly _overlay = inject(Overlay);
  private readonly _elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private _overlayRef: OverlayRef | null = null;

  public readonly tooltipText = input<string | undefined | null>('', { alias: 'hlmTooltip' });

  @HostListener('mouseenter')
  @HostListener('focus')
  show(): void {
    const content = this.tooltipText();
    if (!content || this._overlayRef) return;

    const positionStrategy = this._overlay
      .position()
      .flexibleConnectedTo(this._elementRef)
      .withPositions([
        {
          originX: 'center',
          originY: 'top',
          overlayX: 'center',
          overlayY: 'bottom',
          offsetY: -6,
        },
        {
          originX: 'center',
          originY: 'bottom',
          overlayX: 'center',
          overlayY: 'top',
          offsetY: 6,
        },
      ]);

    this._overlayRef = this._overlay.create({
      positionStrategy,
      scrollStrategy: this._overlay.scrollStrategies.reposition(),
    });

    const portal = new ComponentPortal(HlmTooltipContentComponent);
    const ref = this._overlayRef.attach(portal);
    ref.instance.text.set(content);
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  @HostListener('document:keydown.escape')
  hide(): void {
    if (this._overlayRef) {
      this._overlayRef.dispose();
      this._overlayRef = null;
    }
  }

  ngOnDestroy(): void {
    this.hide();
  }
}

export const HlmTooltipTriggerDirective = HlmTooltipDirective;
export const HlmTooltipImports = [HlmTooltipDirective] as const;
