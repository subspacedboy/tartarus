import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import * as flatbuffers from 'flatbuffers';
import {Contract} from '../subjugated/club/contract';
import {Capabilities} from '../subjugated/club/capabilities';
import {SignedMessage} from '../subjugated/club/signed-message';
import {MessagePayload, unionToMessagePayload} from '../subjugated/club/message-payload';
import jsQR from 'jsqr';
import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {IdHelperService} from '../id-helper.service';
import {EndCondition, PartialContract, WhenISaySo} from '../subjugated/club';
import {CryptoService} from '../crypto.service';
import {ActivatedRoute} from '@angular/router';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';

@Component({
  selector: 'app-new-simple-contract',
  imports: [],
  templateUrl: './new-simple-contract.component.html',
  styleUrl: './new-simple-contract.component.scss'
})
export class NewSimpleContractComponent implements OnInit {
  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;

  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  lockSessionToken: string = '';
  lockSession?: LockSession;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private idHelperService: IdHelperService,
              private cryptoService: CryptoService,
              private activatedRoute: ActivatedRoute,
              private userDataService: UserDataService,) {

    this.lockSessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.lockSessionToken).subscribe(result => {
      console.log(result);
      this.lockSession = result;
    });
  }

  async createContract(): Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const {privatePem, publicPem} = this.userDataService.getAuthorKeypair();
    const keys = await this.cryptoService.importKeyPair(String(privatePem) + String(publicPem));

    const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(keys.publicKey);
    // Create a public key string
    const publicKeyOffset = builder.createByteVector(compressedPublicKey);

    WhenISaySo.startWhenISaySo(builder);
    const whenISaySoOffset = WhenISaySo.endWhenISaySo(builder);

    const notesOffset = builder.createString("Notes!");
    const sessionOffset = builder.createString(this.lockSession?.session!);

    Contract.startContract(builder);
    Contract.addPublicKey(builder, publicKeyOffset);
    Contract.addIsLockOnAccept(builder, true);
    Contract.addIsTemporaryUnlockAllowed(builder, false);
    Contract.addEndCondition(builder, whenISaySoOffset);
    Contract.addEndConditionType(builder, EndCondition.WhenISaySo);
    Contract.addIsUnremovable(builder, true);
    Contract.addSession(builder, sessionOffset);
    Contract.addNotes(builder, notesOffset);
    let fullContractOffset = Contract.endContract(builder);
    builder.finish(fullContractOffset);

    const contractName = this.idHelperService.generateBase32Id()
    // console.log(builder.asUint8Array().length);

    const contractBytes = builder.asUint8Array();
    const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
    const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
    const vTableStart = offsetToTable - offsetToVTable;

    const vtableAndContract = contractBytes.slice(vTableStart);

    const signature = await this.cryptoService.hashAndSignData(keys.privateKey, vtableAndContract);

    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    // const payloadOffset = unionToMessagePayload(MessagePayload.Contract, fullContractOffset);
    // MessagePayload.startMessagePayload(builder);
    // MessagePayload.addType(builder, MessagePayload.Contract); // Set correct type
    // MessagePayload.addTable(builder, contractOffset);
    // const payloadOffset = MessagePayload.endMessagePayload(builder);

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
      throw new Error("Contract is too large");
    }

    await this.generateQRCode(builder.asUint8Array());

    this.tartarusCoordinatorService.saveContract(contractName, builder.asUint8Array()).subscribe(r => {
      console.log("Saved");
    })

    return builder.asUint8Array();
  }

  async generateQRCode( data : Uint8Array) {
    const canvas = this.canvas.nativeElement;
    const qrSize = 256;
    const textHeight = 30; // Space for text
    canvas.width = qrSize;
    canvas.height = qrSize + textHeight;

    try {
      // Generate QR Code on a temporary canvas
      const tempCanvas = document.createElement('canvas');
      tempCanvas.width = qrSize;
      tempCanvas.height = qrSize;
      await QRCode.toCanvas(tempCanvas,
        [{data : data, mode: 'byte'}],
        { width: qrSize, margin: 2, errorCorrectionLevel: 'L' });

      const ctx = canvas.getContext('2d');
      // Clear canvas and draw QR code onto the main canvas
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(tempCanvas, 0, 0);

      this.hiddenDiv.nativeElement.hidden = false;
    } catch (err) {
      console.error('Error generating QR Code:', err);
    }
  }
}
