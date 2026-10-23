import { Component, ElementRef, Input, ViewChild, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { Command } from '../models/command';
import { DatePipe } from '@angular/common';
import * as QRCode from 'qrcode';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { SerialDeviceService } from '../serial-device.service';
import { ToastService } from '../toast.service';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective, type BadgeVariants } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmSpinnerComponent } from '../shared/ui/ui-spinner-helm';

@Component({
  selector: 'app-command-card',
  imports: [
    DatePipe,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmSpinnerComponent,
  ],
  templateUrl: './command-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './command-card.component.scss'
})
export class CommandCardComponent {
  @Input()
  command?: Command;

  @Input() basePath: string = '';

  showQrCodeCommands = false;
  isSendingSerial = false;
  serialError: string | null = null;

  @ViewChild('ackBtn') ackBtn!: ElementRef<HTMLButtonElement>;

  get typeLabel(): string {
    switch (this.command?.type) {
      case 'ACCEPT_CONTRACT':
        return 'Accept Contract';
      case 'LOCK':
        return 'Lock Mechanism';
      case 'UNLOCK':
        return 'Unlock Mechanism';
      case 'TEMPORARY_UNLOCK':
        return 'Temporary Unlock';
      case 'RELEASE':
        return 'Release Contract';
      default:
        return this.command?.type || 'Command';
    }
  }

  get badgeVariant(): BadgeVariants['variant'] {
    switch (this.command?.state) {
      case 'PENDING':
        return 'warning';
      case 'ACKNOWLEDGED':
        return 'success';
      case 'ERROR':
        return 'destructive';
      default:
        return 'secondary';
    }
  }

  get accentBorderClass(): string {
    switch (this.command?.state) {
      case 'PENDING':
        return 'border-l-4 border-l-warning';
      case 'ACKNOWLEDGED':
        return 'border-l-4 border-l-success';
      case 'ERROR':
        return 'border-l-4 border-l-destructive';
      default:
        return 'border-l-4 border-l-muted-foreground/40';
    }
  }

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService,
    private serialDeviceService: SerialDeviceService,
    private toastService: ToastService,
    private cdr: ChangeDetectorRef
  ) {
    this.showQrCodeCommands = this.userDataService.hasLockUserSession();
  }

  drawQrCode(event: Event): void {
    const button = event.currentTarget as HTMLElement;
    const container = button.parentElement;
    if (!container) return;

    if (this.command?.body != null) {
      console.log(this.getBase64Body(this.command.body));
    }

    QRCode.toCanvas(this.command!.body!, { errorCorrectionLevel: 'L' }, (err, canvas) => {
      if (err) throw err;
      container.appendChild(canvas);
      button.remove();
      this.maybeShowAcknowledge();
    });
  }

  async sendViaSerial(): Promise<void> {
    if (!this.command?.body) {
      this.toastService.showError('No command payload to send.');
      return;
    }

    this.isSendingSerial = true;
    this.serialError = null;
    this.cdr.markForCheck();

    try {
      const payload = this.getBase64Body(this.command.body);
      const result = await this.serialDeviceService.sendCommand(payload);

      if (result.type === 'ack') {
        this.tartarusCoordinatorService.manuallyAcknowledgeCommand(
          this.command.contractName!,
          this.command.name!,
          result.resultBase64
        ).subscribe({
          next: (r) => {
            this.command = r;
            this.toastService.showSuccess('Lock acknowledged command!');
            this.isSendingSerial = false;
            this.cdr.markForCheck();
          },
          error: (err) => {
            const msg = err?.message || String(err);
            this.serialError = 'Coordinator failed to accept acknowledgement: ' + msg;
            this.toastService.showError(this.serialError);
            this.isSendingSerial = false;
            this.cdr.markForCheck();
          }
        });
      } else {
        this.serialError = result.errorMessage || 'Device rejected command';
        this.toastService.showError('Device error: ' + this.serialError);
        this.isSendingSerial = false;
        this.cdr.markForCheck();
      }
    } catch (err: any) {
      const msg = err?.message || String(err);
      if (!msg.toLowerCase().includes('no port selected') && !msg.toLowerCase().includes('user cancelled')) {
        this.serialError = 'Serial error: ' + msg;
        this.toastService.showError(this.serialError);
      }
      this.isSendingSerial = false;
      this.cdr.markForCheck();
    }
  }

  private getBase64Body(body: string): string {
    try {
      if (btoa(atob(body)) === body) {
        return body;
      }
    } catch {
      // not valid base64
    }
    try {
      return btoa(body);
    } catch {
      return body;
    }
  }

  maybeShowAcknowledge() {
    if (this.command?.type === 'ACCEPT_CONTRACT') {
      return;
    }

    if (this.ackBtn) {
      this.ackBtn.nativeElement.hidden = false;
    }
  }

  manuallyAcknowledge(_event?: Event): void {
    if (!this.command?.contractName || !this.command?.name) {
      return;
    }
    this.tartarusCoordinatorService.manuallyAcknowledgeCommand(this.command.contractName, this.command.name).subscribe({
      next: (r) => {
        this.command = r;
        this.toastService.showSuccess('Command marked as acknowledged');
        this.cdr.markForCheck();
      },
      error: () => {
        this.toastService.showError('Failed to acknowledge command');
      }
    });
  }
}
