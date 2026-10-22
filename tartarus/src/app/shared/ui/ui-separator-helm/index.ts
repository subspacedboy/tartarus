import { computed, Directive, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Directive({
  selector: '[hlmSeparator]',
  standalone: true,
  host: {
    role: 'separator',
    '[attr.aria-orientation]': 'orientation()',
    '[class]': '_computedClass()',
  },
})
export class HlmSeparatorDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly orientation = input<'horizontal' | 'vertical'>('horizontal');

  protected readonly _computedClass = computed(() =>
    hlm(
      'shrink-0 bg-border',
      this.orientation() === 'horizontal' ? 'h-[1px] w-full' : 'h-full w-[1px]',
      this.userClass()
    )
  );
}

export const HlmSeparatorImports = [HlmSeparatorDirective] as const;
