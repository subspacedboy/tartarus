export class LockState {
  isLocked?: boolean;
  emergencyUnlocked?: boolean;

  constructor(init?: Partial<LockState>) {
    Object.assign(this, init);
  }
}
