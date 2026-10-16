import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BtComponent } from './bt.component';

describe('BtComponent', () => {
  let component: BtComponent;
  let fixture: ComponentFixture<BtComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BtComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BtComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize forms with default empty values', () => {
    expect(component.authForm.get('pin')?.value).toBe('');
    expect(component.wifiForm.get('ssid')?.value).toBe('');
    expect(component.wifiForm.get('password')?.value).toBe('');
    expect(component.commandForm.get('command')?.value).toBe('');
  });

  it('should validate 6-digit PIN format', () => {
    const pinControl = component.authForm.get('pin');

    pinControl?.setValue('');
    expect(pinControl?.valid).toBe(false);

    pinControl?.setValue('12345'); // 5 digits - invalid
    expect(pinControl?.valid).toBe(false);

    pinControl?.setValue('1234567'); // 7 digits - invalid
    expect(pinControl?.valid).toBe(false);

    pinControl?.setValue('abcdef'); // non-digits - invalid
    expect(pinControl?.valid).toBe(false);

    pinControl?.setValue('123456'); // 6 digits - valid
    expect(pinControl?.valid).toBe(true);
  });

  it('should initialize with unauthenticated state', () => {
    expect(component.isAuthenticated()).toBe(false);
    expect(component.authLevel()).toBe('unauthenticated');
    expect(component.isLockedOut()).toBe(false);
  });

  it('should update authentication signals on auth level update', () => {
    component.handleAuthLevelUpdate('authenticated');
    expect(component.isAuthenticated()).toBe(true);
    expect(component.isLockedOut()).toBe(false);

    component.handleAuthLevelUpdate('locked_out');
    expect(component.isAuthenticated()).toBe(false);
    expect(component.isLockedOut()).toBe(true);

    component.handleAuthLevelUpdate('unauthenticated');
    expect(component.isAuthenticated()).toBe(false);
    expect(component.isLockedOut()).toBe(false);
  });
});
