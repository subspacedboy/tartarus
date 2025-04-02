export class KnownToken {
  name?: string;
  state?: string;
  notes?: string;
  shareableToken?: string;
  createdAt?: Date;

  constructor(init?: Partial<KnownToken>) {
    Object.assign(this, init);
  }
}
