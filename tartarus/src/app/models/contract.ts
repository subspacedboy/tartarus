import {LockState} from './lock-state';

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
  lockState?: LockState;
  notes?: string;

  constructor(init?: Partial<Contract>) {
    Object.assign(this, init);
  }
}
