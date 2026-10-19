import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { LockEvent } from '../models/event';
import { DateAgoPipe } from '../date-ago.pipe';

@Component({
  selector: 'app-event-card',
  imports: [
    CommonModule,
    DatePipe,
    DateAgoPipe,
  ],
  templateUrl: './event-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './event-card.component.scss'
})
export class EventCardComponent {
  @Input()
  event?: LockEvent;

  get badgeClass(): string {
    switch (this.event?.eventType) {
      case 'LOCK':
      case 'LOCAL_LOCK':
        return 'bg-secondary';
      case 'UNLOCK':
      case 'LOCAL_UNLOCK':
        return 'bg-success';
      case 'EMERGENCY_UNLOCK':
        return 'bg-danger';
      case 'ACCEPT_CONTRACT':
        return 'bg-primary';
      case 'RELEASE_CONTRACT':
        return 'bg-warning text-dark';
      default:
        return 'bg-info text-dark';
    }
  }

  get icon(): string {
    switch (this.event?.eventType) {
      case 'LOCK':
        return '🔒';
      case 'UNLOCK':
        return '🔓';
      case 'LOCAL_LOCK':
        return '🎚️🔒';
      case 'LOCAL_UNLOCK':
        return '🎚️🔓';
      case 'EMERGENCY_UNLOCK':
        return '🚨';
      case 'ACCEPT_CONTRACT':
        return '📝';
      case 'RELEASE_CONTRACT':
        return '🏁';
      default:
        return '⚡';
    }
  }

  get formattedEventType(): string {
    if (!this.event?.eventType) return 'Unknown Event';
    return this.event.eventType.replace(/_/g, ' ');
  }
}
