import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Component({
  selector: 'hlm-spinner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'status',
    '[attr.aria-label]': 'ariaLabel()',
    '[class]': '_computedClass()',
  },
  template: `
    <svg
      class="animate-spin size-full"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <circle
        class="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        stroke-width="4"
      ></circle>
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
      ></path>
    </svg>
  `,
})
export class HlmSpinnerComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly ariaLabel = input<string>('Loading', { alias: 'aria-label' });
  public readonly size = input<'xs' | 'sm' | 'md' | 'default' | 'lg'>('default');

  protected readonly _computedClass = computed(() => {
    const sizeClasses = {
      xs: 'inline-block size-3.5',
      sm: 'inline-block size-4',
      md: 'inline-block size-6',
      default: 'inline-block size-6',
      lg: 'inline-block size-8',
    };
    return hlm(sizeClasses[this.size()], this.userClass());
  });
}

export const HlmSpinnerImports = [HlmSpinnerComponent] as const;
