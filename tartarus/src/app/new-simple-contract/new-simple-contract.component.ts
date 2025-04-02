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

  keyMessage = "";
  privateKey : CryptoKey | null = null;
  publicKey : CryptoKey | null = null;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private idHelperService: IdHelperService) {
  }

  ngOnInit() {
  }

  async createContract(): Promise<Uint8Array> {
    const builder = new flatbuffers.Builder(1024);

    const compressedPublicKey = await this.generateCompressedPublicKey();
    // Create a public key string
    const publicKeyOffset = builder.createByteVector(compressedPublicKey);

    // Create capabilities array
    // This has to be a multiple of 4.
    Contract.startCapabilitiesVector(builder, 4);
    builder.addInt8(Capabilities.Online);
    builder.addInt8(Capabilities.Time);
    builder.addInt8(0);
    builder.addInt8(0);
    const capabilitiesOffset = builder.endVector();

    const contractName = this.idHelperService.generateBase32Id()
    const partialLocationOffset = builder.createString(`http://192.168.1.168:5200/contract/${contractName}`)

    // Start creating the Contract
    Contract.startContract(builder);
    Contract.addPublicKey(builder, publicKeyOffset);
    Contract.addCapabilities(builder, capabilitiesOffset);
    Contract.addIsPartial(builder, false);
    Contract.addIsLockOnAccept(builder, true);
    Contract.addIsTemporaryUnlockAllowed(builder, true);
    Contract.addCompleteContractAddress(
      builder,
      partialLocationOffset
      )
    const contractOffset = Contract.endContract(builder);

    builder.finish(contractOffset);
    console.log(builder.asUint8Array().length);

    const contractBytes = builder.asUint8Array();
    const offsetToTable = contractBytes[0] | (contractBytes[1] << 8);
    const offsetToVTable = contractBytes[offsetToTable] | (contractBytes[offsetToTable + 1] << 8);
    const vTableStart = offsetToTable - offsetToVTable;

    const vtableAndContract = contractBytes.slice(vTableStart);

    const signature = await this.signData(this.privateKey!, vtableAndContract);

    const signatureOffset = SignedMessage.createSignatureVector(builder, new Uint8Array(signature));

    // const payloadOffset = unionToMessagePayload(MessagePayload.Contract, contractOffset);
    // MessagePayload.startMessagePayload(builder);
    // MessagePayload.addType(builder, MessagePayload.Contract); // Set correct type
    // MessagePayload.addTable(builder, contractOffset);
    // const payloadOffset = MessagePayload.endMessagePayload(builder);

    // @ts-ignore
    console.log("Length of signature " + signature.byteLength);
    console.log("Signature: " + new Uint8Array(signature));
    SignedMessage.startSignedMessage(builder);
    SignedMessage.addPayload(builder, contractOffset);
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

  async sha256(data: Uint8Array): Promise<ArrayBuffer> {
    return await crypto.subtle.digest("SHA-256", data);
  }

  async signData(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {

    console.log("Hashing data: " + new Uint8Array(data));
    const hash = await this.sha256(data); // Compute hash first

    console.log("Hash: " + new Uint8Array(hash));
    const signature = await crypto.subtle.sign(
      { name: "ECDSA", hash: { name: "SHA-256" } },
      privateKey,
      hash
    );

    return signature
    // return this.rawToDER(new Uint8Array(signature));
  }

  rawToDER(rawSig: Uint8Array): Uint8Array {
    if (rawSig.length !== 64) {
      throw new Error("Invalid raw signature length");
    }

    const r = rawSig.slice(0, 32);
    const s = rawSig.slice(32, 64);

    function encodeInteger(bytes: Uint8Array): number[] {
      let arr = Array.from(bytes);

      // Ensure positive number (avoid leading zeros)
      if (arr[0] & 0x80) arr.unshift(0);

      return [0x02, arr.length, ...arr]; // DER INTEGER (0x02) format
    }

    const der = [
      0x30, // DER sequence tag
      4 + encodeInteger(r).length + encodeInteger(s).length, // Length
      ...encodeInteger(r),
      ...encodeInteger(s),
    ];

    return new Uint8Array(der);
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const img = new Image();
      img.src = e.target.result;
      img.onload = () => this.scanQRCode(img);
    };
    reader.readAsDataURL(file);
  }

  scanQRCode(img: HTMLImageElement) {
    const canvas = this.scanCanvas.nativeElement;
    const ctx = canvas.getContext('2d');
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.drawImage(img, 0, 0, img.width, img.height);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const qrCode = jsQR(imageData.data, imageData.width, imageData.height);

    if(qrCode) {
      console.log(qrCode.data);

      this.importKeyPair(qrCode.data).then(keys => {
        console.log(keys);
        this.privateKey = keys.privateKey;
        this.publicKey = keys.publicKey;
        this.keyMessage = qrCode ? 'Valid key' : 'No QR code found.';
      }, e => {
        this.keyMessage = "Invalid key";
      })
    }
  }

  pemToArrayBuffer(pem: string): ArrayBuffer {
    const base64 = pem.replace(/-----(BEGIN|END) [A-Z ]+-----/g, "").replace(/\s+/g, "");
    const binary = atob(base64);

    const buffer = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      buffer[i] = binary.charCodeAt(i);
    }
    return buffer.buffer;
  }

  async importKeyPair(pem: string): Promise<{ privateKey: CryptoKey; publicKey: CryptoKey }> {
    // Extract private and public key parts
    const privateKeyMatch = pem.match(/-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----/s);
    const publicKeyMatch = pem.match(/-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----/s);

    if (!privateKeyMatch || !publicKeyMatch) {
      throw new Error("Invalid PEM format: Missing private or public key");
    }

    const privateKeyPEM = privateKeyMatch[0];
    const publicKeyPEM = publicKeyMatch[0];

    // Convert to binary
    const privateKeyBuffer = this.pemToArrayBuffer(privateKeyPEM);
    const publicKeyBuffer = this.pemToArrayBuffer(publicKeyPEM);

    // Import Private Key
    const privateKey = await crypto.subtle.importKey(
      "pkcs8",
      privateKeyBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign"]
    );

    // Import Public Key
    const publicKey = await crypto.subtle.importKey(
      "spki",
      publicKeyBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["verify"]
    );

    return { privateKey, publicKey };
  }

  async exportRawPublicKey(publicKey: CryptoKey): Promise<Uint8Array> {
    const rawKey = await crypto.subtle.exportKey("raw", publicKey); // Returns x || y
    return new Uint8Array(rawKey);
  }

  compressPublicKey(uncompressedKey: Uint8Array): Uint8Array {
    if (uncompressedKey.length !== 65 || uncompressedKey[0] !== 0x04) {
      throw new Error("Invalid uncompressed public key format");
    }

    const x = uncompressedKey.slice(1, 33); // First 32 bytes after 0x04
    const y = uncompressedKey.slice(33, 65); // Last 32 bytes

    // Determine prefix based on y's parity
    const prefix = (y[y.length - 1] % 2 === 0) ? 0x02 : 0x03;

    // Compressed key format: prefix || x
    return new Uint8Array([prefix, ...x]);
  }

  async generateCompressedPublicKey(): Promise<Uint8Array> {
    // Generate an EC key pair

    // Export and compress public key
    const rawPublicKey = await this.exportRawPublicKey(this.publicKey!);
    const compressedKey = this.compressPublicKey(rawPublicKey);

    console.log("Compressed Public Key:", compressedKey);
    return compressedKey;
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
