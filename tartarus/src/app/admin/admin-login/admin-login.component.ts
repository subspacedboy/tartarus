import {Component, ElementRef, ViewChild, ChangeDetectionStrategy} from '@angular/core';
import {UserDataService} from '../../user-data.service';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {IdHelperService} from '../../id-helper.service';
import {CryptoService} from '../../crypto.service';
import {Router, RouterLink} from '@angular/router';
import {WebsocketService} from '../../websocket.service';
import jsQR from 'jsqr';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../../shared/ui/ui-label-helm';
import { HlmAlertImports } from '../../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';

@Component({
  selector: 'app-admin-login',
  imports: [
    RouterLink,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
    ...HlmAlertImports,
    HlmBadgeDirective,
  ],
  templateUrl: './admin-login.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './admin-login.component.scss'
})
export class AdminLoginComponent {
  scannedResult: string = '';
  loginComplete: boolean = false;

  @ViewChild('scanCanvas', { static: false }) scanCanvas!: ElementRef;

  constructor(private userDataService: UserDataService,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private idHelperService: IdHelperService,
              private cryptoService: CryptoService,
              private websocketService: WebsocketService,
              private router: Router) {
  }

  alreadyLoggedIn(): boolean {
    return this.userDataService.hasAdminSession();
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
      console.log("Loaded correctly");
      this.scannedResult = qrCode ? 'Valid key' : 'No QR code found.';

      let token = this.idHelperService.generateBase32Id();
      const encodedToken = new TextEncoder().encode(token);
      this.cryptoService.hashAndSignData(r.privateKey, new Uint8Array(encodedToken)).then(signature => {
        this.cryptoService.generateCompressedPublicKey(r.publicKey).then(compressedPublicKey => {
          let b64compressed = btoa(String.fromCharCode(...new Uint8Array(compressedPublicKey)));
          let b64Signature = btoa(String.fromCharCode(...new Uint8Array(signature)));
          this.tartarusCoordinatorService.confirmAdminSession(b64compressed, token, b64Signature).subscribe(res => {

            this.userDataService.addPublicAndPrivateKeyToAdminSession(r.privateKeyPem, r.publicKeyPem, String(res.name));
            this.websocketService.reconnect();
            this.loginComplete = true;
          });
          // this.tartarusCoordinatorService.createAuthorSession(b64compressed, token, b64Signature).subscribe(session_result => {
          //   console.log("Session created");
          //
          //   this.userDataService.addPublicAndPrivateKeyToLocalSession(r.privateKeyPem, r.publicKeyPem, String(session_result.sessionToken));
          //   this.loginComplete = true;
          //
          //   this.router.navigate(['/']);
          // });
        })
      });
    });
  }
}
