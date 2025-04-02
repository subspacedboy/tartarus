import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';
import {Contract} from '../models/contract';
import {ContractCardComponent} from '../contract-card/contract-card.component';

@Component({
  selector: 'app-lock-session-detail',
  imports: [
    RouterLink,
    ContractCardComponent
  ],
  templateUrl: './lock-session-detail.component.html',
  styleUrl: './lock-session-detail.component.scss'
})
export class LockSessionDetailComponent implements OnInit {
  sessionToken: string = '';
  lockSession? : LockSession;
  contracts?: Contract[];

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService
  ) {
    this.sessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.sessionToken).subscribe(result => {
      this.lockSession = result;
    });

    this.tartarusCoordinatorService.getContractsForShareable(this.sessionToken).subscribe(result => {
      this.contracts = result;
    })
  }
}
