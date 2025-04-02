export class Contract {
  name?: string;
  publicKey?: string | null;
  shareableToken?: string | null;
  state?: string | null;
  authorSessionName?: string | null;
  body?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  nextCounter?: number;
  serialNumber?: number;

  constructor(init?: Partial<Contract>) {
    Object.assign(this, init);
  }
}
