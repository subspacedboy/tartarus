export class Bot {
  name?: string;
  description?: string;
  publicKey?: string;
  state?: string;
  authorSessionName?: string;
  internalName?: string;
  keyVersion?: number;
  createdAt?: string;
  updatedAt?: string;

  constructor(init?: Partial<Bot>) {
    Object.assign(this, init);
  }
}
