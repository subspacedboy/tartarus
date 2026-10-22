import { computed, Directive, input } from '@angular/core';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

export const badgeVariants = cva(
  'inline-flex items-center gap-1.5 border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        destructive: 'border-transparent bg-destructive text-destructive-foreground',
        outline: 'border-border text-foreground bg-transparent',
        info: 'border-primary/30 bg-accent text-accent-foreground',
        success: 'border-status-unlocked/30 bg-status-unlocked-bg text-status-unlocked',
        warning: 'border-status-warning/30 bg-status-warning-bg text-status-warning',
        locked: 'border-status-locked/30 bg-status-locked-bg text-status-locked',
        unlocked: 'border-status-unlocked/30 bg-status-unlocked-bg text-status-unlocked',
        emergency: 'border-status-emergency/40 bg-status-emergency-bg text-status-emergency',
      },
      shape: {
        default: 'rounded-md',
        pill: 'rounded-full',
      },
    },
    defaultVariants: {
      variant: 'default',
      shape: 'default',
    },
  }
);

export type BadgeVariants = VariantProps<typeof badgeVariants>;

@Directive({
  selector: '[hlmBadge]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmBadgeDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly variant = input<BadgeVariants['variant']>('default');
  public readonly shape = input<BadgeVariants['shape']>('default');

  protected readonly _computedClass = computed(() =>
    hlm(badgeVariants({ variant: this.variant(), shape: this.shape() }), this.userClass())
  );
}

export const HlmBadgeImports = [HlmBadgeDirective] as const;
