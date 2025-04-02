import {Component, ElementRef, ViewChild} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {IdHelperService} from '../id-helper.service';
import * as flatbuffers from 'flatbuffers';
import {SignedMessage} from '../club/subjugated/fb/message/signed-message';
import {MessagePayload} from '../club/subjugated/fb/message/message-payload';
import {CryptoService} from '../crypto.service';
import {QrcodeService} from '../qrcode.service';
import {EndCondition} from '../club/subjugated/fb/message/end-condition';
import {WhenISaySo} from '../club/subjugated/fb/message/when-isay-so';
import {Contract} from '../club/subjugated/fb/message/contract';
import {UserDataService} from '../user-data.service';
import {ActivatedRoute} from '@angular/router';
import {LockSession} from '../models/lock-session';
import * as QRCode from 'qrcode';

@Component({
  selector: 'app-new-full-contract',
  imports: [],
  templateUrl: './new-full-contract.component.html',
  styleUrl: './new-full-contract.component.scss'
})
export class NewFullContractComponent {
  // @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  //
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  keyMessage = "";
  privateKey : CryptoKey | null = null;
  publicKey : CryptoKey | null = null;

  lockSessionToken: string = '';
  lockSession?: LockSession;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private idHelperService: IdHelperService,
              private cryptoService: CryptoService,
              private qrcodeService: QrcodeService,
              private userDataService: UserDataService,
              private activatedRoute: ActivatedRoute,) {

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
    const ecdsKeys = await this.cryptoService.importKeyPairForECDSA(String(privatePem) + String(publicPem));
    const ecdhPrivateKey = await this.cryptoService.importPrivateKeyForECDH(String(privatePem));

    const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(ecdsKeys.publicKey);
    // Create a public key string
    const publicKeyOffset = builder.createByteVector(compressedPublicKey);

    WhenISaySo.startWhenISaySo(builder);
    const whenISaySoOffset = WhenISaySo.endWhenISaySo(builder);

    const sessionOffset = builder.createString(this.lockSession?.shareToken!);

    // Derive secrets.
    // const lockPub = await this.cryptoService.importKeyPair(this.lockSession?.public_key!);
    let pubKey = await this.cryptoService.importPublicKeyOnlyFromPem(this.lockSession?.publicPem!);
    const shared = await this.cryptoService.deriveSharedSecret(ecdhPrivateKey, pubKey);
    const aesKey = await this.cryptoService.deriveAESKey(shared, new Uint8Array(), new Uint8Array());
    const cipher = await this.cryptoService.initializeAESCipher(aesKey);
    const cipherText = await cipher.encrypt("687");
    const nonceOffset = builder.createByteVector(cipher.iv);
    const confirmCodeOffset = builder.createByteVector(new Uint8Array(cipherText));

    const serialNumber = Math.floor(Math.random() * 65536);
    Contract.startContract(builder);
    Contract.addSerialNumber(builder, serialNumber);
    Contract.addPublicKey(builder, publicKeyOffset);
    Contract.addIsTemporaryUnlockAllowed(builder, false);
    Contract.addEndCondition(builder, whenISaySoOffset);
    Contract.addEndConditionType(builder, EndCondition.WhenISaySo);
    //Contractact.addSession(builder, sessionOffset);
    Contract.addConfirmCode(builder, confirmCodeOffset);
    Contract.addNonce(builder, nonceOffset);
    let fullContractOffset = Contract.endContract(builder);
    builder.finish(fullContractOffset);

    const contractName = this.idHelperService.generateBase32Id()

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
      throw new Error("Contract is too large");
    }

    await this.generateQRCode(builder.asUint8Array());

    this.tartarusCoordinatorService.saveContract(this.userDataService.getAuthorName(), this.lockSessionToken, builder.asUint8Array()).subscribe(r => {
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
