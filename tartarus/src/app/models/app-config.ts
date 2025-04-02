export class AppConfig {
  url?: string;
  apiUrl?: string;

  constructor(init?:Partial<AppConfig>) {
    Object.assign(this, init);
  }
}
