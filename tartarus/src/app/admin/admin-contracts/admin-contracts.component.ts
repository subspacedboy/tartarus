import {Component, OnInit, QueryList, ViewChildren} from '@angular/core';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {Contract} from '../../models/contract';
import {ContractCardComponent} from '../../contract-card/contract-card.component';
import {NgbdSortableHeader, SortEvent} from '../../sortable.directive';
import {AsyncPipe, DatePipe} from '@angular/common';
import {Observable} from 'rxjs';
import {RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';


@Component({
  selector: 'app-admin-contracts',
  imports: [
    ContractCardComponent,
    NgbdSortableHeader,
    AsyncPipe,
    RouterLink,
    FormsModule,
    DatePipe
  ],
  templateUrl: './admin-contracts.component.html',
  styleUrl: './admin-contracts.component.scss'
})
export class AdminContractsComponent implements OnInit {
  contracts : Observable<Contract[]> | undefined;

  selectedStatesMap: Record<string, boolean> = {
    CONFIRMED: true,
    ABORTED: false,
    RELEASED: false
  };

  @ViewChildren(NgbdSortableHeader) headers: QueryList<NgbdSortableHeader> | undefined;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService) {
  }

  ngOnInit() {
    this.fetchContracts();
  }

  onSort({ column, direction }: SortEvent) {
    // resetting other headers
    // @ts-ignore
    this.headers.forEach((header) => {
      if (header.sortable !== column) {
        header.direction = '';
      }
    });

    // TODO: Actually implement sorting :-)

    // this.service.sortColumn = column;
    // this.service.sortDirection = direction;
  }

  fetchContracts() {
    const states = Object.keys(this.selectedStatesMap)
      .filter(state => this.selectedStatesMap[state]);

    this.contracts = this.tartarusCoordinatorService.getLiveContractsForAdmin(states);
  }
}
