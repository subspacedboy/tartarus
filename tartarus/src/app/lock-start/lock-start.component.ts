import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';

@Component({
  selector: 'app-lock-start',
  imports: [],
  templateUrl: './lock-start.component.html',
  styleUrl: './lock-start.component.scss'
})
export class LockStartComponent implements OnInit {
  session: string | null = null;
  lockSession? : LockSession;
  publicKey: string | null = null;
  showDetails: boolean = false;

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,) {
    this.activatedRoute.queryParamMap.subscribe(params => {
      this.session = params.get('session');
      this.publicKey = params.get('public');
    });
  }

  ngOnInit() {
    this.tartarusCoordinatorService.saveLockPubKeyAndSession(String(this.publicKey), String(this.session)).subscribe(lockSession => {
      this.lockSession = lockSession;
      this.userDataService.addLockSession(String(this.session), String(this.publicKey));
    });
  }

  showTechnicalDetails() {
    this.showDetails = true;
  }
}
