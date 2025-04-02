export class AdminSession {
  name? : string;

  constructor(init?:Partial<AdminSession>) {
    Object.assign(this, init);
  }
}
