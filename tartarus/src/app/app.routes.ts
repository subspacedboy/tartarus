import { Routes } from '@angular/router';
import {LandingPageComponent} from './landing-page/landing-page.component';
import {NewKeypairComponent} from './new-keypair/new-keypair.component';
import {NewSimpleContractComponent} from './new-simple-contract/new-simple-contract.component';
import {NewFullContractComponent} from './new-full-contract/new-full-contract.component';
import {LockStartComponent} from './lock-start/lock-start.component';
import {LoginComponent} from './login/login.component';
import {LockSessionsComponent} from './lock-sessions/lock-sessions.component';
import {MyLockComponent} from './my-lock/my-lock.component';
import {authGuard} from './auth.guard';
import {LockSessionDetailComponent} from './lock-session-detail/lock-session-detail.component';
import {ContractDetailComponent} from './contract-detail/contract-detail.component';

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
    canActivate: [authGuard],
    component: NewSimpleContractComponent
  },
  {
    path: 'lock-sessions/:sessionToken/full-contract',
    canActivate: [authGuard],
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
    path: 'lock-sessions',
    component: LockSessionsComponent
  },
  {
    path: 'lock-sessions/:sessionToken',
    canActivate: [authGuard],
    component: LockSessionDetailComponent
  },
  {
    path: 'lock-sessions/:sessionToken/contract/:contractName',
    canActivate: [authGuard],
    component: ContractDetailComponent
  },
  {
    path: 'my-lock',
    component: MyLockComponent
  }
];
