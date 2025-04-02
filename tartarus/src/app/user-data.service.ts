import { Injectable } from '@angular/core';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UserDataService {

  constructor() { }

  addLockSession(token: string, publicKey: string) {
    const key = 'lock_sessions';
    const existingTokens = new Set(JSON.parse(localStorage.getItem(key) || '[]'));

    if (!existingTokens.has(token)) {
      existingTokens.add(token);
      localStorage.setItem(key, JSON.stringify(Array.from(existingTokens)));
    }

    const token_to_pubkey_key = `${token}-key`;
    localStorage.setItem(token_to_pubkey_key, JSON.stringify(publicKey));
  }

  getLockSessions(): string[] {
    const key = 'lock_sessions';
    return JSON.parse(localStorage.getItem(key) || '[]');
  }

  hasLockSessions(): boolean {
    return this.getLockSessions().length > 0;
  }

  addlockSessionTokenView(lockSesssionShareToken: string) {
    const key = 'available_shareable_tokens';
    const existingTokens = new Set(JSON.parse(localStorage.getItem(key) || '[]'));

    if (!existingTokens.has(lockSesssionShareToken)) {
      existingTokens.add(lockSesssionShareToken);
      localStorage.setItem(key, JSON.stringify(Array.from(existingTokens)));
    }
  }

  getLockSessionTokenViews(): string[] {
    const key = 'available_shareable_tokens';
    return JSON.parse(localStorage.getItem(key) || '[]');
  }

  getAuthorKeypair() {
    const privatePem = localStorage.getItem("private_pem");
    const publicPem = localStorage.getItem("public_pem");

    return { privatePem, publicPem} ;
  }

  addPublicAndPrivateKeyToLocalSession(privatePEM : string, publicPem: string, token: string) {
    localStorage.setItem('private_pem', privatePEM);
    localStorage.setItem('public_pem', publicPem);

    localStorage.setItem('has_author_session', 'true');
    localStorage.setItem('author_session_token', token);
  }

  logoutAuthorSession() {
    localStorage.removeItem('private_pem');
    localStorage.removeItem('public_pem');

    localStorage.removeItem('has_author_session');
    localStorage.removeItem('author_session_token');
  }

  getAuthorName() : string {
    return String(localStorage.getItem('author_session_token'));
  }

  isAlreadyLoggedIn(): boolean {
    return localStorage.getItem('author_session_token') !== null;
  }
}
