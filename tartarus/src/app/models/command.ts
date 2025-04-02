export class Command {
  name? : string;
  authorName? : string;
  contractName? : string;
  state? : string;
  type? : string;
  message? : string;
  body? : string;
  counter?: number;
  serialNumber?: number;
  createdAt?: string;
  updatedAt?: string;

  constructor(init?: Partial<Command>) {
    Object.assign(this, init);
  }
}
