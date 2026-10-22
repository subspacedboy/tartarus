import { computed, Directive, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Directive({
  selector: 'table[hlmTable], [hlmTable]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmTableDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('w-full caption-bottom text-sm border-collapse', this.userClass())
  );
}

@Directive({
  selector: 'thead[hlmThead], [hlmThead]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmTheadDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('[&_tr]:border-b [&_tr]:border-border bg-muted/40', this.userClass())
  );
}

@Directive({
  selector: 'tbody[hlmTbody], [hlmTbody]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmTbodyDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('[&_tr:last-child]:border-0', this.userClass())
  );
}

@Directive({
  selector: 'tr[hlmTr], tr[hlmTrow], [hlmTrow]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmTrowDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm(
      'border-b border-border transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted',
      this.userClass()
    )
  );
}

@Directive({
  selector: 'th[hlmTh], [hlmTh]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmThDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm(
      'h-10 px-4 text-left align-middle text-xs font-semibold uppercase tracking-wider text-muted-foreground [&:has([role=checkbox])]:pr-0',
      this.userClass()
    )
  );
}

@Directive({
  selector: 'td[hlmTd], [hlmTd]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmTdDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('p-4 align-middle [&:has([role=checkbox])]:pr-0', this.userClass())
  );
}

@Directive({
  selector: 'caption[hlmCaption], [hlmCaption]',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmCaptionDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('mt-4 text-sm text-muted-foreground', this.userClass())
  );
}

export const HlmTableImports = [
  HlmTableDirective,
  HlmTheadDirective,
  HlmTbodyDirective,
  HlmTrowDirective,
  HlmThDirective,
  HlmTdDirective,
  HlmCaptionDirective,
] as const;
