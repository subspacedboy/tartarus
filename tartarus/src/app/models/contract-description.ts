export class ContractDescription {
  constructor(
    public isTemporaryUnlockAllowed: boolean,
    public terms: string,
    public endCondition: string,
    public bots?: any[]) {}
}
