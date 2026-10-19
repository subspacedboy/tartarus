export interface EventDelivery {
  botName: string;
  delivered: boolean;
  deliveredAt?: Date | null;
}

export class LockEvent {
  name?: string;
  sessionToken?: string;
  contractName?: string;
  contractSerialNumber?: number;
  counter?: number;
  eventType?: string;
  correlationId?: string | null;
  deliveries?: EventDelivery[];
  createdAt?: Date | null;

  constructor(init?: Partial<LockEvent>) {
    Object.assign(this, init);
    if (this.createdAt) {
      this.createdAt = new Date(this.createdAt);
    }
    if (this.deliveries) {
      this.deliveries = this.deliveries.map(d => ({
        ...d,
        deliveredAt: d.deliveredAt ? new Date(d.deliveredAt) : null,
      }));
    }
  }
}
