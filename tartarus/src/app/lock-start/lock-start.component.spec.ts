import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { LockStartComponent } from './lock-start.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { ConfigService } from '../config.service';
import { CryptoService } from '../crypto.service';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { LockUserSession } from '../models/lock-user-session';
import { LockSession } from '../models/lock-session';
import { vi } from 'vitest';

describe('LockStartComponent', () => {
  let component: LockStartComponent;
  let fixture: ComponentFixture<LockStartComponent>;
  let coordinatorSpy: any;
  let userDataSpy: any;
  let cryptoSpy: any;
  let wsSpy: any;
  let toastSpy: any;

  beforeEach(async () => {
    const mockLockUserSession = new LockUserSession({
      name: 'lus-1',
      lockSession: new LockSession({
        name: 'ls-1',
        shareToken: 'SHARE-123',
        totalControlToken: 'TC-999',
      }),
    });

    coordinatorSpy = {
      saveLockUserSessionWithCryptogram: vi.fn().mockReturnValue(of(mockLockUserSession)),
    };

    userDataSpy = {
      hasLockUserSessionKeyPair: vi.fn().mockReturnValue(true),
      getLockUserSessionKeyPair: vi.fn().mockReturnValue({
        privatePem: 'priv',
        publicPem: 'pub',
      }),
      setLockUserSessionToken: vi.fn(),
    };

    cryptoSpy = {
      importPublicKeyOnlyFromPem: vi.fn().mockResolvedValue({} as CryptoKey),
      generateCompressedPublicKey: vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
    };

    wsSpy = {
      reconnect: vi.fn(),
    };

    toastSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LockStartComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({ session: 'sess-1', nonce: 'n-1', cipher: 'c-1' })),
          },
        },
        { provide: TartarusCoordinatorService, useValue: coordinatorSpy },
        { provide: UserDataService, useValue: userDataSpy },
        {
          provide: ConfigService,
          useValue: { getConfig: () => ({ webUri: 'https://tartarus.example.com' }) },
        },
        { provide: CryptoService, useValue: cryptoSpy },
        { provide: WebsocketService, useValue: wsSpy },
        { provide: ToastService, useValue: toastSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LockStartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and register lock user session', async () => {
    await component.ngOnInit();
    expect(component).toBeTruthy();
    expect(component.loaded).toBe(true);
    expect(component.errorMessage).toBeNull();
    expect(component.getShareLink()).toBe('https://tartarus.example.com/lock-sessions/SHARE-123');
    expect(component.getTotalControlLink()).toBe('https://tartarus.example.com/lock-sessions/TC-999');
  });

  it('should toggle invite link mode and technical details', () => {
    expect(component.inviteLinkMode).toBe('reviewable');
    component.setInviteLinkMode('total-control');
    expect(component.inviteLinkMode).toBe('total-control');

    expect(component.showDetails).toBe(false);
    component.showTechnicalDetails();
    expect(component.showDetails).toBe(true);
  });
});
