import { Injectable } from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {UserDataService} from './user-data.service';
import {CryptoService} from './crypto.service';
import {ConfigService} from './config.service';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<string>();
  private keepaliveInterval: any = null;
  private connectingPromise: Promise<void> | null = null;
  private connectionEpoch = 0;

  constructor(private userDataService: UserDataService,
              private cryptoService: CryptoService,
              private configService: ConfigService,) {

    const appConfig = this.configService.getConfig();
    if (appConfig?.wsUri) {
      this.connect(appConfig.wsUri).catch(err => {
        console.warn('Initial WebSocket connection skipped or failed:', err);
      });
    }
  }

  async connect(url?: string): Promise<void> {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    if (this.connectingPromise) {
      return this.connectingPromise;
    }

    this.connectingPromise = this.internalConnect(url);
    try {
      await this.connectingPromise;
    } finally {
      this.connectingPromise = null;
    }
  }

  private async internalConnect(url?: string): Promise<void> {
    const targetUrl = url ?? this.configService.getConfig()?.wsUri;
    if (!targetUrl) {
      return;
    }

    const currentEpoch = this.connectionEpoch;
    const params = new URLSearchParams();

    // 1. Lock User JWT: only attach if lock user session and keypair exist
    if (this.userDataService.hasLockUserSession() && this.userDataService.hasLockUserSessionKeyPair()) {
      const subject = this.userDataService.getLockUserSessionToken();
      if (subject && subject !== 'null' && subject !== 'undefined') {
        const jwt = await this.cryptoService.jwtForKeypair(subject, this.userDataService.getLockUserSessionKeyPair());
        params.set('lockUserSessionJwt', jwt);
      }
    }

    // 2. Author JWT: attach if author session exists
    if (this.userDataService.hasAuthorSession()) {
      const subject = this.userDataService.getAuthorName();
      if (subject && subject !== 'null' && subject !== 'undefined') {
        const jwt = await this.cryptoService.jwtForKeypair(subject, this.userDataService.getAuthorKeypair());
        params.set('authorJwt', jwt);
      }
    }

    // 3. Admin JWT: attach if admin session exists
    if (this.userDataService.hasAdminSession()) {
      const subject = this.userDataService.getAdminSessionToken();
      if (subject && subject !== 'null' && subject !== 'undefined') {
        const jwt = await this.cryptoService.jwtForKeypair(subject, this.userDataService.getAdminKeypair());
        params.set('adminJwt', jwt);
      }
    }

    // If connection was cancelled or re-triggered during JWT generation, abort
    if (currentEpoch !== this.connectionEpoch) {
      return;
    }

    const queryString = params.toString();
    if (!queryString) {
      // Avoid connecting without credentials to prevent 403 Forbidden handshake failure
      return;
    }

    // Double check that an existing open/connecting socket wasn't created while awaiting JWTs
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    const wsUrl = this.normalizeWsUrl(targetUrl);
    const socket = new WebSocket(`${wsUrl}?${queryString}`);
    this.socket = socket;

    socket.onopen = () => {
      console.log('WebSocket connected');
      this.startKeepalive();
    };
    socket.onmessage = (event) => this.messageSubject.next(event.data);
    socket.onerror = (error) => console.error('WebSocket Error:', error);
    socket.onclose = () => {
      console.log('WebSocket disconnected');
      this.stopKeepalive();
      if (this.socket === socket) {
        this.socket = null;
      }
    };
  }

  async reconnect(): Promise<void> {
    this.close();
    if (this.connectingPromise) {
      try {
        await this.connectingPromise;
      } catch {
        // ignore
      }
    }
    await this.connect();
  }

  private startKeepalive(): void {
    this.stopKeepalive();
    this.keepaliveInterval = setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        this.socket.send(JSON.stringify({ type: 'ping' }));
      }
    }, 30000);
  }

  private stopKeepalive(): void {
    if (this.keepaliveInterval) {
      clearInterval(this.keepaliveInterval);
      this.keepaliveInterval = null;
    }
  }

  onMessage(): Observable<string> {
    return this.messageSubject.asObservable();
  }

  close(): void {
    this.connectionEpoch++;
    this.stopKeepalive();
    if (this.socket) {
      const sock = this.socket;
      this.socket = null;
      sock.onopen = null;
      sock.onmessage = null;
      sock.onerror = null;
      sock.onclose = null;
      try {
        sock.close();
      } catch {
        // ignore
      }
    }
  }

  private normalizeWsUrl(url: string): string {
    let normalized = url.trim();
    if (normalized.startsWith('http://')) {
      normalized = 'ws://' + normalized.slice(7);
    } else if (normalized.startsWith('https://')) {
      normalized = 'wss://' + normalized.slice(8);
    }

    try {
      const parsed = new URL(normalized);
      if (!parsed.pathname || parsed.pathname === '/' || parsed.pathname === '') {
        parsed.pathname = '/ws/events';
      }
      return parsed.toString().replace(/\/$/, '');
    } catch {
      // Fallback if URL parsing fails on custom strings
      if (!normalized.includes('/ws/events')) {
        normalized = normalized.replace(/\/+$/, '') + '/ws/events';
      }
      return normalized;
    }
  }
}
