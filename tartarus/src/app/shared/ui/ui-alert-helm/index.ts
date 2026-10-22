import { computed, Directive, input } from '@angular/core';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

export const alertVariants = cva(
  'relative w-full rounded-md border p-4 text-sm [&>[hlmAlertIcon]]:absolute [&>[hlmAlertIcon]]:left-4 [&>[hlmAlertIcon]]:top-4 [&>[hlmAlertIcon]+div]:translate-y-[-3px] [&:has([hlmAlertIcon])]:pl-11',
  {
    variants: {
      variant: {
        default: 'border-border bg-card text-card-foreground',
        destructive: 'border-destructive/50 bg-status-emergency-bg text-status-emergency',
        warning: 'border-status-warning/50 bg-status-warning-bg text-status-warning',
        success: 'border-status-unlocked/50 bg-status-unlocked-bg text-status-unlocked',
        info: 'border-primary/40 bg-accent text-accent-foreground',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
);

export type AlertVariants = VariantProps<typeof alertVariants>;

@Directive({
  selector: '[hlmAlert]',
  standalone: true,
  host: {
    role: 'alert',
    '[class]': '_computedClass()',
  },
})
export class HlmAlertDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly variant = input<AlertVariants['variant']>('default');

  protected readonly _computedClass = computed(() =>
    hlm(alertVariants({ variant: this.variant() }), this.userClass())
  );
}

@Directive({
  selector: '[hlmAlertTitle]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmAlertTitleDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('mb-1 font-semibold leading-none tracking-tight', this.userClass())
  );
}

@Directive({
  selector: '[hlmAlertDescription]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmAlertDescriptionDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-sm leading-relaxed', this.userClass())
  );
}

@Directive({
  selector: '[hlmAlertIcon]',
  standalone: true,
  host: {
    'aria-hidden': 'true',
  },
})
export class HlmAlertIconDirective {}

export const HlmAlertImports = [
  HlmAlertDirective,
  HlmAlertTitleDirective,
  HlmAlertDescriptionDirective,
  HlmAlertIconDirective,
] as const;
