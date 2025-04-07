import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';
import {ConfigService} from '../config.service';
import {ContractCardComponent} from '../contract-card/contract-card.component';
import {Contract} from '../models/contract';
import {ToastService} from '../toast.service';
import {DatePipe} from "@angular/common";
import {DateAgoPipe} from "../date-ago.pipe";

@Component({
  selector: 'app-my-lock',
  imports: [
    ContractCardComponent,
    DatePipe,
    DateAgoPipe
  ],
  templateUrl: './my-lock.component.html',
  styleUrl: './my-lock.component.scss'
})
export class MyLockComponent implements OnInit {
  lockSession? : LockSession;
  contracts? : Contract[];

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,
              private configService: ConfigService,
              private toastService: ToastService) {
  }

  ngOnInit(): void {
    // TODO: Make this safer.
    const sessionToken = this.userDataService.getLockSessions()[0];
    this.tartarusCoordinatorService.getMyLockSession(sessionToken).subscribe(lockSession => {
      this.lockSession = lockSession;
    });

    this.tartarusCoordinatorService.getContractsForLockSession().subscribe(contracts => {
      this.contracts = contracts;
    });

  }

  getShareLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.shareToken}`;
  }

  getTotalControlLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.totalControlToken}`;
  }

  copyToClipboard(incoming_data : string) {
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
    } catch(error) {
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
