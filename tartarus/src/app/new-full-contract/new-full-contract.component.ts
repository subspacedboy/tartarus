import {Component, ElementRef, ViewChild} from '@angular/core';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {IdHelperService} from '../id-helper.service';
import * as flatbuffers from 'flatbuffers';
import {PartialContract} from '../subjugated/club/partial-contract';
import {SignedMessage} from '../subjugated/club/signed-message';
import {MessagePayload} from '../subjugated/club/message-payload';
import {CryptoService} from '../crypto.service';
import {QrcodeService} from '../qrcode.service';
import {Capabilities, Contract, EndCondition, WhenISaySo} from '../subjugated/club';

@Component({
  selector: 'app-new-full-contract',
  imports: [],
  templateUrl: './new-full-contract.component.html',
  styleUrl: './new-full-contract.component.scss'
})
export class NewFullContractComponent {
  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;

  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  keyMessage = "";
  privateKey : CryptoKey | null = null;
  publicKey : CryptoKey | null = null;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private idHelperService: IdHelperService,
              private cryptoService: CryptoService,
              private qrcodeService: QrcodeService) {
  }

  ngOnInit() {
  }

  async createContract(): Promise<Uint8Array> {
    // Build the full contract first
    const builder = new flatbuffers.Builder(1024);
    const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(this.publicKey!);
    const contractName = this.idHelperService.generateBase32Id()

    // Create a public key string
    const publicKeyOffset = builder.createByteVector(compressedPublicKey);
    // Create capabilities array
    // This has to be a multiple of 4.
    // Contract.startCapabilitiesVector(builder, 4);
    // builder.addInt8(Capabilities.Online);
    // builder.addInt8(Capabilities.Time);
    // builder.addInt8(0);
    // builder.addInt8(0);
    // const capabilitiesOffset = builder.endVector();

    const endCondition = EndCondition.WhenISaySo;
    WhenISaySo.startWhenISaySo(builder);
    const whenISaySoOffset = WhenISaySo.endWhenISaySo(builder);

    Contract.startContract(builder);
    Contract.addPublicKey(builder, publicKeyOffset);
    // Contract.addCapabilities(builder, capabilitiesOffset);
    // Contract.addIsLockOnAccept(builder, true);
    Contract.addIsTemporaryUnlockAllowed(builder, true);
    Contract.addEndCondition(builder, whenISaySoOffset);
    Contract.addEndConditionType(builder, EndCondition.WhenISaySo);
    let fullContractOffset = Contract.endContract(builder);
    builder.finish(fullContractOffset);

    const contractBytes = builder.asUint8Array();
    const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
    const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
    const vTableStart = offsetToTable - offsetToVTable;
    const vtableAndContract = contractBytes.slice(vTableStart);

    const signature = await this.cryptoService.hashAndSignData(this.privateKey!, vtableAndContract);

    // const partialLocationOffset = builder.createString(`http://192.168.1.168:5200/contract/${contractName}`)

    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));
    //
    //
    // // Start creating the Contract
    // PartialContract.startPartialContract(builder);
    // PartialContract.addPublicKey(builder, publicKeyOffset);
    // // Contract.addCapabilities(builder, capabilitiesOffset);
    // PartialContract.addCompleteContractAddress(
    //   builder,
    //   partialLocationOffset
    // )
    // const contractOffset = PartialContract.endPartialContract(builder);

    // builder.finish(contractOffset);
    // console.log(builder.asUint8Array().length);
    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, fullContractOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.Contract);
    SignedMessage.addSignature(builder, signatureOffset);
    const signedMessageOffset = SignedMessage.endSignedMessage(builder);
    builder.finish(signedMessageOffset);

    this.tartarusCoordinatorService.saveContract(contractName, builder.asUint8Array()).subscribe(r => {
      console.log("Saved");
    })

    // const contractBytes = builder.asUint8Array();
    // const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
    // const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
    // const vTableStart = offsetToTable - offsetToVTable;

    // const vtableAndContract = contractBytes.slice(vTableStart);
    //
    // const signature = await this.cryptoService.signData(this.privateKey!, vtableAndContract);



    // const payloadOffset = unionToMessagePayload(MessagePayload.Contract, contractOffset);
    // MessagePayload.startMessagePayload(builder);
    // MessagePayload.addType(builder, MessagePayload.Contract); // Set correct type
    // MessagePayload.addTable(builder, contractOffset);
    // const payloadOffset = MessagePayload.endMessagePayload(builder);

    // @ts-ignore
    // console.log("Length of signature " + signature.byteLength);
    // console.log("Signature: " + new Uint8Array(signature));
    // SignedMessage.startSignedMessage(builder);
    // SignedMessage.addPayload(builder, contractOffset);
    // SignedMessage.addPayloadType(builder, MessagePayload.PartialContract);
    // SignedMessage.addSignature(builder, signatureOffset);
    // const signedMessageOffset = SignedMessage.endSignedMessage(builder);

    // console.log(builder.asUint8Array().length);
    // console.log(builder.asUint8Array());

    // if(builder.asUint8Array().length > 254) {
    //   throw new Error("Contract is too large");
    // }

    await this.qrcodeService.generateQRCode(builder.asUint8Array(), this.canvas);



    return builder.asUint8Array();
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const img = new Image();
      img.src = e.target.result;
      img.onload = () => {
        this.qrcodeService.loadKeysFromQRCode(img, this.scanCanvas).then(keys => {
          this.publicKey = keys?.publicKey!;
          this.privateKey = keys?.privateKey!;
          this.keyMessage = keys ? 'Valid key' : 'No QR code found.';
        });
      };
    };
    reader.readAsDataURL(file);
  }




}
