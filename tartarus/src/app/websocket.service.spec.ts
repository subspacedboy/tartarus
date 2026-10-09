import { TestBed } from '@angular/core/testing';

import { WebsocketService } from './websocket.service';

import testProviders from '../test-providers';
import { UserDataService } from './user-data.service';
import { CryptoService } from './crypto.service';
import { ConfigService } from './config.service';

describe('WebsocketService', () => {
  let service: WebsocketService;
  let userDataService: UserDataService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...testProviders]
    });
    service = TestBed.inject(WebsocketService);
    userDataService = TestBed.inject(UserDataService);
  });

  afterEach(() => {
    service.close();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should not connect when no credentials exist', async () => {
    localStorage.clear();
    await service.connect('ws://localhost:5002/ws/events');
    expect(service['socket']).toBeNull();
  });

  it('should not connect if only lock user keypair exists without a session token', async () => {
    localStorage.clear();
    userDataService.addPublicAndPrivateKeyToLocalUserSession('priv', 'pub');
    expect(userDataService.hasLockUserSessionKeyPair()).toBe(true);
    expect(userDataService.hasLockUserSession()).toBe(false);

    await service.connect('ws://localhost:5002/ws/events');
    expect(service['socket']).toBeNull();
  });

  it('should normalize ws and http urls to include /ws/events', () => {
    expect(service['normalizeWsUrl']('http://localhost:5002')).toBe('ws://localhost:5002/ws/events');
    expect(service['normalizeWsUrl']('ws://localhost:5002')).toBe('ws://localhost:5002/ws/events');
    expect(service['normalizeWsUrl']('ws://localhost:5002/ws/events')).toBe('ws://localhost:5002/ws/events');
    expect(service['normalizeWsUrl']('https://tartarus-api.subjugated.club:4446')).toBe('wss://tartarus-api.subjugated.club:4446/ws/events');
  });
});
