import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { LockSession } from '../models/lock-session';
import { ConfigService } from '../config.service';
import { ContractCardComponent } from '../contract-card/contract-card.component';
import { CommandCardComponent } from '../command-card/command-card.component';
import { Contract } from '../models/contract';
import { Command } from '../models/command';
import { ToastService } from '../toast.service';
import { WebsocketService } from '../websocket.service';
import { DateAgoPipe } from "../date-ago.pipe";
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-my-lock',
  imports: [
    RouterLink,
    ContractCardComponent,
    CommandCardComponent,
    DateAgoPipe,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './my-lock.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './my-lock.component.scss'
})
export class MyLockComponent implements OnInit {
  lockSession?: LockSession;
  contracts?: Contract[];
  activeContractCommands: Command[] = [];

  inviteLinkMode: 'reviewable' | 'total-control' = 'reviewable';
  showAcknowledgedCommands = false;
  showHistory = false;

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService,
    private configService: ConfigService,
    private toastService: ToastService,
    private websocketService: WebsocketService,
  ) {}

  ngOnInit(): void {
    this.websocketService.connect();
    this.refreshData();

    this.websocketService.onMessage().subscribe({
      next: () => this.refreshData(),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  refreshData(): void {
    this.tartarusCoordinatorService.getMyLockSession().subscribe(lockSession => {
      this.lockSession = lockSession;
    });

    this.tartarusCoordinatorService.getContractsForLockSession().subscribe(contracts => {
      this.contracts = contracts;
      const active = this.activeContract;
      if (active?.name) {
        this.tartarusCoordinatorService.getCommandsForContractForLockUser(active.name).subscribe({
          next: (commands) => {
            this.activeContractCommands = commands;
          },
          error: () => {
            this.activeContractCommands = [];
          }
        });
      } else {
        this.activeContractCommands = [];
      }
    });
  }

  get activeContract(): Contract | undefined {
    return this.contracts?.find(c => c.state === 'CONFIRMED' || c.state === 'ACCEPTED');
  }

  get pendingProposals(): Contract[] {
    return this.contracts?.filter(c => c.state === 'CREATED') ?? [];
  }

  get historicalContracts(): Contract[] {
    return this.contracts?.filter(
      c => c.state !== 'CONFIRMED' && c.state !== 'ACCEPTED' && c.state !== 'CREATED'
    ) ?? [];
  }

  get pendingCommands(): Command[] {
    return this.activeContractCommands.filter(c => c.state === 'PENDING' || c.state === 'ERROR');
  }

  get acknowledgedCommands(): Command[] {
    return this.activeContractCommands.filter(c => c.state === 'ACKNOWLEDGED');
  }

  setInviteLinkMode(mode: 'reviewable' | 'total-control'): void {
    this.inviteLinkMode = mode;
  }

  getShareLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.shareToken}`;
  }

  getTotalControlLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.totalControlToken}`;
  }

  copyToClipboard(incoming_data: string) {
    try {
      const type = "text/plain";
      const blob = new Blob([incoming_data], { type });
      const data = [new ClipboardItem({ [type]: blob })];

      navigator.clipboard.write(data).then(
        () => {
          /* success */
          this.toastService.showSuccess("Copied to clipboard");
        },
        () => {
          this.toastService.showSuccess("Copy to clipboard failed...");
          /* failure */
        },
      );
    } catch (error) {
      navigator.clipboard.writeText(incoming_data).then(
        () => {
          this.toastService.showSuccess("Copied to clipboard");
        },
        () => {
          this.toastService.showSuccess("Copy to clipboard failed...");
        },
      );
    }
  }
}
