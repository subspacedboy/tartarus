import {
  APP_INITIALIZER,
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import {ConfigService} from './config.service';
import {authInterceptor} from './auth.interceptor';

function loadConfig(configService: ConfigService) : Promise<void>
{
  //NB: This must return a promise or Angular won't wait.
  return configService.loadConfig();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptors([authInterceptor])),
    ConfigService,
    provideAppInitializer(() => loadConfig(inject(ConfigService))),
  ]
};
