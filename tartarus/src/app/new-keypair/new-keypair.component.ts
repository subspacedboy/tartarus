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
  qrText: string = '';
  scannedResult: string = '';
  displayQRCode: boolean = false;

  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;

  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private tartarusCoordinatorService : TartarusCoordinatorService,
              private platform: Platform,
              private cryptoService: CryptoService) {
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
    const imageData = canvas.toDataURL('image/png');

    if (this.platform.IOS) {
      const newWindow = window.open(imageData, '_blank');
      if (!newWindow) {
        const link = document.createElement('a');
        link.href = imageData;
        link.target = '_blank';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      }
    } else {
      const link = document.createElement('a');
      link.href = imageData;
      link.download = 'qrcode.png';
      link.click();
    }
  }
}
