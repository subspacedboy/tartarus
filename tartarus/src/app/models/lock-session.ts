import {LockUserSession} from './lock-user-session';
import {KnownToken} from './known-token';
import {LockState} from './lock-state';

export class LockSession {
  // public_key is secp1 compressed, web crypto can't use it.
  publicKey?: string;
  publicPem?: string;
  shareToken?: string;
  totalControlToken?: string;
  lockUserSession?: LockUserSession;
  knownToken?: KnownToken;
  lockState?: LockState;
  availableForContract? : boolean;

  constructor(init?:Partial<LockSession>) {
    Object.assign(this, init);
  }

  isTotalControlToken(): boolean {
    return this.totalControlToken !== undefined && this.totalControlToken !== null && this.totalControlToken !== "";
  }
}
