import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {WebsocketService} from '../../websocket.service';
import {LockSession} from '../../models/lock-session';
import {Contract} from '../../models/contract';
import {DateAgoPipe} from '../../date-ago.pipe';
import {DatePipe} from '@angular/common';
import {ToastService} from '../../toast.service';

@Component({
  selector: 'app-admin-lock-session-detail',
  imports: [
    RouterLink,
    DateAgoPipe,
    DatePipe,
  ],
  templateUrl: './admin-lock-session-detail.component.html',
  styleUrl: './admin-lock-session-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AdminLockSessionDetailComponent implements OnInit {
  sessionToken: string;
  lockSession?: LockSession;
  contracts: Contract[] = [];
  copiedPem: boolean = false;

  constructor(
    private activatedRoute: ActivatedRoute,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private websocketService: WebsocketService,
    private toastService: ToastService,
  ) {
    this.sessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
  }

  ngOnInit() {
    this.websocketService.connect();
    this.refreshData();

    this.websocketService.onMessage().subscribe({
      next: () => this.refreshData(),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  refreshData() {
    this.tartarusCoordinatorService.getLockSessionForAdmin(this.sessionToken).subscribe({
      next: (session) => {
        this.lockSession = session;
      },
      error: (err) => {
        console.error('Failed to load lock session:', err);
      },
    });

    this.tartarusCoordinatorService.getContractsForLockSessionForAdmin(this.sessionToken).subscribe({
      next: (contracts) => {
        this.contracts = contracts;
      },
      error: (err) => {
        console.error('Failed to load lock session contracts:', err);
      },
    });
  }

  copyPem() {
    if (this.lockSession?.publicPem) {
      navigator.clipboard.writeText(this.lockSession.publicPem).then(() => {
        this.copiedPem = true;
        this.toastService.showSuccess('Public Key PEM copied to clipboard');
        setTimeout(() => (this.copiedPem = false), 2000);
      });
    }
  }
}
