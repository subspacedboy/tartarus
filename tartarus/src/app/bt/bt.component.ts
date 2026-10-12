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

interface LogEntry {
  timestamp: string;
  type: 'info' | 'success' | 'warn' | 'error';
  message: string;
}

@Component({
  selector: 'app-bt',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
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

  scanAllDevices = signal<boolean>(false);

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

  constructor(
    private fb: FormBuilder,
    private configService: ConfigService,
    private tartarusCoordinatorService: TartarusCoordinatorService
  ) {
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
      includeSafetyKeys: [false],
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

      this.connectionState.set('connected');
      this.addLog('success', 'Tartarus ready for configuration and commands!');
    } catch (err: any) {
      this.connectionState.set('disconnected');
      if (err.name === 'NotFoundError') {
        this.addLog('warn', 'Device selection was cancelled.');
      } else {
        this.addLog('error', `Bluetooth connection error: ${err.message || err}`);
      }
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
    this.addLog('warn', 'Tartarus disconnected.');
  }

  async sendWifi(): Promise<void> {
    if (this.connectionState() !== 'connected' || !this.ssidChar || !this.passwordChar) {
      this.addLog('error', 'Cannot send Wi-Fi: Device is not connected.');
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
}
