export class Bot {
  name? : string;
  description? : string;
  publicKey? : string;
  createdAt? : string;
  updatedAt? : string;

  constructor(init?:Partial<Bot>) {
    Object.assign(this, init);
  }
}
