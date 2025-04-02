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
import {EndCondition} from '../club/subjugated/fb/message/end-condition';
import {TimeEndCondition} from '../club/subjugated/fb/message/time-end-condition';
import {WhenISaySo} from '../club/subjugated/fb/message/when-isay-so';

@Component({
  selector: 'app-lock-user-contract-detail',
  imports: [],
  templateUrl: './lock-user-contract-detail.component.html',
  styleUrl: './lock-user-contract-detail.component.scss'
})
export class LockUserContractDetailComponent implements OnInit {
  contractName: string;
  contract?: Contract;

  contractDescription?: ContractDescription;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,
              private cryptoService: CryptoService) {

    this.contractName = String(this.activatedRoute.snapshot.paramMap.get('contractName'));
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getContractByName(this.contractName).subscribe(contract => {
      this.contract = contract;
      this.parseContract();
    });
  }

  approve() {
    this.tartarusCoordinatorService.approveContract(this.contractName).subscribe(contract => {
      console.log("Approved");
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

    let endConditionType = contract.endConditionType();
    let endCondition;
    let endDescription;
    switch (endConditionType) {
      case EndCondition.TimeEndCondition:
        endCondition = contract.endCondition(new TimeEndCondition()) as TimeEndCondition;
        endDescription = "Time based";
        break;
      case EndCondition.WhenISaySo:
        endCondition = contract.endCondition(new WhenISaySo()) as WhenISaySo;
        endDescription = "When I Say So 😈";
        break;
      default:
        throw new Error("Unknown end condition type");
    }

    // contract.end
    const confirmCode = contract.confirmCodeArray(); // Uint8Array
    const nonce = contract.nonceArray(); // Uint8Array

    this.contractDescription = new ContractDescription(isTemporaryUnlockAllowed, endDescription);
  }
}
