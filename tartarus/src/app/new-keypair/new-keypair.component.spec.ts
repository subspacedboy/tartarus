import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { NewKeypairComponent } from './new-keypair.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { CryptoService } from '../crypto.service';
import { IdHelperService } from '../id-helper.service';
import { UserDataService } from '../user-data.service';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { vi } from 'vitest';

describe('NewKeypairComponent', () => {
  let component: NewKeypairComponent;
  let fixture: ComponentFixture<NewKeypairComponent>;
  let coordinatorSpy: any;
  let cryptoSpy: any;
  let userDataSpy: any;
  let wsSpy: any;
  let toastSpy: any;
  let router: Router;

  beforeEach(async () => {
    coordinatorSpy = {
      createAuthorSession: vi.fn().mockReturnValue(of({ sessionToken: 'sess-tok-1' })),
    };

    cryptoSpy = {
      generateKeyPair: vi.fn().mockResolvedValue({
        privateKeyPEM: 'priv-pem',
        publicKeyPEM: 'pub-pem',
      }),
      importKeyPairForECDSA: vi.fn().mockResolvedValue({
        privateKey: {} as CryptoKey,
        publicKey: {} as CryptoKey,
        privateKeyPem: 'priv-pem',
        publicKeyPem: 'pub-pem',
      }),
      hashAndSignData: vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer),
      generateCompressedPublicKey: vi.fn().mockResolvedValue(new Uint8Array([4, 5, 6])),
    };

    userDataSpy = {
      addPublicAndPrivateKeyToAuthorSession: vi.fn(),
    };

    wsSpy = {
      reconnect: vi.fn(),
    };

    toastSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [NewKeypairComponent],
      providers: [
        provideRouter([]),
        { provide: TartarusCoordinatorService, useValue: coordinatorSpy },
        { provide: CryptoService, useValue: cryptoSpy },
        { provide: IdHelperService, useValue: { generateBase32Id: () => 'tok-123' } },
        { provide: UserDataService, useValue: userDataSpy },
        { provide: WebsocketService, useValue: wsSpy },
        { provide: ToastService, useValue: toastSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(NewKeypairComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should allow signing in directly with the generated keypair', async () => {
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.generatedKeyPair = { privateKeyPEM: 'priv-pem', publicKeyPEM: 'pub-pem' };
    component.hasBackedUpKey = true;

    await component.loginWithGeneratedKey();

    expect(coordinatorSpy.createAuthorSession).toHaveBeenCalled();
    expect(userDataSpy.addPublicAndPrivateKeyToAuthorSession).toHaveBeenCalledWith(
      'priv-pem',
      'pub-pem',
      'sess-tok-1',
    );
    expect(wsSpy.reconnect).toHaveBeenCalled();
    expect(toastSpy.showSuccess).toHaveBeenCalledWith('Signed in with your new keypair');
    expect(navSpy).toHaveBeenCalledWith(['/']);
  });
});
