import {SafetyKey} from './safety-key';

export class AppConfig {
  webUri?: string;
  wsUri?: string;
  apiUri?: string;
  mqttUri?: string;
  safetyKeys?: SafetyKey[];

  constructor(init?:Partial<AppConfig>) {
    Object.assign(this, init);
  }
}
