import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class UserDataService {

  constructor() { }

  hasLockUserSessionKeyPair() {
    return localStorage.getItem("lock_user_private_pem") !== null;
  }

  getLockUserSessionKeyPair() {
    const privatePem = localStorage.getItem("lock_user_private_pem");
    const publicPem = localStorage.getItem("lock_user_public_pem");

    return {privatePem, publicPem} ;
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

  hasLockUserSession(): boolean {
    return localStorage.getItem("lock_user_session_token") !== null;
  }

  getAuthorKeypair() {
    const privatePem = localStorage.getItem("private_pem");
    const publicPem = localStorage.getItem("public_pem");

    return { privatePem, publicPem} ;
  }

  addPublicAndPrivateKeyToAuthorSession(privatePEM : string, publicPem: string, token: string) {
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

  logoutLockUserSession() {
    localStorage.removeItem('lock_user_private_pem');
    localStorage.removeItem('lock_user_public_pem');

    localStorage.removeItem('has_lock_user_session');
    localStorage.removeItem('lock_user_session_token');

    localStorage.removeItem('lock_sessions');
  }

  getAuthorName() : string {
    return String(localStorage.getItem('author_session_token'));
  }

  hasAuthorSession(): boolean {
    return localStorage.getItem('author_session_token') !== null;
  }

  // ADMIN sessions

  hasAdminSession(): boolean {
    return localStorage.getItem('admin_session_token') !== null;
  }

  addPublicAndPrivateKeyToAdminSession(privatePem: string, publicPem: string, token : string) {
    localStorage.setItem('admin_private_pem', privatePem);
    localStorage.setItem('admin_public_pem', publicPem);

    localStorage.setItem('has_admin_session', 'true');
    localStorage.setItem('admin_session_token', token);
  }

  getAdminKeypair() {
    const privatePem = localStorage.getItem("admin_private_pem");
    const publicPem = localStorage.getItem("admin_public_pem");

    return { privatePem, publicPem} ;
  }

  getAdminSessionToken() : string {
    return String(localStorage.getItem('admin_session_token'));
  }
}
