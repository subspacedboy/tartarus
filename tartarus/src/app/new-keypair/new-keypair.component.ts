import { Component, ElementRef, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import * as QRCode from 'qrcode';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { Platform } from '@angular/cdk/platform';
import { CryptoService } from '../crypto.service';
import { IdHelperService } from '../id-helper.service';
import { UserDataService } from '../user-data.service';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';

@Component({
  selector: 'app-new-keypair',
  imports: [
    FormsModule,
    RouterLink,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
  ],
  templateUrl: './new-keypair.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './new-keypair.component.scss'
})
export class NewKeypairComponent implements OnInit {
  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('qrCodeContainer', { static: false }) qrCodeContainer!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  showDownloadButton = false;
  hasBackedUpKey = false;
  isSigningIn = false;
  generatedKeyPair?: { privateKeyPEM: string; publicKeyPEM: string };

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private platform: Platform,
    private cryptoService: CryptoService,
    private idHelperService: IdHelperService,
    private userDataService: UserDataService,
    private websocketService: WebsocketService,
    private toastService: ToastService,
    private router: Router,
  ) {
    this.showDownloadButton = false;
  }

  async ngOnInit() {}

  async generateQRCode() {
    const key_pair = await this.cryptoService.generateKeyPair();
    this.generatedKeyPair = key_pair;
    this.hasBackedUpKey = false;
    const whole_key: string = key_pair.privateKeyPEM + '\n' + key_pair.publicKeyPEM;

    const canvas = this.canvas.nativeElement;
    const qrSize = 256;
    const textHeight = 30;
    canvas.width = qrSize;
    canvas.height = qrSize + textHeight;

    try {
      const tempCanvas = document.createElement('canvas');
      tempCanvas.width = qrSize;
      tempCanvas.height = canvas.height;
      const ctx = tempCanvas.getContext('2d')!;

      const qrCanvas = document.createElement('canvas');
      qrCanvas.width = qrSize;
      qrCanvas.height = qrSize;
      await QRCode.toCanvas(qrCanvas, whole_key, { width: qrSize, margin: 2 });

      ctx.drawImage(qrCanvas, 0, 0);

      ctx.font = '16px Arial';
      ctx.fillStyle = 'red';
      ctx.textAlign = 'center';
      ctx.fillText('Do Not Share', qrSize / 2, qrSize + 20);

      const pngDataUrl = tempCanvas.toDataURL('image/png');

      const qrImage = document.createElement('img');
      qrImage.src = pngDataUrl;
      qrImage.alt = 'QR Code';
      qrImage.style.cursor = 'pointer';
      qrImage.style.width = `${canvas.width}px`;
      qrImage.style.height = `${canvas.height}px`;

      const container = this.qrCodeContainer.nativeElement;
      container.innerHTML = '';
      container.appendChild(qrImage);
      this.showDownloadButton = true;
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

    const link = document.createElement('a');
    link.href = qrImage.src;
    link.download = 'tartarus-author-key.png';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    this.hasBackedUpKey = true;
  }

  async loginWithGeneratedKey(): Promise<void> {
    if (!this.generatedKeyPair || this.isSigningIn) {
      return;
    }

    this.isSigningIn = true;
    try {
      const wholeKey = `${this.generatedKeyPair.privateKeyPEM}\n${this.generatedKeyPair.publicKeyPEM}`;
      const imported = await this.cryptoService.importKeyPairForECDSA(wholeKey);
      const token = this.idHelperService.generateBase32Id();
      const encodedToken = new TextEncoder().encode(token);
      const signature = await this.cryptoService.hashAndSignData(imported.privateKey, new Uint8Array(encodedToken));
      const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(imported.publicKey);

      const b64compressed = btoa(String.fromCharCode(...new Uint8Array(compressedPublicKey)));
      const b64Signature = btoa(String.fromCharCode(...new Uint8Array(signature)));

      this.tartarusCoordinatorService.createAuthorSession(b64compressed, token, b64Signature).subscribe({
        next: (sessionResult) => {
          this.userDataService.addPublicAndPrivateKeyToAuthorSession(
            imported.privateKeyPem,
            imported.publicKeyPem,
            String(sessionResult.sessionToken),
          );
          this.websocketService.reconnect();
          this.isSigningIn = false;
          this.toastService.showSuccess('Signed in with your new keypair');
          this.router.navigate(['/']);
        },
        error: () => {
          this.isSigningIn = false;
          this.toastService.showError('Failed to create author session');
        },
      });
    } catch {
      this.isSigningIn = false;
      this.toastService.showError('Failed to sign in with generated key');
    }
  }
}
