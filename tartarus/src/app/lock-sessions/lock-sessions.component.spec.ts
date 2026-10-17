import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LockSessionsComponent } from './lock-sessions.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { ToastService } from '../toast.service';
import { KnownToken } from '../models/known-token';
import { vi } from 'vitest';

describe('LockSessionsComponent', () => {
  let component: LockSessionsComponent;
  let fixture: ComponentFixture<LockSessionsComponent>;
  let coordinatorServiceSpy: any;
  let toastServiceSpy: any;

  beforeEach(async () => {
    coordinatorServiceSpy = {
      getKnownTokensForAuthor: vi.fn().mockReturnValue(of([
        new KnownToken({ name: 'kt-1', shareableToken: 'tok-1', notes: 'Lock 1' })
      ])),
      deleteKnownToken: vi.fn().mockReturnValue(of(true))
    };

    toastServiceSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [LockSessionsComponent],
      providers: [
        provideRouter([]),
        { provide: TartarusCoordinatorService, useValue: coordinatorServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockSessionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load known tokens', () => {
    expect(component).toBeTruthy();
    expect(component.knownTokens.length).toBe(1);
    expect(component.knownTokens[0].name).toBe('kt-1');
  });

  it('should delete a token and show success toast', () => {
    const token = component.knownTokens[0];
    component.deleteToken(token);
    expect(coordinatorServiceSpy.deleteKnownToken).toHaveBeenCalledWith('kt-1');
    expect(component.knownTokens.length).toBe(0);
    expect(toastServiceSpy.showSuccess).toHaveBeenCalledWith('Lock session deleted');
  });
});
