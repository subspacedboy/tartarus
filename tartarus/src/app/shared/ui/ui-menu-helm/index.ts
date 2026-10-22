import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, computed, Directive, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Directive({
  selector: '[hlmMenuTriggerFor]',
  standalone: true,
  hostDirectives: [
    {
      directive: CdkMenuTrigger,
      inputs: ['cdkMenuTriggerFor: hlmMenuTriggerFor'],
    },
  ],
})
export class HlmMenuTriggerDirective {}

@Component({
  selector: 'hlm-menu, [hlmMenu]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  hostDirectives: [CdkMenu],
  host: {
    '[class]': '_computedClass()',
  },
  template: `<ng-content />`,
})
export class HlmMenuComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm(
      'z-50 min-w-[12rem] overflow-hidden rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md',
      this.userClass()
    )
  );
}

@Directive({
  selector: '[hlmMenuItem]',
  standalone: true,
  hostDirectives: [
    {
      directive: CdkMenuItem,
      inputs: ['cdkMenuItemDisabled: disabled'],
    },
  ],
  host: {
    '[class]': '_computedClass()',
  },
})
export class HlmMenuItemDirective {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly disabled = input<boolean>(false);

  protected readonly _computedClass = computed(() =>
    hlm(
      'relative flex w-full cursor-pointer select-none items-center gap-2 rounded-sm px-2.5 py-1.5 text-sm outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50 no-underline text-popover-foreground',
      this.userClass()
    )
  );
}

@Component({
  selector: 'hlm-menu-separator, [hlmMenuSeparator]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'separator',
    '[class]': '_computedClass()',
  },
  template: ``,
})
export class HlmMenuSeparatorComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('-mx-1 my-1 h-px bg-border block', this.userClass())
  );
}

@Component({
  selector: 'hlm-menu-label, [hlmMenuLabel]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': '_computedClass()',
  },
  template: `<ng-content />`,
})
export class HlmMenuLabelComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm('px-2.5 py-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground block', this.userClass())
  );
}

export const HlmMenuImports = [
  HlmMenuTriggerDirective,
  HlmMenuComponent,
  HlmMenuItemDirective,
  HlmMenuSeparatorComponent,
  HlmMenuLabelComponent,
] as const;
