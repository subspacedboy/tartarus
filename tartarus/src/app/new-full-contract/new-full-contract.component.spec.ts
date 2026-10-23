import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import * as flatbuffers from 'flatbuffers';
import { NewFullContractComponent } from './new-full-contract.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { CryptoService } from '../crypto.service';
import { UserDataService } from '../user-data.service';
import { ToastService } from '../toast.service';
import { LockSession } from '../models/lock-session';
import { Bot } from '../models/bot';
import { SignedMessage } from '../club/subjugated/fb/message/signed-message';
import { Contract as FBContract } from '../club/subjugated/fb/message/contract';
import { vi } from 'vitest';

describe('NewFullContractComponent', () => {
  let component: NewFullContractComponent;
  let fixture: ComponentFixture<NewFullContractComponent>;
  let coordinatorServiceSpy: any;
  let cryptoServiceSpy: any;
  let userDataServiceSpy: any;
  let toastServiceSpy: any;
  let router: Router;

  const mockBots: Bot[] = [
    new Bot({
      name: 'timer-bot',
      description: 'Automated release timer',
      publicKey: btoa('0123456789abcdef0123456789abcdef'),
    }),
    new Bot({
      name: 'audit-bot',
      description: 'Read-only event logger',
      publicKey: btoa('fedcba9876543210fedcba9876543210'),
    }),
  ];

  beforeEach(async () => {
    const mockLockSession = new LockSession({
      name: 'ls-1',
      shareToken: 'QAAAB3',
      validatedFirmware: true,
      knownToken: {
        name: 'kt-1',
        shareableToken: 'QAAAB3',
        notes: 'Alex Lock',
      },
    });

    coordinatorServiceSpy = {
      getLockSession: vi.fn().mockReturnValue(of(mockLockSession)),
      getBots: vi.fn().mockReturnValue(of(mockBots)),
      saveContract: vi.fn().mockReturnValue(of({ name: 'ctr-new-1' })),
    };

    cryptoServiceSpy = {
      importKeyPairForECDSA: vi.fn().mockResolvedValue({
        privateKey: {} as CryptoKey,
        publicKey: {} as CryptoKey,
      }),
      generateCompressedPublicKey: vi.fn().mockResolvedValue(new Uint8Array(33).fill(7)),
      hashAndSignData: vi.fn().mockResolvedValue(new Uint8Array(64).fill(9).buffer),
    };

    userDataServiceSpy = {
      getAuthorKeypair: vi.fn().mockReturnValue({
        privatePem: '-----BEGIN PRIVATE KEY-----\nmock\n-----END PRIVATE KEY-----',
        publicPem: '-----BEGIN PUBLIC KEY-----\nmock\n-----END PUBLIC KEY-----',
      }),
      getAuthorName: vi.fn().mockReturnValue('author-1'),
    };

    toastServiceSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [NewFullContractComponent],
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
        { provide: CryptoService, useValue: cryptoServiceSpy },
        { provide: UserDataService, useValue: userDataServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(NewFullContractComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load lock session and bots', () => {
    expect(component).toBeTruthy();
    expect(component.lockSessionToken).toBe('QAAAB3');
    expect(component.sessionLabel).toBe('Alex Lock');
    expect(component.bots.length).toBe(2);
    expect(component.isEmergencyUnlockAllowed).toBe(true);
    expect(component.isTempUnlockAllowed).toBe(false);
  });

  it('should add a bot with all 4 permissions enabled by default and allow removing it', () => {
    component.addBot(mockBots[0]);
    expect(component.selectedBots.length).toBe(1);
    expect(component.selectedBotNames).toEqual(['timer-bot']);

    const botGroup = component.selectedBotGroups[0];
    expect(botGroup.value).toEqual({
      id: 0,
      name: 'timer-bot',
      receiveEvents: true,
      canLock: true,
      canUnlock: true,
      canRelease: true,
    });

    // Adding the same bot again should not duplicate it
    component.addBot(mockBots[0]);
    expect(component.selectedBots.length).toBe(1);

    component.removeBotByName('timer-bot');
    expect(component.selectedBots.length).toBe(0);
  });

  it('should encode custom per-bot permissions into the signed FlatBuffers contract and navigate on save', async () => {
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.contractForm.get('terms')!.setValue('Strict 24h lockup');
    component.contractForm.get('notes')!.setValue('Weekend session');
    component.contractForm.get('isTempUnlockAllowed')!.setValue(true);
    component.contractForm.get('isEmergencyUnlockAllowed')!.setValue(false);

    // Attach two bots with distinct permissions
    component.addBot(mockBots[0]);
    component.addBot(mockBots[1]);

    // Make audit-bot read-only (receiveEvents only)
    const auditBotGroup = component.selectedBotGroups[1];
    auditBotGroup.patchValue({
      receiveEvents: true,
      canLock: false,
      canUnlock: false,
      canRelease: false,
    });

    const signedBytes = await component.createContract();
    expect(signedBytes.length).toBeGreaterThan(0);

    // Unpack FlatBuffer to verify terms, safety flags, and per-bot permissions
    const bb = new flatbuffers.ByteBuffer(signedBytes);
    const signedMsg = SignedMessage.getRootAsSignedMessage(bb);
    const fbContract = signedMsg.payload(new FBContract()) as FBContract;

    expect(fbContract.terms()).toBe('Strict 24h lockup');
    expect(fbContract.isTemporaryUnlockAllowed()).toBe(true);
    expect(fbContract.isEmergencyUnlockAllowed()).toBe(false);
    expect(fbContract.botsLength()).toBe(2);

    const bot0 = fbContract.bots(0)!;
    expect(bot0.name()).toBe('timer-bot');
    expect(bot0.permissions()!.receiveEvents()).toBe(true);
    expect(bot0.permissions()!.canLock()).toBe(true);
    expect(bot0.permissions()!.canUnlock()).toBe(true);
    expect(bot0.permissions()!.canRelease()).toBe(true);

    const bot1 = fbContract.bots(1)!;
    expect(bot1.name()).toBe('audit-bot');
    expect(bot1.permissions()!.receiveEvents()).toBe(true);
    expect(bot1.permissions()!.canLock()).toBe(false);
    expect(bot1.permissions()!.canUnlock()).toBe(false);
    expect(bot1.permissions()!.canRelease()).toBe(false);

    expect(coordinatorServiceSpy.saveContract).toHaveBeenCalledWith(
      'author-1',
      'QAAAB3',
      signedBytes,
      'Weekend session',
    );
    expect(toastServiceSpy.showSuccess).toHaveBeenCalledWith('Saved');
    expect(navSpy).toHaveBeenCalledWith(['lock-sessions', 'QAAAB3', 'contract', 'ctr-new-1']);
  });
});
