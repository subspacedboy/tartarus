import { Injectable, signal } from '@angular/core';
import { Transport } from 'esptool-js';
import * as flatbuffers from 'flatbuffers';
import { SignedMessage } from './club/subjugated/fb/message/signed-message';
import { MessagePayload } from './club/subjugated/fb/message/message-payload';
import { Acknowledgement } from './club/subjugated/fb/message/acknowledgement';
import { Error as FBError } from './club/subjugated/fb/message/error';

export interface DeviceCommandResult {
  type: 'ack' | 'error';
  resultBase64: string;
  errorMessage?: string;
  serialNumber?: number;
  counter?: number;
}

@Injectable({
  providedIn: 'root'
})
export class SerialDeviceService {
  isSerialSupported = signal<boolean>(typeof navigator !== 'undefined' && 'serial' in navigator);
  isConnected = signal<boolean>(false);
  isConnecting = signal<boolean>(false);
  isTransmitting = signal<boolean>(false);
  baudRate = signal<number>(115200);

  private port: any = null;
  private transport: Transport | null = null;
  private isReading = false;
  private textDecoder = new TextDecoder('utf-8', { fatal: false });
  private lineBuffer = '';
  private lineListeners: ((line: string) => void)[] = [];

  constructor() {}

  async connect(baudRate: number = 115200): Promise<boolean> {
    if (this.isConnected() && this.transport) {
      return true;
    }
    if (!this.isSerialSupported()) {
      throw new Error('Web Serial is not supported in this browser.');
    }

    try {
      this.isConnecting.set(true);
      this.baudRate.set(baudRate);
      this.port = await (navigator as any).serial.requestPort();
      this.transport = new Transport(this.port, false, false);

      this.transport.setDeviceLostCallback(() => {
        void this.disconnect();
      });

      await this.transport.connect(baudRate);
      this.isConnected.set(true);
      this.isConnecting.set(false);
      this.startReading();
      return true;
    } catch (err: any) {
      this.isConnecting.set(false);
      await this.disconnect();
      throw err;
    }
  }

  async disconnect(): Promise<void> {
    this.isReading = false;
    try {
      if (this.transport) {
        await this.transport.disconnect();
      }
    } catch {
      // ignore
    } finally {
      this.transport = null;
      this.port = null;
      this.isConnected.set(false);
      this.isConnecting.set(false);
      this.lineBuffer = '';
      this.lineListeners = [];
    }
  }

  private startReading(): void {
    if (!this.transport) return;
    this.isReading = true;

    void this.transport.rawRead(
      (data: Uint8Array) => {
        if (!this.isReading) return;
        const text = this.textDecoder.decode(data, { stream: true });
        this.processIncomingChunk(text);
      },
      () => !this.isReading
    );
  }

  private processIncomingChunk(chunk: string): void {
    this.lineBuffer += chunk;
    const lines = this.lineBuffer.split(/\r?\n/);
    this.lineBuffer = lines.pop() ?? '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.length > 0) {
        for (const listener of [...this.lineListeners]) {
          listener(trimmed);
        }
      }
    }
  }

  async sendCommand(base64Payload: string, timeoutMs: number = 10000): Promise<DeviceCommandResult> {
    if (!this.isConnected() || !this.transport) {
      await this.connect(this.baudRate());
    }

    this.isTransmitting.set(true);

    try {
      const cleanPayload = base64Payload.trim() + '\n';
      const encoder = new TextEncoder();
      const data = encoder.encode(cleanPayload);

      return await new Promise<DeviceCommandResult>((resolve, reject) => {
        let timer: any = null;

        const lineHandler = (line: string) => {
          if (line.startsWith('RESULT:')) {
            cleanup();
            const resultBase64 = line.substring('RESULT:'.length).trim();
            try {
              const parsed = this.parseResultPayload(resultBase64);
              resolve(parsed);
            } catch (parseErr) {
              reject(parseErr);
            }
          }
        };

        const cleanup = () => {
          if (timer) clearTimeout(timer);
          this.lineListeners = this.lineListeners.filter(l => l !== lineHandler);
        };

        timer = setTimeout(() => {
          cleanup();
          reject(new Error(`Timeout waiting for device response (${timeoutMs}ms)`));
        }, timeoutMs);

        this.lineListeners.push(lineHandler);

        // Write raw bytes directly to WebSerial stream
        void (async () => {
          try {
            if (this.port?.writable) {
              const writer = this.port.writable.getWriter();
              await writer.write(data);
              writer.releaseLock();
            } else if (this.transport?.device?.writable) {
              const writer = this.transport.device.writable.getWriter();
              await writer.write(data);
              writer.releaseLock();
            } else {
              cleanup();
              reject(new Error('Serial port is not writable'));
            }
          } catch (writeErr) {
            cleanup();
            reject(writeErr);
          }
        })();
      });
    } finally {
      this.isTransmitting.set(false);
    }
  }

  parseResultPayload(resultBase64: string): DeviceCommandResult {
    const rawBinary = atob(resultBase64);
    const buffer = new Uint8Array(rawBinary.length);
    for (let i = 0; i < rawBinary.length; i++) {
      buffer[i] = rawBinary.charCodeAt(i);
    }
    const byteBuffer = new flatbuffers.ByteBuffer(buffer);
    const signedMessage = SignedMessage.getRootAsSignedMessage(byteBuffer);

    const payloadType = signedMessage.payloadType();
    if (payloadType === MessagePayload.Acknowledgement) {
      const ack = signedMessage.payload(new Acknowledgement());
      return {
        type: 'ack',
        resultBase64,
        serialNumber: ack?.serialNumber(),
        counter: ack?.counter()
      };
    } else if (payloadType === MessagePayload.Error) {
      const err = signedMessage.payload(new FBError());
      return {
        type: 'error',
        resultBase64,
        errorMessage: err?.message() || 'Error from device',
        serialNumber: err?.serialNumber(),
        counter: err?.counter()
      };
    } else {
      return {
        type: 'ack',
        resultBase64
      };
    }
  }
}
