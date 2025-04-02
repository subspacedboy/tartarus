import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    RouterLink
  ],
  templateUrl: './lock-sessions.component.html',
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  shareableTokens?: (string | undefined)[];

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,
              ) {
    // this.shareableTokens = userDataService.getLockSessionTokenViews();
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getKnownTokensForAuthor().subscribe(knownTokens => {
      this.shareableTokens = knownTokens.map(t => t.shareableToken);
    });
  }
}
