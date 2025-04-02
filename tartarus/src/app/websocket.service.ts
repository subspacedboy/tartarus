import { Injectable } from '@angular/core';
import {Observable, Subject} from 'rxjs';
import {UserDataService} from './user-data.service';
import {CryptoService} from './crypto.service';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private socket: WebSocket | null = null;
  private messageSubject = new Subject<string>();

  constructor(private userDataService: UserDataService,
              private cryptoService: CryptoService,) { }

  async connect(url: string): Promise<void> {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      let credentials = "?";
      if(this.userDataService.hasLockUserSessionKeyPair()){
        const subject = this.userDataService.getLockUserSessionToken();
        const jwt = await this.cryptoService.jwtForKeypair(subject, this.userDataService.getLockUserSessionKeyPair());
        credentials += `&lockUserSessionJwt=${jwt}`;
      }
      if(this.userDataService.hasAuthorSession()){
        const subject = this.userDataService.getAuthorName();
        const jwt = await this.cryptoService.jwtForKeypair(subject, this.userDataService.getAuthorKeypair());
        credentials += `&authorJwt=${jwt}`;
      }

      this.socket = new WebSocket(url + credentials);

      this.socket.onopen = () => console.log('WebSocket connected');
      this.socket.onmessage = (event) => this.messageSubject.next(event.data);
      this.socket.onerror = (error) => console.error('WebSocket Error:', error);
      this.socket.onclose = () => console.log('WebSocket disconnected');
    }
  }

  onMessage(): Observable<string> {
    return this.messageSubject.asObservable();
  }

  sendMessage(message: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(message);
    } else {
      console.error('WebSocket is not open');
    }
  }

  close(): void {
    this.socket?.close();
  }
}
