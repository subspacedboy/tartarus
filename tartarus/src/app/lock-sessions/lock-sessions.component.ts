import { Component, OnInit, ChangeDetectionStrategy, TemplateRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { KnownToken } from '../models/known-token';
import { LockSessionCardComponent } from '../lock-session-card/lock-session-card.component';
import { ToastService } from '../toast.service';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmDialogImports } from '../shared/ui/ui-dialog-helm';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    LockSessionCardComponent,
    HlmButtonDirective,
    ...HlmCardImports,
    ...HlmDialogImports,
  ],
  templateUrl: './lock-sessions.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  knownTokens: KnownToken[];
  tokenToDelete?: KnownToken;

  constructor(
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tartarusCoordinatorService: TartarusCoordinatorService,
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

  confirmDelete(token: KnownToken, _modalContent?: TemplateRef<any>) {
    this.tokenToDelete = token;
  }

  cancelDelete() {
    this.tokenToDelete = undefined;
  }

  confirmDeleteAction() {
    if (this.tokenToDelete) {
      this.deleteToken(this.tokenToDelete);
    }
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

