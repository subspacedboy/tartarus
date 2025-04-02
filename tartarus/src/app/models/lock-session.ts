export class LockSession {
  // public_key is secp1 compressed, web crypto can't use it.
  public_key?: string;
  public_pem?: string;
  session?: string;

  constructor(init?:Partial<LockSession>) {
    // if (init!.associatedTask) {
    //   this.associatedTask = new Task(init!.associatedTask);
    //   delete init?.associatedTask;
    // }

    Object.assign(this, init);
  }
}
