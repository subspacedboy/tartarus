import { Routes } from '@angular/router';
import {LandingPageComponent} from './landing-page/landing-page.component';
import {NewKeypairComponent} from './new-keypair/new-keypair.component';
import {NewSimpleContractComponent} from './new-simple-contract/new-simple-contract.component';
import {NewFullContractComponent} from './new-full-contract/new-full-contract.component';
import {LockStartComponent} from './lock-start/lock-start.component';
import {LoginComponent} from './login/login.component';
import {LockSessionsComponent} from './lock-sessions/lock-sessions.component';

export const routes: Routes = [
  {
    path: '',
    component: LandingPageComponent
  },
  {
    path: 'keypair',
    component: NewKeypairComponent
  },
  {
    path: 'lock-sessions/:sessionToken/simple-contract',
    component: NewSimpleContractComponent
  },
  {
    path: 'full-contract',
    component: NewFullContractComponent
  },
  {
    path: 'lock-start',
    component: LockStartComponent
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'lock-sessions/:sessionToken',
    component: LockSessionsComponent
  }
];
