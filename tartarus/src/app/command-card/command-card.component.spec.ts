import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { CommandCardComponent } from './command-card.component';
import { Command } from '../models/command';
import { SerialDeviceService } from '../serial-device.service';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { ToastService } from '../toast.service';

describe('CommandCardComponent', () => {
  let component: CommandCardComponent;
  let fixture: ComponentFixture<CommandCardComponent>;
  let serialDeviceService: SerialDeviceService;
  let coordinatorService: TartarusCoordinatorService;
  let toastService: ToastService;

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
    serialDeviceService = TestBed.inject(SerialDeviceService);
    coordinatorService = TestBed.inject(TartarusCoordinatorService);
    toastService = TestBed.inject(ToastService);
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

  it('should send payload over serial and acknowledge command when serial returns ack', async () => {
    const base64Body = btoa('command-payload');
    component.command = new Command({
      name: 'cmd-1',
      contractName: 'contract-1',
      state: 'PENDING',
      type: 'UNLOCK',
      body: base64Body
    });

    const mockResult = { type: 'ack' as const, resultBase64: 'mock-ack-base64' };
    vi.spyOn(serialDeviceService, 'sendCommand').mockResolvedValue(mockResult);
    const ackSpy = vi.spyOn(coordinatorService, 'manuallyAcknowledgeCommand').mockReturnValue(
      of(new Command({ name: 'cmd-1', contractName: 'contract-1', state: 'ACKNOWLEDGED', type: 'UNLOCK' }))
    );

    await component.sendViaSerial();

    expect(serialDeviceService.sendCommand).toHaveBeenCalledWith(base64Body);
    expect(ackSpy).toHaveBeenCalledWith('contract-1', 'cmd-1', 'mock-ack-base64');
    expect(component.command.state).toBe('ACKNOWLEDGED');
  });

  it('should display error message when serial returns error result', async () => {
    const toastSpy = vi.spyOn(toastService, 'showError');

    component.command = new Command({
      name: 'cmd-2',
      contractName: 'contract-1',
      state: 'PENDING',
      type: 'UNLOCK',
      body: btoa('payload')
    });

    const mockErrorResult = { type: 'error' as const, resultBase64: 'err-b64', errorMessage: 'Lock error' };
    vi.spyOn(serialDeviceService, 'sendCommand').mockResolvedValue(mockErrorResult);

    await component.sendViaSerial();

    expect(component.serialError).toBe('Lock error');
    expect(toastSpy).toHaveBeenCalledWith('Device error: Lock error');
  });
});
