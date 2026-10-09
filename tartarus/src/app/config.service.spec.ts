import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ConfigService } from './config.service';
import { AppConfig } from './models/app-config';
import { environment } from '../environments/environment';

describe('ConfigService', () => {
  let service: ConfigService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        ConfigService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(ConfigService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
    httpTestingController.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return default AppConfig instance when config is not yet loaded', () => {
    const config = service.getConfig();
    expect(config).toBeInstanceOf(AppConfig);
  });

  it('should set, get, and clear overrides in localStorage', () => {
    expect(service.getOverrides()).toBeNull();

    service.setOverride('http://api.local', 'ws://api.local');
    const overrides = service.getOverrides();
    expect(overrides).toEqual({ apiUri: 'http://api.local', wsUri: 'ws://api.local' });

    service.clearOverride();
    expect(service.getOverrides()).toBeNull();
  });

  it('should load config via http and apply overrides if present', async () => {
    service.setOverride('http://override.api', 'ws://override.ws');

    const loadPromise = service.loadConfig();

    const req = httpTestingController.expectOne(`/${environment.config}`);
    expect(req.request.method).toBe('GET');
    req.flush(new AppConfig({
      apiUri: 'http://default.api',
      wsUri: 'ws://default.ws',
      webUri: 'http://default.web'
    }));

    await loadPromise;

    const config = service.getConfig();
    expect(config.apiUri).toBe('http://override.api');
    expect(config.wsUri).toBe('ws://override.ws');
    expect(config.webUri).toBe('http://default.web');
  });
});
