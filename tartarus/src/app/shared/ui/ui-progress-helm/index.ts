import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Component({
  selector: 'hlm-progress',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'progressbar',
    'aria-valuemin': '0',
    '[attr.aria-valuemax]': 'max()',
    '[attr.aria-valuenow]': 'value()',
    '[class]': '_computedClass()',
  },
  template: `
    <div
      class="h-full w-full flex-1 bg-primary transition-all duration-200 ease-in-out"
      [style.transform]="'translateX(-' + (100 - percentage()) + '%)'"
    ></div>
  `,
})
export class HlmProgressComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly value = input<number>(0);
  public readonly max = input<number>(100);

  protected readonly percentage = computed(() => {
    const maxVal = this.max() || 100;
    const clamped = Math.min(Math.max(this.value() || 0, 0), maxVal);
    return Math.round((clamped / maxVal) * 100);
  });

  protected readonly _computedClass = computed(() =>
    hlm('relative h-3 w-full overflow-hidden rounded-full bg-secondary', this.userClass())
  );
}

export const HlmProgressImports = [HlmProgressComponent] as const;
