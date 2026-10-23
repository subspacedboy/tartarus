import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LandingPageComponent } from './landing-page.component';
import { AppComponent } from '../app.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { Contract } from '../models/contract';
import { KnownToken } from '../models/known-token';
import { LockSession } from '../models/lock-session';
import { vi } from 'vitest';

describe('LandingPageComponent', () => {
  let component: LandingPageComponent;
  let fixture: ComponentFixture<LandingPageComponent>;
  let coordinatorSpy: any;
  let userDataSpy: any;

  beforeEach(async () => {
    coordinatorSpy = {
      getConfirmedContractsForAuthor: vi.fn().mockReturnValue(of([new Contract({ name: 'ctr-1', state: 'CONFIRMED' })])),
      getKnownTokensForAuthor: vi.fn().mockReturnValue(of([new KnownToken({ name: 'kt-1', shareableToken: 'tok-1' })])),
      getContractsForLockSessionForReview: vi.fn().mockReturnValue(of([])),
      getMyLockSession: vi.fn().mockReturnValue(of(new LockSession({ name: 'ls-1', lockState: { isLocked: true, emergencyUnlocked: false } }))),
      getContractsForLockSession: vi.fn().mockReturnValue(of([new Contract({ name: 'ctr-lock-active', state: 'CONFIRMED' })])),
    };

    userDataSpy = {
      hasAuthorSession: vi.fn().mockReturnValue(true),
      hasLockUserSession: vi.fn().mockReturnValue(true),
    };

    await TestBed.configureTestingModule({
      imports: [LandingPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: AppComponent,
          useValue: {
            hasAuthorSession: true,
            hasLockSessions: true,
          },
        },
        { provide: TartarusCoordinatorService, useValue: coordinatorSpy },
        { provide: UserDataService, useValue: userDataSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load author and lock owner summaries when authenticated', () => {
    expect(component).toBeTruthy();
    expect(component.confirmedContracts.length).toBe(1);
    expect(component.knownTokens.length).toBe(1);
    expect(component.myLockSession?.lockState?.isLocked).toBe(true);
    expect(component.activeLockContract?.name).toBe('ctr-lock-active');
  });
});
