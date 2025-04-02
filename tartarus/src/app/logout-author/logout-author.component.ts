import { Component } from '@angular/core';
import {UserDataService} from '../user-data.service';
import {Router} from '@angular/router';

@Component({
  selector: 'app-logout-author',
  imports: [],
  templateUrl: './logout-author.component.html',
  styleUrl: './logout-author.component.scss'
})
export class LogoutAuthorComponent {
  constructor(private userDataService: UserDataService, private router: Router) {
    this.userDataService.logoutAuthorSession();
    this.router.navigate(['/']);
  }
}
