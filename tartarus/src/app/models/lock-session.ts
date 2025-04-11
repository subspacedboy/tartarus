import {LockUserSession} from './lock-user-session';
import {KnownToken} from './known-token';
import {LockState} from './lock-state';

export class LockSession {
  // public_key is secp1 compressed, web crypto can't use it.
  name?: string;
  publicKey?: string;
  publicPem?: string;
  shareToken?: string;
  totalControlToken?: string;
  lockUserSession?: LockUserSession;
  knownToken?: KnownToken;
  lockState?: LockState;
  availableForContract? : boolean;
  createdAt?: Date;
  updatedAt?: Date;

  constructor(init?:Partial<LockSession>) {
    Object.assign(this, init);

    this.createdAt = new Date(this.createdAt!!);
    this.updatedAt = new Date(this.updatedAt!!);
  }

  isTotalControlToken(): boolean {
    return this.totalControlToken !== undefined && this.totalControlToken !== null && this.totalControlToken !== "";
  }

  isStale() : boolean {
    const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
    // const updatedAt = new Date(this.updatedAt!!)
    return this.updatedAt!! < oneDayAgo;
  }
}
