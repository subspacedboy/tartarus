import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';

@Component({
  selector: 'app-lock-start',
  imports: [],
  templateUrl: './lock-start.component.html',
  styleUrl: './lock-start.component.scss'
})
export class LockStartComponent implements OnInit {
  session: string | null = null;
  publicKey: string | null = null;

  constructor(private activatedRoute: ActivatedRoute,
              private router: Router,
              private tartarusCoordinatorService: TartarusCoordinatorService,) {
    this.activatedRoute.queryParamMap.subscribe(params => {
      this.session = params.get('session');
      this.publicKey = params.get('publicKey');
    });
  }

  ngOnInit() {}
}
