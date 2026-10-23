import { Component, Inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UserDataService } from '../user-data.service';
import { AppComponent } from '../app.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { Contract } from '../models/contract';
import { KnownToken } from '../models/known-token';
import { LockSession } from '../models/lock-session';
import { ContractCardComponent } from '../contract-card/contract-card.component';
import { DateAgoPipe } from '../date-ago.pipe';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';

@Component({
  selector: 'app-landing-page',
  imports: [
    RouterLink,
    ContractCardComponent,
    DateAgoPipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
  ],
  templateUrl: './landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './landing-page.component.scss'
})
export class LandingPageComponent implements OnInit {
  confirmedContracts: Contract[] = [];
  knownTokens: KnownToken[] = [];

  reviewPending: Contract[] = [];
  myLockSession?: LockSession;
  myLockContracts: Contract[] = [];

  constructor(
    @Inject(AppComponent) public parent: AppComponent,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService,
  ) {}

  ngOnInit() {
    if (this.userDataService.hasAuthorSession()) {
      this.tartarusCoordinatorService.getConfirmedContractsForAuthor()?.subscribe(contracts => {
        this.confirmedContracts = contracts;
      });
      this.tartarusCoordinatorService.getKnownTokensForAuthor()?.subscribe(tokens => {
        this.knownTokens = tokens;
      });
    }

    if (this.userDataService.hasLockUserSession()) {
      this.tartarusCoordinatorService.getContractsForLockSessionForReview()?.subscribe(contracts => {
        this.reviewPending = contracts;
      });
      this.tartarusCoordinatorService.getMyLockSession()?.subscribe(session => {
        this.myLockSession = session;
      });
      this.tartarusCoordinatorService.getContractsForLockSession()?.subscribe(contracts => {
        this.myLockContracts = contracts;
      });
    }
  }

  get activeLockContract(): Contract | undefined {
    return this.myLockContracts.find(c => c.state === 'CONFIRMED' || c.state === 'ACCEPTED');
  }
}
