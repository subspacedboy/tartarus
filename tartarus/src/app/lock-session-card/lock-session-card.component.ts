import {Component, Input, Output, EventEmitter, ChangeDetectionStrategy} from '@angular/core';
import {KnownToken} from '../models/known-token';
import {RouterLink} from '@angular/router';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-lock-session-card',
  imports: [
    RouterLink,
    DatePipe
  ],
  templateUrl: './lock-session-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './lock-session-card.component.scss'
})
export class LockSessionCardComponent {
  @Input() knownToken?: KnownToken;
  @Input() basePath: string = '/lock-sessions/';
  @Output() delete = new EventEmitter<KnownToken>();

  onDelete(event: Event) {
    event.stopPropagation();
    event.preventDefault();
    if (this.knownToken) {
      this.delete.emit(this.knownToken);
    }
  }
}
