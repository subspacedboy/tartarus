import {
  Component,
  signal,
  ChangeDetectionStrategy,
  OnDestroy,
  ElementRef,
  ViewChild,
  ChangeDetectorRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Transport } from 'esptool-js';
import { ToastService } from '../toast.service';

export type LineEnding = '\r\n' | '\n' | '\r' | '';

@Component({
  selector: 'app-console',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './console.component.html',
  styleUrl: './console.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsoleComponent implements OnDestroy {
  @ViewChild('terminalContainer') private terminalContainer?: ElementRef<HTMLDivElement>;
  @ViewChild('cmdInput') private cmdInput?: ElementRef<HTMLInputElement>;

  // Web Serial support
  isSerialSupported = signal<boolean>(typeof navigator !== 'undefined' && 'serial' in navigator);

  // Connection states
  isConnected = signal<boolean>(false);
  isConnecting = signal<boolean>(false);
  baudRate = signal<number>(115200);

  // Terminal state
  terminalHtml = signal<string>('');
  rawLogs = '';
  autoScroll = signal<boolean>(true);
  echoInput = signal<boolean>(true);

  // Command input & history
  commandText = signal<string>('');
  selectedLineEnding = signal<LineEnding>('\r\n');
  commandHistory: string[] = [];
  historyIndex = -1;

  // WebSerial objects
  private port: any = null;
  private transport: Transport | null = null;
  private isReading = false;
  private textDecoder = new TextDecoder('utf-8', { fatal: false });

  constructor(
    private toastService: ToastService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnDestroy(): void {
    void this.disconnect();
  }

  // --------------------------------------------------------------------------
  // Connection Management
  // --------------------------------------------------------------------------

  async connect(): Promise<void> {
    if (!this.isSerialSupported()) {
      this.toastService.showError('Web Serial API is not supported by your browser.');
      return;
    }

    try {
      this.isConnecting.set(true);

      // Request port from user
      this.port = await (navigator as any).serial.requestPort();
      this.transport = new Transport(this.port, false, false);

      this.transport.setDeviceLostCallback(() => {
        this.appendSystemNotice('Device disconnected or lost.');
        void this.disconnect();
      });

      await this.transport.connect(this.baudRate());

      this.isConnected.set(true);
      this.isConnecting.set(false);
      this.appendSystemNotice(`Connected to serial device at ${this.baudRate()} baud.`);
      this.toastService.showSuccess(`Connected at ${this.baudRate()} baud`);

      // Start reading stream
      this.startReading();

      // Focus command input
      setTimeout(() => this.cmdInput?.nativeElement?.focus(), 100);
    } catch (err: any) {
      this.isConnecting.set(false);
      const msg = err?.message || String(err);
      if (!msg.toLowerCase().includes('no port selected') && !msg.toLowerCase().includes('user cancelled')) {
        this.appendSystemNotice(`Failed to connect: ${msg}`, 'error');
        this.toastService.showError(`Connection failed: ${msg}`);
      }
      await this.disconnect();
    }
  }

  async disconnect(): Promise<void> {
    this.isReading = false;
    try {
      if (this.transport) {
        await this.transport.disconnect();
      }
    } catch {
      // Ignore disconnect errors
    } finally {
      this.transport = null;
      this.port = null;
      this.isConnected.set(false);
      this.isConnecting.set(false);
      this.appendSystemNotice('Disconnected from serial device.');
    }
  }

  async changeBaudRate(newBaud: number): Promise<void> {
    this.baudRate.set(newBaud);
    if (this.isConnected() && this.transport) {
      try {
        await this.transport.changeBaudrate(newBaud);
        this.appendSystemNotice(`Changed baud rate to ${newBaud}.`);
      } catch (err: any) {
        this.appendSystemNotice(`Failed to change baud rate: ${err?.message || err}`, 'error');
      }
    }
  }

  // --------------------------------------------------------------------------
  // Hardware Reset (RTS Toggle)
  // --------------------------------------------------------------------------

  async resetDevice(): Promise<void> {
    if (!this.transport || !this.isConnected()) return;

    try {
      this.appendSystemNotice('Resetting device via RTS pin (EN toggle)...');
      // EN = LOW (Reset)
      await this.transport.setDTR(false);
      await this.transport.setRTS(true);
      await new Promise((r) => setTimeout(r, 120));
      // EN = HIGH (Run)
      await this.transport.setRTS(false);
      this.appendSystemNotice('Device reset triggered.');
    } catch (err: any) {
      this.appendSystemNotice(`Reset failed: ${err?.message || err}`, 'error');
    }
  }

  // --------------------------------------------------------------------------
  // Reading & Stream Handling
  // --------------------------------------------------------------------------

  private startReading(): void {
    if (!this.transport) return;
    this.isReading = true;

    void this.transport.rawRead(
      (data: Uint8Array) => {
        if (!this.isReading) return;
        const decoded = this.textDecoder.decode(data, { stream: true });
        this.appendIncomingText(decoded);
      },
      () => !this.isReading
    );
  }

  private appendIncomingText(rawText: string): void {
    this.rawLogs += rawText;
    const formatted = this.ansiToHtml(rawText);
    this.terminalHtml.update((curr) => curr + formatted);

    if (this.autoScroll()) {
      this.scrollToBottom();
    }
  }

  private appendSystemNotice(message: string, type: 'info' | 'error' = 'info'): void {
    const time = new Date().toLocaleTimeString();
    const colorClass = type === 'error' ? 'text-danger' : 'text-warning';
    const line = `<div class="system-line ${colorClass}">[${time}] *** ${this.escapeHtml(message)} ***</div>`;
    this.terminalHtml.update((curr) => curr + line);
    this.rawLogs += `\n[${time}] *** ${message} ***\n`;

    if (this.autoScroll()) {
      this.scrollToBottom();
    }
  }

  // --------------------------------------------------------------------------
  // Command Transmission (STDIN)
  // --------------------------------------------------------------------------

  async sendCommand(): Promise<void> {
    const cmd = this.commandText();
    if (!this.isConnected() || !this.transport) {
      this.toastService.showError('Not connected to any serial device.');
      return;
    }

    try {
      const payload = cmd + this.selectedLineEnding();
      const encoder = new TextEncoder();
      const data = encoder.encode(payload);

      // Write raw bytes directly to WebSerial stream
      // NOTE: Do NOT use transport.write() here because esptool-js wraps data in SLIP protocol bytes (0xc0)
      if (this.port?.writable) {
        const writer = this.port.writable.getWriter();
        await writer.write(data);
        writer.releaseLock();
      } else if (this.transport?.device?.writable) {
        const writer = this.transport.device.writable.getWriter();
        await writer.write(data);
        writer.releaseLock();
      }

      if (this.echoInput()) {
        const time = new Date().toLocaleTimeString();
        const echoLine = `<div class="echo-line text-info">[${time}] &gt; ${this.escapeHtml(cmd)}</div>`;
        this.terminalHtml.update((curr) => curr + echoLine);
        this.rawLogs += `\n[${time}] > ${cmd}\n`;
        if (this.autoScroll()) {
          this.scrollToBottom();
        }
      }

      // Add to command history
      if (cmd.trim().length > 0) {
        if (this.commandHistory.length === 0 || this.commandHistory[this.commandHistory.length - 1] !== cmd) {
          this.commandHistory.push(cmd);
        }
      }
      this.historyIndex = this.commandHistory.length;
      this.commandText.set('');
    } catch (err: any) {
      this.appendSystemNotice(`Failed to send command: ${err?.message || err}`, 'error');
    }
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      void this.sendCommand();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (this.commandHistory.length > 0 && this.historyIndex > 0) {
        this.historyIndex--;
        this.commandText.set(this.commandHistory[this.historyIndex]);
      }
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (this.historyIndex < this.commandHistory.length - 1) {
        this.historyIndex++;
        this.commandText.set(this.commandHistory[this.historyIndex]);
      } else {
        this.historyIndex = this.commandHistory.length;
        this.commandText.set('');
      }
    }
  }

  // --------------------------------------------------------------------------
  // Terminal Utilities
  // --------------------------------------------------------------------------

  clearTerminal(): void {
    this.terminalHtml.set('');
    this.rawLogs = '';
  }

  downloadLogs(): void {
    if (!this.rawLogs) {
      this.toastService.showError('No logs to download.');
      return;
    }
    const blob = new Blob([this.rawLogs], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `tartarus-serial-log-${new Date().toISOString().replace(/[:.]/g, '-')}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.terminalContainer?.nativeElement) {
        this.terminalContainer.nativeElement.scrollTop = this.terminalContainer.nativeElement.scrollHeight;
      }
    }, 10);
  }

  private escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  private ansiToHtml(text: string): string {
    let escaped = this.escapeHtml(text);

    // Map common ANSI foreground color codes to Bootstrap styling
    escaped = escaped
      .replace(/\x1b\[0;31m|\x1b\[31m/g, '<span class="text-danger">')
      .replace(/\x1b\[0;32m|\x1b\[32m/g, '<span class="text-success">')
      .replace(/\x1b\[0;33m|\x1b\[33m/g, '<span class="text-warning">')
      .replace(/\x1b\[0;34m|\x1b\[34m/g, '<span class="text-primary">')
      .replace(/\x1b\[0;35m|\x1b\[35m/g, '<span style="color:#d63384;">')
      .replace(/\x1b\[0;36m|\x1b\[36m/g, '<span class="text-info">')
      .replace(/\x1b\[0;37m|\x1b\[37m/g, '<span class="text-light">')
      .replace(/\x1b\[1;([0-9]+)m/g, '<span class="fw-bold">')
      .replace(/\x1b\[0m|\x1b\[m/g, '</span>')
      // Strip any other ANSI escape sequences
      .replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '');

    return escaped;
  }
}
