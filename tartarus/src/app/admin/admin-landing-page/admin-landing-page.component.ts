import { Component, ChangeDetectionStrategy } from '@angular/core';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-admin-landing-page',
  imports: [
    RouterLink
  ],
  templateUrl: './admin-landing-page.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './admin-landing-page.component.scss'
})
export class AdminLandingPageComponent {

}
