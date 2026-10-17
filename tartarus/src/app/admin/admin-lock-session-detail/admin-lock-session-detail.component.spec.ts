import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminLockSessionDetailComponent } from './admin-lock-session-detail.component';
import { TartarusCoordinatorService } from '../../tartarus-coordinator.service';
import { WebsocketService } from '../../websocket.service';
import { ToastService } from '../../toast.service';
import { LockSession } from '../../models/lock-session';
import { vi } from 'vitest';

describe('AdminLockSessionDetailComponent', () => {
  let component: AdminLockSessionDetailComponent;
  let fixture: ComponentFixture<AdminLockSessionDetailComponent>;
  let coordinatorServiceMock: any;
  let websocketServiceMock: any;
  let toastServiceMock: any;

  beforeEach(async () => {
    coordinatorServiceMock = {
      getLockSessionForAdmin: vi.fn().mockReturnValue(
        of(new LockSession({ name: 'ls-test', sessionToken: 'tok-123', createdAt: new Date(), updatedAt: new Date() }))
      ),
      getContractsForLockSessionForAdmin: vi.fn().mockReturnValue(of([])),
    };

    websocketServiceMock = {
      connect: vi.fn(),
      onMessage: vi.fn().mockReturnValue(of()),
    };

    toastServiceMock = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminLockSessionDetailComponent],
      providers: [
        provideRouter([]),
        { provide: TartarusCoordinatorService, useValue: coordinatorServiceMock },
        { provide: WebsocketService, useValue: websocketServiceMock },
        { provide: ToastService, useValue: toastServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminLockSessionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load lock session details', () => {
    expect(component).toBeTruthy();
    expect(coordinatorServiceMock.getLockSessionForAdmin).toHaveBeenCalled();
    expect(coordinatorServiceMock.getContractsForLockSessionForAdmin).toHaveBeenCalled();
    expect(component.lockSession?.name).toBe('ls-test');
  });
});
