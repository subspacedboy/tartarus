import {AfterViewInit, Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';
import {ConfigService} from '../config.service';
import {CryptoService} from '../crypto.service';
import {firstValueFrom, Subject} from 'rxjs';

@Component({
  selector: 'app-lock-start',
  imports: [],
  templateUrl: './lock-start.component.html',
  styleUrl: './lock-start.component.scss'
})
export class LockStartComponent implements OnInit, AfterViewInit {
  session: string | null = null;
  lockSession? : LockSession;
  publicKey: string | null = null;
  showDetails: boolean = false;

  loaded = false;

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,
              private configService: ConfigService,
              private cryptoService: CryptoService,) {
    this.activatedRoute.queryParamMap.subscribe(params => {
      this.session = params.get('session');
      this.publicKey = params.get('public');
    });
  }

  async ngOnInit() {
    if(!this.userDataService.hasLockUserSessionKeyPair()){
      const key_pair = await this.cryptoService.generateKeyPair();
      this.userDataService.addPublicAndPrivateKeyToLocalUserSession(key_pair.privateKeyPEM, key_pair.publicKeyPEM);
    }

    const key_pair = this.userDataService.getLockUserSessionKeyPair();
    const publicECKey = await this.cryptoService.importPublicKeyOnlyFromPem(key_pair.publicPem!);
    const compressedPublicKey = await this.cryptoService.generateCompressedPublicKey(publicECKey);

    this.lockSession = await firstValueFrom(this.tartarusCoordinatorService.saveLockPubKeyAndSession(String(this.publicKey), String(this.session), btoa(String.fromCharCode(...compressedPublicKey))));
    this.userDataService.addLockSession(String(this.session), String(this.publicKey));
    this.userDataService.setLockUserSessionToken(this.lockSession.lockUserSession?.name!);
    this.loaded = true;
  }

  ngAfterViewInit() {
  }

  showTechnicalDetails() {
    this.showDetails = true;
  }

  getShareLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.shareToken}`;
  }

  getTotalControlLink(): string {
    const baseUrl = this.configService.getConfig().webUri;
    return `${baseUrl}/lock-sessions/${this.lockSession!.totalControlToken}`;
  }
}
