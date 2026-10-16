import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as flatbuffers from 'flatbuffers';
import { LockUserContractDetailComponent } from './lock-user-contract-detail.component';
import { SignedMessage } from '../club/subjugated/fb/message/signed-message';
import { MessagePayload } from '../club/subjugated/fb/message/message-payload';
import { Contract } from '../club/subjugated/fb/message/contract';
import { Bot as FBBot } from '../club/subjugated/fb/message/bot';
import { Permission } from '../club/subjugated/fb/message/permission';
import { Contract as ContractModel } from '../models/contract';
import { Bot } from '../models/bot';

describe('LockUserContractDetailComponent', () => {
  let component: LockUserContractDetailComponent;
  let fixture: ComponentFixture<LockUserContractDetailComponent>;

  function createSignedContractBuffer(options?: {
    serialNumber?: number;
    terms?: string;
    isTempUnlockAllowed?: boolean;
    isEmergencyUnlockAllowed?: boolean;
    bots?: Array<{
      name: string;
      publicKeyBytes: Uint8Array;
      receiveEvents?: boolean;
      canUnlock?: boolean;
      canRelease?: boolean;
      canLock?: boolean;
    }>;
  }): string {
    const builder = new flatbuffers.Builder(1024);
    const termsStr = builder.createString(options?.terms ?? 'Standard contract terms');

    let botsVectorOffset: flatbuffers.Offset | null = null;
    if (options?.bots && options.bots.length > 0) {
      const botOffsets: flatbuffers.Offset[] = [];
      for (const b of options.bots) {
        Permission.startPermission(builder);
        Permission.addReceiveEvents(builder, b.receiveEvents ?? false);
        Permission.addCanUnlock(builder, b.canUnlock ?? false);
        Permission.addCanRelease(builder, b.canRelease ?? false);
        Permission.addCanLock(builder, b.canLock ?? false);
        const permOffset = Permission.endPermission(builder);

        const nameOffset = builder.createString(b.name);
        const keyOffset = builder.createByteVector(b.publicKeyBytes);

        FBBot.startBot(builder);
        FBBot.addName(builder, nameOffset);
        FBBot.addPublicKey(builder, keyOffset);
        FBBot.addPermissions(builder, permOffset);
        botOffsets.push(FBBot.endBot(builder));
      }
      botsVectorOffset = Contract.createBotsVector(builder, botOffsets);
    }

    Contract.startContract(builder);
    Contract.addSerialNumber(builder, options?.serialNumber ?? 1234);
    Contract.addTerms(builder, termsStr);
    Contract.addIsTemporaryUnlockAllowed(builder, options?.isTempUnlockAllowed ?? false);
    if (options?.isEmergencyUnlockAllowed !== undefined) {
      Contract.addIsEmergencyUnlockAllowed(builder, options.isEmergencyUnlockAllowed);
    }
    if (botsVectorOffset !== null) {
      Contract.addBots(builder, botsVectorOffset);
    }
    const contractOffset = Contract.endContract(builder);

    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, contractOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.Contract);
    const signedMsgOffset = SignedMessage.endSignedMessage(builder);
    builder.finish(signedMsgOffset);

    return btoa(String.fromCharCode(...builder.asUint8Array()));
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockUserContractDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockUserContractDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should parse contract without bots and not populate bots', () => {
    const rawBody = createSignedContractBuffer({
      terms: 'No bots here',
      isTempUnlockAllowed: true
    });
    component.contract = new ContractModel({
      name: 'contract-no-bots',
      state: 'CREATED',
      body: rawBody
    });

    component.parseContract();

    expect(component.contractDescription).toBeDefined();
    expect(component.contractDescription?.terms).toBe('No bots here');
    expect(component.contractDescription?.isTemporaryUnlockAllowed).toBe(true);
    expect(component.contractDescription?.isEmergencyUnlockAllowed).toBe(true);
    expect(component.contractDescription?.bots).toEqual([]);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.bot-card')).toBeNull();
    expect(compiled.textContent).not.toContain('Attached Bots');
    expect(compiled.textContent).toContain('Emergency unlock allowed: true');
  });

  it('should parse contract with emergency unlock disabled', () => {
    const rawBody = createSignedContractBuffer({
      terms: 'Disabled emergency unlock terms',
      isTempUnlockAllowed: false,
      isEmergencyUnlockAllowed: false
    });
    component.contract = new ContractModel({
      name: 'contract-no-emergency',
      state: 'CREATED',
      body: rawBody
    });

    component.parseContract();

    expect(component.contractDescription).toBeDefined();
    expect(component.contractDescription?.isEmergencyUnlockAllowed).toBe(false);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Emergency unlock allowed: false');
  });

  it('should parse contract with bots, permissions, and public key', () => {
    const botKey = new Uint8Array([10, 20, 30, 40]);
    const rawBody = createSignedContractBuffer({
      terms: 'Contract with Butler bot',
      isTempUnlockAllowed: false,
      bots: [
        {
          name: 'Butler Bot',
          publicKeyBytes: botKey,
          receiveEvents: true,
          canUnlock: true,
          canRelease: true,
          canLock: false
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'contract-with-bot',
      state: 'CREATED',
      body: rawBody
    });

    component.parseContract();

    expect(component.contractDescription?.bots?.length).toBe(1);
    const bot = component.contractDescription?.bots?.[0];
    expect(bot?.name).toBe('Butler Bot');
    expect(bot?.publicKey).toBe(btoa(String.fromCharCode(...botKey)));
    expect(bot?.permissions?.canUnlock).toBe(true);
    expect(bot?.permissions?.canRelease).toBe(true);
    expect(bot?.permissions?.receiveEvents).toBe(true);
    expect(bot?.permissions?.canLock).toBe(false);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Attached Bots');
    expect(compiled.textContent).toContain('Butler Bot');
    expect(compiled.textContent).toContain('Can Unlock');
    expect(compiled.textContent).toContain('Can Release');
    expect(compiled.textContent).toContain('Receive Events');
    expect(compiled.textContent).not.toContain('Can Lock');
  });

  it('should enrich contract bots with coordinator metadata (description and createdAt)', () => {
    component.availableBots = [
      new Bot({
        name: 'Guard Bot',
        description: 'Automated guardian for timed releases',
        createdAt: '2026-04-10T12:00:00Z',
        publicKey: btoa(String.fromCharCode(1, 2, 3))
      })
    ];

    const rawBody = createSignedContractBuffer({
      bots: [
        {
          name: 'Guard Bot',
          publicKeyBytes: new Uint8Array([1, 2, 3]),
          receiveEvents: true,
          canUnlock: false,
          canRelease: true
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'contract-guard',
      state: 'CREATED',
      body: rawBody
    });

    component.parseContract();

    const bot = component.contractDescription?.bots?.[0];
    expect(bot?.name).toBe('Guard Bot');
    expect(bot?.description).toBe('Automated guardian for timed releases');
    expect(bot?.createdAt).toBe('2026-04-10T12:00:00Z');

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Automated guardian for timed releases');
  });

  it('should enrich bots after fetchBots if contract was parsed beforehand', () => {
    const rawBody = createSignedContractBuffer({
      bots: [
        {
          name: 'Enrich Later Bot',
          publicKeyBytes: new Uint8Array([5, 6, 7]),
          receiveEvents: false,
          canUnlock: true,
          canRelease: false
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'contract-enrich-later',
      state: 'CREATED',
      body: rawBody
    });

    component.parseContract();
    expect(component.contractDescription?.bots?.[0].description).toBeUndefined();

    // Now availableBots arrives from coordinator
    component.availableBots = [
      new Bot({
        name: 'Enrich Later Bot',
        description: 'Delayed arrival metadata',
        createdAt: '2026-05-01T08:30:00Z'
      })
    ];
    component.enrichBotsWithCoordinatorData();

    expect(component.contractDescription?.bots?.[0].description).toBe('Delayed arrival metadata');
    expect(component.contractDescription?.bots?.[0].createdAt).toBe('2026-05-01T08:30:00Z');

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Delayed arrival metadata');
  });

  it('should copy bot public key to clipboard when copyKey is called', async () => {
    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy
      }
    });

    const stopPropagationSpy = vi.fn();
    const fakeEvent = { stopPropagation: stopPropagationSpy } as unknown as Event;

    await component.copyKey('test-key-123', 'My Bot', fakeEvent);

    expect(stopPropagationSpy).toHaveBeenCalled();
    expect(writeTextSpy).toHaveBeenCalledWith('test-key-123');
    expect(component.copiedBotName).toBe('My Bot');
  });
});
