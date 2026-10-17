import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LockSessionCardComponent } from './lock-session-card.component';
import { KnownToken } from '../models/known-token';
import { vi } from 'vitest';

describe('LockSessionCardComponent', () => {
  let component: LockSessionCardComponent;
  let fixture: ComponentFixture<LockSessionCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockSessionCardComponent],
      providers: [provideRouter([])]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockSessionCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
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
