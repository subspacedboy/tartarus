import { TestBed } from '@angular/core/testing';
import { UserDataService } from './user-data.service';

describe('UserDataService', () => {
  let service: UserDataService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(UserDataService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Lock User Session', () => {
    it('should manage lock user session keypair', () => {
      expect(service.hasLockUserSessionKeyPair()).toBe(false);

      service.addPublicAndPrivateKeyToLocalUserSession('priv-key', 'pub-key');
      expect(service.hasLockUserSessionKeyPair()).toBe(true);

      const keys = service.getLockUserSessionKeyPair();
      expect(keys.privatePem).toBe('priv-key');
      expect(keys.publicPem).toBe('pub-key');
    });

    it('should manage lock user session token and logout', () => {
      expect(service.hasLockUserSession()).toBe(false);

      service.setLockUserSessionToken('user-token-123');
      expect(service.hasLockUserSession()).toBe(true);
      expect(service.getLockUserSessionToken()).toBe('user-token-123');

      service.addPublicAndPrivateKeyToLocalUserSession('priv-key', 'pub-key');
      service.logoutLockUserSession();

      expect(service.hasLockUserSession()).toBe(false);
      expect(service.hasLockUserSessionKeyPair()).toBe(false);
    });
  });

  describe('Author Session', () => {
    it('should manage author session keypair and token', () => {
      expect(service.hasAuthorSession()).toBe(false);

      service.addPublicAndPrivateKeyToAuthorSession('author-priv', 'author-pub', 'author-token-abc');
      expect(service.hasAuthorSession()).toBe(true);
      expect(service.getAuthorName()).toBe('author-token-abc');

      const keys = service.getAuthorKeypair();
      expect(keys.privatePem).toBe('author-priv');
      expect(keys.publicPem).toBe('author-pub');

      service.logoutAuthorSession();
      expect(service.hasAuthorSession()).toBe(false);
    });
  });

  describe('Admin Session', () => {
    it('should manage admin session keypair and token', () => {
      expect(service.hasAdminSession()).toBe(false);

      service.addPublicAndPrivateKeyToAdminSession('admin-priv', 'admin-pub', 'admin-token-xyz');
      expect(service.hasAdminSession()).toBe(true);
      expect(service.getAdminSessionToken()).toBe('admin-token-xyz');

      const keys = service.getAdminKeypair();
      expect(keys.privatePem).toBe('admin-priv');
      expect(keys.publicPem).toBe('admin-pub');
    });
  });
});
