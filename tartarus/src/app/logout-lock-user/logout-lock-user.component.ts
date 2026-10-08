import { Component, ChangeDetectionStrategy } from '@angular/core';
import {UserDataService} from '../user-data.service';
import {Router} from '@angular/router';

@Component({
  selector: 'app-logout-lock-user',
  imports: [],
  templateUrl: './logout-lock-user.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './logout-lock-user.component.scss'
})
export class LogoutLockUserComponent {
  constructor(private userDataService: UserDataService, private router: Router) {
    this.userDataService.logoutLockUserSession();
    this.router.navigate(['/']);
  }
}
