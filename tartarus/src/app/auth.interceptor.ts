import { HttpInterceptorFn } from '@angular/common/http';
import {inject} from '@angular/core';
import {UserDataService} from './user-data.service';
import { SignJWT } from 'jose';
import {CryptoService} from './crypto.service';
import {from, switchMap} from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const skipAuth = req.headers.has('X-Skip-Auth');
  if (skipAuth) {
    const authReq = req.clone({ headers: req.headers.delete('X-Skip-Auth') });
    return next(authReq);
  }
  const requireAuthor = req.headers.has('X-Require-Auth');
  if (!requireAuthor) {
    return next(req);
  }

  const userDataService = inject(UserDataService);
  const cryptoService = inject(CryptoService);
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
        console.log(jwt);
        return next(
          req.clone({
            setHeaders: { Authorization: `Bearer ${jwt}` },
            headers: req.headers.delete('X-Require-Auth')
          })
        );
      })
    );
};
