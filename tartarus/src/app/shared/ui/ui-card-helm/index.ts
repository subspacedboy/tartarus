import { computed, Directive, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Directive({
  selector: '[hlmCard]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('rounded-xl border border-border bg-card text-card-foreground shadow-sm', this.userClass())
  );
}

@Directive({
  selector: '[hlmCardHeader]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardHeaderDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex flex-col space-y-1.5 p-6', this.userClass())
  );
}

@Directive({
  selector: '[hlmCardTitle]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardTitleDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-base font-semibold leading-none tracking-tight text-card-foreground', this.userClass())
  );
}

@Directive({
  selector: '[hlmCardDescription]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardDescriptionDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-sm text-muted-foreground', this.userClass())
  );
}

@Directive({
  selector: '[hlmCardContent]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardContentDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('p-6 pt-0', this.userClass())
  );
}

@Directive({
  selector: '[hlmCardFooter]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCardFooterDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex items-center p-6 pt-0', this.userClass())
  );
}

export const HlmCardImports = [
  HlmCardDirective,
  HlmCardHeaderDirective,
  HlmCardTitleDirective,
  HlmCardDescriptionDirective,
  HlmCardContentDirective,
  HlmCardFooterDirective,
] as const;
