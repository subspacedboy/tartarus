import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {Contract} from '../../models/contract';
import {ContractCardComponent} from '../../contract-card/contract-card.component';

@Component({
  selector: 'app-admin-contracts',
  imports: [
    ContractCardComponent
  ],
  templateUrl: './admin-contracts.component.html',
  styleUrl: './admin-contracts.component.scss'
})
export class AdminContractsComponent implements OnInit {
  liveContracts : Contract[];

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService) {
    this.liveContracts = [];
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLiveContractsForAdmin().subscribe(contracts => {
      this.liveContracts = contracts;
    });
  }

}
