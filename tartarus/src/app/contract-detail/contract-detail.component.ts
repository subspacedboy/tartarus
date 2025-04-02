import {Component, OnInit} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {Contract} from '../models/contract';
import {ActivatedRoute} from '@angular/router';
import {UserDataService} from '../user-data.service';
import * as flatbuffers from 'flatbuffers';
import {CryptoService} from '../crypto.service';
import {UnlockCommand} from '../club/subjugated/fb/message/unlock-command';
import {SignedMessage} from '../club/subjugated/fb/message/signed-message';
import {MessagePayload} from '../club/subjugated/fb/message/message-payload';
import {LockCommand} from '../club/subjugated/fb/message/lock-command';
import {ReleaseCommand} from '../club/subjugated/fb/message/release-command';

@Component({
  selector: 'app-contract-detail',
  imports: [],
  templateUrl: './contract-detail.component.html',
  styleUrl: './contract-detail.component.scss'
})
export class ContractDetailComponent implements OnInit {
  contractName: string;
  contract?: Contract;
  lockSessionShareableToken? : string;

  constructor(private tartarusCoordinatorService: TartarusCoordinatorService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,
              private cryptoService: CryptoService,) {
    this.contractName = String(this.activatedRoute.snapshot.paramMap.get('contractName'));
    this.lockSessionShareableToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getContractByName(this.contractName).subscribe(contract => {
      this.contract = contract;
    });
  }

  unlock() {
    const authorName = this.userDataService.getAuthorName();
    this.makeUnlockMessage().then(unlockSignedMessage => {
      this.tartarusCoordinatorService.saveCommand(this.contractName, authorName, this.lockSessionShareableToken!, unlockSignedMessage).subscribe(r => {
        console.log("Unlock code saved");
      });
    });
  }

  lock() {
    const authorName = this.userDataService.getAuthorName();
    this.makeLockMessage().then(lockSignedMessage => {
      this.tartarusCoordinatorService.saveCommand(this.contractName, authorName, this.lockSessionShareableToken!, lockSignedMessage).subscribe(r => {
        console.log("Lock code saved");
      });
    });
  }

  release() {
    const authorName = this.userDataService.getAuthorName();
    this.makeReleaseMessage().then(releaseSignedMessage => {
      this.tartarusCoordinatorService.saveCommand(this.contractName, authorName, this.lockSessionShareableToken!, releaseSignedMessage).subscribe(r => {
        console.log("Release code saved");
      });
    });
  }

  async makeUnlockMessage() : Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const {privatePem, publicPem} = this.userDataService.getAuthorKeypair();
    const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));

    const serialNumber = Math.floor(Math.random() * 65536);

    UnlockCommand.startUnlockCommand(builder);
    UnlockCommand.addCounter(builder, this.contract!.nextCounter!);
    UnlockCommand.addContractSerialNumber(builder, this.contract!.serialNumber!);
    UnlockCommand.addSerialNumber(builder, serialNumber);
    const unlockOffset = UnlockCommand.endUnlockCommand(builder);
    builder.finish(unlockOffset);

    const signature = await this.cryptoService.hashAndSignFb(ecdsKeys.privateKey, builder.asUint8Array())
    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, unlockOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.UnlockCommand);
    SignedMessage.addSignature(builder, signatureOffset);
    const signedMessageOffset = SignedMessage.endSignedMessage(builder);
    builder.finish(signedMessageOffset);

    const finishedSignedMessageBytes = builder.asUint8Array();
    console.log(finishedSignedMessageBytes);
    return finishedSignedMessageBytes;
  }

  async makeLockMessage() : Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const {privatePem, publicPem} = this.userDataService.getAuthorKeypair();
    const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));

    const serialNumber = Math.floor(Math.random() * 65536);

    LockCommand.startLockCommand(builder);
    LockCommand.addCounter(builder, this.contract!.nextCounter!);
    LockCommand.addContractSerialNumber(builder, this.contract!.serialNumber!);
    LockCommand.addSerialNumber(builder, serialNumber);
    const lockOffset = LockCommand.endLockCommand(builder);
    builder.finish(lockOffset);

    const signature = await this.cryptoService.hashAndSignFb(ecdsKeys.privateKey, builder.asUint8Array())
    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, lockOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.LockCommand);
    SignedMessage.addSignature(builder, signatureOffset);
    const signedMessageOffset = SignedMessage.endSignedMessage(builder);
    builder.finish(signedMessageOffset);

    const finishedSignedMessageBytes = builder.asUint8Array();
    console.log(finishedSignedMessageBytes);
    return finishedSignedMessageBytes;
  }

  async makeReleaseMessage() : Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const {privatePem, publicPem} = this.userDataService.getAuthorKeypair();
    const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));

    const serialNumber = Math.floor(Math.random() * 65536);

    ReleaseCommand.startReleaseCommand(builder);
    ReleaseCommand.addCounter(builder, this.contract!.nextCounter!);
    ReleaseCommand.addContractSerialNumber(builder, this.contract!.serialNumber!);
    ReleaseCommand.addSerialNumber(builder, serialNumber);
    const releaseOffset = ReleaseCommand.endReleaseCommand(builder);
    builder.finish(releaseOffset);

    const signature = await this.cryptoService.hashAndSignFb(ecdsKeys.privateKey, builder.asUint8Array())
    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, releaseOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.ReleaseCommand);
    SignedMessage.addSignature(builder, signatureOffset);
    const signedMessageOffset = SignedMessage.endSignedMessage(builder);
    builder.finish(signedMessageOffset);

    const finishedSignedMessageBytes = builder.asUint8Array();
    console.log(finishedSignedMessageBytes);
    return finishedSignedMessageBytes;
  }
}
