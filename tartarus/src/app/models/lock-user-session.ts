export class LockUserSession {
  name?: string;

  constructor(init?:Partial<LockUserSession>) {
    Object.assign(this, init);
  }
}
