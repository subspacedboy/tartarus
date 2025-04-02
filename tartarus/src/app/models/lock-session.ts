export class LockSession {
  public_key?: string;
  session?: string;

  constructor(init?:Partial<LockSession>) {
    // if (init!.associatedTask) {
    //   this.associatedTask = new Task(init!.associatedTask);
    //   delete init?.associatedTask;
    // }

    Object.assign(this, init);
  }
}
