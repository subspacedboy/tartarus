export class Bot {
  name? : string;
  description? : string;
  publicKey? : string;

  constructor(init?:Partial<Bot>) {
    Object.assign(this, init);
  }
}
