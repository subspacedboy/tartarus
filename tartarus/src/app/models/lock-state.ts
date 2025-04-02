export class LockState {
  isLocked?: boolean;

  constructor(init?: Partial<LockState>) {
    Object.assign(this, init);
  }
}
