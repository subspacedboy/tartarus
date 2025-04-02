import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';

import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {Platform} from '@angular/cdk/platform';
import {CryptoService} from '../crypto.service';

@Component({
  selector: 'app-new-keypair',
  imports: [
    FormsModule
  ],
  templateUrl: './new-keypair.component.html',
  styleUrl: './new-keypair.component.scss'
})
export class NewKeypairComponent implements OnInit {
  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('qrCodeContainer', { static: false }) qrCodeContainer!: ElementRef;

  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  showDownloadButton = false;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private platform: Platform,
              private cryptoService: CryptoService) {
    this.showDownloadButton = false;
  }

  async ngOnInit() {

  }

  async generateQRCode() {
    let key_pair = await this.cryptoService.generateKeyPair();
    let whole_key : string = key_pair.privateKeyPEM + "\n" + key_pair.publicKeyPEM;

    const canvas = this.canvas.nativeElement;
    const qrSize = 256;
    const textHeight = 30; // Space for text
    canvas.width = qrSize;
    canvas.height = qrSize + textHeight;

    try {
      // Create a temporary canvas
      const tempCanvas = document.createElement('canvas');
      tempCanvas.width = qrSize;
      tempCanvas.height = canvas.height;
      const ctx = tempCanvas.getContext('2d')!;

      // Generate the QR code on a separate QR canvas
      const qrCanvas = document.createElement('canvas');
      qrCanvas.width = qrSize;
      qrCanvas.height = qrSize;
      await QRCode.toCanvas(qrCanvas, whole_key, { width: qrSize, margin: 2 });

      // Draw the QR code onto the main canvas
      ctx.drawImage(qrCanvas, 0, 0);

      // Add text label below the QR code
      ctx.font = '16px Arial';
      ctx.fillStyle = 'red';
      ctx.textAlign = 'center';
      ctx.fillText("Do Not Share", qrSize / 2, qrSize + 20);

      const pngDataUrl = tempCanvas.toDataURL('image/png');

      // Create an image element
      const qrImage = document.createElement('img');
      qrImage.src = pngDataUrl;
      qrImage.alt = "QR Code";
      qrImage.style.cursor = 'pointer'; // Indicate tap & hold action
      qrImage.style.width = `${canvas.width}px`; // Ensure proper size
      qrImage.style.height = `${canvas.height}px`;

      // Clear previous content and add the image to the page
      const container = this.qrCodeContainer.nativeElement;
      container.innerHTML = ''; // Remove previous QR codes
      container.appendChild(qrImage);
      this.showDownloadButton = true;

      // this.tartarusCoordinatorService.saveKeyRecord(key_pair.publicKeyPEM).subscribe(r => {
      //   console.log("Public key saved");
      // });
    } catch (err) {
      console.error('Error generating QR Code:', err);
    }
  }

  downloadQRCode() {
    const container = this.qrCodeContainer.nativeElement;
    const qrImage = container.querySelector('img');

    if (!qrImage) {
      console.error('No QR code image found.');
      return;
    }

    // Create a download link
    const link = document.createElement('a');
    link.href = qrImage.src;  // Get the image source URL
    link.download = 'qrcode.png';  // Filename when saved
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link); // Cleanup
  }
}
