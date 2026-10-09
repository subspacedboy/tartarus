import {Component, OnInit, ChangeDetectionStrategy, TemplateRef} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {UserDataService} from '../user-data.service';
import {KnownToken} from '../models/known-token';
import {LockSessionCardComponent} from '../lock-session-card/lock-session-card.component';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ToastService} from '../toast.service';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    RouterLink,
    LockSessionCardComponent
  ],
  templateUrl: './lock-sessions.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  knownTokens: KnownToken[];
  tokenToDelete?: KnownToken;

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private modalService: NgbModal,
              private toastService: ToastService,
              ) {
    this.knownTokens = [];
  }

  ngOnInit() {
    this.loadKnownTokens();
  }

  loadKnownTokens() {
    this.tartarusCoordinatorService.getKnownTokensForAuthor().subscribe(knownTokens => {
      this.knownTokens = knownTokens;
    });
  }

  confirmDelete(token: KnownToken, modalContent: TemplateRef<any>) {
    this.tokenToDelete = token;
    this.modalService.open(modalContent, { centered: true }).result.then(
      (result) => {
        if (result === 'confirm') {
          this.deleteToken(token);
        }
      },
      () => {
        this.tokenToDelete = undefined;
      }
    );
  }

  deleteToken(token: KnownToken) {
    if (!token.name) {
      return;
    }

    this.tartarusCoordinatorService.deleteKnownToken(token.name).subscribe({
      next: () => {
        this.knownTokens = this.knownTokens.filter(k => k.name !== token.name);
        this.toastService.showSuccess("Lock session deleted");
        this.tokenToDelete = undefined;
      },
      error: () => {
        this.toastService.showError("Failed to delete lock session");
        this.tokenToDelete = undefined;
      }
    });
  }
}

