import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {ActivatedRoute} from '@angular/router';
import {UserDataService} from '../user-data.service';
import {CryptoService} from '../crypto.service';
import {Contract} from '../models/contract';
import * as flatbuffers from 'flatbuffers';
import {SignedMessage} from '../club/subjugated/fb/message/signed-message';
import {MessagePayload} from '../club/subjugated/fb/message/message-payload';
import {Contract as FBContract} from '../club/subjugated/fb/message/contract';
import {ContractDescription} from '../models/contract-description';
import {WebsocketService} from '../websocket.service';
import {Command} from '../models/command';
import {CommandCardComponent} from '../command-card/command-card.component';

@Component({
  selector: 'app-lock-user-contract-detail',
  imports: [
    CommandCardComponent
  ],
  templateUrl: './lock-user-contract-detail.component.html',
  styleUrl: './lock-user-contract-detail.component.scss'
})
export class LockUserContractDetailComponent implements OnInit {
  contractName: string;
  contract?: Contract;

  commands: Command[];

  contractDescription?: ContractDescription;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,
              private cryptoService: CryptoService,
              private websocketService: WebsocketService) {

    this.commands = [];
    this.contractName = String(this.activatedRoute.snapshot.paramMap.get('contractName'));

    this.websocketService.onMessage().subscribe({
      next: (message) => this.handleMessage(message),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  ngOnInit() {
    this.refreshData()
  }

  refreshData() {
    this.tartarusCoordinatorService.getContractByNameForLockUser(this.contractName).subscribe(contract => {
      this.contract = contract;
      this.parseContract();

      this.tartarusCoordinatorService.getCommandsForContractForLockUser(this.contractName).subscribe(commands => {
        this.commands = commands;
      })
    });
  }

  handleMessage(message : string) {
    console.log("Specific contract subscription -> Message: "+ message);
    this.refreshData();
  }

  approve() {
    this.tartarusCoordinatorService.approveContract(this.contractName).subscribe(contract => {
      this.contract = contract;
    })
  }

  reject() {
    this.tartarusCoordinatorService.rejectContract(this.contractName).subscribe(contract => {
      this.contract = contract;
    })
  }

  parseContract() {
    const buffer = new Uint8Array([...atob(this.contract!.body!)].map(c => c.charCodeAt(0)));
    const byteBuffer = new flatbuffers.ByteBuffer(buffer);

    const signedMessage = SignedMessage.getRootAsSignedMessage(byteBuffer);
    const payloadType = signedMessage.payloadType();
    if (payloadType !== MessagePayload.Contract) {
      throw new Error("Unexpected payload type, expected Contract");
    }
    const contract = signedMessage.payload(new FBContract());
    if (!contract) {
      throw new Error("Failed to parse Contract from SignedMessage payload");
    }

    const serialNumber = contract.serialNumber();
    const publicKey = contract.publicKeyArray(); // Uint8Array
    const isTemporaryUnlockAllowed = contract.isTemporaryUnlockAllowed();

    let endDescription= "When I Say So 😈";

    this.contractDescription = new ContractDescription(isTemporaryUnlockAllowed, contract.terms(), endDescription);
  }
}
