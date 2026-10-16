import {
  Component,
  signal,
  computed,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ESPLoader, Transport, FlashOptions, IEspLoaderTerminal } from 'esptool-js';
import { ToastService } from '../toast.service';
import {
  FirmwareManifest,
  FirmwareRelease,
  FirmwareTrack,
  FlashTargetMode,
  ArbitrarySource,
} from '../models/firmware';

export interface LogEntry {
  timestamp: string;
  type: 'info' | 'success' | 'warn' | 'error';
  message: string;
}

export interface CustomFileSlot {
  label: string;
  address: string;
  urlFilename: string;
  fileData: Uint8Array | null;
  fileName: string | null;
  useUpload: boolean;
}

// Protected NVS flash range (0x9000 to 0xF000, 24 KiB)
const NVS_START_ADDR = 0x9000;
const NVS_END_ADDR = 0xF000;

@Component({
  selector: 'app-updater',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './updater.component.html',
  styleUrl: './updater.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpdaterComponent implements OnInit, OnDestroy {
  @ViewChild('logContainer') private logContainer?: ElementRef<HTMLDivElement>;

  // Web Serial availability
  isSerialSupported = signal<boolean>(typeof navigator !== 'undefined' && 'serial' in navigator);

  // Connection & operation states
  connectionState = signal<'disconnected' | 'connecting' | 'connected' | 'flashing' | 'done' | 'error'>('disconnected');
  connectedChip = signal<string | null>(null);
  currentAction = signal<string>('');
  progressPercent = signal<number>(0);
  progressDetails = signal<string>('');

  // Server & Manifest configuration
  baseUrl = signal<string>('https://tartarus.subjugated.club/firmware2/');
  manifest = signal<FirmwareManifest | null>(null);
  manifestLoading = signal<boolean>(false);
  manifestError = signal<string | null>(null);

  // Selection state
  selectedTrack = signal<FirmwareTrack>('arbitrary');
  selectedVersion = signal<string | null>(null);
  selectedFlashScope = signal<FlashTargetMode>('app_only');
  arbitrarySource = signal<ArbitrarySource>('manifest');
  baudRate = signal<number>(460800);

  // Computed release lists based on manifest
  signedReleases = computed<FirmwareRelease[]>(() => {
    const list = this.manifest()?.releases ?? [];
    return list.filter((r) => r.signed);
  });

  arbitraryReleases = computed<FirmwareRelease[]>(() => {
    const list = this.manifest()?.releases ?? [];
    return list.filter((r) => !r.signed);
  });

  currentReleaseList = computed<FirmwareRelease[]>(() => {
    return this.selectedTrack() === 'signed' ? this.signedReleases() : this.arbitraryReleases();
  });

  currentRelease = computed<FirmwareRelease | null>(() => {
    const list = this.currentReleaseList();
    if (list.length === 0) return null;
    const version = this.selectedVersion();
    if (!version) return list[0];
    return list.find((r) => r.version === version) ?? list[0];
  });

  canMigrateCurrentRelease = computed<boolean>(() => {
    const rel = this.currentRelease();
    return !!(rel && rel.bootloader && rel.partitions);
  });

  // Custom slots for arbitrary local binary upload
  customBootloader: CustomFileSlot = {
    label: 'Bootloader (0x0000)',
    address: '0x0000',
    urlFilename: 'bootloader.bin',
    fileData: null,
    fileName: null,
    useUpload: true,
  };

  customPartitions: CustomFileSlot = {
    label: 'Partition Table (0x8000)',
    address: '0x8000',
    urlFilename: 'partitions.bin',
    fileData: null,
    fileName: null,
    useUpload: true,
  };

  customApp: CustomFileSlot = {
    label: 'Application Binary (0x10000)',
    address: '0x10000',
    urlFilename: 'app.bin',
    fileData: null,
    fileName: null,
    useUpload: true,
  };

  // Activity logs
  logs = signal<LogEntry[]>([]);

  private port: any = null;
  private transport: Transport | null = null;
  private esploader: ESPLoader | null = null;

  constructor(private toastService: ToastService) {}

  ngOnInit(): void {
    void this.fetchManifest();
  }

  ngOnDestroy(): void {
    void this.disconnect();
  }

  // --------------------------------------------------------------------------
  // Manifest Management
  // --------------------------------------------------------------------------

  async fetchManifest(): Promise<void> {
    const rawUrl = this.baseUrl().trim();
    if (!rawUrl) {
      this.manifestError.set('Firmware server URL cannot be empty.');
      return;
    }

    const cleanBase = rawUrl.endsWith('/') ? rawUrl : `${rawUrl}/`;
    const manifestUrl = `${cleanBase}firmware.json?_cb=${Date.now()}`;

    this.manifestLoading.set(true);
    this.manifestError.set(null);
    this.addLog(`Fetching firmware manifest from ${manifestUrl}...`, 'info');

    try {
      const response = await fetch(manifestUrl, { cache: 'no-store' });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status} (${response.statusText})`);
      }

      const contentType = response.headers.get('content-type') || '';
      const text = await response.text();

      // Check if CloudFront / S3 returned an HTML error page
      if (contentType.toLowerCase().includes('text/html') || text.trim().startsWith('<')) {
        throw new Error(
          'Server returned an HTML document instead of JSON. Ensure firmware.json exists on the server.'
        );
      }

      const data: FirmwareManifest = JSON.parse(text);
      if (!data || !Array.isArray(data.releases)) {
        throw new Error('Invalid manifest format: "releases" array is missing.');
      }

      this.manifest.set(data);
      this.manifestLoading.set(false);
      this.addLog(`Successfully loaded manifest with ${data.releases.length} total releases.`, 'success');

      // Prefer arbitrary/unsigned releases if available; fallback to signed if only signed exist
      if (this.arbitraryReleases().length > 0) {
        this.selectedTrack.set('arbitrary');
      } else if (this.signedReleases().length > 0) {
        this.selectedTrack.set('signed');
      }

      // Auto-select latest version in current track
      this.syncSelectedVersion();
    } catch (err: any) {
      const msg = err?.message || String(err);
      this.manifestLoading.set(false);
      this.manifestError.set(msg);
      this.addLog(`Failed to fetch firmware.json: ${msg}`, 'error');
      this.toastService.showError(`Manifest load failed: ${msg}`);
    }
  }

  setTrack(track: FirmwareTrack): void {
    this.selectedTrack.set(track);
    this.syncSelectedVersion();
  }

  private syncSelectedVersion(): void {
    const list = this.currentReleaseList();
    if (list.length > 0) {
      const current = this.selectedVersion();
      if (!current || !list.some((r) => r.version === current)) {
        this.selectedVersion.set(list[0].version);
      }
    } else {
      this.selectedVersion.set(null);
    }

    // Reset scope if current release cannot migrate
    if (!this.canMigrateCurrentRelease() && this.selectedFlashScope() === 'migration') {
      this.selectedFlashScope.set('app_only');
    }
  }

  onVersionChange(version: string): void {
    this.selectedVersion.set(version);
    if (!this.canMigrateCurrentRelease() && this.selectedFlashScope() === 'migration') {
      this.selectedFlashScope.set('app_only');
    }
  }

  // --------------------------------------------------------------------------
  // Connection Management
  // --------------------------------------------------------------------------

  async connect(): Promise<boolean> {
    if (!this.isSerialSupported()) {
      this.addLog('Web Serial is not supported by this browser. Use Chrome, Edge, or Opera.', 'error');
      return false;
    }

    try {
      this.connectionState.set('connecting');
      this.currentAction.set('Requesting serial port...');
      this.addLog('Requesting USB Serial device from user...', 'info');

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

      this.addLog('Connecting to ESP ROM bootloader stub...', 'info');
      const chip = await this.esploader.main();
      const isStub = (this.esploader as any)?.IS_STUB === true;
      const isSdm = (this.esploader as any)?.secureDownloadMode === true;
      if (!isStub || isSdm) {
        (this.esploader as any).FLASH_WRITE_SIZE = 0x400;
        (this.esploader as any).ERASE_REGION_TIMEOUT_PER_MB = 120000;
        this.addLog('Secure Download Mode / ROM bootloader active: set flash block size to 1024 bytes (0x400).', 'info');
        await this.configureRomSpiFlash(this.esploader, 4 * 1024 * 1024);
      }
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

      if (this.selectedTrack() === 'signed' || (this.selectedTrack() === 'arbitrary' && this.arbitrarySource() === 'manifest')) {
        const release = this.currentRelease();
        if (!release) {
          throw new Error('No firmware release selected.');
        }

        const scope = this.selectedFlashScope();

        if (scope === 'migration') {
          if (!release.bootloader || !release.partitions) {
            throw new Error(`Release ${release.version} does not include bootloader and partitions required for migration.`);
          }

          this.addLog(`Scope: Full Migration / Provisioning for version ${release.version}`, 'info');

          this.currentAction.set(`Downloading bootloader (${release.bootloader})...`);
          const bootloaderData = await this.fetchBinary(release.bootloader, 'Bootloader', '0x0000');
          filesToFlash.push({ data: bootloaderData, address: 0x0000, name: `Bootloader (0x0)` });

          this.currentAction.set(`Downloading partitions (${release.partitions})...`);
          const partitionData = await this.fetchBinary(release.partitions, 'Partition Table', '0x8000');
          filesToFlash.push({ data: partitionData, address: 0x8000, name: `Partitions (0x8000)` });

          this.currentAction.set(`Downloading app (${release.app})...`);
          const appData = await this.fetchBinary(release.app, 'Application Firmware', '0x10000');
          filesToFlash.push({ data: appData, address: 0x10000, name: `App (0x10000)` });
        } else {
          // App Update Only
          this.addLog(`Scope: Application Update Only for version ${release.version}`, 'info');
          this.currentAction.set(`Downloading app (${release.app})...`);
          const appData = await this.fetchBinary(release.app, 'Application Firmware', '0x10000');
          filesToFlash.push({ data: appData, address: 0x10000, name: `App (0x10000)` });
        }
      } else {
        // Arbitrary mode with local file upload
        this.addLog('Scope: Custom Local Binary Upload', 'info');

        if (this.customBootloader.fileData) {
          filesToFlash.push({
            data: this.customBootloader.fileData,
            address: parseInt(this.customBootloader.address, 16),
            name: `Bootloader (${this.customBootloader.address})`,
          });
        }
        if (this.customPartitions.fileData) {
          filesToFlash.push({
            data: this.customPartitions.fileData,
            address: parseInt(this.customPartitions.address, 16),
            name: `Partitions (${this.customPartitions.address})`,
          });
        }
        if (this.customApp.fileData) {
          filesToFlash.push({
            data: this.customApp.fileData,
            address: parseInt(this.customApp.address, 16),
            name: `App (${this.customApp.address})`,
          });
        }

        if (filesToFlash.length === 0) {
          throw new Error('Please select at least one local .bin file to flash.');
        }
      }

      // Determine if compression can be used (only supported by RAM stub; ROM bootloader in Secure Download Mode requires uncompressed)
      const isStub = (this.esploader as any)?.IS_STUB === true;
      const isSdm = (this.esploader as any)?.secureDownloadMode === true;
      const useCompress = isStub && !isSdm;

      if (!useCompress) {
        // ESP32-S3 ROM bootloader (SDM / no-stub) only supports up to 0x400 (1024 bytes) write blocks.
        // esptool-js initializes FLASH_WRITE_SIZE to 0x4000 (16KB stub size) and never scales down for ROM,
        // which causes ESP_FLASH_BEGIN to fail with status 1,5 (ROM_INVALID_RECV_MSG).
        (this.esploader as any).FLASH_WRITE_SIZE = 0x400;
        // Increase erase region timeout per MB to 120,000ms (120s/MB) so ROM sector erasing doesn't time out
        (this.esploader as any).ERASE_REGION_TIMEOUT_PER_MB = 120000;
        this.addLog('Secure Download Mode / ROM bootloader active: 1024-byte blocks and 120s/MB erase timeout configured.', 'info');

        // Explicitly attach SPI flash and configure parameters in ROM bootloader (matches esptool.py --no-stub)
        await this.configureRomSpiFlash(this.esploader, 4 * 1024 * 1024);
      } else {
        // CRITICAL NVS PROTECTION:
        // esptool-js defaults FLASH_WRITE_SIZE to 0x4000 (16 KiB).
        // If an uncompressed file or small block (like the 4 KiB partition table at 0x8000)
        // is written, esptool-js pads it up to FLASH_WRITE_SIZE with 0xFF.
        // With a 16 KiB write size, writing 4 KiB at 0x8000 writes 16 KiB (0x8000 - 0xC000),
        // which overwrites the first 12 KiB of the NVS partition (0x9000 - 0xF000) with 0xFF!
        // Setting FLASH_WRITE_SIZE to 0x1000 (4 KiB = 1 SPI flash sector) ensures all block
        // writes and padding strictly respect 4 KiB sector boundaries and never bleed into NVS.
        (this.esploader as any).FLASH_WRITE_SIZE = 0x1000;
      }

      // ======================================================================
      // CRITICAL NVS INTEGRITY SAFETY ENFORCEMENT
      // Under no circumstances should NVS (0x9000 - 0xF000) be written or erased!
      // ======================================================================
      this.assertNvsSafety(filesToFlash);
      this.addLog('🛡️ NVS Safety Check PASSED: Flash ranges verified completely outside NVS partition (0x9000 - 0xF000).', 'success');

      this.addLog(`Prepared ${filesToFlash.length} binary part(s) for flashing:`, 'info');
      for (const item of filesToFlash) {
        this.addLog(
          `  -> ${item.name}: ${item.data.length.toLocaleString()} bytes at offset 0x${item.address.toString(16)}`,
          'info'
        );
      }

      // Configure FlashOptions
      // CRITICAL: eraseAll is explicitly false to prevent erasing NVS!
      const flashOptions: FlashOptions = {
        fileArray: filesToFlash.map((f) => ({ data: f.data, address: f.address })),
        flashMode: 'keep',
        flashFreq: 'keep',
        flashSize: '4MB',
        eraseAll: false, // HARD PRESERVATION OF NVS!
        compress: useCompress,

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
      this.progressDetails.set('Erasing target flash sectors on device (takes ~30–60s in Secure Download Mode)...');
      this.addLog('Starting flash write. Erasing target flash sectors (NVS protected)...', 'info');

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

  /**
   * Mathematically verifies that none of the target flash writes intersect the NVS partition (0x9000 - 0xF000),
   * taking into account any block padding that esptool-js might apply.
   * Throws an exception and terminates the flash procedure if an overlap is detected.
   */
  private assertNvsSafety(files: { data: Uint8Array; address: number; name: string }[]): void {
    const writeBlockSize = (this.esploader as any)?.FLASH_WRITE_SIZE || 0x1000;
    for (const f of files) {
      const start = f.address;
      const rawEnd = f.address + f.data.length;
      // Account for block padding applied to the final block of a write
      const paddedLength = Math.ceil(f.data.length / writeBlockSize) * writeBlockSize;
      const paddedEnd = f.address + paddedLength;

      // Check for overlap: [start, end) intersects [NVS_START_ADDR, NVS_END_ADDR)
      if ((start < NVS_END_ADDR && rawEnd > NVS_START_ADDR) || (start < NVS_END_ADDR && paddedEnd > NVS_START_ADDR)) {
        const errorMsg = `CRITICAL SAFETY ABORT: Binary "${f.name}" target range (0x${start.toString(16)} - 0x${paddedEnd.toString(16)}, padded to 0x${writeBlockSize.toString(16)} blocks) overlaps the protected NVS partition (0x${NVS_START_ADDR.toString(16)} - 0x${NVS_END_ADDR.toString(16)}). Flashing halted to protect device keys and state!`;
        this.addLog(errorMsg, 'error');
        throw new Error(errorMsg);
      }
    }
  }

  // --------------------------------------------------------------------------
  // Binary Fetch Helpers
  // --------------------------------------------------------------------------

  private resolveUrl(filePath: string): string {
    if (filePath.startsWith('http://') || filePath.startsWith('https://')) {
      return filePath;
    }
    const cleanBase = this.baseUrl().trim().endsWith('/')
      ? this.baseUrl().trim()
      : `${this.baseUrl().trim()}/`;
    return `${cleanBase}${filePath}`;
  }

  private async fetchBinary(filePath: string, label: string, expectedAddress?: string): Promise<Uint8Array> {
    const fullUrl = this.resolveUrl(filePath);
    const cacheBustUrl = `${fullUrl}?_cb=${Date.now()}`;

    this.addLog(`Fetching ${label} from ${fullUrl}...`, 'info');

    const response = await fetch(cacheBustUrl, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error(`Failed to download ${label} from ${fullUrl}: HTTP ${response.status} (${response.statusText})`);
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
        `Server returned an HTML document (${data.length} bytes) for ${label}. Ensure the file exists at ${fullUrl}.`
      );
    }

    // Sanity check ESP image header for binaries flashed at 0x0 or 0x10000
    if (data.length > 0 && (expectedAddress === '0x0000' || expectedAddress === '0x10000')) {
      if (data[0] !== 0xe9) {
        throw new Error(
          `Invalid ESP32 binary format for ${label} (magic byte was 0x${data[0].toString(16)}, expected 0xe9).`
        );
      }
    }

    // Partition table sanity check (magic byte 0xaa 0x50)
    if (data.length > 1 && expectedAddress === '0x8000') {
      if (data[0] !== 0xaa || data[1] !== 0x50) {
        this.addLog(
          `Note: Partition table header at 0x8000 did not match standard 0xaa50 (bytes: 0x${data[0].toString(16)} 0x${data[1].toString(16)}).`,
          'warn'
        );
      }
    }

    this.addLog(`Successfully loaded ${label} (${data.length.toLocaleString()} bytes).`, 'success');
    return data;
  }

  onLocalFileSelected(slot: CustomFileSlot, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      slot.fileName = file.name;
      const reader = new FileReader();
      reader.onload = () => {
        slot.fileData = new Uint8Array(reader.result as ArrayBuffer);
        this.addLog(
          `Loaded local file '${file.name}' (${slot.fileData.length.toLocaleString()} bytes) for ${slot.label}.`,
          'info'
        );
      };
      reader.readAsArrayBuffer(file);
    }
  }

  /**
   * Directly configures the ROM bootloader's SPI flash controller and parameters.
   * Matches Python esptool.py --no-stub behavior:
   * 1. ESP_SPI_ATTACH (0x0D): Enables SPI flash peripheral & pin routing.
   * 2. ESP_SPI_SET_PARAMS (0x0B): Initializes flash size and sector geometry in ROM RAM.
   */
  private async configureRomSpiFlash(esploader: any, flashSizeBytes: number = 4 * 1024 * 1024): Promise<void> {
    this.addLog('Enabling default SPI flash mode in ROM bootloader...', 'info');

    // 1. SPI Attach (0x0D): ROM loader takes 8 bytes (<I for hspi_arg, plus 4 zero bytes for legacy flag)
    const attachPkt = new Uint8Array(8); // hspi_arg = 0, is_legacy = 0
    await esploader.checkCommand('configure SPI flash pins', 0x0D, attachPkt);
    this.addLog('SPI flash pins attached.', 'info');

    // 2. SPI Set Parameters (0x0B): 6 32-bit unsigned integers:
    // [fl_id (0), total_size, block_size (64KB), sector_size (4KB), page_size (256), status_mask (0xFFFF)]
    const paramsPkt = new Uint8Array(24);
    const view = new DataView(paramsPkt.buffer);
    view.setUint32(0, 0, true);              // fl_id = 0
    view.setUint32(4, flashSizeBytes, true); // total_size (4MB)
    view.setUint32(8, 64 * 1024, true);      // block_size = 64KB
    view.setUint32(12, 4 * 1024, true);      // sector_size = 4KB
    view.setUint32(16, 256, true);           // page_size = 256
    view.setUint32(20, 0xFFFF, true);        // status_mask = 0xFFFF
    await esploader.checkCommand('set SPI params', 0x0B, paramsPkt);
    this.addLog('SPI flash parameters configured (4MB, 64KB block, 4KB sector).', 'info');
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
