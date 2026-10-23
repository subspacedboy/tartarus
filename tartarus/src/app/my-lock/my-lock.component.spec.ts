import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { MyLockComponent } from './my-lock.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { WebsocketService } from '../websocket.service';
import { ConfigService } from '../config.service';
import { ToastService } from '../toast.service';
import { LockSession } from '../models/lock-session';
import { Contract } from '../models/contract';
import { Command } from '../models/command';

describe('MyLockComponent', () => {
  let component: MyLockComponent;
  let fixture: ComponentFixture<MyLockComponent>;
  let coordinatorMock: any;
  let wsSubject: Subject<string>;
  let websocketMock: any;

  beforeEach(async () => {
    wsSubject = new Subject<string>();
    coordinatorMock = {
      getMyLockSession: vi.fn().mockReturnValue(
        of(
          new LockSession({
            name: 'lock-01',
            shareToken: 'SHARE123',
            totalControlToken: 'TOTAL999',
            availableForContract: false,
            validatedFirmware: true,
            currentFirmware: 'v1.4.2',
            lockState: { isLocked: true, emergencyUnlocked: false } as any,
            updatedAt: new Date(),
            createdAt: new Date(),
          })
        )
      ),
      getContractsForLockSession: vi.fn().mockReturnValue(
        of([
          new Contract({ name: 'contract-active', state: 'CONFIRMED', serialNumber: 7, notes: 'Active contract note' }),
          new Contract({ name: 'contract-proposal', state: 'CREATED', serialNumber: 8, notes: 'Proposal note' }),
          new Contract({ name: 'contract-old', state: 'RELEASED', serialNumber: 6, updatedAt: new Date().toISOString() }),
        ])
      ),
      getCommandsForContractForLockUser: vi.fn().mockReturnValue(
        of([
          new Command({ name: 'cmd-1', contractName: 'contract-active', type: 'UNLOCK', state: 'PENDING' }),
          new Command({ name: 'cmd-2', contractName: 'contract-active', type: 'LOCK', state: 'ACKNOWLEDGED', updatedAt: new Date().toISOString() }),
        ])
      ),
    };

    websocketMock = {
      connect: vi.fn(),
      onMessage: vi.fn().mockReturnValue(wsSubject.asObservable()),
    };

    await TestBed.configureTestingModule({
      imports: [MyLockComponent],
      providers: [
        provideRouter([]),
        { provide: TartarusCoordinatorService, useValue: coordinatorMock },
        { provide: WebsocketService, useValue: websocketMock },
        {
          provide: ConfigService,
          useValue: { getConfig: () => ({ webUri: 'https://tartarus.example', apiUri: 'https://api.tartarus.example' }) },
        },
        {
          provide: ToastService,
          useValue: { showSuccess: vi.fn(), showError: vi.fn(), showInfo: vi.fn() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MyLockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and segment contracts into active, pending proposals, and history', () => {
    expect(component).toBeTruthy();
    expect(component.activeContract?.name).toBe('contract-active');
    expect(component.pendingProposals.length).toBe(1);
    expect(component.pendingProposals[0].name).toBe('contract-proposal');
    expect(component.historicalContracts.length).toBe(1);
    expect(component.historicalContracts[0].name).toBe('contract-old');
  });

  it('should load commands for the active contract and surface pending commands inline', () => {
    expect(coordinatorMock.getCommandsForContractForLockUser).toHaveBeenCalledWith('contract-active');
    expect(component.pendingCommands.length).toBe(1);
    expect(component.pendingCommands[0].type).toBe('UNLOCK');
    expect(component.acknowledgedCommands.length).toBe(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Active Contract');
    expect(compiled.textContent).toContain('Pending Commands for Your Lock (1)');
  });

  it('should default Invite a Keyholder card to Reviewable Link and switch to Total Control on tab click', () => {
    expect(component.inviteLinkMode).toBe('reviewable');
    const compiled = fixture.nativeElement as HTMLElement;
    const shareInput = compiled.querySelector('#lockLink') as HTMLInputElement;
    expect(shareInput).toBeTruthy();
    expect(shareInput.value).toBe('https://tartarus.example/lock-sessions/SHARE123');
    expect(compiled.querySelector('#lockLink2')).toBeNull();

    component.setInviteLinkMode('total-control');
    fixture.detectChanges();

    const totalControlInput = compiled.querySelector('#lockLink2') as HTMLInputElement;
    expect(totalControlInput).toBeTruthy();
    expect(totalControlInput.value).toBe('https://tartarus.example/lock-sessions/TOTAL999');
    expect(compiled.textContent).toContain('High Trust Only');
  });
});
