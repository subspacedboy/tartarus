export class AppConfig {
  url?: string;
  apiUrl?: string;
  mqttUri?: string;

  constructor(init?:Partial<AppConfig>) {
    Object.assign(this, init);
  }
}
