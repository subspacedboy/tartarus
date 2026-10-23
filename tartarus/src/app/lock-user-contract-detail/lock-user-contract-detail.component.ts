import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UserDataService } from '../user-data.service';
import { CryptoService } from '../crypto.service';
import { Contract } from '../models/contract';
import * as flatbuffers from 'flatbuffers';
import { SignedMessage } from '../club/subjugated/fb/message/signed-message';
import { MessagePayload } from '../club/subjugated/fb/message/message-payload';
import { Contract as FBContract } from '../club/subjugated/fb/message/contract';
import { ContractDescription, ContractBot } from '../models/contract-description';
import { WebsocketService } from '../websocket.service';
import { Command } from '../models/command';
import { CommandCardComponent } from '../command-card/command-card.component';
import { Bot } from '../models/bot';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmTooltipTriggerDirective } from '../shared/ui/ui-tooltip-helm';

@Component({
  selector: 'app-lock-user-contract-detail',
  imports: [
    RouterLink,
    CommandCardComponent,
    DatePipe,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmTooltipTriggerDirective,
  ],
  templateUrl: './lock-user-contract-detail.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-user-contract-detail.component.scss'
})
export class LockUserContractDetailComponent implements OnInit {
  contractName: string;
  contract?: Contract;

  commands: Command[];

  contractDescription?: ContractDescription;
  availableBots: Bot[] = [];
  copiedBotName: string | null = null;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,
              private cryptoService: CryptoService,
              private websocketService: WebsocketService) {

    this.commands = [];
    this.contractName = String(this.activatedRoute.snapshot.paramMap.get('contractName'));

    this.websocketService.onMessage().subscribe({
      next: (message) => this.handleMessage(message),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  ngOnInit() {
    this.websocketService.connect();
    this.refreshData();
    this.fetchBots();
  }

  fetchBots() {
    this.tartarusCoordinatorService.getBots().subscribe({
      next: (bots) => {
        this.availableBots = bots;
        this.enrichBotsWithCoordinatorData();
      },
      error: (err) => {
        console.warn('Could not fetch bots metadata', err);
      }
    });
  }

  enrichBotsWithCoordinatorData() {
    if (this.contractDescription?.bots && this.availableBots.length > 0) {
      for (const bot of this.contractDescription.bots) {
        const matched = this.availableBots.find(b => b.name === bot.name);
        if (matched) {
          if (!bot.description && matched.description) {
            bot.description = matched.description;
          }
          if (!bot.createdAt && matched.createdAt) {
            bot.createdAt = matched.createdAt;
          }
        }
      }
    }
  }

  refreshData() {
    this.tartarusCoordinatorService.getContractByNameForLockUser(this.contractName).subscribe(contract => {
      this.contract = contract;
      this.parseContract();

      this.tartarusCoordinatorService.getCommandsForContractForLockUser(this.contractName).subscribe(commands => {
        this.commands = commands;
      })
    });
  }

  handleMessage(message : string) {
    console.log("Specific contract subscription -> Message: "+ message);
    this.refreshData();
  }

  approve() {
    this.tartarusCoordinatorService.approveContract(this.contractName).subscribe(contract => {
      this.contract = contract;
    })
  }

  reject() {
    this.tartarusCoordinatorService.rejectContract(this.contractName).subscribe(contract => {
      this.contract = contract;
    })
  }

  async copyKey(key?: string, botName?: string, event?: Event): Promise<void> {
    if (event) {
      event.stopPropagation();
    }
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key);
      this.copiedBotName = botName || null;
      setTimeout(() => {
        if (this.copiedBotName === botName) {
          this.copiedBotName = null;
        }
      }, 2000);
    } catch {
      // Fallback or ignore if clipboard is not accessible
    }
  }

  parseContract() {
    if (!this.contract?.body) {
      return;
    }
    const buffer = new Uint8Array([...atob(this.contract.body)].map(c => c.charCodeAt(0)));
    const byteBuffer = new flatbuffers.ByteBuffer(buffer);

    const signedMessage = SignedMessage.getRootAsSignedMessage(byteBuffer);
    const payloadType = signedMessage.payloadType();
    if (payloadType !== MessagePayload.Contract) {
      throw new Error("Unexpected payload type, expected Contract");
    }
    const contract = signedMessage.payload(new FBContract());
    if (!contract) {
      throw new Error("Failed to parse Contract from SignedMessage payload");
    }

    const serialNumber = contract.serialNumber();
    const publicKey = contract.publicKeyArray(); // Uint8Array
    const isTemporaryUnlockAllowed = contract.isTemporaryUnlockAllowed();
    const isEmergencyUnlockAllowed = contract.isEmergencyUnlockAllowed();

    let endDescription= "When I Say So 😈";

    const bots: ContractBot[] = [];
    const botsLength = contract.botsLength();
    for (let i = 0; i < botsLength; i++) {
      const bot = contract.bots(i);
      if (!bot) continue;

      const botName = bot.name() ?? '';
      const pubKeyArr = bot.publicKeyArray();
      const pubKeyBase64 = pubKeyArr ? btoa(String.fromCharCode(...pubKeyArr)) : '';
      const permissions = bot.permissions();

      const matched = this.availableBots.find(b => b.name === botName);

      bots.push({
        name: botName,
        publicKey: pubKeyBase64,
        description: matched?.description,
        createdAt: matched?.createdAt,
        permissions: permissions ? {
          receiveEvents: permissions.receiveEvents(),
          canUnlock: permissions.canUnlock(),
          canLock: permissions.canLock(),
          canRelease: permissions.canRelease()
        } : undefined
      });
    }

    this.contractDescription = new ContractDescription(
      isTemporaryUnlockAllowed,
      contract.terms() ?? '',
      endDescription,
      bots,
      isEmergencyUnlockAllowed
    );
  }
}

