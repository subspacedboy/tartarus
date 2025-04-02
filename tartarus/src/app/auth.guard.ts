import {CanActivateFn, Router} from '@angular/router';
import {UserDataService} from './user-data.service';
import {inject} from '@angular/core';

export const authGuard: CanActivateFn = (route, state) => {
  const userDataService: UserDataService = inject(UserDataService);
  if(userDataService.isAlreadyLoggedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/']);
};
