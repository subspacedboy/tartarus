import {Component, Inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {UserDataService} from '../user-data.service';
import {AppComponent} from '../app.component';

@Component({
  selector: 'app-landing-page',
  imports: [
    RouterLink
  ],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss'
})
export class LandingPageComponent {

  constructor(@Inject(AppComponent) public parent: AppComponent) {

  }
}
