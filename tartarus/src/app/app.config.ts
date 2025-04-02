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
import {provideHttpClient} from '@angular/common/http';
import {ConfigService} from './config.service';

function loadConfig(configService: ConfigService)
{
  console.log('Loading config...');
  configService.loadConfig().then(thingy => {});
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(),
    ConfigService,
    provideAppInitializer(() => loadConfig(inject(ConfigService))),
  ]
};
