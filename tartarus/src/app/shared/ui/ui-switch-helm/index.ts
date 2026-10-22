import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  model,
  output,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import type { ClassValue } from 'clsx';
import { hlm } from '../utils/hlm';

@Component({
  selector: 'hlm-switch',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => HlmSwitchComponent),
      multi: true,
    },
  ],
  template: `
    <button
      type="button"
      role="switch"
      [id]="id() || null"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="ariaLabel() || null"
      [attr.data-state]="checked() ? 'checked' : 'unchecked'"
      [disabled]="disabled()"
      [class]="_computedClass()"
      (click)="toggle()"
    >
      <span
        [attr.data-state]="checked() ? 'checked' : 'unchecked'"
        [class]="_thumbClass()"
      ></span>
    </button>
  `,
})
export class HlmSwitchComponent implements ControlValueAccessor {
  public readonly userClass = input<ClassValue>('', { alias: 'class' });
  public readonly id = input<string>('');
  public readonly ariaLabel = input<string>('', { alias: 'aria-label' });
  public readonly checked = model<boolean>(false);
  public readonly disabled = model<boolean>(false);
  public readonly changed = output<boolean>();

  private _onChange: (value: boolean) => void = () => {};
  private _onTouched: () => void = () => {};

  protected readonly _computedClass = computed(() =>
    hlm(
      'peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50',
      this.checked() ? 'bg-primary' : 'bg-input',
      this.userClass()
    )
  );

  protected readonly _thumbClass = computed(() =>
    hlm(
      'pointer-events-none block h-4 w-4 rounded-full bg-background shadow-lg ring-0 transition-transform',
      this.checked() ? 'translate-x-4' : 'translate-x-0'
    )
  );

  toggle(): void {
    if (this.disabled()) return;
    const next = !this.checked();
    this.checked.set(next);
    this._onChange(next);
    this._onTouched();
    this.changed.emit(next);
  }

  writeValue(value: boolean): void {
    this.checked.set(booleanAttribute(value));
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this._onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this._onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}

export const HlmSwitchImports = [HlmSwitchComponent] as const;
