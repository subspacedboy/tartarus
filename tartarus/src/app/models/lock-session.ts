export class LockSession {
  // public_key is secp1 compressed, web crypto can't use it.
  publicKey?: string;
  publicPem?: string;
  shareToken?: string;
  totalControlToken?: string;

  constructor(init?:Partial<LockSession>) {
    // if (init!.associatedTask) {
    //   this.associatedTask = new Task(init!.associatedTask);
    //   delete init?.associatedTask;
    // }

    Object.assign(this, init);
  }

  getShareLink(): string {
    return `http://localhost:4200/lock-sessions/${this.shareToken}`;
  }

  getTotalControlLink(): string {
    return `http://localhost:4200/lock-sessions/${this.totalControlToken}`;
  }
}
