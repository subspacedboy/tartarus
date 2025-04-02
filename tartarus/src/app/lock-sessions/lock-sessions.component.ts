import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';

@Component({
  selector: 'app-lock-sessions',
  imports: [
    RouterLink
  ],
  templateUrl: './lock-sessions.component.html',
  styleUrl: './lock-sessions.component.scss'
})
export class LockSessionsComponent implements OnInit {
  sessionToken: string = '';

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService
              ) {
    this.sessionToken = String(this.activatedRoute.snapshot.paramMap.get('sessionToken'));
  }

  ngOnInit() {
    this.tartarusCoordinatorService.getLockSession(this.sessionToken).subscribe(result => {
      console.log(result);
    });
  }
}
