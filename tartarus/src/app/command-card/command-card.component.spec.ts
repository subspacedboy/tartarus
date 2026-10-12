import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CommandCardComponent } from './command-card.component';
import { Command } from '../models/command';

describe('CommandCardComponent', () => {
  let component: CommandCardComponent;
  let fixture: ComponentFixture<CommandCardComponent>;

  beforeEach(async () => {
    HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({
      createImageData: vi.fn().mockReturnValue({ data: [] }),
      putImageData: vi.fn(),
      drawImage: vi.fn(),
      clearRect: vi.fn(),
      fillRect: vi.fn()
    }) as any;

    await TestBed.configureTestingModule({
      imports: [CommandCardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CommandCardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should print base64 command body to console when drawQrCode is called with already base64 body', () => {
    const consoleSpy = vi.spyOn(console, 'log');
    const base64Body = btoa('sample-command-body');
    component.command = new Command({
      state: 'PENDING',
      type: 'UNLOCK',
      body: base64Body
    });

    const fakeButton = document.createElement('button');
    const fakeContainer = document.createElement('p');
    fakeContainer.appendChild(fakeButton);
    const fakeEvent = { currentTarget: fakeButton } as unknown as Event;

    component.drawQrCode(fakeEvent);

    expect(consoleSpy).toHaveBeenCalledWith(base64Body);
  });

  it('should encode raw body to base64 and print to console when drawQrCode is called with raw body', () => {
    const consoleSpy = vi.spyOn(console, 'log');
    const rawBody = 'raw command payload';
    component.command = new Command({
      state: 'PENDING',
      type: 'UNLOCK',
      body: rawBody
    });

    const fakeButton = document.createElement('button');
    const fakeContainer = document.createElement('p');
    fakeContainer.appendChild(fakeButton);
    const fakeEvent = { currentTarget: fakeButton } as unknown as Event;

    component.drawQrCode(fakeEvent);

    expect(consoleSpy).toHaveBeenCalledWith(btoa(rawBody));
  });

  it('should print base64 command body to console when "Show QR Code" button is clicked in template', () => {
    const consoleSpy = vi.spyOn(console, 'log');
    const base64Body = btoa('test-command-body');
    component.command = new Command({
      state: 'PENDING',
      type: 'UNLOCK',
      body: base64Body
    });
    component.showQrCodeCommands = true;
    fixture.detectChanges();

    const button: HTMLButtonElement | null = fixture.nativeElement.querySelector('button.btn-info');
    expect(button).not.toBeNull();
    button?.click();

    expect(consoleSpy).toHaveBeenCalledWith(base64Body);
  });
});

