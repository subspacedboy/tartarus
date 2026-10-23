import { Component, OnInit, ChangeDetectionStrategy, TemplateRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { KnownToken } from '../models/known-token';
import { LockSessionCardComponent } from '../lock-session-card/lock-session-card.component';
import { ToastService } from '../toast.service';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmDialogImports } from '../shared/ui/ui-dialog-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    FormsModule,
    LockSessionCardComponent,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    ...HlmDialogImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './lock-sessions.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  knownTokens: KnownToken[] = [];
  tokenToDelete?: KnownToken;

  connectInput: string = '';
  searchQuery: string = '';

  constructor(
    private activatedRoute: ActivatedRoute,
    private router: Router,
    private tartarusCoordinatorService: TartarusCoordinatorService,
    private toastService: ToastService,
  ) {}

  ngOnInit() {
    this.loadKnownTokens();
  }

  loadKnownTokens() {
    this.tartarusCoordinatorService.getKnownTokensForAuthor().subscribe(knownTokens => {
      this.knownTokens = knownTokens;
    });
  }

  get filteredKnownTokens(): KnownToken[] {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      return this.knownTokens;
    }
    return this.knownTokens.filter(t =>
      (t.notes && t.notes.toLowerCase().includes(q)) ||
      (t.shareableToken && t.shareableToken.toLowerCase().includes(q)) ||
      (t.name && t.name.toLowerCase().includes(q))
    );
  }

  get labeledCount(): number {
    return this.knownTokens.filter(t => !!t.notes?.trim()).length;
  }

  extractSessionToken(rawInput: string): string {
    const trimmed = rawInput.trim();
    if (!trimmed) {
      return '';
    }

    const match = trimmed.match(/\/lock-sessions?\/([^/?#]+)/i);
    if (match?.[1]) {
      return decodeURIComponent(match[1]);
    }

    // If user pasted a URL without /lock-sessions/, take the last non-empty path segment
    if (trimmed.includes('://') || trimmed.includes('/')) {
      const withoutQuery = trimmed.split(/[?#]/)[0].replace(/\/+$/, '');
      const segments = withoutQuery.split('/').filter(Boolean);
      return segments.length > 0 ? decodeURIComponent(segments[segments.length - 1]) : '';
    }

    return trimmed;
  }

  connectToSession() {
    const token = this.extractSessionToken(this.connectInput);
    if (!token) {
      this.toastService.showError('Enter a valid session token or invite URL');
      return;
    }

    this.router.navigate(['/lock-sessions', token]);
  }

  clearSearch() {
    this.searchQuery = '';
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
