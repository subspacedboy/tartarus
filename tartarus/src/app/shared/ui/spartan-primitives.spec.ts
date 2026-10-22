import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  hlm,
  HlmAlertImports,
  HlmBadgeDirective,
  HlmButtonDirective,
  HlmCardImports,
  HlmSwitchComponent,
} from './index';

@Component({
  standalone: true,
  imports: [
    HlmButtonDirective,
    HlmBadgeDirective,
    ...HlmCardImports,
    ...HlmAlertImports,
    HlmSwitchComponent,
  ],
  template: `
    <section hlmCard id="test-card">
      <div hlmCardHeader>
        <h3 hlmCardTitle>Cryptographic Card</h3>
        <span hlmBadge variant="locked" shape="pill" id="test-badge">Locked</span>
      </div>
      <div hlmCardContent>
        <div hlmAlert variant="destructive" id="test-alert">
          <h4 hlmAlertTitle>Emergency</h4>
          <div hlmAlertDescription>Explicit lock required</div>
        </div>
        <hlm-switch id="test-switch" [(checked)]="switchState" aria-label="Allow temporary unlock" />
      </div>
      <div hlmCardFooter>
        <button type="button" hlmBtn variant="default" size="lg" id="test-btn">Execute</button>
      </div>
    </section>
  `,
})
class TestHostComponent {
  switchState = false;
}

describe('Spartan.ng Phase 0 UI Primitives', () => {
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
  });

  it('merges classes using hlm() utility', () => {
    expect(hlm('px-2 py-1', 'px-4')).toBe('py-1 px-4');
  });

  it('applies hlmCard, hlmBadge, hlmAlert, and hlmBtn classes and accessibility attributes', () => {
    const el: HTMLElement = fixture.nativeElement;

    const card = el.querySelector('#test-card')!;
    expect(card.className).toContain('rounded-xl');
    expect(card.className).toContain('bg-card');

    const badge = el.querySelector('#test-badge')!;
    expect(badge.className).toContain('rounded-full');
    expect(badge.className).toContain('text-status-locked');

    const alert = el.querySelector('#test-alert')!;
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.className).toContain('text-status-emergency');

    const btn = el.querySelector('#test-btn')!;
    expect(btn.className).toContain('bg-primary');
    expect(btn.className).toContain('h-11');
  });

  it('toggles HlmSwitchComponent state with role="switch" and aria-checked', () => {
    const el: HTMLElement = fixture.nativeElement;
    const switchBtn = el.querySelector('button[role="switch"]') as HTMLButtonElement;
    expect(switchBtn).toBeTruthy();
    expect(switchBtn.getAttribute('aria-checked')).toBe('false');

    switchBtn.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.switchState).toBe(true);
    expect(switchBtn.getAttribute('aria-checked')).toBe('true');
  });
});
