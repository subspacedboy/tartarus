import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { NgxSonnerToaster, type ToasterProps } from 'ngx-sonner';
import { hlm } from '../utils/hlm';

if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

@Component({
  selector: 'hlm-toaster',
  standalone: true,
  imports: [NgxSonnerToaster],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ngx-sonner-toaster
      class="toaster group"
      [theme]="theme()"
      [class]="_computedClass()"
      [position]="position()"
      [richColors]="richColors()"
      [closeButton]="closeButton()"
      [toastOptions]="{
        classes: {
          toast:
            'group toast group-[.toaster]:bg-card group-[.toaster]:text-card-foreground group-[.toaster]:border-border group-[.toaster]:shadow-lg rounded-xl',
          description: 'group-[.toast]:text-muted-foreground',
          actionButton: 'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
          cancelButton: 'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground'
        }
      }"
    />
  `,
})
export class HlmToasterComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly theme = input<ToasterProps['theme']>('system');
  public readonly position = input<ToasterProps['position']>('bottom-right');
  public readonly richColors = input<boolean>(true);
  public readonly closeButton = input<boolean>(true);

  protected readonly _computedClass = computed(() => hlm('toaster group', this.userClass()));
}

export const HlmToasterImports = [HlmToasterComponent] as const;
