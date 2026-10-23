import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { LockSessionDetailComponent } from './lock-session-detail.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { ToastService } from '../toast.service';
import { WebsocketService } from '../websocket.service';
import { LockSession } from '../models/lock-session';
import { Contract } from '../models/contract';
import { vi } from 'vitest';

describe('LockSessionDetailComponent', () => {
  let component: LockSessionDetailComponent;
  let fixture: ComponentFixture<LockSessionDetailComponent>;
  let coordinatorServiceSpy: any;
  let toastServiceSpy: any;
  let wsSubject: Subject<any>;
  let websocketServiceSpy: any;

  beforeEach(async () => {
    wsSubject = new Subject<any>();

    const mockLockSession = new LockSession({
      name: 'ls-1',
      shareToken: 'QAAAB3',
      availableForContract: true,
      validatedFirmware: true,
      currentFirmware: 'v1.4.2',
      updatedAt: new Date(),
      lockState: {
        isLocked: true,
        emergencyUnlocked: false,
      },
      knownToken: {
        name: 'kt-1',
        shareableToken: 'QAAAB3',
        notes: 'Alex Lock',
      },
    });

    const mockContracts = [
      new Contract({ name: 'ctr-active', state: 'CONFIRMED', serialNumber: 4, notes: 'Active contract' }),
      new Contract({ name: 'ctr-pending', state: 'CREATED', notes: 'Proposal' }),
      new Contract({ name: 'ctr-old', state: 'RELEASED' }),
    ];

    coordinatorServiceSpy = {
      getLockSession: vi.fn().mockReturnValue(of(mockLockSession)),
      getContractsForShareable: vi.fn().mockReturnValue(of(mockContracts)),
      saveNotesForKnownToken: vi.fn().mockReturnValue(of({})),
    };

    toastServiceSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    websocketServiceSpy = {
      connect: vi.fn(),
      onMessage: vi.fn().mockReturnValue(wsSubject.asObservable()),
    };

    await TestBed.configureTestingModule({
      imports: [LockSessionDetailComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ sessionToken: 'QAAAB3' }),
            },
          },
        },
        { provide: TartarusCoordinatorService, useValue: coordinatorServiceSpy },
        { provide: UserDataService, useValue: {} },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: WebsocketService, useValue: websocketServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LockSessionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and segment contracts by lifecycle state', () => {
    expect(component).toBeTruthy();
    expect(component.sessionToken).toBe('QAAAB3');
    expect(component.sessionTitle).toBe('Alex Lock');
    expect(component.activeContract?.name).toBe('ctr-active');
    expect(component.pendingProposals.length).toBe(1);
    expect(component.pendingProposals[0].name).toBe('ctr-pending');
    expect(component.historicalContracts.length).toBe(1);
    expect(component.historicalContracts[0].name).toBe('ctr-old');
  });

  it('should save updated session notes and display toast', () => {
    component.knownTokenForm.get('notes')!.setValue('Renamed Lock');
    component.saveNotes();

    expect(coordinatorServiceSpy.saveNotesForKnownToken).toHaveBeenCalledWith('kt-1', 'Renamed Lock');
    expect(component.lockSession?.knownToken?.notes).toBe('Renamed Lock');
    expect(toastServiceSpy.showSuccess).toHaveBeenCalledWith('Saved');
  });

  it('should refresh data when a WebSocket message arrives', () => {
    expect(coordinatorServiceSpy.getLockSession).toHaveBeenCalledTimes(1);
    wsSubject.next({ type: 'UPDATE' });
    expect(coordinatorServiceSpy.getLockSession).toHaveBeenCalledTimes(2);
    expect(coordinatorServiceSpy.getContractsForShareable).toHaveBeenCalledTimes(2);
  });
});
