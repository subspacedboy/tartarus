import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as flatbuffers from 'flatbuffers';
import { ContractDetailComponent } from './contract-detail.component';
import { SignedMessage } from '../club/subjugated/fb/message/signed-message';
import { MessagePayload } from '../club/subjugated/fb/message/message-payload';
import { Contract } from '../club/subjugated/fb/message/contract';
import { Bot as FBBot } from '../club/subjugated/fb/message/bot';
import { Permission } from '../club/subjugated/fb/message/permission';
import { Contract as ContractModel } from '../models/contract';
import { Bot } from '../models/bot';

describe('ContractDetailComponent', () => {
  let component: ContractDetailComponent;
  let fixture: ComponentFixture<ContractDetailComponent>;

  function createSignedContractBuffer(options?: {
    serialNumber?: number;
    terms?: string;
    isTempUnlockAllowed?: boolean;
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
      imports: [ContractDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ContractDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should parse contract without bots and not display bots section', () => {
    const rawBody = createSignedContractBuffer({
      terms: 'Author terms with no bots',
      isTempUnlockAllowed: true
    });
    component.contract = new ContractModel({
      name: 'author-contract-no-bots',
      state: 'CONFIRMED',
      body: rawBody
    });

    component.parseContract();

    expect(component.contractDescription).toBeDefined();
    expect(component.contractDescription?.terms).toBe('Author terms with no bots');
    expect(component.contractDescription?.isTemporaryUnlockAllowed).toBe(true);
    expect(component.contractDescription?.bots).toEqual([]);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.bot-card')).toBeNull();
    expect(compiled.textContent).not.toContain('Attached Bots');
  });

  it('should parse contract with bots, permissions, and public key', () => {
    const botKey = new Uint8Array([55, 66, 77, 88]);
    const rawBody = createSignedContractBuffer({
      terms: 'Author contract with Sentry Bot',
      isTempUnlockAllowed: false,
      bots: [
        {
          name: 'Sentry Bot',
          publicKeyBytes: botKey,
          receiveEvents: true,
          canUnlock: true,
          canRelease: true,
          canLock: false
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'author-contract-with-bot',
      state: 'CONFIRMED',
      body: rawBody
    });

    component.parseContract();

    expect(component.contractDescription?.bots?.length).toBe(1);
    const bot = component.contractDescription?.bots?.[0];
    expect(bot?.name).toBe('Sentry Bot');
    expect(bot?.publicKey).toBe(btoa(String.fromCharCode(...botKey)));
    expect(bot?.permissions?.canUnlock).toBe(true);
    expect(bot?.permissions?.canRelease).toBe(true);
    expect(bot?.permissions?.receiveEvents).toBe(true);
    expect(bot?.permissions?.canLock).toBe(false);

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Attached Bots');
    expect(compiled.textContent).toContain('Sentry Bot');
    expect(compiled.textContent).toContain('Can Unlock');
    expect(compiled.textContent).toContain('Can Release');
    expect(compiled.textContent).toContain('Receive Events');
    expect(compiled.textContent).not.toContain('Can Lock');
  });

  it('should enrich contract bots with coordinator metadata (description and createdAt)', () => {
    component.availableBots = [
      new Bot({
        name: 'Overseer Bot',
        description: 'Automated overseer bot description',
        createdAt: '2026-02-15T10:00:00Z',
        publicKey: btoa(String.fromCharCode(9, 8, 7))
      })
    ];

    const rawBody = createSignedContractBuffer({
      bots: [
        {
          name: 'Overseer Bot',
          publicKeyBytes: new Uint8Array([9, 8, 7]),
          receiveEvents: true,
          canUnlock: true,
          canRelease: false
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'contract-overseer',
      state: 'CONFIRMED',
      body: rawBody
    });

    component.parseContract();

    const bot = component.contractDescription?.bots?.[0];
    expect(bot?.name).toBe('Overseer Bot');
    expect(bot?.description).toBe('Automated overseer bot description');
    expect(bot?.createdAt).toBe('2026-02-15T10:00:00Z');

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Automated overseer bot description');
  });

  it('should enrich bots after fetchBots if contract was parsed beforehand', () => {
    const rawBody = createSignedContractBuffer({
      bots: [
        {
          name: 'Delayed Bot',
          publicKeyBytes: new Uint8Array([3, 4, 5]),
          receiveEvents: true,
          canUnlock: true,
          canRelease: true
        }
      ]
    });

    component.contract = new ContractModel({
      name: 'contract-delayed',
      state: 'CONFIRMED',
      body: rawBody
    });

    component.parseContract();
    expect(component.contractDescription?.bots?.[0].description).toBeUndefined();

    component.availableBots = [
      new Bot({
        name: 'Delayed Bot',
        description: 'Metadata that arrived after contract was parsed',
        createdAt: '2026-06-01T09:00:00Z'
      })
    ];
    component.enrichBotsWithCoordinatorData();

    expect(component.contractDescription?.bots?.[0].description).toBe('Metadata that arrived after contract was parsed');
    expect(component.contractDescription?.bots?.[0].createdAt).toBe('2026-06-01T09:00:00Z');

    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Metadata that arrived after contract was parsed');
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

    await component.copyKey('author-bot-pubkey', 'Test Bot', fakeEvent);

    expect(stopPropagationSpy).toHaveBeenCalled();
    expect(writeTextSpy).toHaveBeenCalledWith('author-bot-pubkey');
    expect(component.copiedBotName).toBe('Test Bot');
  });
});
