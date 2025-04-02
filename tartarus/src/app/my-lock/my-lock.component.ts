import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';
import {ConfigService} from '../config.service';

@Component({
  selector: 'app-my-lock',
  imports: [],
  templateUrl: './my-lock.component.html',
  styleUrl: './my-lock.component.scss'
})
export class MyLockComponent implements OnInit {
  lockSession? : LockSession;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,
              private configService: ConfigService,) {
  }

  ngOnInit(): void {
    // TODO: Make this safer.
    const sessionToken = this.userDataService.getLockSessions()[0];
    this.tartarusCoordinatorService.getMyLockSession(sessionToken).subscribe(lockSession => {
      this.lockSession = lockSession;
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
}
