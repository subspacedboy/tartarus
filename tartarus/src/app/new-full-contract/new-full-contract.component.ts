import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {IdHelperService} from '../id-helper.service';
import * as flatbuffers from 'flatbuffers';
import {SignedMessage} from '../club/subjugated/fb/message/signed-message';
import {MessagePayload} from '../club/subjugated/fb/message/message-payload';
import {CryptoService} from '../crypto.service';
import {QrcodeService} from '../qrcode.service';
import {Contract} from '../club/subjugated/fb/message/contract';
import {UserDataService} from '../user-data.service';
import {ActivatedRoute, Router} from '@angular/router';
import {LockSession} from '../models/lock-session';
import * as QRCode from 'qrcode';
import {FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {ToastService} from '../toast.service';
import {Permission} from '../club/subjugated/fb/message/permission';
import {Bot as FBBot} from '../club/subjugated/fb/message/bot';
import {Bot} from '../models/bot';

@Component({
  selector: 'app-new-full-contract',
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './new-full-contract.component.html',
  styleUrl: './new-full-contract.component.scss'
})
export class NewFullContractComponent implements OnInit {
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  lockSessionToken: string = '';
  lockSession?: LockSession;

  contractForm: FormGroup;

  bots: Bot[] = [];

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private idHelperService: IdHelperService,
              private cryptoService: CryptoService,
              private qrcodeService: QrcodeService,
              private userDataService: UserDataService,
              private activatedRoute: ActivatedRoute,
              private fb: FormBuilder,
              private toastService: ToastService,
              private router: Router,) {
    this.lockSessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
    this.bots = [];

    this.contractForm = this.fb.group({
      notes: new FormControl('', []),
      terms: new FormControl('', []),
      bots: this.fb.array([]),
      isTempUnlockAllowed: new FormControl(false),
    });
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.lockSessionToken).subscribe(result => {
      this.lockSession = result;
    });
    this.tartarusCoordinatorService.getBots().subscribe(result => {
      this.bots = result;
    })
  }

  get selectedBots(): FormArray {
    return this.contractForm.get('bots') as FormArray;
  }

  addBot(item: Bot, id : number): void {
    const itemExists = this.selectedBots.controls.some(control => control.value.name === item.name);
    if (!itemExists) {
      const group = this.fb.group({
        id: id,
        name: item.name
      });
      this.selectedBots.push(group);
    }
  }

  removeBot(index: number): void {
    this.selectedBots.removeAt(index);
  }

  async createContract(): Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const {privatePem, publicPem} = this.userDataService.getAuthorKeypair();
    const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));
    const ecdhPrivateKey = await this.cryptoService.importPrivateKeyForECDH(String(privatePem));

    const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(ecdsKeys.publicKey);
    // Create a public key string
    const publicKeyOffset = builder.createByteVector(compressedPublicKey);

    const termsStringOffset = builder.createString(this.contractForm.get('terms')!.value!)

    // const sessionOffset = builder.createString(this.lockSession?.shareToken!);

    Permission.startPermission(builder);
    Permission.addReceiveEvents(builder, true);
    Permission.addCanUnlock(builder, true);
    Permission.addCanRelease(builder, true);
    const permissionOffset = Permission.endPermission(builder);

    const bots = [];
    for (const b of this.selectedBots.controls) {
      const actualBot = this.bots.find(bot => bot.name == b.value['name'])!;

      const botNameOffset = builder.createString(actualBot.name);
      const keyBytes = Uint8Array.from(atob(actualBot.publicKey!), c => c.codePointAt(0)!)
      const keyBytesOffset = builder.createByteVector(keyBytes);
      FBBot.startBot(builder);
      FBBot.addName(builder, botNameOffset);
      FBBot.addPublicKey(builder, keyBytesOffset);
      FBBot.addPermissions(builder, permissionOffset);
      const bot1Offset = FBBot.endBot(builder);
      bots.push(bot1Offset);
    }

    const botsVector = Contract.createBotsVector(builder, bots);

    // Derive secrets.
    // const lockPub = await this.cryptoService.importKeyPair(this.lockSession?.public_key!);
    let pubKey = await this.cryptoService.importPublicKeyOnlyFromPem(this.lockSession?.publicPem!);
    // const shared = await this.cryptoService.deriveSharedSecret(ecdhPrivateKey, pubKey);
    // const aesKey = await this.cryptoService.deriveAESKey(shared, new Uint8Array(), new Uint8Array());
    // const cipher = await this.cryptoService.initializeAESCipher(aesKey);
    // const cipherText = await cipher.encrypt("687");
    // const nonceOffset = builder.createByteVector(cipher.iv);
    // const confirmCodeOffset = builder.createByteVector(new Uint8Array(cipherText));

    const serialNumber = Math.floor(Math.random() * 65536);
    Contract.startContract(builder);
    Contract.addSerialNumber(builder, serialNumber);
    Contract.addPublicKey(builder, publicKeyOffset);
    Contract.addIsTemporaryUnlockAllowed(builder, this.contractForm.get('isTempUnlockAllowed')!.value!);
    Contract.addBots(builder, botsVector);
    Contract.addTerms(builder, termsStringOffset);
    let fullContractOffset = Contract.endContract(builder);
    builder.finish(fullContractOffset);

    const contractBytes = builder.asUint8Array();
    const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
    const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
    const vTableStart = offsetToTable - offsetToVTable;
    const vtableAndContract = contractBytes.slice(vTableStart);

    const signature = await this.cryptoService.hashAndSignData(ecdsKeys.privateKey, vtableAndContract);

    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    // @ts-ignore
    console.log("Length of signature " + signature.byteLength);
    console.log("Signature: " + new Uint8Array(signature));
    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, fullContractOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.Contract);
    SignedMessage.addSignature(builder, signatureOffset);
    const signedMessageOffset = SignedMessage.endSignedMessage(builder);

    builder.finish(signedMessageOffset);

    console.log(builder.asUint8Array().length);
    console.log(builder.asUint8Array());

    if(builder.asUint8Array().length > 254) {
      // throw new Error("Contract is too large");
      console.log("!!! Contract is too large for QR code!!!");
    }

    await this.qrcodeService.generateQRCode(builder.asUint8Array(), this.canvas);
    this.hiddenDiv.nativeElement.hidden = false;

    this.tartarusCoordinatorService.saveContract(
      this.userDataService.getAuthorName(),
      this.lockSessionToken,
      builder.asUint8Array(),
      this.contractForm.get('notes')!.value!).subscribe(r => {
        this.toastService.showSuccess("Saved");
        this.router.navigate(['lock-sessions',this.lockSessionToken, 'contract', r.name]);
    })

    return builder.asUint8Array();
  }
}
