import {LockSession} from './lock-session';

export class LockUserSession {
  name?: string;
  lockSession?: LockSession;

  constructor(init?:Partial<LockUserSession>) {
    Object.assign(this, init);
  }
}
