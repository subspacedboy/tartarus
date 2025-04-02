export class SafetyKey {
  name? : string;
  publicKey? : string;

  constructor(init?:Partial<SafetyKey>) {
    Object.assign(this, init);
  }
}
