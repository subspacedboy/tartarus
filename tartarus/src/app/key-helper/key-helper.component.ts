import {Component, ElementRef, ViewChild} from '@angular/core';
import {CryptoService} from '../crypto.service';
import jsQR from 'jsqr';

@Component({
  selector: 'app-key-helper',
  imports: [],
  templateUrl: './key-helper.component.html',
  styleUrl: './key-helper.component.scss'
})
export class KeyHelperComponent {
  scannedResult: string = '';

  compressedKeyInBase64 = "";
  publicPem = "";

  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('qrCodeContainer', { static: false }) qrCodeContainer!: ElementRef;

  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private cryptoService: CryptoService) {
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

    this.cryptoService.importKeyPairForECDSA(qrCode?.data!).then(r => {
      this.scannedResult = qrCode ? 'Valid key' : 'No QR code found.';

      this.cryptoService.generateCompressedPublicKey(r.publicKey).then(publicKey => {
        this.compressedKeyInBase64 = btoa(String.fromCharCode(...publicKey));
        this.publicPem = r.publicKeyPem;
      });
    });
  }
}
