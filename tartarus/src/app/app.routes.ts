import { Routes } from '@angular/router';
import {LandingPageComponent} from './landing-page/landing-page.component';
import {NewKeypairComponent} from './new-keypair/new-keypair.component';
import {NewFullContractComponent} from './new-full-contract/new-full-contract.component';
import {LockStartComponent} from './lock-start/lock-start.component';
import {LoginComponent} from './login/login.component';
import {LockSessionsComponent} from './lock-sessions/lock-sessions.component';
import {MyLockComponent} from './my-lock/my-lock.component';
import {authGuard} from './auth.guard';
import {LockSessionDetailComponent} from './lock-session-detail/lock-session-detail.component';
import {ContractDetailComponent} from './contract-detail/contract-detail.component';
import {CoordinatorConfigurationComponent} from './coordinator-configuration/coordinator-configuration.component';
import {LogoutAuthorComponent} from './logout-author/logout-author.component';
import {LockUserContractDetailComponent} from './lock-user-contract-detail/lock-user-contract-detail.component';
import {WifiHelperComponent} from './wifi-helper/wifi-helper.component';
import {KeyHelperComponent} from './key-helper/key-helper.component';
import {AdminLoginComponent} from './admin/admin-login/admin-login.component';
import {AdminContractsComponent} from './admin/admin-contracts/admin-contracts.component';
import {AdminContractDetailComponent} from './admin/admin-contract-detail/admin-contract-detail.component';
import {AdminAddFirmwareComponent} from "./admin/admin-add-firmware/admin-add-firmware.component";
import {adminGuard} from "./admin.guard";

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
    path: 'logout-author',
    component: LogoutAuthorComponent
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
  },
  {
    path: 'my-lock/contract/:contractName',
    component: LockUserContractDetailComponent
  },
  {
    path: 'configuration',
    component: CoordinatorConfigurationComponent
  },
  {
    path: 'wifi-helper',
    component: WifiHelperComponent
  },
  {
    path: 'key-helper',
    component: KeyHelperComponent
  },
  {
    path: 'admin/login',
    component: AdminLoginComponent
  },
  {
    path: 'admin/contracts',
    canActivate: [adminGuard],
    component: AdminContractsComponent
  },
  {
    path: 'admin/contract/:contractName',
    canActivate: [adminGuard],
    component: AdminContractDetailComponent
  },
  {
    path: 'admin/add-firmware',
    canActivate: [adminGuard],
    component: AdminAddFirmwareComponent
  }
];
