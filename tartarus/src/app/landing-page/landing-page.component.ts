import {Component, Inject, OnInit} from '@angular/core';
import {RouterLink} from '@angular/router';
import {UserDataService} from '../user-data.service';
import {AppComponent} from '../app.component';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {Contract} from '../models/contract';
import {ContractCardComponent} from '../contract-card/contract-card.component';

@Component({
  selector: 'app-landing-page',
  imports: [
    RouterLink,
    ContractCardComponent
  ],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss'
})
export class LandingPageComponent implements OnInit {
  confirmedContracts: Contract[];

  reviewPending: Contract[];

  constructor(@Inject(AppComponent) public parent: AppComponent,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService) {
    this.confirmedContracts = [];
    this.reviewPending = [];
  }

  ngOnInit() {
    if(this.userDataService.hasAuthorSession()) {
      // const authorName = this.userDataService.getAuthorName();
      this.tartarusCoordinatorService.getConfirmedContractsForAuthor().subscribe(contracts => {
        this.confirmedContracts = contracts;
      });
    }

    if (this.userDataService.hasLockUserSession()) {
      this.tartarusCoordinatorService.getContractsForLockSessionForReview().subscribe(contracts => {
        this.reviewPending = contracts;
      })
    }
  }
}
