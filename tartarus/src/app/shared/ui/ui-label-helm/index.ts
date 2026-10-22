import { computed, Directive, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Directive({
  selector: 'label[hlmLabel], [hlmLabel]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmLabelDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm(
      'text-sm font-medium leading-none text-foreground peer-disabled:cursor-not-allowed peer-disabled:opacity-70',
      this.userClass()
    )
  );
}

export const HlmLabelImports = [HlmLabelDirective] as const;
