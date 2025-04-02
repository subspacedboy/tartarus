import { HttpInterceptorFn } from '@angular/common/http';
import {inject} from '@angular/core';
import {UserDataService} from './user-data.service';
import { SignJWT } from 'jose';
import {CryptoService} from './crypto.service';
import {from, switchMap} from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const requireAuthor = req.headers.has('X-Require-Author');
  const requireLockUser = req.headers.has('X-Require-LockUser');
  const requireAdminUser = req.headers.has('X-Require-Admin');

  if (!requireAuthor && !requireLockUser && !requireAdminUser) {
    return next(req);
  }

  const userDataService = inject(UserDataService);
  const cryptoService = inject(CryptoService);

  if(requireAuthor) {
    const name = userDataService.getAuthorName();
    return from(cryptoService.jwtForKeypair(name, userDataService.getAuthorKeypair())).pipe(
      switchMap(jwt => {
        return next(
          req.clone({
            setHeaders: { Authorization: `Bearer ${jwt}` },
            headers: req.headers.delete('X-Require-Author')
          }));
      })
    );
  } else if(requireAdminUser) {
    const name = userDataService.getAdminSessionToken();
    return from(cryptoService.jwtForKeypair(name, userDataService.getAdminKeypair())).pipe(
      switchMap(jwt => {
        return next(
          req.clone({
            setHeaders: { Authorization: `Bearer ${jwt}` },
            headers: req.headers.delete('X-Require-Admin')
          }));
      })
    );
  } else if(requireLockUser) {
    const name = userDataService.getLockUserSessionToken();
    return from(cryptoService.jwtForKeypair(name, userDataService.getLockUserSessionKeyPair())).pipe(
      switchMap(jwt => {
        return next(
          req.clone({
            setHeaders: { Authorization: `Bearer ${jwt}` },
            headers: req.headers.delete('X-Require-LockUser')
          }));
      })
    );
  } else {
    throw "Unreachable";
  }
};
