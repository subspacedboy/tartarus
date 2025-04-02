import {Component, ElementRef, ViewChild} from '@angular/core';
import {UserDataService} from '../../user-data.service';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {IdHelperService} from '../../id-helper.service';
import {CryptoService} from '../../crypto.service';
import {Router} from '@angular/router';
import jsQR from 'jsqr';

@Component({
  selector: 'app-admin-login',
  imports: [],
  templateUrl: './admin-login.component.html',
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
