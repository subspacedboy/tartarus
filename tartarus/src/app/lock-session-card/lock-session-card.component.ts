import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { KnownToken } from '../models/known-token';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { ToastService } from '../toast.service';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';

@Component({
  selector: 'app-lock-session-card',
  imports: [
    RouterLink,
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
  ],
  templateUrl: './lock-session-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-session-card.component.scss'
})
export class LockSessionCardComponent {
  @Input() knownToken?: KnownToken;
  @Input() basePath: string = '/lock-sessions/';
  @Output() delete = new EventEmitter<KnownToken>();

  constructor(private toastService: ToastService) {}

  get hasCustomLabel(): boolean {
    return !!this.knownToken?.notes?.trim();
  }

  get displayTitle(): string {
    if (this.knownToken?.notes?.trim()) {
      return this.knownToken.notes.trim();
    }
    return 'Unlabeled Session';
  }

  copyToken(event: Event): void {
    event.stopPropagation();
    event.preventDefault();
    const token = this.knownToken?.shareableToken;
    if (!token) {
      return;
    }

    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(token).then(
        () => this.toastService.showSuccess('Copied session token'),
        () => this.toastService.showError('Failed to copy token'),
      );
    }
  }

  onDelete(event: Event) {
    event.stopPropagation();
    event.preventDefault();
    if (this.knownToken) {
      this.delete.emit(this.knownToken);
    }
  }
}
