import { AfterViewInit, Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { ConfigService } from '../config.service';
import { CryptoService } from '../crypto.service';
import { firstValueFrom } from 'rxjs';
import { LockUserSession } from '../models/lock-user-session';
import { WebsocketService } from '../websocket.service';
import { ToastService } from '../toast.service';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';
import { HlmSpinnerComponent } from '../shared/ui/ui-spinner-helm';

@Component({
  selector: 'app-lock-start',
  imports: [
    RouterLink,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
    HlmSpinnerComponent,
  ],
  templateUrl: './lock-start.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-start.component.scss'
})
export class LockStartComponent implements OnInit, AfterViewInit {
  session: string | null = null;
  lockUserSession?: LockUserSession;
  nonce: string | null = null;
  cipher: string | null = null;
  showDetails: boolean = false;
  inviteLinkMode: 'reviewable' | 'total-control' = 'reviewable';

  loaded = false;
  errorMessage: string | null = null;

  constructor(
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService,
    private configService: ConfigService,
    private cryptoService: CryptoService,
    private websocketService: WebsocketService,
    private toastService: ToastService,
  ) {
    this.activatedRoute.queryParamMap?.subscribe(params => {
      this.session = params.get('session');
      this.nonce = params.get('nonce');
      this.cipher = params.get('cipher');
    });
  }

  async ngOnInit() {
    await this.registerLockSession();
  }

  async registerLockSession() {
    this.loaded = false;
    this.errorMessage = null;

    try {
      if (!this.userDataService.hasLockUserSessionKeyPair()) {
        const key_pair = await this.cryptoService.generateKeyPair();
        this.userDataService.addPublicAndPrivateKeyToLocalUserSession(key_pair.privateKeyPEM, key_pair.publicKeyPEM);
      }

      const key_pair = this.userDataService.getLockUserSessionKeyPair();
      const publicECKey = await this.cryptoService.importPublicKeyOnlyFromPem(key_pair.publicPem!);
      const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(publicECKey);

      const b64compressed = btoa(String.fromCharCode(...new Uint8Array(compressedPublicKey)));
      this.lockUserSession = await firstValueFrom(
        this.tartarusCoordinatorService.saveLockUserSessionWithCryptogram(
          this.nonce!,
          this.cipher!,
          this.session!,
          b64compressed
        )
      );
      this.userDataService.setLockUserSessionToken(this.lockUserSession.name!);
      this.websocketService.reconnect();
      this.loaded = true;
    } catch (err: any) {
      this.errorMessage = err?.message || 'Unable to register lock session with coordinator.';
      this.loaded = false;
    }
  }

  ngAfterViewInit() {}

  setInviteLinkMode(mode: 'reviewable' | 'total-control'): void {
    this.inviteLinkMode = mode;
  }

  showTechnicalDetails() {
    this.showDetails = !this.showDetails;
  }

  getShareLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockUserSession?.lockSession?.shareToken ?? ''}`;
  }

  getTotalControlLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockUserSession?.lockSession?.totalControlToken ?? ''}`;
  }

  copyToClipboard(text: string): void {
    if (!text) return;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(
        () => this.toastService.showSuccess('Copied to clipboard'),
        () => this.toastService.showError('Failed to copy link'),
      );
    }
  }
}
