import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {LockSession} from '../models/lock-session';
import {Contract} from '../models/contract';
import {ContractCardComponent} from '../contract-card/contract-card.component';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ToastService} from '../toast.service';
import {DateAgoPipe} from "../date-ago.pipe";

@Component({
  selector: 'app-lock-session-detail',
    imports: [
        RouterLink,
        ContractCardComponent,
        ReactiveFormsModule,
        DateAgoPipe
    ],
  templateUrl: './lock-session-detail.component.html',
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
