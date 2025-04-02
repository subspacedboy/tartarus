import {LockUserSession} from './lock-user-session';
import {KnownToken} from './known-token';

export class LockSession {
  // public_key is secp1 compressed, web crypto can't use it.
  publicKey?: string;
  publicPem?: string;
  shareToken?: string;
  totalControlToken?: string;
  lockUserSession?: LockUserSession;
  knownToken?: KnownToken;

  constructor(init?:Partial<LockSession>) {
    // if (init!.associatedTask) {
    //   this.associatedTask = new Task(init!.associatedTask);
    //   delete init?.associatedTask;
    // }

    Object.assign(this, init);
  }

  isTotalControlToken(): boolean {
    return this.totalControlToken !== undefined && this.totalControlToken !== null;
  }
}
