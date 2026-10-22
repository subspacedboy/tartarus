import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TartarusCoordinatorService } from '../../tartarus-coordinator.service';
import { ToastService } from '../../toast.service';
import { DateAgoPipe } from '../../date-ago.pipe';
import { ProvisioningIngestModalComponent } from './provisioning-ingest-modal.component';
import { HardwareUnitDetailModalComponent } from './hardware-unit-detail-modal.component';
import {
  ProvisionedHardwareUnitSummary,
  ProvisionedHardwareUnitDetail
} from '../../models/provisioned-hardware-unit';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmButtonDirective } from '../../shared/ui/ui-button-helm';
import { HlmBadgeDirective } from '../../shared/ui/ui-badge-helm';
import { HlmInputDirective } from '../../shared/ui/ui-input-helm';
import { HlmTableImports } from '../../shared/ui/ui-table-helm';
import { HlmSpinnerComponent } from '../../shared/ui/ui-spinner-helm';

@Component({
  selector: 'app-hardware-provisioning',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    DateAgoPipe,
    ProvisioningIngestModalComponent,
    HardwareUnitDetailModalComponent,
    ...HlmCardImports,
    HlmButtonDirective,
    HlmBadgeDirective,
    HlmInputDirective,
    ...HlmTableImports,
    HlmSpinnerComponent,
  ],
  templateUrl: './hardware-provisioning.component.html',
  styleUrl: './hardware-provisioning.component.scss'
})
export class HardwareProvisioningComponent implements OnInit {
  private coordinatorService = inject(TartarusCoordinatorService);
  private toastService = inject(ToastService);

  units = signal<ProvisionedHardwareUnitSummary[]>([]);
  isLoading = signal<boolean>(true);

  // Filters
  searchTerm = signal<string>('');
  selectedModeFilter = signal<'ALL' | 'HARD' | 'SOFT' | 'REVOKED'>('ALL');

  // Modals state
  showIngestModal = signal<boolean>(false);
  selectedUnitHardwareId = signal<string | null>(null);

  // Computed summary metrics
  totalCount = computed(() => this.units().length);
  hardCount = computed(() => this.units().filter(u => u.provisioningMode === 'HARD' && !u.isRevoked).length);
  softCount = computed(() => this.units().filter(u => u.provisioningMode === 'SOFT' && !u.isRevoked).length);
  revokedCount = computed(() => this.units().filter(u => u.isRevoked).length);

  // Filtered unit list
  filteredUnits = computed(() => {
    let list = this.units();
    const mode = this.selectedModeFilter();
    if (mode === 'HARD') {
      list = list.filter(u => u.provisioningMode === 'HARD' && !u.isRevoked);
    } else if (mode === 'SOFT') {
      list = list.filter(u => u.provisioningMode === 'SOFT' && !u.isRevoked);
    } else if (mode === 'REVOKED') {
      list = list.filter(u => u.isRevoked);
    }

    const term = this.searchTerm().trim().toLowerCase();
    if (term) {
      list = list.filter(u =>
        u.hardwareId.toLowerCase().includes(term) ||
        u.initialSessionToken.toLowerCase().includes(term) ||
        (u.lastKnownSessionToken?.toLowerCase().includes(term) == true) ||
        (u.label?.toLowerCase().includes(term) == true)
      );
    }

    return list;
  });

  ngOnInit() {
    this.loadUnits();
  }

  loadUnits() {
    this.isLoading.set(true);
    this.coordinatorService.listProvisionedUnits().subscribe({
      next: (list) => {
        this.units.set(list);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = err?.error?.message || err?.message || 'Failed to load provisioned hardware units';
        this.toastService.showError(msg);
      }
    });
  }

  setFilter(filter: 'ALL' | 'HARD' | 'SOFT' | 'REVOKED') {
    this.selectedModeFilter.set(filter);
  }

  openIngestModal() {
    this.showIngestModal.set(true);
  }

  closeIngestModal() {
    this.showIngestModal.set(false);
  }

  onUnitSaved(_newUnit: ProvisionedHardwareUnitDetail) {
    this.showIngestModal.set(false);
    this.loadUnits();
  }

  inspectUnit(hardwareId: string) {
    this.selectedUnitHardwareId.set(hardwareId);
  }

  closeDetailModal() {
    this.selectedUnitHardwareId.set(null);
  }

  onUnitUpdated(_updated: ProvisionedHardwareUnitDetail) {
    this.loadUnits();
  }

  copyHardwareId(id: string, event: Event) {
    event.stopPropagation();
    navigator.clipboard.writeText(id).then(() => {
      this.toastService.showSuccess('Copied Hardware ID to clipboard!');
    });
  }
}
