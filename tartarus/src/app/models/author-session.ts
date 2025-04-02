export class AuthorSession {
  sessionToken?: string;

  constructor(init?:Partial<AuthorSession>) {
    Object.assign(this, init);
  }
}
