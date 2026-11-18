import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AdminContractDetailComponent } from './admin-contract-detail.component';
import { TartarusCoordinatorService } from '../../tartarus-coordinator.service';
import { WebsocketService } from '../../websocket.service';
import { ToastService } from '../../toast.service';
import { Contract } from '../../models/contract';
import { Command } from '../../models/command';

describe('AdminContractDetailComponent', () => {
  let component: AdminContractDetailComponent;
  let fixture: ComponentFixture<AdminContractDetailComponent>;
  let coordinatorSpy: any;

  beforeEach(async () => {
    coordinatorSpy = {
      getContractByNameForAdmin: vi.fn().mockReturnValue(
        of(new Contract({ name: 'c-test', state: 'ACCEPTED' }))
      ),
      getCommandsForContractForAdmin: vi.fn().mockReturnValue(of([])),
      getEventsForContractForAdmin: vi.fn().mockReturnValue(of([])),
      markContractError: vi.fn().mockReturnValue(
        of(new Contract({ name: 'c-test', state: 'ERROR' }))
      ),
      abortContract: vi.fn().mockReturnValue(of(new Contract({ name: 'c-test', state: 'ABORTED' }))),
      fullReset: vi.fn().mockReturnValue(of(new Contract({ name: 'c-test', state: 'ABORTED' }))),
    };

    const wsSpy = {
      connect: vi.fn(),
      onMessage: vi.fn().mockReturnValue(of()),
    };

    const toastSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminContractDetailComponent],
      providers: [
        provideHttpClient(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'c-test' } } } },
        { provide: TartarusCoordinatorService, useValue: coordinatorSpy },
        { provide: WebsocketService, useValue: wsSpy },
        { provide: ToastService, useValue: toastSpy },
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminContractDetailComponent);
    component = fixture.componentInstance;
    vi.spyOn(component, 'parseContract').mockImplementation(() => {});
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show Move to Error State button when ACCEPTED and ACCEPT_CONTRACT failed', () => {
    component.contract = new Contract({ name: 'c-test', state: 'ACCEPTED' });
    component.commands = [new Command({ name: 'cmd-1', type: 'ACCEPT_CONTRACT', state: 'ERROR', message: 'Lock already under contract' })];
    fixture.detectChanges();

    const btn: HTMLButtonElement | null = fixture.nativeElement.querySelector('button');
    expect(component.hasFailedAcceptContractCommand).toBe(true);
    expect(btn?.textContent).toContain('Move to Error State');

    btn?.click();
    expect(coordinatorSpy.markContractError).toHaveBeenCalledWith('c-test');
  });

  it('should not show Move to Error State button when ACCEPT_CONTRACT is PENDING', () => {
    component.contract = new Contract({ name: 'c-test', state: 'ACCEPTED' });
    component.commands = [new Command({ name: 'cmd-1', type: 'ACCEPT_CONTRACT', state: 'PENDING' })];
    fixture.detectChanges();

    expect(component.hasFailedAcceptContractCommand).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Move to Error State');
  });
});
