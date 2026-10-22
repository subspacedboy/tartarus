import { Component, EventEmitter, Output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TartarusCoordinatorService } from '../../tartarus-coordinator.service';
import { ToastService } from '../../toast.service';
import { SerialDeviceService } from '../../serial-device.service';
import {
  ProvisioningPreviewResult,
  ProvisionedHardwareUnitDetail
} from '../../models/provisioned-hardware-unit';
import { HlmDialogImports } from '../../shared/ui/ui-dialog-helm';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmButtonDirective } from '../../shared/ui/ui-button-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';
import { HlmInputDirective } from '../../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../../shared/ui/ui-label-helm';
import { HlmAlertImports } from '../../shared/ui/ui-alert-helm';
import { HlmSpinnerComponent } from '../../shared/ui/ui-spinner-helm';

@Component({
  selector: 'app-provisioning-ingest-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ...HlmDialogImports,
    ...HlmCardImports,
    HlmButtonDirective,
    HlmBadgeDirective,
    HlmInputDirective,
    HlmLabelDirective,
    ...HlmAlertImports,
    HlmSpinnerComponent,
  ],
  templateUrl: './provisioning-ingest-modal.component.html',
  styleUrl: './provisioning-ingest-modal.component.scss'
})
export class ProvisioningIngestModalComponent {
  private coordinatorService = inject(TartarusCoordinatorService);
  private toastService = inject(ToastService);
  public serialService = inject(SerialDeviceService);

  @Output() unitSaved = new EventEmitter<ProvisionedHardwareUnitDetail>();
  @Output() modalClosed = new EventEmitter<void>();

  rawPayload = signal<string>('');
  label = signal<string>('');
  notes = signal<string>('');

  preview = signal<ProvisioningPreviewResult | null>(null);
  isLoadingPreview = signal<boolean>(false);
  isSaving = signal<boolean>(false);
  isSerialBusy = signal<boolean>(false);
  serialStatusMessage = signal<string>('');

  private previewDebounceTimer: any = null;

  onPayloadInput(value: string) {
    this.rawPayload.set(value);
    if (this.previewDebounceTimer) {
      clearTimeout(this.previewDebounceTimer);
    }
    if (!value.trim()) {
      this.preview.set(null);
      return;
    }
    this.previewDebounceTimer = setTimeout(() => {
      this.triggerPreview();
    }, 400);
  }

  triggerPreview() {
    const raw = this.rawPayload().trim();
    if (!raw) return;

    this.isLoadingPreview.set(true);
    this.coordinatorService.verifyProvisioningPreview({
      rawPayload: raw,
      label: this.label().trim() || undefined,
      notes: this.notes().trim() || undefined
    }).subscribe({
      next: (res) => {
        this.preview.set(res);
        this.isLoadingPreview.set(false);
      },
      error: (err) => {
        this.isLoadingPreview.set(false);
        const errMsg = err?.error?.message || err?.message || 'Verification failed';
        this.preview.set({
          valid: false,
          provisioningMode: 'UNKNOWN',
          hardwareId: '',
          sessionToken: '',
          leafCertSubject: '',
          leafCertIssuer: '',
          leafCertSerialHex: '',
          chainValid: false,
          attestationDigestValid: false,
          signatureValid: false,
          efuseStatus: null,
          warnings: [],
          errors: [errMsg]
        });
      }
    });
  }

  onFileSelected(event: any) {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      if (content) {
        this.onPayloadInput(content);
      }
    };
    reader.readAsText(file);
  }

  async runSerialCommand(cmd: 'SOFT_PROVISION' | 'PROVISION' | 'EXPORT_CERT') {
    if (!this.serialService.isSerialSupported()) {
      this.toastService.showError('Web Serial is not supported in this browser.');
      return;
    }

    this.isSerialBusy.set(true);
    this.serialStatusMessage.set(`Connecting and transmitting ${cmd}...`);

    try {
      if (!this.serialService.isConnected()) {
        await this.serialService.connect(115200);
      }

      const linePromise = new Promise<string>((resolve, reject) => {
        const timeout = setTimeout(() => {
          reject(new Error(`Timed out waiting for device response to ${cmd}`));
        }, 15000);

        // We use navigator.serial / line read via standard command or custom listener
        const expectedPrefixes = ['PROVISION_RESULT:', 'SOFT_PROVISION_RESULT:', 'CERT_EXPORT:'];
        const lineHandler = (line: string) => {
          for (const prefix of expectedPrefixes) {
            if (line.startsWith(prefix)) {
              clearTimeout(timeout);
              resolve(line);
              return;
            }
          }
        };

        // Hook into command
        this.serialService.sendCommand(cmd).then(res => {
          // If returned as raw text or ack
          if (res.resultBase64) {
            resolve(res.resultBase64);
          }
        }).catch(err => {
          // Ignore if parser error and wait for raw line
        });

        // Add 5s timeout fallback
        setTimeout(() => {
          clearTimeout(timeout);
          reject(new Error('Device did not return a valid provisioning blob. Ensure firmware supports ' + cmd));
        }, 8000);
      });

      const line = await linePromise;
      this.onPayloadInput(line);
      this.serialStatusMessage.set(`Received payload from device!`);
      this.toastService.showSuccess(`Captured ${cmd} output!`);
    } catch (err: any) {
      const msg = err?.message || String(err);
      this.serialStatusMessage.set(`Serial command error: ${msg}`);
      this.toastService.showError(`Serial Error: ${msg}`);
    } finally {
      this.isSerialBusy.set(false);
    }
  }

  saveUnit() {
    const raw = this.rawPayload().trim();
    if (!raw) {
      this.toastService.showError('Please paste or capture a provisioning blob first.');
      return;
    }

    this.isSaving.set(true);
    this.coordinatorService.provisionHardwareUnit({
      rawPayload: raw,
      label: this.label().trim() || undefined,
      notes: this.notes().trim() || undefined
    }).subscribe({
      next: (detail) => {
        this.isSaving.set(false);
        this.toastService.showSuccess(`Hardware unit ${detail.hardwareId.substring(0, 16)}... registered successfully!`);
        this.unitSaved.emit(detail);
      },
      error: (err) => {
        this.isSaving.set(false);
        const msg = err?.error?.message || err?.message || 'Failed to save hardware unit';
        this.toastService.showError(`Save Failed: ${msg}`);
      }
    });
  }

  close() {
    this.modalClosed.emit();
  }
}
