import { Component, ElementRef, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { UserDataService } from '../user-data.service';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import jsQR from 'jsqr';
import { IdHelperService } from '../id-helper.service';
import { CryptoService } from '../crypto.service';
import { Router, RouterLink } from '@angular/router';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-login',
  imports: [
    RouterLink,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  scannedResult: string = '';
  errorMessage: string | null = null;
  isAuthenticating: boolean = false;
  loginComplete: boolean = false;

  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;

  constructor(
    private userDataService: UserDataService,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private idHelperService: IdHelperService,
    private cryptoService: CryptoService,
    private websocketService: WebsocketService,
    private toastService: ToastService,
    private router: Router,
  ) {}

  ngOnInit() {}

  alreadyLoggedIn(): boolean {
    return this.userDataService.hasAuthorSession();
  }

  onFileSelected(event: any) {
    const file = event.target?.files?.[0];
    if (!file) return;

    this.errorMessage = null;
    this.scannedResult = '';

    const reader = new FileReader();
    reader.onload = (e: any) => {
      const img = new Image();
      img.src = e.target.result;
      img.onload = () => this.scanQRCode(img);
    };
    reader.readAsDataURL(file);
  }

  async scanQRCode(img: HTMLImageElement): Promise<void> {
    this.errorMessage = null;
    const canvas = this.scanCanvas.nativeElement;
    const ctx = canvas.getContext('2d');
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.drawImage(img, 0, 0, img.width, img.height);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const qrCode = jsQR(imageData.data, imageData.width, imageData.height);

    if (!qrCode?.data) {
      this.scannedResult = 'No QR code found.';
      this.errorMessage = 'No QR code was detected in the selected image. Please choose your saved Tartarus key QR image.';
      return;
    }

    this.isAuthenticating = true;
    try {
      const r = await this.cryptoService.importKeyPairForECDSA(qrCode.data);
      this.scannedResult = 'Valid key';

      const token = this.idHelperService.generateBase32Id();
      const encodedToken = new TextEncoder().encode(token);
      const signature = await this.cryptoService.hashAndSignData(r.privateKey, new Uint8Array(encodedToken));
      const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(r.publicKey);

      const b64compressed = btoa(String.fromCharCode(...new Uint8Array(compressedPublicKey)));
      const b64Signature = btoa(String.fromCharCode(...new Uint8Array(signature)));

      this.tartarusCoordinatorService.createAuthorSession(b64compressed, token, b64Signature).subscribe({
        next: (session_result) => {
          this.userDataService.addPublicAndPrivateKeyToAuthorSession(
            r.privateKeyPem,
            r.publicKeyPem,
            String(session_result.sessionToken),
          );
          this.websocketService.reconnect();
          this.isAuthenticating = false;
          this.loginComplete = true;
          this.toastService.showSuccess('Authenticated as contract author');
          this.router.navigate(['/']);
        },
        error: () => {
          this.isAuthenticating = false;
          this.errorMessage = 'Failed to authenticate with the coordinator. Please try again.';
        },
      });
    } catch {
      this.isAuthenticating = false;
      this.scannedResult = 'Invalid key format.';
      this.errorMessage = 'The QR code in this image is not a valid Tartarus ECDSA keypair.';
    }
  }
}
