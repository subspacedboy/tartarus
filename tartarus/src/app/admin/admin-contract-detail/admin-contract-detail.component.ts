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
import * as flatbuffers from 'flatbuffers';
import {SignedMessage} from '../../club/subjugated/fb/message/signed-message';
import {MessagePayload} from '../../club/subjugated/fb/message/message-payload';
import {Contract as FBContract} from '../../club/subjugated/fb/message/contract';
import {ContractDescription} from '../../models/contract-description';

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
  contractDescription?: ContractDescription;

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

      this.parseContract();

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

    // There's really only the one type.
    let endDescription= "When I Say So 😈";

    let bots = [];
    for (let i = 0; i < contract.botsLength(); i++) {
      const bot = contract.bots(i)!;
      const permissions = bot.permissions()!;

      bots.push({
        name: bot.name(),
        publicKey: btoa(String.fromCharCode(...bot.publicKeyArray())),
        permissions: {
          receiveEvents: permissions.receiveEvents(),
          canUnlock: permissions.canUnlock(),
          canRelease: permissions.canRelease()
        }
      });
    }

    console.log(bots);

    this.contractDescription = new ContractDescription(isTemporaryUnlockAllowed, contract.terms(), endDescription, bots);
  }

  protected readonly JSON = JSON;
}
