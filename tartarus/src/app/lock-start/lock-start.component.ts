import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';

@Component({
  selector: 'app-lock-start',
  imports: [],
  templateUrl: './lock-start.component.html',
  styleUrl: './lock-start.component.scss'
})
export class LockStartComponent implements OnInit {
  session: string | null = null;
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
    this.tartarusCoordinatorService.saveLockPubKeyAndSession(String(this.publicKey), String(this.session)).subscribe(r => {
      this.userDataService.addLockSession(String(this.session), String(this.publicKey));
    });
  }

  getSessionLink() : string {
    return `http://localhost:4200/lock_session/${this.session}`;
  }

  showTechnicalDetails() {
    this.showDetails = true;
  }
}
