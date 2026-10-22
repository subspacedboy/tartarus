import {
  ChangeDetectionStrategy,
  Component,
  computed,
  Directive,
  inject,
  InjectionToken,
  input,
  model,
} from '@angular/core';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

export interface TabsContext {
  activeTab: () => string;
  setTab: (tab: string) => void;
}

export const HLM_TABS_CONTEXT = new InjectionToken<TabsContext>('HLM_TABS_CONTEXT');

@Component({
  selector: 'hlm-tabs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: HLM_TABS_CONTEXT,
      useExisting: HlmTabsComponent,
    },
  ],
  host: {
    '[class]': '_computedClass()',
  },
  template: `<ng-content />`,
})
export class HlmTabsComponent implements TabsContext {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly tab = model<string>('');

  protected readonly _computedClass = computed(() => hlm('block w-full', this.userClass()));

  activeTab(): string {
    return this.tab();
  }

  setTab(next: string): void {
    this.tab.set(next);
  }
}

@Component({
  selector: 'hlm-tabs-list, [hlmTabsList]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'tablist',
    '[class]': '_computedClass()',
  },
  template: `<ng-content />`,
})
export class HlmTabsListComponent {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly _computedClass = computed(() =>
    hlm(
      'inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground',
      this.userClass()
    )
  );
}

@Directive({
  selector: 'button[hlmTabsTrigger]',
  standalone: true,
  host: {
    type: 'button',
    role: 'tab',
    '[attr.aria-selected]': '_isActive()',
    '[attr.data-state]': '_isActive() ? "active" : "inactive"',
    '[class]': '_computedClass()',
    '(click)': '_activate()',
  },
})
export class HlmTabsTriggerDirective {
  private readonly _tabs = inject(HLM_TABS_CONTEXT, { optional: true });
  public readonly triggerFor = input.required<string>({ alias: 'hlmTabsTrigger' });
  public readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly _isActive = computed(() => this._tabs?.activeTab() === this.triggerFor());

  protected readonly _computedClass = computed(() =>
    hlm(
      'inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-xs font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 cursor-pointer',
      this._isActive()
        ? 'bg-background text-foreground shadow-sm'
        : 'text-muted-foreground hover:text-foreground',
      this.userClass()
    )
  );

  protected _activate(): void {
    this._tabs?.setTab(this.triggerFor());
  }
}

@Directive({
  selector: '[hlmTabsContent]',
  standalone: true,
  host: {
    role: 'tabpanel',
    '[attr.data-state]': '_isActive() ? "active" : "inactive"',
    '[hidden]': '!_isActive()',
    '[class]': '_computedClass()',
  },
})
export class HlmTabsContentDirective {
  private readonly _tabs = inject(HLM_TABS_CONTEXT, { optional: true });
  public readonly contentFor = input.required<string>({ alias: 'hlmTabsContent' });
  public readonly userClass = input<ClassValue>('', { alias: 'class' });

  protected readonly _isActive = computed(() => this._tabs?.activeTab() === this.contentFor());

  protected readonly _computedClass = computed(() =>
    hlm(
      'mt-2 ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
      this.userClass()
    )
  );
}

export const HlmTabsImports = [
  HlmTabsComponent,
  HlmTabsListComponent,
  HlmTabsTriggerDirective,
  HlmTabsContentDirective,
] as const;
