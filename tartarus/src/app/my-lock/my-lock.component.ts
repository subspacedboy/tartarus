import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';

@Component({
  selector: 'app-my-lock',
  imports: [],
  templateUrl: './my-lock.component.html',
  styleUrl: './my-lock.component.scss'
})
export class MyLockComponent implements OnInit {
  lockSession? : LockSession;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService) {
  }

  ngOnInit(): void {
    // TODO: Make this safer.
    const sessionToken = this.userDataService.getLockSessions()[0];
    this.tartarusCoordinatorService.getMyLockSession(sessionToken).subscribe(lockSession => {
      this.lockSession = lockSession;
    });
  }
}
