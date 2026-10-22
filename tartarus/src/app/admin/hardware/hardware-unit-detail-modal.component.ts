import { Component, EventEmitter, Input, OnInit, Output, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TartarusCoordinatorService } from '../../tartarus-coordinator.service';
import { ToastService } from '../../toast.service';
import { DateAgoPipe } from '../../date-ago.pipe';
import {
  ProvisionedHardwareUnitDetail,
  UpdateHardwareUnitRequest
} from '../../models/provisioned-hardware-unit';
import { HlmDialogImports } from '../../shared/ui/ui-dialog-helm';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmButtonDirective } from '../../shared/ui/ui-button-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';
import { HlmInputDirective } from '../../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../../shared/ui/ui-label-helm';
import { HlmTableImports } from '../../shared/ui/ui-table-helm';
import { HlmSpinnerComponent } from '../../shared/ui/ui-spinner-helm';

@Component({
  selector: 'app-hardware-unit-detail-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DateAgoPipe,
    ...HlmDialogImports,
    ...HlmCardImports,
    HlmButtonDirective,
    HlmBadgeDirective,
    HlmInputDirective,
    HlmLabelDirective,
    ...HlmTableImports,
    HlmSpinnerComponent,
  ],
  templateUrl: './hardware-unit-detail-modal.component.html',
  styleUrl: './hardware-unit-detail-modal.component.scss'
})
export class HardwareUnitDetailModalComponent implements OnInit {
  private coordinatorService = inject(TartarusCoordinatorService);
  private toastService = inject(ToastService);

  @Input({ required: true }) hardwareId!: string;
  @Output() modalClosed = new EventEmitter<void>();
  @Output() unitUpdated = new EventEmitter<ProvisionedHardwareUnitDetail>();

  unit = signal<ProvisionedHardwareUnitDetail | null>(null);
  isLoading = signal<boolean>(true);
  isSaving = signal<boolean>(false);

  editLabel = signal<string>('');
  editNotes = signal<string>('');
  editIsRevoked = signal<boolean>(false);
  editRevokedReason = signal<string>('');

  ngOnInit() {
    this.loadDetail();
  }

  loadDetail() {
    this.isLoading.set(true);
    this.coordinatorService.getProvisionedUnitDetail(this.hardwareId).subscribe({
      next: (detail) => {
        this.unit.set(detail);
        this.editLabel.set(detail.label || '');
        this.editNotes.set(detail.notes || '');
        this.editIsRevoked.set(detail.isRevoked);
        this.editRevokedReason.set(detail.revokedReason || '');
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = err?.error?.message || err?.message || 'Failed to load hardware unit details';
        this.toastService.showError(msg);
      }
    });
  }

  saveChanges() {
    this.isSaving.set(true);
    const req: UpdateHardwareUnitRequest = {
      label: this.editLabel().trim() || null,
      notes: this.editNotes().trim() || null,
      isRevoked: this.editIsRevoked(),
      revokedReason: this.editIsRevoked() ? (this.editRevokedReason().trim() || 'Revoked by administrator') : null
    };

    this.coordinatorService.updateHardwareUnit(this.hardwareId, req).subscribe({
      next: (updated) => {
        this.unit.set(updated);
        this.isSaving.set(false);
        this.toastService.showSuccess('Hardware unit updated successfully');
        this.unitUpdated.emit(updated);
      },
      error: (err) => {
        this.isSaving.set(false);
        const msg = err?.error?.message || err?.message || 'Failed to update hardware unit';
        this.toastService.showError(msg);
      }
    });
  }

  copyToClipboard(text: string, label: string) {
    navigator.clipboard.writeText(text).then(() => {
      this.toastService.showSuccess(`Copied ${label} to clipboard!`);
    });
  }

  close() {
    this.modalClosed.emit();
  }
}
