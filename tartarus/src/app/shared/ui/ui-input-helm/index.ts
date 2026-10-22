import { computed, Directive, input } from '@angular/core';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

export const inputVariants = cva(
  'flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50',
  {
    variants: {
      size: {
        default: 'h-9',
        sm: 'h-8 px-2.5 py-1 text-xs',
        lg: 'h-11 px-4 text-base',
        auto: 'h-auto',
      },
    },
    defaultVariants: {
      size: 'default',
    },
  }
);

export type InputVariants = VariantProps<typeof inputVariants>;

@Directive({
  selector: 'input[hlmInput], textarea[hlmInput], select[hlmInput]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmInputDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly size = input<InputVariants['size']>('default');

  protected readonly _computedClass = computed(() =>
    hlm(inputVariants({ size: this.size() }), this.userClass())
  );
}

export const HlmInputImports = [HlmInputDirective] as const;
