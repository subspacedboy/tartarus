import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { UserDataService } from '../user-data.service';
import { LockSession } from '../models/lock-session';
import { Contract } from '../models/contract';
import { ContractCardComponent } from '../contract-card/contract-card.component';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastService } from '../toast.service';
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
  lockSession? : LockSession;
  contracts?: Contract[];

  knownTokenForm;

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private userDataService: UserDataService,
              private fb: FormBuilder,
              private toastService: ToastService,
  ) {
    this.sessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
    this.knownTokenForm = this.fb.group({
      notes: ['', [Validators.required]],
    });
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.sessionToken).subscribe(result => {
      this.lockSession = result;
      this.knownTokenForm.get('notes')!.setValue(this.lockSession.knownToken?.notes!);
    });

    this.tartarusCoordinatorService.getContractsForShareable(this.sessionToken).subscribe(result => {
      this.contracts = result;
    })
  }

  saveNotes() {
    const notes = this.knownTokenForm.get('notes')!.value;
    this.tartarusCoordinatorService.saveNotesForKnownToken(this.lockSession?.knownToken?.name!, notes!).subscribe(result => {
      this.toastService.showSuccess("Saved");
    })
  }

  navToNewContract() {
    this.router.navigate(['full-contract'], {relativeTo: this.activatedRoute});
  }
}
