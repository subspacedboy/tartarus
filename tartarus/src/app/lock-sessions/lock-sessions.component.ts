import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {KnownToken} from '../models/known-token';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    RouterLink
  ],
  templateUrl: './lock-sessions.component.html',
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  knownTokens?: KnownToken[];

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              ) {
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getKnownTokensForAuthor().subscribe(knownTokens => {
      this.knownTokens = knownTokens;
    });
  }
}
