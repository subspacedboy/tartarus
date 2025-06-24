import {Component, ElementRef, Input, ViewChild} from '@angular/core';
import {Contract} from '../models/contract';
import {Command} from '../models/command';
import {DatePipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {SignedMessage} from '../club/subjugated/fb/message/signed-message';

import * as flatbuffers from 'flatbuffers';

@Component({
  selector: 'app-command-card',
  imports: [
    DatePipe,
    RouterLink
  ],
  templateUrl: './command-card.component.html',
  styleUrl: './command-card.component.scss'
})
export class CommandCardComponent {
  @Input()
  command?: Command;

  @Input() basePath: string = '';

  showQrCodeCommands = false;

  @ViewChild('ackBtn') ackBtn!: ElementRef<HTMLButtonElement>;

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService
  ) {
    this.showQrCodeCommands = this.userDataService.hasLockUserSession()
  }

  drawQrCode(event: Event): void {
    const button = event.currentTarget as HTMLElement;
    const container = button.parentElement;
    if (!container) return;

    // const text = new TextDecoder().decode(uint8Array);
    QRCode.toCanvas(this.command!.body!, { errorCorrectionLevel: 'L' }, (err, canvas) => {
      if (err) throw err;
      container.appendChild(canvas);
      button.remove();
      this.ackBtn.nativeElement.hidden = false;
    });
  }

  manuallyAcknowledge(event: Event): void {
    this.tartarusCoordinatorService.manuallyAcknowledgeCommand(this.command?.contractName!, this.command?.name!).subscribe(r => {
      this.command = r;
    });
  }
}
