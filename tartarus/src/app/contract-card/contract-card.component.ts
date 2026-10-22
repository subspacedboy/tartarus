import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { Contract } from '../models/contract';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HlmBadgeDirective, type BadgeVariants } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';

@Component({
  selector: 'app-contract-card',
  imports: [
    RouterLink,
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
  ],
  templateUrl: './contract-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './contract-card.component.scss'
})
export class ContractCardComponent {
  @Input()
  contract?: Contract;

  @Input() basePath: string = '';

  get badgeVariant(): BadgeVariants['variant'] {
    switch (this.contract?.state) {
      case 'CONFIRMED':
      case 'ACCEPTED':
        return 'success';
      case 'CREATED':
      case 'PENDING':
        return 'warning';
      case 'REJECTED':
        return 'destructive';
      case 'RELEASED':
      default:
        return 'secondary';
    }
  }

  get accentBorderClass(): string {
    switch (this.contract?.state) {
      case 'CONFIRMED':
      case 'ACCEPTED':
        return 'border-l-4 border-l-success';
      case 'CREATED':
      case 'PENDING':
        return 'border-l-4 border-l-warning';
      case 'REJECTED':
        return 'border-l-4 border-l-destructive';
      case 'RELEASED':
      default:
        return 'border-l-4 border-l-muted-foreground/40';
    }
  }

  get stateLabel(): string {
    switch (this.contract?.state) {
      case 'CONFIRMED':
        return 'Active';
      case 'ACCEPTED':
        return 'Accepted (Unconfirmed)';
      case 'RELEASED':
        return 'Released';
      case 'CREATED':
        return 'Created / In Review';
      case 'PENDING':
        return 'Pending';
      case 'REJECTED':
        return 'Rejected';
      default:
        return this.contract?.state || 'Unknown';
    }
  }
}
