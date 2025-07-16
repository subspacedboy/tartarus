import { Component } from '@angular/core';
import {ContractCardComponent} from '../../contract-card/contract-card.component';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-admin-landing-page',
  imports: [
    ContractCardComponent,
    RouterLink
  ],
  templateUrl: './admin-landing-page.component.html',
  styleUrl: './admin-landing-page.component.scss'
})
export class AdminLandingPageComponent {

}
