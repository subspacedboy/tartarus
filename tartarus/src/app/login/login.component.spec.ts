import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoginComponent } from './login.component';
import { UserDataService } from '../user-data.service';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { CryptoService } from '../crypto.service';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { vi } from 'vitest';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let cryptoServiceSpy: any;

  beforeEach(async () => {
    cryptoServiceSpy = {
      importKeyPairForECDSA: vi.fn().mockRejectedValue(new Error('Invalid PEM')),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: UserDataService, useValue: { hasAuthorSession: () => false } },
        { provide: TartarusCoordinatorService, useValue: {} },
        { provide: CryptoService, useValue: cryptoServiceSpy },
        { provide: WebsocketService, useValue: { reconnect: vi.fn() } },
        { provide: ToastService, useValue: { showSuccess: vi.fn(), showError: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should handle images without a QR code gracefully without throwing', async () => {
    const fakeCanvas = {
      width: 0,
      height: 0,
      getContext: () => ({
        drawImage: vi.fn(),
        getImageData: () => ({
          data: new Uint8ClampedArray(100 * 100 * 4),
          width: 100,
          height: 100,
        }),
      }),
    };
    component.scanCanvas = { nativeElement: fakeCanvas } as any;

    const fakeImg = { width: 100, height: 100 } as HTMLImageElement;
    await component.scanQRCode(fakeImg);

    expect(component.scannedResult).toBe('No QR code found.');
    expect(component.errorMessage).toContain('No QR code was detected');
  });
});
