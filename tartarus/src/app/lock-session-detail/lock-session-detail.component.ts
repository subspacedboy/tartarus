import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { LockSession } from '../models/lock-session';
import { Contract } from '../models/contract';
import { ContractCardComponent } from '../contract-card/contract-card.component';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastService } from '../toast.service';
import { WebsocketService } from '../websocket.service';
import { DateAgoPipe } from "../date-ago.pipe";
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-lock-session-detail',
  imports: [
    RouterLink,
    ContractCardComponent,
    ReactiveFormsModule,
    DateAgoPipe,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './lock-session-detail.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-session-detail.component.scss'
})
export class LockSessionDetailComponent implements OnInit {
  sessionToken: string = '';
  lockSession?: LockSession;
  contracts?: Contract[];
  showHistory = false;

  knownTokenForm;

  constructor(
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private userDataService: UserDataService,
    private fb: FormBuilder,
    private toastService: ToastService,
    private websocketService: WebsocketService,
  ) {
    this.sessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
    this.knownTokenForm = this.fb.group({
      notes: ['', [Validators.required]],
    });
  }

  ngOnInit() {
    this.websocketService.connect();
    this.refreshData(true);

    this.websocketService.onMessage().subscribe({
      next: () => this.refreshData(false),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  refreshData(initial = false) {
    this.tartarusCoordinatorService.getLockSession(this.sessionToken).subscribe(result => {
      this.lockSession = result;
      if (initial || !this.knownTokenForm.dirty) {
        this.knownTokenForm.get('notes')!.setValue(this.lockSession.knownToken?.notes ?? '');
      }
    });

    this.tartarusCoordinatorService.getContractsForShareable(this.sessionToken).subscribe(result => {
      this.contracts = result;
    });
  }

  get activeContract(): Contract | undefined {
    return this.contracts?.find(c => c.state === 'CONFIRMED' || c.state === 'ACCEPTED');
  }

  get pendingProposals(): Contract[] {
    return this.contracts?.filter(c => c.state === 'CREATED') ?? [];
  }

  get historicalContracts(): Contract[] {
    return this.contracts?.filter(
      c => c.state !== 'CONFIRMED' && c.state !== 'ACCEPTED' && c.state !== 'CREATED'
    ) ?? [];
  }

  get canCreateContract(): boolean {
    if (!this.lockSession) {
      return false;
    }
    if (this.lockSession.isTotalControlToken()) {
      return !!this.lockSession.availableForContract;
    }
    return true;
  }

  get sessionTitle(): string {
    if (this.lockSession?.knownToken?.notes?.trim()) {
      return this.lockSession.knownToken.notes.trim();
    }
    return 'Lock Session';
  }

  copySessionToken(): void {
    if (!this.sessionToken) {
      return;
    }
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(this.sessionToken).then(
        () => this.toastService.showSuccess('Copied session token'),
        () => this.toastService.showError('Failed to copy session token'),
      );
    }
  }

  saveNotes() {
    const notes = this.knownTokenForm.get('notes')!.value ?? '';
    const tokenName = this.lockSession?.knownToken?.name;
    if (!tokenName) {
      return;
    }
    this.tartarusCoordinatorService.saveNotesForKnownToken(tokenName, notes).subscribe(() => {
      if (this.lockSession?.knownToken) {
        this.lockSession.knownToken.notes = notes;
      }
      this.knownTokenForm.markAsPristine();
      this.toastService.showSuccess("Saved");
    });
  }

  navToNewContract() {
    this.router.navigate(['full-contract'], { relativeTo: this.activatedRoute });
  }
}
