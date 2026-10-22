import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  Directive,
  HostListener,
  input,
  output,
} from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Component({
  selector: 'hlm-sheet',
  standalone: true,
  imports: [A11yModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 bg-black/65 backdrop-blur-[1px] flex"
        [class.justify-end]="side() === 'right'"
        [class.justify-start]="side() === 'left'"
        (click)="onBackdropClick($event)"
      >
        <div
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="ariaLabel() || null"
          [class]="_sheetClass()"
          (click)="$event.stopPropagation()"
        >
          <ng-content />
        </div>
      </div>
    }
  `,
})
export class HlmSheetComponent {
  public readonly open = input<boolean>(true);
  public readonly side = input<'right' | 'left'>('right');
  public readonly ariaLabel = input<string>('', { alias: 'aria-label' });
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly closed = output<void>();

  protected readonly _sheetClass = computed(() =>
    hlm(
      'relative z-50 flex h-full w-full flex-col overflow-y-auto bg-card p-6 text-card-foreground shadow-xl sm:max-w-2xl',
      this.side() === 'right' ? 'border-l border-border' : 'border-r border-border',
      this.userClass()
    )
  );

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}

@Directive({
  selector: '[hlmSheetHeader], hlm-sheet-header',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmSheetHeaderDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex flex-col space-y-1.5 text-left pb-4 border-b border-border', this.userClass())
  );
}

@Directive({
  selector: '[hlmSheetFooter], hlm-sheet-footer',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmSheetFooterDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 pt-4 border-t border-border mt-auto', this.userClass())
  );
}

@Directive({
  selector: '[hlmSheetTitle], hlm-sheet-title',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmSheetTitleDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-lg font-semibold leading-none tracking-tight text-foreground', this.userClass())
  );
}

@Directive({
  selector: '[hlmSheetDescription], hlm-sheet-description',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmSheetDescriptionDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-sm text-muted-foreground', this.userClass())
  );
}

export const HlmSheetImports = [
  HlmSheetComponent,
  HlmSheetHeaderDirective,
  HlmSheetFooterDirective,
  HlmSheetTitleDirective,
  HlmSheetDescriptionDirective,
] as const;
