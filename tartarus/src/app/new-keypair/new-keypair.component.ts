import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';

import jsQR from 'jsqr';
import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';

@Component({
  selector: 'app-new-keypair',
  imports: [
    FormsModule
  ],
  templateUrl: './new-keypair.component.html',
  styleUrl: './new-keypair.component.scss'
})
export class NewKeypairComponent implements OnInit {
  qrText: string = '';
  scannedResult: string = '';
  displayQRCode: boolean = false;

  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;

  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService) {
  }

  ngOnInit(): void {

  }

  async generateQRCode() {
    let key_pair = await this.generateKeyPair();
    let whole_key : string = key_pair.privateKeyPEM + "\n" + key_pair.publicKeyPEM;

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
      await QRCode.toCanvas(tempCanvas, whole_key, { width: qrSize, margin: 2 });

      const ctx = canvas.getContext('2d');
      // Clear canvas and draw QR code onto the main canvas
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(tempCanvas, 0, 0);

      // Add text label below the QR code
      ctx.font = '16px Arial';
      ctx.fillStyle = 'red';
      ctx.textAlign = 'center';
      ctx.fillText("Do Not Share", canvas.width / 2, qrSize + 20);

      this.hiddenDiv.nativeElement.hidden = false;
      this.tartarusCoordinatorService.saveKeyRecord(key_pair.publicKeyPEM).subscribe(r => {
        console.log("Public key saved");
      });
    } catch (err) {
      console.error('Error generating QR Code:', err);
    }
  }

  downloadQRCode() {
    const canvas = this.canvas.nativeElement;
    const link = document.createElement('a');
    link.href = canvas.toDataURL('image/png');
    link.download = 'qrcode.png';
    link.click();
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

    this.scannedResult = qrCode ? qrCode.data : 'No QR code found.';
  }


  async generateKeyPair(): Promise<{ privateKeyPEM: string; publicKeyPEM: string }> {
    // Generate key pair using WebCrypto API
    const keyPair = await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true, // Extractable keys
      ["sign", "verify"]
    );

    // Export the private key (PKCS#8 format)
    const privateKeyDER = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);
    const privateKeyPEM = this.derToPem(new Uint8Array(privateKeyDER), "PRIVATE KEY");

    // Export the public key (SPKI format)
    const publicKeyDER = await crypto.subtle.exportKey("spki", keyPair.publicKey);
    const publicKeyPEM = this.derToPem(new Uint8Array(publicKeyDER), "PUBLIC KEY");

    return { privateKeyPEM, publicKeyPEM };
  }

  derToPem(der: Uint8Array, label: string): string {
    const base64 = btoa(String.fromCharCode(...der));
    const formatted = base64.match(/.{1,64}/g)?.join("\n") ?? "";
    return `-----BEGIN ${label}-----\n${formatted}\n-----END ${label}-----`;
  }
}
