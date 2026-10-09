export interface ContractBotPermissions {
  receiveEvents?: boolean;
  canUnlock?: boolean;
  canLock?: boolean;
  canRelease?: boolean;
}

export interface ContractBot {
  name: string;
  publicKey?: string;
  permissions?: ContractBotPermissions;
  description?: string;
  createdAt?: string;
}

export class ContractDescription {
  constructor(
    public isTemporaryUnlockAllowed: boolean,
    public terms: string,
    public endCondition: string,
    public bots?: ContractBot[]) {}
}

