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

  hasLockUserSessionKeyPair() {
    return localStorage.getItem("lock_user_private_pem") !== null;
  }

  getLockUserSessionKeyPair() {
    const privatePem = localStorage.getItem("lock_user_private_pem");
    const publicPem = localStorage.getItem("lock_user_public_pem");

    return { privatePem, publicPem} ;
  }

  addPublicAndPrivateKeyToLocalUserSession(privatePem: string, publicPem: string) {
    localStorage.setItem('lock_user_private_pem', privatePem);
    localStorage.setItem('lock_user_public_pem', publicPem);
  }

  setLockUserSessionToken(token: string) {
    localStorage.setItem('has_lock_user_session', 'true');
    localStorage.setItem('lock_user_session_token', token);
  }

  getLockUserSessionToken() : string {
    return String(localStorage.getItem('lock_user_session_token'));
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
