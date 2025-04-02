import { Routes } from '@angular/router';
import {LandingPageComponent} from './landing-page/landing-page.component';
import {NewKeypairComponent} from './new-keypair/new-keypair.component';
import {NewSimpleContractComponent} from './new-simple-contract/new-simple-contract.component';
import {NewFullContractComponent} from './new-full-contract/new-full-contract.component';

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
    path: 'simple-contract',
    component: NewSimpleContractComponent
  },
  {
    path: 'full-contract',
    component: NewFullContractComponent
  }
];
