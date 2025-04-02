import {ElementRef, Injectable} from '@angular/core';
import {CryptoService} from './crypto.service';
import jsQR from 'jsqr';
import * as QRCode from 'qrcode';

@Injectable({
  providedIn: 'root'
})
export class QrcodeService {

  constructor(private cryptoService: CryptoService) { }

  async loadKeysFromQRCode(img: HTMLImageElement, scanCanvas: ElementRef) : Promise<{
    privateKey: CryptoKey;
    publicKey: CryptoKey
  } | null> {
    const canvas = scanCanvas.nativeElement;
    const ctx = canvas.getContext('2d');
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.drawImage(img, 0, 0, img.width, img.height);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const qrCode = jsQR(imageData.data, imageData.width, imageData.height);
    let result = null;

    if(qrCode) {
      console.log("QR Data: " + qrCode.data);

      //.then(keys => {
      return await this.cryptoService.importKeyPair(qrCode.data);
      //   // this.privateKey = keys.privateKey;
      //   // this.publicKey = keys.publicKey;
      //   result = {
      //     publicKey : keys.publicKey,
      //     privateKey : keys.privateKey
      //   };
      //   console.log('wtf');
      //   console.log(keys);
      //   console.log("Import result: " + JSON.stringify(result));
      //   // this.keyMessage = qrCode ? 'Valid key' : 'No QR code found.';
      // }, e => {
      // });
    }
    return null;
  }

  async generateQRCode( data : Uint8Array, canvasInput: ElementRef) {
    const canvas = canvasInput.nativeElement;
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

      // this.hiddenDiv.nativeElement.hidden = false;
    } catch (err) {
      console.error('Error generating QR Code:', err);
    }
  }
}
