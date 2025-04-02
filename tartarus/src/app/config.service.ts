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
  }

  getConfig(): AppConfig {
    return this.config!
  }
}
