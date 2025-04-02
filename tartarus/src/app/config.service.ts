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
    firstValueFrom(this.http.get<AppConfig>(`/${environment.config}`)).then(c => {
      this.config = c;
    });
  }

  async loadConfig(): Promise<void> {
    firstValueFrom(this.http.get<AppConfig>(`/${environment.config}`)).then(c => {
      this.config = c;
    });
  }

  getConfig(): AppConfig {
    return this.config!
  }
}
