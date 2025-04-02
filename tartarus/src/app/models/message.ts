export class Message {
  name? : string;
  body? : string;
  botName? : string;
  createdAt? : string;

  constructor(init?: Partial<Message>) {
    Object.assign(this, init);
  }
}
