import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {Observable} from 'rxjs';
import {LockSession} from '../../models/lock-session';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {AsyncPipe, DatePipe} from '@angular/common';
import {NgbdSortableHeader} from '../../sortable.directive';
import {ReactiveFormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-admin-lock-sessions',
  imports: [
    AsyncPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './admin-lock-sessions.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './admin-lock-sessions.component.scss'
})
export class AdminLockSessionsComponent implements OnInit {
  lockSessions : Observable<LockSession[]> | undefined;

  constructor(
    private tartarusCoordinatorService: TartarusCoordinatorService,
  ) {

  }

  ngOnInit() {
    this.lockSessions = this.tartarusCoordinatorService.getLockSessionsForAdmin();
  }
}
