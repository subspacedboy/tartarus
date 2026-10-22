import { Component, ChangeDetectionStrategy } from '@angular/core';
import {RouterLink} from '@angular/router';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';

@Component({
  selector: 'app-admin-landing-page',
  imports: [
    RouterLink,
    ...HlmCardImports,
    HlmBadgeDirective,
  ],
  templateUrl: './admin-landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './admin-landing-page.component.scss'
})
export class AdminLandingPageComponent {

}
