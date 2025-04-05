import {Component, OnInit} from '@angular/core';
import {Contract} from '../../models/contract';
import {Command} from '../../models/command';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {ActivatedRoute} from '@angular/router';
import {UserDataService} from '../../user-data.service';
import {CryptoService} from '../../crypto.service';
import {WebsocketService} from '../../websocket.service';
import {ToastService} from '../../toast.service';
import {CommandCardComponent} from '../../command-card/command-card.component';

@Component({
  selector: 'app-admin-contract-detail',
  imports: [
    CommandCardComponent
  ],
  templateUrl: './admin-contract-detail.component.html',
  styleUrl: './admin-contract-detail.component.scss'
})
export class AdminContractDetailComponent implements OnInit {
  contractName: string;
  contract?: Contract;

  commands : Command[];

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,
              private cryptoService: CryptoService,
              private websocketService: WebsocketService,
              private toastService: ToastService,) {
    this.contractName = String(this.activatedRoute.snapshot.paramMap.get('contractName'));
    this.commands = [];
  }

  ngOnInit() {
    this.refreshData();
  }

  refreshData()  {
    this.tartarusCoordinatorService.getContractByNameForAdmin(this.contractName).subscribe(contract => {
      this.contract = contract;

      this.tartarusCoordinatorService.getCommandsForContractForAdmin(this.contractName).subscribe(commands => {
        this.commands = commands;
      })
    });
  }

  abort() {
    this.tartarusCoordinatorService.abortContract(this.contractName).subscribe(aborted => {
      this.refreshData();
    });
  }

  reset() {
    this.tartarusCoordinatorService.fullReset(this.contractName).subscribe(aborted => {
      this.refreshData();
    });
  }
}
