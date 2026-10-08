import { LockSession } from './lock-session';

describe('LockSession', () => {
  it('should initialize properties correctly from partial init', () => {
    const now = new Date();
    const session = new LockSession({
      name: 'lock-1',
      sessionToken: 'token-abc',
      createdAt: now,
      updatedAt: now,
      totalControlToken: 'tc-123'
    });

    expect(session.name).toBe('lock-1');
    expect(session.sessionToken).toBe('token-abc');
    expect(session.isTotalControlToken()).toBe(true);
  });

  it('isTotalControlToken should return false when undefined, null, or empty string', () => {
    const session1 = new LockSession({ totalControlToken: undefined });
    expect(session1.isTotalControlToken()).toBe(false);

    const session2 = new LockSession({ totalControlToken: '' });
    expect(session2.isTotalControlToken()).toBe(false);
  });

  it('isStale should return true when updatedAt is older than 24 hours', () => {
    const twoDaysAgo = new Date(Date.now() - 48 * 60 * 60 * 1000);
    const session = new LockSession({ updatedAt: twoDaysAgo });
    expect(session.isStale()).toBe(true);
  });

  it('isStale should return false when updatedAt is within 24 hours', () => {
    const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
    const session = new LockSession({ updatedAt: oneHourAgo });
    expect(session.isStale()).toBe(false);
  });
});
