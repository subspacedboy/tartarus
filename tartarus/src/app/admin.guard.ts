import { CanActivateFn } from '@angular/router';
import {UserDataService} from './user-data.service';
import {inject} from '@angular/core';

export const adminGuard: CanActivateFn = (route, state) => {
  let userDataService = inject(UserDataService);
  return userDataService.hasAdminSession();
};
