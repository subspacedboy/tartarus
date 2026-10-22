import { Component, signal, ChangeDetectionStrategy, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import * as flatbuffers from 'flatbuffers';
import { ConfigService } from '../config.service';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { AppConfig } from '../models/app-config';
import { Key } from '../club/subjugated/fb/message/configuration/key';
import { CoordinatorConfiguration } from '../club/subjugated/fb/message/configuration/coordinator-configuration';

export const TARTARUS_SERVICE_UUID = 'da0c2439-e9f6-4aac-8948-33031d7ef384';
export const WIFI_SSID_CHAR_UUID = '5d288ec6-65bc-47a6-b4f0-ea4e28547951';
export const WIFI_PASSWORD_CHAR_UUID = '73ced3a6-b6c8-48b4-ab9e-4cec6a83a46e';
export const COMMAND_CHAR_UUID = 'fc57b56e-fb30-4227-8a88-cf2b80ccced0';
export const STATUS_CHAR_UUID = 'f41280b4-af44-4b14-b5eb-83d4b2a78f35';
export const AUTH_CHAR_UUID = 'a79b2001-f012-4c8d-b3ef-88267df8461a';
export const AUTH_LEVEL_CHAR_UUID = 'a79b2002-f012-4c8d-b3ef-88267df8461a';
export const EVENT_QUEUE_COUNT_CHAR_UUID = 'a79b2003-f012-4c8d-b3ef-88267df8461a';
export const EVENT_QUEUE_LIST_CHAR_UUID = 'a79b2004-f012-4c8d-b3ef-88267df8461a';
export const EVENT_QUEUE_CTRL_CHAR_UUID = 'a79b2005-f012-4c8d-b3ef-88267df8461a';

export interface EventQueueItem {
  slot: number;
  contract: number;
  counter: number;
  type: string;
}

interface LogEntry {
  timestamp: string;
  type: 'info' | 'success' | 'warn' | 'error';
  message: string;
}

import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmBadgeDirective } from '../shared/ui/ui-badge-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';
import { HlmSpinnerComponent } from '../shared/ui/ui-spinner-helm';
import { HlmTableImports } from '../shared/ui/ui-table-helm';

@Component({
  selector: 'app-bt',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    ...HlmAlertImports,
    HlmBadgeDirective,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
    HlmSpinnerComponent,
    ...HlmTableImports,
  ],
  templateUrl: './bt.component.html',
  styleUrl: './bt.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BtComponent implements OnInit, OnDestroy {
  isBluetoothSupported = signal<boolean>(typeof navigator !== 'undefined' && 'bluetooth' in navigator);
  connectionState = signal<'disconnected' | 'connecting' | 'connected'>('disconnected');
  connectedDeviceName = signal<string | null>(null);
  deviceStatus = signal<string>('idle');
  showPassword = signal<boolean>(false);
  logs = signal<LogEntry[]>([]);
  isSendingWifi = signal<boolean>(false);
  isSendingCommand = signal<boolean>(false);
  isSendingConfig = signal<boolean>(false);
  isLoadingConfig = signal<boolean>(false);

  // Authentication Signals
  isAuthenticated = signal<boolean>(false);
  authLevel = signal<string>('unauthenticated');
  isAuthenticating = signal<boolean>(false);
  authError = signal<string | null>(null);
  isLockedOut = signal<boolean>(false);

  // Event Queue Signals
  eventQueueCount = signal<number | null>(null);
  eventQueueItems = signal<EventQueueItem[]>([]);
  eventQueueRaw = signal<string>('');
  eventQueueCtrlStatus = signal<string>('idle');
  isEventQueueLoading = signal<boolean>(false);
  isClearingQueue = signal<boolean>(false);
  isReenqueuing = signal<boolean>(false);
  showRawEventQueue = signal<boolean>(false);

  scanAllDevices = signal<boolean>(false);

  authForm: FormGroup;
  wifiForm: FormGroup;
  commandForm: FormGroup;
  coordinatorConfigForm: FormGroup;
  configFromApi?: AppConfig;

  private bluetoothDevice: any = null;
  private gattServer: any = null;
  private ssidChar: any = null;
  private passwordChar: any = null;
  private commandChar: any = null;
  private statusChar: any = null;
  private authChar: any = null;
  private authLevelChar: any = null;
  private eventQueueCountChar: any = null;
  private eventQueueListChar: any = null;
  private eventQueueCtrlChar: any = null;

  constructor(
    private fb: FormBuilder,
    private configService: ConfigService,
    private tartarusCoordinatorService: TartarusCoordinatorService
  ) {
    this.authForm = this.fb.group({
      pin: ['', [Validators.required, Validators.pattern('^[0-9]{6}$')]],
    });

    this.wifiForm = this.fb.group({
      ssid: ['', [Validators.required]],
      password: ['', [Validators.required]],
    });

    this.commandForm = this.fb.group({
      command: ['', [Validators.required]],
    });

    this.coordinatorConfigForm = this.fb.group({
      webUri: ['', [Validators.required]],
      wsUri: ['', [Validators.required]],
      apiUri: ['', [Validators.required]],
      mqttUri: ['', [Validators.required]],
      includeSafetyKeys: [true],
    });

    if (!this.isBluetoothSupported()) {
      this.addLog('warn', 'Web Bluetooth is not supported in this browser. Please use Chrome, Edge, or Bluefy (on iOS).');
    } else {
      this.addLog('info', 'Web Bluetooth ready. Click "Connect to Tartarus" to begin.');
    }
  }

  async ngOnInit(): Promise<void> {
    await this.loadCoordinatorConfig();
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  async loadCoordinatorConfig(): Promise<void> {
    this.isLoadingConfig.set(true);

    // 1. Try local config first as baseline
    const localCfg = this.configService.getConfig();
    if (localCfg) {
      this.coordinatorConfigForm.patchValue({
        webUri: localCfg.webUri || '',
        wsUri: localCfg.wsUri || '',
        apiUri: localCfg.apiUri || '',
        mqttUri: localCfg.mqttUri || '',
      });
    }

    // 2. Try fetching from the coordinator service API
    try {
      this.configFromApi = await firstValueFrom(this.tartarusCoordinatorService.getConfigurationFromCoordinator());
      if (this.configFromApi) {
        this.coordinatorConfigForm.patchValue({
          webUri: this.configFromApi.webUri || this.coordinatorConfigForm.get('webUri')?.value || '',
          wsUri: this.configFromApi.wsUri || this.coordinatorConfigForm.get('wsUri')?.value || '',
          apiUri: this.configFromApi.apiUri || this.coordinatorConfigForm.get('apiUri')?.value || '',
          mqttUri: this.configFromApi.mqttUri || this.coordinatorConfigForm.get('mqttUri')?.value || '',
        });
        this.addLog('info', 'Loaded coordinator configuration from API.');
      }
    } catch {
      // Coordinator API might be offline, which is normal in standalone mode
    } finally {
      this.isLoadingConfig.set(false);
    }
  }

  toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  toggleScanAllDevices(): void {
    this.scanAllDevices.update((val) => !val);
  }

  addLog(type: 'info' | 'success' | 'warn' | 'error', message: string): void {
    const now = new Date();
    const timestamp = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
    this.logs.update((current) => [...current, { timestamp, type, message }]);
  }

  clearLogs(): void {
    this.logs.set([]);
  }

  async connect(): Promise<void> {
    if (!this.isBluetoothSupported()) {
      this.addLog('error', 'Cannot connect: Web Bluetooth is not supported in this browser.');
      return;
    }

    try {
      this.connectionState.set('connecting');
      this.addLog('info', 'Requesting Tartarus Bluetooth device...');

      const navBt = (navigator as any).bluetooth;
      const options: any = this.scanAllDevices()
        ? {
            acceptAllDevices: true,
            optionalServices: [TARTARUS_SERVICE_UUID],
          }
        : {
            filters: [
              { services: [TARTARUS_SERVICE_UUID] },
              { name: 'Tartarus' },
              { namePrefix: 'Tartarus' },
            ],
            optionalServices: [TARTARUS_SERVICE_UUID],
          };

      this.bluetoothDevice = await navBt.requestDevice(options);

      this.connectedDeviceName.set(this.bluetoothDevice.name || 'Tartarus');
      this.addLog('info', `Found device: ${this.connectedDeviceName()}. Connecting to GATT server...`);

      this.bluetoothDevice.addEventListener('gattserverdisconnected', () => {
        this.onDisconnected();
      });

      this.gattServer = await this.bluetoothDevice.gatt.connect();
      this.addLog('success', 'Connected to GATT server.');

      this.addLog('info', `Discovering Tartarus Primary Service (${TARTARUS_SERVICE_UUID})...`);
      const service = await this.gattServer.getPrimaryService(TARTARUS_SERVICE_UUID);

      this.addLog('info', 'Discovering characteristics...');
      this.ssidChar = await service.getCharacteristic(WIFI_SSID_CHAR_UUID);
      this.passwordChar = await service.getCharacteristic(WIFI_PASSWORD_CHAR_UUID);
      this.commandChar = await service.getCharacteristic(COMMAND_CHAR_UUID);

      try {
        this.authChar = await service.getCharacteristic(AUTH_CHAR_UUID);
      } catch (e) {
        this.addLog('warn', 'Authentication characteristic not found on device.');
      }

      try {
        this.authLevelChar = await service.getCharacteristic(AUTH_LEVEL_CHAR_UUID);
        await this.checkAuthLevel();

        // Subscribe to notifications on authLevelChar if supported
        try {
          await this.authLevelChar.startNotifications();
          this.authLevelChar.addEventListener('characteristicvaluechanged', (event: any) => {
            const val = new TextDecoder().decode(event.target.value);
            this.handleAuthLevelUpdate(val);
          });
        } catch {
          // Notifications might not be supported or required
        }
      } catch (e) {
        this.addLog('warn', 'Authentication level characteristic not found on device.');
      }

      try {
        this.statusChar = await service.getCharacteristic(STATUS_CHAR_UUID);
        const statusVal = await this.statusChar.readValue();
        const statusStr = new TextDecoder().decode(statusVal);
        this.deviceStatus.set(statusStr || 'ready');
        this.addLog('info', `Device Status: ${statusStr}`);
      } catch {
        this.addLog('info', 'Status characteristic ready.');
      }

      // Try reading existing SSID if available
      try {
        const ssidVal = await this.ssidChar.readValue();
        const currentSsid = new TextDecoder().decode(ssidVal).trim();
        if (currentSsid && currentSsid.length > 0) {
          this.wifiForm.patchValue({ ssid: currentSsid });
          this.addLog('info', `Read current SSID from device: '${currentSsid}'`);
        }
      } catch {
        // SSID characteristic may be write-only or empty initially
      }

      // Discover Event Queue Characteristics
      try {
        this.eventQueueCountChar = await service.getCharacteristic(EVENT_QUEUE_COUNT_CHAR_UUID);
        await this.readEventQueueCount();
        try {
          await this.eventQueueCountChar.startNotifications();
          this.eventQueueCountChar.addEventListener('characteristicvaluechanged', (e: any) => {
            const val = new TextDecoder().decode(e.target.value);
            const count = parseInt(val.trim(), 10);
            this.eventQueueCount.set(isNaN(count) ? 0 : count);
          });
        } catch {
          // Notifications might not be supported
        }
      } catch {
        this.addLog('info', 'Event Queue Count characteristic ready.');
      }

      try {
        this.eventQueueListChar = await service.getCharacteristic(EVENT_QUEUE_LIST_CHAR_UUID);
        await this.readEventQueueList();
        try {
          await this.eventQueueListChar.startNotifications();
          this.eventQueueListChar.addEventListener('characteristicvaluechanged', (e: any) => {
            const val = new TextDecoder().decode(e.target.value);
            this.parseEventQueueList(val);
          });
        } catch {
          // Notifications might not be supported
        }
      } catch {
        this.addLog('info', 'Event Queue List characteristic ready.');
      }

      try {
        this.eventQueueCtrlChar = await service.getCharacteristic(EVENT_QUEUE_CTRL_CHAR_UUID);
        try {
          await this.eventQueueCtrlChar.startNotifications();
          this.eventQueueCtrlChar.addEventListener('characteristicvaluechanged', (e: any) => {
            const val = new TextDecoder().decode(e.target.value);
            this.eventQueueCtrlStatus.set(val);
            this.addLog('info', `Event Queue Control: ${val}`);
          });
        } catch {
          // Notifications might not be supported
        }
      } catch {
        this.addLog('info', 'Event Queue Control characteristic ready.');
      }

      this.connectionState.set('connected');
      if (this.isAuthenticated()) {
        this.addLog('success', 'Tartarus connected and authenticated!');
      } else {
        this.addLog('info', 'Tartarus connected. Please enter device PIN to authenticate.');
      }
    } catch (err: any) {
      this.connectionState.set('disconnected');
      if (err.name === 'NotFoundError') {
        this.addLog('warn', 'Device selection was cancelled.');
      } else {
        this.addLog('error', `Bluetooth connection error: ${err.message || err}`);
      }
    }
  }

  handleAuthLevelUpdate(level: string): void {
    const trimmed = level.trim();
    this.authLevel.set(trimmed);
    if (trimmed === 'authenticated') {
      this.isAuthenticated.set(true);
      this.isLockedOut.set(false);
      this.authError.set(null);
      this.addLog('success', 'Device is now authenticated!');
    } else if (trimmed === 'locked_out') {
      this.isAuthenticated.set(false);
      this.isLockedOut.set(true);
      this.authError.set('Tartarus is locked out after failed attempts. Press D1 on the Tartarus screen to regenerate PIN.');
      this.addLog('error', 'Tartarus authentication locked out. Press D1 on device to reset PIN.');
    } else {
      this.isAuthenticated.set(false);
      this.isLockedOut.set(false);
    }
  }

  async checkAuthLevel(): Promise<void> {
    if (!this.authLevelChar) return;
    try {
      const val = await this.authLevelChar.readValue();
      const level = new TextDecoder().decode(val);
      this.handleAuthLevelUpdate(level);
    } catch (err: any) {
      this.addLog('warn', `Failed to read authentication level: ${err.message || err}`);
    }
  }

  async authenticate(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.authChar) {
      this.addLog('error', 'Cannot authenticate: Device is not connected.');
      return;
    }

    if (this.authForm.invalid) {
      this.authForm.markAllAsTouched();
      this.authError.set('Please enter a valid 6-digit PIN.');
      this.addLog('warn', 'Invalid PIN format: must be 6 numeric digits.');
      return;
    }

    const pin = (this.authForm.get('pin')?.value || '').trim();

    try {
      this.isAuthenticating.set(true);
      this.authError.set(null);
      this.addLog('info', 'Sending authentication PIN to Tartarus...');

      const encoder = new TextEncoder();
      const payload = encoder.encode(pin);

      if (this.authChar.writeValueWithResponse) {
        await this.authChar.writeValueWithResponse(payload);
      } else {
        await this.authChar.writeValue(payload);
      }

      // Query authLevel to verify result
      await this.checkAuthLevel();

      if (this.isAuthenticated()) {
        this.addLog('success', 'Authentication accepted! Controls unlocked.');
      } else if (this.isLockedOut()) {
        this.addLog('error', 'Authentication failed: Device is locked out after multiple failed attempts. Press D1 on device to reset PIN.');
      } else {
        this.authError.set('Incorrect PIN. Please check the Tartarus display and try again.');
        this.addLog('error', 'Authentication failed: Incorrect PIN.');
      }
    } catch (err: any) {
      this.authError.set(`Authentication transmission error: ${err.message || err}`);
      this.addLog('error', `Authentication error: ${err.message || err}`);
    } finally {
      this.isAuthenticating.set(false);
    }
  }

  disconnect(): void {
    if (this.bluetoothDevice && this.bluetoothDevice.gatt && this.bluetoothDevice.gatt.connected) {
      this.addLog('info', 'Disconnecting from device...');
      this.bluetoothDevice.gatt.disconnect();
    }
    this.onDisconnected();
  }

  private onDisconnected(): void {
    this.connectionState.set('disconnected');
    this.connectedDeviceName.set(null);
    this.gattServer = null;
    this.ssidChar = null;
    this.passwordChar = null;
    this.commandChar = null;
    this.statusChar = null;
    this.authChar = null;
    this.authLevelChar = null;
    this.eventQueueCountChar = null;
    this.eventQueueListChar = null;
    this.eventQueueCtrlChar = null;
    this.eventQueueCount.set(null);
    this.eventQueueItems.set([]);
    this.eventQueueRaw.set('');
    this.eventQueueCtrlStatus.set('idle');
    this.isEventQueueLoading.set(false);
    this.isClearingQueue.set(false);
    this.isReenqueuing.set(false);
    this.isAuthenticated.set(false);
    this.isLockedOut.set(false);
    this.authLevel.set('unauthenticated');
    this.authError.set(null);
    this.addLog('warn', 'Tartarus disconnected.');
  }

  async sendWifi(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.ssidChar || !this.passwordChar) {
      this.addLog('error', 'Cannot send Wi-Fi: Device is not connected.');
      return;
    }

    if (!this.isAuthenticated()) {
      this.addLog('error', 'Cannot send Wi-Fi: Device is not authenticated. Please enter PIN first.');
      return;
    }

    if (this.wifiForm.invalid) {
      this.wifiForm.markAllAsTouched();
      this.addLog('warn', 'Please enter both SSID and Password.');
      return;
    }

    const ssid = (this.wifiForm.get('ssid')?.value || '').trim();
    const password = (this.wifiForm.get('password')?.value || '').trim();

    try {
      this.isSendingWifi.set(true);
      const encoder = new TextEncoder();

      this.addLog('info', `Writing Wi-Fi SSID: '${ssid}'...`);
      const ssidPayload = encoder.encode(ssid);
      if (this.ssidChar.writeValueWithResponse) {
        await this.ssidChar.writeValueWithResponse(ssidPayload);
      } else {
        await this.ssidChar.writeValue(ssidPayload);
      }

      this.addLog('info', `Writing Wi-Fi Password (${password.length} chars)...`);
      const passPayload = encoder.encode(password);
      if (this.passwordChar.writeValueWithResponse) {
        await this.passwordChar.writeValueWithResponse(passPayload);
      } else {
        await this.passwordChar.writeValue(passPayload);
      }

      this.addLog('success', `Wi-Fi credentials successfully transmitted! Device will connect to '${ssid}'.`);
    } catch (err: any) {
      this.addLog('error', `Failed to send Wi-Fi credentials: ${err.message || err}`);
    } finally {
      this.isSendingWifi.set(false);
    }
  }

  buildCoordinatorPayload(): { data: Uint8Array; base64Data: string } {
    const builder = new flatbuffers.Builder(1024);
    const includeSafetyKeys = !!this.coordinatorConfigForm.get('includeSafetyKeys')?.value;
    let keysVector;

    const safetyKeys = this.configFromApi?.safetyKeys || this.configService.getConfig()?.safetyKeys;

    if (includeSafetyKeys && safetyKeys && safetyKeys.length > 0) {
      const keyOffsets = [];
      for (const k of safetyKeys) {
        const nameOffset = builder.createString(k.name || '');
        const publicKeyData = Uint8Array.from(atob(k.publicKey || ''), (c) => c.charCodeAt(0));
        const publicKeyOffset = Key.createPublicKeyVector(builder, publicKeyData);

        Key.startKey(builder);
        Key.addName(builder, nameOffset);
        Key.addPublicKey(builder, publicKeyOffset);
        const keyOffset = Key.endKey(builder);
        keyOffsets.push(keyOffset);
      }
      keysVector = CoordinatorConfiguration.createSafetyKeysVector(builder, keyOffsets);
    }

    const webUriOffset = builder.createString(this.coordinatorConfigForm.get('webUri')?.value || '');
    const wsUriOffset = builder.createString(this.coordinatorConfigForm.get('wsUri')?.value || '');
    const mqttUriOffset = builder.createString(this.coordinatorConfigForm.get('mqttUri')?.value || '');
    const apiUriOffset = builder.createString(this.coordinatorConfigForm.get('apiUri')?.value || '');

    CoordinatorConfiguration.startCoordinatorConfiguration(builder);
    CoordinatorConfiguration.addWebUri(builder, webUriOffset);
    CoordinatorConfiguration.addWsUri(builder, wsUriOffset);
    CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset);
    CoordinatorConfiguration.addApiUri(builder, apiUriOffset);

    if (includeSafetyKeys && keysVector) {
      CoordinatorConfiguration.addSafetyKeys(builder, keysVector);
    }
    const configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder);
    builder.finish(configOffset);

    const data = builder.asUint8Array();
    let binaryString = '';
    for (let i = 0; i < data.length; i++) {
      binaryString += String.fromCharCode(data[i]);
    }
    const base64Data = btoa(binaryString);

    return { data, base64Data };
  }

  async sendCoordinatorConfiguration(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.commandChar) {
      this.addLog('error', 'Cannot send configuration: Device is not connected.');
      return;
    }

    if (!this.isAuthenticated()) {
      this.addLog('error', 'Cannot send configuration: Device is not authenticated. Please enter PIN first.');
      return;
    }

    if (this.coordinatorConfigForm.invalid) {
      this.coordinatorConfigForm.markAllAsTouched();
      this.addLog('warn', 'Please fill in all required URI fields.');
      return;
    }

    try {
      this.isSendingConfig.set(true);

      const { data, base64Data } = this.buildCoordinatorPayload();

      this.addLog('info', `Built Coordinator Configuration FlatBuffer (${data.length} bytes, Base64: ${base64Data.length} chars).`);
      this.addLog('info', 'Transmitting configuration to Tartarus over BLE command characteristic...');

      const encoder = new TextEncoder();
      const payload = encoder.encode(base64Data);

      if (this.commandChar.writeValueWithResponse) {
        await this.commandChar.writeValueWithResponse(payload);
      } else {
        await this.commandChar.writeValue(payload);
      }

      this.addLog('success', 'Coordinator configuration successfully transmitted over BLE!');
    } catch (err: any) {
      this.addLog('error', `Failed to send configuration: ${err.message || err}`);
    } finally {
      this.isSendingConfig.set(false);
    }
  }

  copyConfigToCommand(): void {
    if (this.coordinatorConfigForm.invalid) {
      this.coordinatorConfigForm.markAllAsTouched();
      this.addLog('warn', 'Please fill in all required URI fields first.');
      return;
    }
    const { base64Data } = this.buildCoordinatorPayload();
    this.commandForm.patchValue({ command: base64Data });
    this.addLog('info', 'Generated configuration Base64 copied into Command field.');
  }

  async sendCommand(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.commandChar) {
      this.addLog('error', 'Cannot send command: Device is not connected.');
      return;
    }

    if (!this.isAuthenticated()) {
      this.addLog('error', 'Cannot send command: Device is not authenticated. Please enter PIN first.');
      return;
    }

    if (this.commandForm.invalid) {
      this.commandForm.markAllAsTouched();
      this.addLog('warn', 'Please enter a Base64 command string.');
      return;
    }

    const command = (this.commandForm.get('command')?.value || '').trim();

    try {
      this.isSendingCommand.set(true);
      const encoder = new TextEncoder();

      this.addLog('info', `Writing command (${command.length} characters)...`);
      const payload = encoder.encode(command);
      if (this.commandChar.writeValueWithResponse) {
        await this.commandChar.writeValueWithResponse(payload);
      } else {
        await this.commandChar.writeValue(payload);
      }

      this.addLog('success', 'Command successfully written to Tartarus!');
    } catch (err: any) {
      this.addLog('error', `Failed to send command: ${err.message || err}`);
    } finally {
      this.isSendingCommand.set(false);
    }
  }

  parseEventQueueList(raw: string): void {
    const trimmed = raw.trim();
    this.eventQueueRaw.set(trimmed);
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        this.eventQueueItems.set(parsed);
      } else {
        this.eventQueueItems.set([]);
      }
    } catch {
      this.eventQueueItems.set([]);
    }
  }

  async readEventQueueList(): Promise<void> {
    if (!this.eventQueueListChar) return;
    try {
      const val = await this.eventQueueListChar.readValue();
      const str = new TextDecoder().decode(val);
      this.parseEventQueueList(str);
    } catch (err: any) {
      this.addLog('warn', `Failed to read event queue list: ${err.message || err}`);
    }
  }

  async readEventQueueCount(): Promise<void> {
    if (!this.eventQueueCountChar) return;
    try {
      const val = await this.eventQueueCountChar.readValue();
      const str = new TextDecoder().decode(val);
      const count = parseInt(str.trim(), 10);
      this.eventQueueCount.set(isNaN(count) ? 0 : count);
    } catch (err: any) {
      this.addLog('warn', `Failed to read event queue count: ${err.message || err}`);
    }
  }

  async refreshEventQueue(): Promise<void> {
    if (this.connectionState() !== 'connected') {
      this.addLog('error', 'Cannot refresh event queue: Device is not connected.');
      return;
    }

    try {
      this.isEventQueueLoading.set(true);
      this.addLog('info', 'Refreshing event queue from Tartarus...');

      if (this.eventQueueCtrlChar && this.isAuthenticated()) {
        try {
          const payload = new TextEncoder().encode('refresh');
          if (this.eventQueueCtrlChar.writeValueWithResponse) {
            await this.eventQueueCtrlChar.writeValueWithResponse(payload);
          } else {
            await this.eventQueueCtrlChar.writeValue(payload);
          }
          await new Promise((r) => setTimeout(r, 100));
        } catch {}
      }

      await this.readEventQueueCount();
      await this.readEventQueueList();
      this.addLog('success', `Event queue refreshed: ${this.eventQueueCount() ?? 0} events in flash.`);
    } catch (err: any) {
      this.addLog('error', `Failed to refresh event queue: ${err.message || err}`);
    } finally {
      this.isEventQueueLoading.set(false);
    }
  }

  async clearEventQueue(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.eventQueueCtrlChar) {
      this.addLog('error', 'Cannot clear event queue: Device is not connected.');
      return;
    }

    if (!this.isAuthenticated()) {
      this.addLog('error', 'Cannot clear event queue: Device is not authenticated. Please enter PIN first.');
      return;
    }

    if (!confirm('Are you sure you want to clear all unacknowledged events from flash storage?')) {
      return;
    }

    try {
      this.isClearingQueue.set(true);
      this.addLog('info', 'Sending command to clear event queue...');
      const payload = new TextEncoder().encode('clear');
      if (this.eventQueueCtrlChar.writeValueWithResponse) {
        await this.eventQueueCtrlChar.writeValueWithResponse(payload);
      } else {
        await this.eventQueueCtrlChar.writeValue(payload);
      }

      await new Promise((r) => setTimeout(r, 200));
      await this.readEventQueueCount();
      await this.readEventQueueList();
      this.addLog('success', 'Event queue successfully cleared from Tartarus flash.');
    } catch (err: any) {
      this.addLog('error', `Failed to clear event queue: ${err.message || err}`);
    } finally {
      this.isClearingQueue.set(false);
    }
  }

  async reenqueueEventQueue(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.eventQueueCtrlChar) {
      this.addLog('error', 'Cannot re-enqueue events: Device is not connected.');
      return;
    }

    if (!this.isAuthenticated()) {
      this.addLog('error', 'Cannot re-enqueue events: Device is not authenticated. Please enter PIN first.');
      return;
    }

    try {
      this.isReenqueuing.set(true);
      this.addLog('info', 'Sending command to force re-enqueue unacknowledged events...');
      const payload = new TextEncoder().encode('reenqueue');
      if (this.eventQueueCtrlChar.writeValueWithResponse) {
        await this.eventQueueCtrlChar.writeValueWithResponse(payload);
      } else {
        await this.eventQueueCtrlChar.writeValue(payload);
      }
      this.addLog('success', 'Force re-enqueue command sent. Events will be transmitted over MQTT.');
    } catch (err: any) {
      this.addLog('error', `Failed to re-enqueue events: ${err.message || err}`);
    } finally {
      this.isReenqueuing.set(false);
    }
  }

  toggleShowRawEventQueue(): void {
    this.showRawEventQueue.update((v) => !v);
  }
}
