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

  it('should initialize event queue signals with empty defaults', () => {
    expect(component.eventQueueCount()).toBeNull();
    expect(component.eventQueueItems()).toEqual([]);
    expect(component.eventQueueCtrlStatus()).toBe('idle');
    expect(component.showRawEventQueue()).toBe(false);
  });

  it('should parse event queue JSON list correctly', () => {
    const jsonStr = JSON.stringify([
      { slot: 0, contract: 1, counter: 0, type: 'AcceptContract' },
      { slot: 1, contract: 1, counter: 1, type: 'Lock' },
      { slot: 2, contract: 1, counter: 2, type: 'Unlock' },
    ]);

    component.parseEventQueueList(jsonStr);
    expect(component.eventQueueItems().length).toBe(3);
    expect(component.eventQueueItems()[0]).toEqual({
      slot: 0,
      contract: 1,
      counter: 0,
      type: 'AcceptContract',
    });
    expect(component.eventQueueItems()[1].type).toBe('Lock');
    expect(component.eventQueueItems()[2].type).toBe('Unlock');
    expect(component.eventQueueRaw()).toBe(jsonStr);
  });

  it('should handle invalid or non-array event queue JSON gracefully', () => {
    component.parseEventQueueList('not-json');
    expect(component.eventQueueItems()).toEqual([]);

    component.parseEventQueueList('{"error":"not-array"}');
    expect(component.eventQueueItems()).toEqual([]);
  });

  it('should toggle show raw event queue signal', () => {
    expect(component.showRawEventQueue()).toBe(false);
    component.toggleShowRawEventQueue();
    expect(component.showRawEventQueue()).toBe(true);
    component.toggleShowRawEventQueue();
    expect(component.showRawEventQueue()).toBe(false);
  });
});
