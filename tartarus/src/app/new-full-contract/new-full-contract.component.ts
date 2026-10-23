import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { IdHelperService } from '../id-helper.service';
import * as flatbuffers from 'flatbuffers';
import { SignedMessage } from '../club/subjugated/fb/message/signed-message';
import { MessagePayload } from '../club/subjugated/fb/message/message-payload';
import { CryptoService } from '../crypto.service';
import { Contract } from '../club/subjugated/fb/message/contract';
import { UserDataService } from '../user-data.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LockSession } from '../models/lock-session';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ToastService } from '../toast.service';
import { Permission } from '../club/subjugated/fb/message/permission';
import { Bot as FBBot } from '../club/subjugated/fb/message/bot';
import { Bot } from '../models/bot';

import { BotSelectorComponent } from '../bot-selector/bot-selector.component';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-new-full-contract',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    BotSelectorComponent,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './new-full-contract.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './new-full-contract.component.scss'
})
export class NewFullContractComponent implements OnInit {
  lockSessionToken: string = '';
  lockSession?: LockSession;

  contractForm: FormGroup;
  bots: Bot[] = [];
  isSubmitting = false;

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private idHelperService: IdHelperService,
    private cryptoService: CryptoService,
    private userDataService: UserDataService,
    private activatedRoute: ActivatedRoute,
    private fb: FormBuilder,
    private toastService: ToastService,
    private router: Router,
  ) {
    this.lockSessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
    this.bots = [];

    this.contractForm = this.fb.group({
      notes: new FormControl('', []),
      terms: new FormControl('', []),
      bots: this.fb.array([]),
      isTempUnlockAllowed: new FormControl(false),
      isEmergencyUnlockAllowed: new FormControl(true),
    });
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.lockSessionToken).subscribe(result => {
      this.lockSession = result;
    });
    this.tartarusCoordinatorService.getBots().subscribe(result => {
      this.bots = result;
    });
  }

  get selectedBots(): FormArray {
    return this.contractForm.get('bots') as FormArray;
  }

  get selectedBotGroups(): FormGroup[] {
    return this.selectedBots.controls as FormGroup[];
  }

  get selectedBotNames(): string[] {
    return this.selectedBots.controls.map(control => control.value.name);
  }

  get sessionLabel(): string {
    if (this.lockSession?.knownToken?.notes?.trim()) {
      return this.lockSession.knownToken.notes.trim();
    }
    return this.lockSessionToken || 'Lock Session';
  }

  get isEmergencyUnlockAllowed(): boolean {
    return !!this.contractForm.get('isEmergencyUnlockAllowed')?.value;
  }

  get isTempUnlockAllowed(): boolean {
    return !!this.contractForm.get('isTempUnlockAllowed')?.value;
  }

  get termsLength(): number {
    return (this.contractForm.get('terms')?.value || '').length;
  }

  getBotDescription(botName: string): string | undefined {
    return this.bots.find(b => b.name === botName)?.description;
  }

  addBot(item: Bot, id?: number): void {
    const itemExists = this.selectedBots.controls.some(control => control.value.name === item.name);
    if (!itemExists) {
      const botIndex = id ?? this.bots.findIndex(b => b.name === item.name);
      const group = this.fb.group({
        id: botIndex,
        name: item.name,
        receiveEvents: true,
        canLock: true,
        canUnlock: true,
        canRelease: true,
      });
      this.selectedBots.push(group);
    }
  }

  removeBot(index: number): void {
    this.selectedBots.removeAt(index);
  }

  removeBotByName(botName: string): void {
    const index = this.selectedBots.controls.findIndex(c => c.value.name === botName);
    if (index !== -1) {
      this.selectedBots.removeAt(index);
    }
  }

  async createContract(): Promise<Uint8Array> {
    if (this.isSubmitting) {
      return new Uint8Array();
    }
    this.isSubmitting = true;

    try {
      const builder = new flatbuffers.Builder(1024);

      const { privatePem, publicPem } = this.userDataService.getAuthorKeypair();
      const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));

      const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(ecdsKeys.publicKey);
      const publicKeyOffset = builder.createByteVector(compressedPublicKey);

      const termsStringOffset = builder.createString(this.contractForm.get('terms')!.value ?? '');

      const bots = [];
      for (const b of this.selectedBots.controls) {
        const actualBot = this.bots.find(bot => bot.name == b.value['name'])!;

        const botNameOffset = builder.createString(actualBot.name);
        const keyBytes = Uint8Array.from(atob(actualBot.publicKey!), c => c.codePointAt(0)!);
        const keyBytesOffset = builder.createByteVector(keyBytes);

        const permissionOffset = Permission.createPermission(
          builder,
          !!b.value['receiveEvents'],
          !!b.value['canUnlock'],
          !!b.value['canLock'],
          !!b.value['canRelease'],
        );

        FBBot.startBot(builder);
        FBBot.addName(builder, botNameOffset);
        FBBot.addPublicKey(builder, keyBytesOffset);
        FBBot.addPermissions(builder, permissionOffset);
        const bot1Offset = FBBot.endBot(builder);
        bots.push(bot1Offset);
      }

      const botsVector = Contract.createBotsVector(builder, bots);

      const serialNumber = Math.floor(Math.random() * 65536);
      Contract.startContract(builder);
      Contract.addSerialNumber(builder, serialNumber);
      Contract.addPublicKey(builder, publicKeyOffset);
      Contract.addIsTemporaryUnlockAllowed(builder, !!this.contractForm.get('isTempUnlockAllowed')!.value);
      Contract.addIsEmergencyUnlockAllowed(builder, !!this.contractForm.get('isEmergencyUnlockAllowed')!.value);
      Contract.addBots(builder, botsVector);
      Contract.addTerms(builder, termsStringOffset);
      const fullContractOffset = Contract.endContract(builder);
      builder.finish(fullContractOffset);

      const contractBytes = builder.asUint8Array();
      const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
      const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
      const vTableStart = offsetToTable - offsetToVTable;
      const vtableAndContract = contractBytes.slice(vTableStart);

      const signature = await this.cryptoService.hashAndSignData(ecdsKeys.privateKey, vtableAndContract);
      const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

      SignedMessage.startSignedMessage(builder);
      SignedMessage.addPayload(builder, fullContractOffset);
      SignedMessage.addPayloadType(builder, MessagePayload.Contract);
      SignedMessage.addSignature(builder, signatureOffset);
      const signedMessageOffset = SignedMessage.endSignedMessage(builder);

      builder.finish(signedMessageOffset);

      const signedBytes = builder.asUint8Array();

      this.tartarusCoordinatorService.saveContract(
        this.userDataService.getAuthorName(),
        this.lockSessionToken,
        signedBytes,
        this.contractForm.get('notes')!.value ?? '',
      ).subscribe({
        next: (r) => {
          this.isSubmitting = false;
          this.toastService.showSuccess('Saved');
          this.router.navigate(['lock-sessions', this.lockSessionToken, 'contract', r.name]);
        },
        error: () => {
          this.isSubmitting = false;
          this.toastService.showError('Failed to save contract');
        },
      });

      return signedBytes;
    } catch (error) {
      this.isSubmitting = false;
      this.toastService.showError('Failed to sign contract');
      throw error;
    }
  }
}
