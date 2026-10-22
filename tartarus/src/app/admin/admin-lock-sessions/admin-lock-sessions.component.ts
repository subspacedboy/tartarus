import {Component, OnInit, ChangeDetectionStrategy} from '@angular/core';
import {Observable} from 'rxjs';
import {LockSession} from '../../models/lock-session';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {AsyncPipe} from '@angular/common';
import {RouterLink} from '@angular/router';
import {DateAgoPipe} from '../../date-ago.pipe';
import { HlmTableImports } from '../../shared/ui/ui-table-helm';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';

@Component({
  selector: 'app-admin-lock-sessions',
  imports: [
    AsyncPipe,
    RouterLink,
    DateAgoPipe,
    ...HlmTableImports,
    ...HlmCardImports,
    HlmBadgeDirective,
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
