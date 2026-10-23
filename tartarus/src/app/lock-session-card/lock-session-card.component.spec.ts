import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LockSessionCardComponent } from './lock-session-card.component';
import { KnownToken } from '../models/known-token';
import { ToastService } from '../toast.service';
import { vi } from 'vitest';

describe('LockSessionCardComponent', () => {
  let component: LockSessionCardComponent;
  let fixture: ComponentFixture<LockSessionCardComponent>;
  let toastServiceSpy: { showSuccess: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    toastServiceSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LockSessionCardComponent],
      providers: [
        provideRouter([]),
        { provide: ToastService, useValue: toastServiceSpy },
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockSessionCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display custom notes when set, or Unlabeled Session fallback when empty', () => {
    component.knownToken = new KnownToken({ name: 'kt-123', shareableToken: 'QAAAB3', notes: 'Alex Lock' });
    expect(component.hasCustomLabel).toBe(true);
    expect(component.displayTitle).toBe('Alex Lock');

    component.knownToken = new KnownToken({ name: 'kt-456', shareableToken: 'QBBBB4', notes: '   ' });
    expect(component.hasCustomLabel).toBe(false);
    expect(component.displayTitle).toBe('Unlabeled Session');
  });

  it('should emit delete event with token when onDelete is called', () => {
    const token = new KnownToken({ name: 'kt-123', shareableToken: 'QAAAB3', notes: 'My Lock' });
    component.knownToken = token;
    vi.spyOn(component.delete, 'emit');

    const fakeEvent = new MouseEvent('click');
    vi.spyOn(fakeEvent, 'stopPropagation');
    vi.spyOn(fakeEvent, 'preventDefault');

    component.onDelete(fakeEvent);

    expect(fakeEvent.stopPropagation).toHaveBeenCalled();
    expect(fakeEvent.preventDefault).toHaveBeenCalled();
    expect(component.delete.emit).toHaveBeenCalledWith(token);
  });
});
