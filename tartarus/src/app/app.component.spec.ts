import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AppComponent } from './app.component';
import { UserDataService } from './user-data.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'tartarus' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('tartarus');
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.navbar-brand')?.textContent).toContain('Tartarus');
  });

  it('should render accessible skip link, header, primary nav, and main landmark', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const skipLink = compiled.querySelector('a[href="#main-content"]');
    expect(skipLink?.textContent).toContain('Skip to main content');
    expect(compiled.querySelector('header nav[aria-label="Primary"]')).toBeTruthy();
    expect(compiled.querySelector('main#main-content')).toBeTruthy();
  });

  it('should surface session links and badges when sessions are active', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const userData = TestBed.inject(UserDataService);
    vi.spyOn(userData, 'hasAuthorSession').mockReturnValue(true);
    vi.spyOn(userData, 'hasLockUserSession').mockReturnValue(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Keyholder Active');
    expect(compiled.textContent).toContain('Lock Owner Active');
    expect(compiled.textContent).toContain('Lock Sessions');
    expect(compiled.textContent).toContain('My Lock');
    const lockSessionsLink = compiled.querySelector('a[href="/lock-sessions"]');
    expect(lockSessionsLink).toBeTruthy();
  });
});
