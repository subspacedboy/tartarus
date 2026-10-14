import { Component, signal, ChangeDetectionStrategy, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ESPLoader, Transport, FlashOptions, IEspLoaderTerminal } from 'esptool-js';
import { ToastService } from '../toast.service';

export interface LogEntry {
  timestamp: string;
  type: 'info' | 'success' | 'warn' | 'error';
  message: string;
}

export type FlashingPreset = 'migration' | 'app_only' | 'custom';

export interface CustomFileSlot {
  label: string;
  address: string;
  urlFilename: string;
  fileData: Uint8Array | null;
  fileName: string | null;
  useUpload: boolean;
}

@Component({
  selector: 'app-updater',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './updater.component.html',
  styleUrl: './updater.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpdaterComponent implements OnDestroy {
  @ViewChild('logContainer') private logContainer?: ElementRef<HTMLDivElement>;

  // Web Serial availability
  isSerialSupported = signal<boolean>(typeof navigator !== 'undefined' && 'serial' in navigator);

  // Connection & operation states
  connectionState = signal<'disconnected' | 'connecting' | 'connected' | 'flashing' | 'done' | 'error'>('disconnected');
  connectedChip = signal<string | null>(null);
  currentAction = signal<string>('');
  progressPercent = signal<number>(0);
  progressDetails = signal<string>('');

  // Configuration options
  selectedPreset = signal<FlashingPreset>('migration');
  baseUrl = signal<string>('https://tartarus.subjugated.club/firmware2/');
  baudRate = signal<number>(460800);

  // Custom slots
  customBootloader: CustomFileSlot = {
    label: 'Bootloader (Signed)',
    address: '0x0000',
    urlFilename: 'bootloader.bin',
    fileData: null,
    fileName: null,
    useUpload: false,
  };

  customPartitions: CustomFileSlot = {
    label: 'Partition Table',
    address: '0x8000',
    urlFilename: 'partitions.bin',
    fileData: null,
    fileName: null,
    useUpload: false,
  };

  customApp: CustomFileSlot = {
    label: 'Application Firmware',
    address: '0x10000',
    urlFilename: 'app.bin',
    fileData: null,
    fileName: null,
    useUpload: false,
  };

  // Log entries
  logs = signal<LogEntry[]>([]);

  private port: any = null;
  private transport: Transport | null = null;
  private esploader: ESPLoader | null = null;

  constructor(private toastService: ToastService) {}

  ngOnDestroy(): void {
    void this.disconnect();
  }

  // --------------------------------------------------------------------------
  // Connection Management
  // --------------------------------------------------------------------------

  async connect(): Promise<boolean> {
    if (!this.isSerialSupported()) {
      this.addLog('Web Serial is not supported by this browser. Use Chrome or Edge.', 'error');
      return false;
    }

    try {
      this.connectionState.set('connecting');
      this.currentAction.set('Requesting serial port...');
      this.addLog('Requesting USB Serial device from user...', 'info');

      // Request port access
      this.port = await (navigator as any).serial.requestPort();
      this.transport = new Transport(this.port, true);

      const terminal: IEspLoaderTerminal = {
        clean: () => this.clearLogs(),
        writeLine: (data: string) => this.addLog(data, 'info'),
        write: (data: string) => this.addLog(data, 'info'),
      };

      this.currentAction.set('Synchronizing with ESP bootloader...');
      this.esploader = new ESPLoader({
        transport: this.transport,
        baudrate: this.baudRate(),
        romBaudrate: 115200,
        terminal: terminal,
      });

      this.addLog('Connecting to ESP bootloader stub...', 'info');
      const chip = await this.esploader.main();
      this.connectedChip.set(chip);
      this.connectionState.set('connected');
      this.currentAction.set(`Connected to ${chip}`);
      this.addLog(`Successfully connected to ${chip}!`, 'success');
      this.toastService.showSuccess(`Connected to ${chip}`);
      return true;
    } catch (err: any) {
      this.connectionState.set('error');
      const errMsg = err?.message || String(err);
      this.currentAction.set(`Connection error: ${errMsg}`);
      this.addLog(`Failed to connect: ${errMsg}`, 'error');
      this.toastService.showError(`Connection failed: ${errMsg}`);
      await this.disconnect();
      return false;
    }
  }

  async disconnect(): Promise<void> {
    try {
      if (this.transport) {
        await this.transport.disconnect();
      }
    } catch {
      // Ignore disconnect errors
    } finally {
      this.port = null;
      this.transport = null;
      this.esploader = null;
      this.connectedChip.set(null);
      if (this.connectionState() !== 'flashing') {
        this.connectionState.set('disconnected');
        this.currentAction.set('');
      }
    }
  }

  // --------------------------------------------------------------------------
  // Flashing Execution
  // --------------------------------------------------------------------------

  async startFlashing(): Promise<void> {
    if (this.connectionState() === 'flashing') return;

    // Connect if not already connected
    if (this.connectionState() !== 'connected' || !this.esploader) {
      const ok = await this.connect();
      if (!ok) return;
    }

    try {
      this.connectionState.set('flashing');
      this.progressPercent.set(0);
      this.progressDetails.set('Preparing firmware binaries...');
      this.addLog('Preparing firmware parts for flashing...', 'info');

      const filesToFlash: { data: Uint8Array; address: number; name: string }[] = [];
      const preset = this.selectedPreset();

      if (preset === 'migration') {
        // Secure Boot v2 migration: bootloader (0x0), partitions (0x8000), signed app (0x10000)
        // CRITICAL: NVS range (0x9000 - 0xD000) is SKIPPED and preserved!
        this.addLog('Preset: Secure Boot v2 Migration (NVS Preserved)', 'info');

        this.currentAction.set('Downloading bootloader.bin...');
        const bootloader = await this.getPartData(this.customBootloader);
        filesToFlash.push({ data: bootloader, address: 0x0000, name: 'bootloader.bin (0x0)' });

        this.currentAction.set('Downloading partitions.bin...');
        const partitions = await this.getPartData(this.customPartitions);
        filesToFlash.push({ data: partitions, address: 0x8000, name: 'partitions.bin (0x8000)' });

        this.currentAction.set('Downloading tartarus-firmware-signed.bin...');
        const app = await this.getPartData(this.customApp);
        filesToFlash.push({ data: app, address: 0x10000, name: 'app.bin (0x10000)' });
      } else if (preset === 'app_only') {
        // Application update only: signed app (0x10000)
        this.addLog('Preset: Application Update Only (0x10000)', 'info');
        this.currentAction.set('Downloading tartarus-firmware-signed.bin...');
        const app = await this.getPartData(this.customApp);
        filesToFlash.push({ data: app, address: 0x10000, name: 'app.bin (0x10000)' });
      } else {
        // Custom preset
        this.addLog('Preset: Custom configuration', 'info');
        if (this.customBootloader.fileData || !this.customBootloader.useUpload) {
          const bootloader = await this.getPartData(this.customBootloader);
          filesToFlash.push({
            data: bootloader,
            address: parseInt(this.customBootloader.address, 16),
            name: `Bootloader (${this.customBootloader.address})`,
          });
        }
        if (this.customPartitions.fileData || !this.customPartitions.useUpload) {
          const partitions = await this.getPartData(this.customPartitions);
          filesToFlash.push({
            data: partitions,
            address: parseInt(this.customPartitions.address, 16),
            name: `Partitions (${this.customPartitions.address})`,
          });
        }
        if (this.customApp.fileData || !this.customApp.useUpload) {
          const app = await this.getPartData(this.customApp);
          filesToFlash.push({
            data: app,
            address: parseInt(this.customApp.address, 16),
            name: `App (${this.customApp.address})`,
          });
        }
      }

      this.addLog(`Prepared ${filesToFlash.length} partition image(s).`, 'info');
      for (const item of filesToFlash) {
        this.addLog(`  -> ${item.name}: ${item.data.length.toLocaleString()} bytes at 0x${item.address.toString(16)}`, 'info');
      }

      // Configure FlashOptions
      // CRITICAL: eraseAll is explicitly false to prevent erasing NVS (0x9000 - 0xD000)
      const flashOptions: FlashOptions = {
        fileArray: filesToFlash.map((f) => ({ data: f.data, address: f.address })),
        flashMode: 'dio',
        flashFreq: '80m',
        flashSize: '4MB',
        eraseAll: false, // PRESERVES NVS!
        compress: true,
        reportProgress: (fileIndex: number, written: number, total: number) => {
          const currentFile = filesToFlash[fileIndex];
          const percent = Math.round((written / total) * 100);
          this.progressPercent.set(percent);
          this.progressDetails.set(
            `Writing ${currentFile?.name ?? `part ${fileIndex + 1}`}: ${percent}% (${written.toLocaleString()} / ${total.toLocaleString()} bytes)`
          );
        },
      };

      this.currentAction.set('Writing flash memory (NVS preserved)...');
      this.addLog('Starting flash write. eraseAll=false (NVS protected).', 'info');

      await this.esploader!.writeFlash(flashOptions);

      this.progressPercent.set(100);
      this.currentAction.set('Flashing completed! Resetting device...');
      this.addLog('Flash write complete! Resetting device into new firmware...', 'success');

      try {
        await this.esploader!.after('hard_reset');
      } catch {
        // Resetting the device will disconnect the serial port
      }

      this.connectionState.set('done');
      this.currentAction.set('Device updated successfully!');
      this.toastService.showSuccess('Firmware update completed successfully!');
      this.addLog('Device has been rebooted into the updated firmware.', 'success');
    } catch (err: any) {
      this.connectionState.set('error');
      const errMsg = err?.message || String(err);
      this.currentAction.set(`Flash failed: ${errMsg}`);
      this.addLog(`Flashing error: ${errMsg}`, 'error');
      this.toastService.showError(`Flashing failed: ${errMsg}`);
    } finally {
      await this.disconnect();
    }
  }

  // --------------------------------------------------------------------------
  // Binary Helpers
  // --------------------------------------------------------------------------

  private async getPartData(slot: CustomFileSlot): Promise<Uint8Array> {
    if (slot.useUpload && slot.fileData) {
      return slot.fileData;
    }

    const cleanBase = this.baseUrl().endsWith('/') ? this.baseUrl() : `${this.baseUrl()}/`;
    
    // Attempt download, with fallback if the primary name fails with HTML/404
    const filenamesToTry = [slot.urlFilename];
    if (slot.urlFilename === 'tartarus-firmware-signed.bin') {
      filenamesToTry.push('app.bin');
    } else if (slot.urlFilename === 'app.bin') {
      filenamesToTry.push('tartarus-firmware-signed.bin');
    }

    let lastError: Error | null = null;

    for (const filename of filenamesToTry) {
      const targetUrl = `${cleanBase}${filename}`;
      const cacheBustUrl = `${targetUrl}?_cb=${Date.now()}`;

      this.addLog(`Fetching ${filename} from ${targetUrl}...`, 'info');

      try {
        const response = await fetch(cacheBustUrl, { cache: 'no-store' });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status} (${response.statusText})`);
        }

        const contentType = response.headers.get('content-type') || '';
        const buffer = await response.arrayBuffer();
        const data = new Uint8Array(buffer);

        // Check if response is an HTML error page (e.g. CloudFront SPA fallback)
        const isHtml =
          contentType.toLowerCase().includes('text/html') ||
          (data.length > 0 && data[0] === 0x3c); // 0x3c is '<'

        if (isHtml) {
          throw new Error(
            `Server returned an HTML document (${data.length} bytes, Content-Type: ${contentType || 'unknown'}). CloudFront/S3 returns HTML when a file is not found (404 SPA fallback).`
          );
        }

        // Sanity check ESP image header for binaries flashed at 0x0 or 0x10000
        if (data.length > 0 && (slot.address === '0x0000' || slot.address === '0x10000')) {
          if (data[0] !== 0xe9) {
            throw new Error(
              `Invalid ESP32 binary format (magic byte was 0x${data[0].toString(16)}, expected 0xe9).`
            );
          }
        }

        // Partition table sanity check (magic byte 0xaa 0x50)
        if (data.length > 1 && slot.address === '0x8000') {
          if (data[0] !== 0xaa || data[1] !== 0x50) {
            this.addLog(`Note: Partition table header at 0x8000 did not match standard 0xaa50 (bytes: 0x${data[0].toString(16)} 0x${data[1].toString(16)}).`, 'warn');
          }
        }

        this.addLog(`Successfully loaded ${filename} (${data.length.toLocaleString()} bytes).`, 'success');
        return data;
      } catch (err: any) {
        lastError = err;
        this.addLog(`Could not load ${filename}: ${err?.message || err}`, 'warn');
      }
    }

    throw new Error(
      `Failed to download binary for ${slot.label}: ${lastError?.message || 'File not found or CORS blocked'}`
    );
  }

  onFileSelected(slot: CustomFileSlot, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      slot.fileName = file.name;
      const reader = new FileReader();
      reader.onload = () => {
        slot.fileData = new Uint8Array(reader.result as ArrayBuffer);
        slot.useUpload = true;
        this.addLog(`Loaded local file '${file.name}' (${slot.fileData.length.toLocaleString()} bytes) for ${slot.label}.`, 'info');
      };
      reader.readAsArrayBuffer(file);
    }
  }

  // --------------------------------------------------------------------------
  // Logging Utilities
  // --------------------------------------------------------------------------

  addLog(message: string, type: 'info' | 'success' | 'warn' | 'error' = 'info'): void {
    const time = new Date().toLocaleTimeString();
    this.logs.update((current) => [...current, { timestamp: time, type, message }]);
    setTimeout(() => {
      if (this.logContainer?.nativeElement) {
        this.logContainer.nativeElement.scrollTop = this.logContainer.nativeElement.scrollHeight;
      }
    }, 10);
  }

  clearLogs(): void {
    this.logs.set([]);
  }
}
