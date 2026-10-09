import { Component, ChangeDetectionStrategy } from '@angular/core';
import {UserDataService} from '../user-data.service';
import {Router} from '@angular/router';
import {WebsocketService} from '../websocket.service';

@Component({
  selector: 'app-logout-author',
  imports: [],
  templateUrl: './logout-author.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './logout-author.component.scss'
})
export class LogoutAuthorComponent {
  constructor(private userDataService: UserDataService,
              private websocketService: WebsocketService,
              private router: Router) {
    this.userDataService.logoutAuthorSession();
    this.websocketService.reconnect();
    this.router.navigate(['/']);
  }
}
