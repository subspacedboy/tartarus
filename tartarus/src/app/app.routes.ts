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
import {BtComponent} from './bt/bt.component';
import {KeyHelperComponent} from './key-helper/key-helper.component';
import {AdminLoginComponent} from './admin/admin-login/admin-login.component';
import {AdminContractsComponent} from './admin/admin-contracts/admin-contracts.component';
import {AdminContractDetailComponent} from './admin/admin-contract-detail/admin-contract-detail.component';
import {AdminAddFirmwareComponent} from "./admin/admin-add-firmware/admin-add-firmware.component";
import {adminGuard} from "./admin.guard";
import {LogoutLockUserComponent} from "./logout-lock-user/logout-lock-user.component";
import {AdminLandingPageComponent} from './admin/admin-landing-page/admin-landing-page.component';
import {AdminLockSessionsComponent} from './admin/admin-lock-sessions/admin-lock-sessions.component';
import {AdminLockSessionDetailComponent} from './admin/admin-lock-session-detail/admin-lock-session-detail.component';
import {UpdaterComponent} from './updater/updater.component';
import {ConsoleComponent} from './console/console.component';

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
    path: 'logout-lock-user',
    component: LogoutLockUserComponent
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
    path: 'bt',
    component: BtComponent
  },
  {
    path: 'key-helper',
    component: KeyHelperComponent
  },
  {
    path: 'updater',
    component: UpdaterComponent
  },
  {
    path: 'console',
    component: ConsoleComponent
  },
  {
    path: 'admin',
    component: AdminLandingPageComponent,
    canActivate: [adminGuard],
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
    path: 'admin/lock_sessions/:sessionToken',
    canActivate: [adminGuard],
    component: AdminLockSessionDetailComponent
  },
  {
    path: 'admin/lock_session/:sessionToken',
    canActivate: [adminGuard],
    component: AdminLockSessionDetailComponent
  },
  {
    path: 'admin/lock_sessions',
    pathMatch: 'full',
    canActivate: [adminGuard],
    component: AdminLockSessionsComponent
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
