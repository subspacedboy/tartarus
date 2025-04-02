import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet} from '@angular/router';
import {NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle} from '@ng-bootstrap/ng-bootstrap';
import {UserDataService} from './user-data.service';
import {WebsocketService} from './websocket.service';
import {ConfigService} from './config.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NgbDropdownItem, NgbDropdownMenu, NgbDropdown, RouterLink, NgbDropdownToggle],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  title = 'tartarus';

  hasLockSessions = false;
  hasAuthorSession = false;


  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private userData: UserDataService,
    private websocketService : WebsocketService,
    private configService: ConfigService,) {

    this.updateSessionStatus();
    const appConfig = this.configService.getConfig();
    this.websocketService.connect(appConfig.wsUri!);

    this.websocketService.onMessage().subscribe({
      next: (message) => console.log("Message: "+ message),
      error: (err) => console.error('WebSocket error:', err),
    });
  }

  ngOnInit() {
    this.updateSessionStatus();

    this.router.events.subscribe(event => {
      if (event instanceof NavigationEnd) {
        this.updateSessionStatus();
      }
    });
  }

  private updateSessionStatus() {
    this.hasLockSessions = this.userData.hasLockSessions();
    this.hasAuthorSession = this.userData.hasAuthorSession();
  }
}
