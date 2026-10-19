import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EventCardComponent } from './event-card.component';
import { LockEvent } from '../models/event';

describe('EventCardComponent', () => {
  let component: EventCardComponent;
  let fixture: ComponentFixture<EventCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(EventCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display event details when event is set', () => {
    component.event = new LockEvent({
      name: 'evt-1',
      counter: 42,
      eventType: 'LOCK',
      correlationId: 'corr-xyz-123',
      createdAt: new Date(),
      deliveries: [
        {
          botName: 'TelegramBot',
          delivered: true,
          deliveredAt: new Date(),
          acknowledged: true,
          acknowledgedAt: new Date()
        },
        {
          botName: 'DiscordBot',
          delivered: false,
          deliveredAt: null,
          acknowledged: false,
          acknowledgedAt: null
        }
      ]
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('LOCK');
    expect(compiled.textContent).toContain('#42');
    expect(compiled.textContent).toContain('corr-xyz-123');
    expect(compiled.textContent).toContain('TelegramBot');
    expect(compiled.textContent).toContain('DiscordBot');
    expect(compiled.textContent).toContain('Delivered');
    expect(compiled.textContent).toContain('Pending');
  });

  it('should compute icons and formatted event type correctly', () => {
    component.event = new LockEvent({ eventType: 'EMERGENCY_UNLOCK' });
    expect(component.icon).toBe('🚨');
    expect(component.formattedEventType).toBe('EMERGENCY UNLOCK');
    expect(component.badgeClass).toBe('bg-danger');

    component.event = new LockEvent({ eventType: 'ACCEPT_CONTRACT' });
    expect(component.icon).toBe('📝');
    expect(component.formattedEventType).toBe('ACCEPT CONTRACT');

    component.event = new LockEvent({ eventType: 'UNLOCK' });
    expect(component.icon).toBe('🔓');
  });
});
