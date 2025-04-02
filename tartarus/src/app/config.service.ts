import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';
import {AppConfig} from './models/app-config';
import {environment} from '../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  config?: AppConfig;

  constructor(private http: HttpClient) {
  }

  async loadConfig(): Promise<void> {
    this.config = await firstValueFrom(this.http.get<AppConfig>(`/${environment.config}`));
    const overrides = this.getOverrides();
    if(overrides != null) {
      this.config.wsUri = overrides.wsUri;
      this.config.apiUri = overrides.apiUri;
    }
  }

  getConfig(): AppConfig {
    return this.config!
  }

  setOverride(apiUri: string, wsUri: string): void {
    localStorage.setItem("apiUri", apiUri);
    localStorage.setItem("wsUri", apiUri);
  }

  getOverrides(): {"apiUri": string, wsUri: string} | null {
    const wsUri = localStorage.getItem("wsUri");
    const apiUri = localStorage.getItem("apiUri");

    if(wsUri && apiUri){
      return {"apiUri": apiUri, "wsUri": wsUri};
    } else {
      return null;
    }
  }

  clearOverride(): void {
    localStorage.removeItem("apiUri");
    localStorage.removeItem("wsUri");
  }
}
