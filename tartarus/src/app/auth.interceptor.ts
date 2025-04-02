import { HttpInterceptorFn } from '@angular/common/http';
import {inject} from '@angular/core';
import {UserDataService} from './user-data.service';
import { SignJWT } from 'jose';
import {CryptoService} from './crypto.service';
import {from, switchMap} from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const requireAuthor = req.headers.has('X-Require-Author');
  const requireLockUser = req.headers.has('X-Require-LockUser');
  if (!requireAuthor && !requireLockUser) {
    return next(req);
  }

  const userDataService = inject(UserDataService);
  const cryptoService = inject(CryptoService);

  if(requireAuthor) {
    const name = userDataService.getAuthorName();

    return from(cryptoService.importKeyPairForECDSA(`${userDataService.getAuthorKeypair().privatePem}${userDataService.getAuthorKeypair().publicPem}`))
      .pipe(
        switchMap(thingy =>
          from(new SignJWT({ sub: name, name: name })
            .setProtectedHeader({ alg: 'ES256' })
            .setIssuedAt()
            .setExpirationTime('1h')
            .sign(thingy.privateKey))
        ),
        switchMap(jwt => {
          return next(
            req.clone({
              setHeaders: { Authorization: `Bearer ${jwt}` },
              headers: req.headers.delete('X-Require-Author')
            })
          );
        })
      );
  } else {
    const name = userDataService.getLockUserSessionToken();

    return from(cryptoService.importKeyPairForECDSA(`${userDataService.getLockUserSessionKeyPair().privatePem}${userDataService.getLockUserSessionKeyPair().publicPem}`))
      .pipe(
        switchMap(thingy =>
          from(new SignJWT({ sub: name, name: name })
            .setProtectedHeader({ alg: 'ES256' })
            .setIssuedAt()
            .setExpirationTime('1h')
            .sign(thingy.privateKey))
        ),
        switchMap(jwt => {
          return next(
            req.clone({
              setHeaders: { Authorization: `Bearer ${jwt}` },
              headers: req.headers.delete('X-Require-LockUser')
            })
          );
        })
      );
  }
};
