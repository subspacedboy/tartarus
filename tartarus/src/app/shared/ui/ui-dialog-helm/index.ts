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
  selector: 'hlm-dialog',
  standalone: true,
  imports: [A11yModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/65 backdrop-blur-[1px]"
        (click)="onBackdropClick($event)"
      >
        <div
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="ariaLabel() || null"
          [class]="_contentClass()"
          (click)="$event.stopPropagation()"
        >
          <ng-content />
        </div>
      </div>
    }
  `,
})
export class HlmDialogComponent {
  public readonly open = input<boolean>(true);
  public readonly ariaLabel = input<string>('', { alias: 'aria-label' });
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly closed = output<void>();

  protected readonly _contentClass = computed(() =>
    hlm(
      'relative z-50 grid w-full max-w-lg max-h-[90vh] overflow-y-auto gap-4 rounded-xl border border-border bg-card p-6 text-card-foreground shadow-lg',
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
  selector: '[hlmDialogHeader], hlm-dialog-header',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmDialogHeaderDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex flex-col space-y-1.5 text-left', this.userClass())
  );
}

@Directive({
  selector: '[hlmDialogFooter], hlm-dialog-footer',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmDialogFooterDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 pt-2', this.userClass())
  );
}

@Directive({
  selector: '[hlmDialogTitle], hlm-dialog-title',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmDialogTitleDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-lg font-semibold leading-none tracking-tight text-foreground', this.userClass())
  );
}

@Directive({
  selector: '[hlmDialogDescription], hlm-dialog-description',
  standalone: true,
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmDialogDescriptionDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('text-sm text-muted-foreground', this.userClass())
  );
}

export const HlmDialogImports = [
  HlmDialogComponent,
  HlmDialogHeaderDirective,
  HlmDialogFooterDirective,
  HlmDialogTitleDirective,
  HlmDialogDescriptionDirective,
] as const;
