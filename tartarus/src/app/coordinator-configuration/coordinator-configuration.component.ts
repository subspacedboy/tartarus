import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ConfigService} from '../config.service';
import * as flatbuffers from 'flatbuffers';
import {Key} from '../club/subjugated/fb/message/configuration/key';
import {CoordinatorConfiguration} from '../club/subjugated/fb/message/configuration/coordinator-configuration';
import * as QRCode from 'qrcode';

@Component({
  selector: 'app-coordinator-configuration',
  imports: [],
  templateUrl: './coordinator-configuration.component.html',
  styleUrl: './coordinator-configuration.component.scss'
})
export class CoordinatorConfigurationComponent implements AfterViewInit {
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private configService: ConfigService) {
  }

  ngAfterViewInit() {
    this.buildConfiguration();
  }

  buildConfiguration() {
    const config = this.configService.getConfig();

    const builder = new flatbuffers.Builder(1024);

    // Create keys
    const nameOffset = builder.createString("example_key");
    const publicKeyData = [1, 2, 3, 4, 5]; // Example public key data
    const publicKeyOffset = Key.createPublicKeyVector(builder, publicKeyData);

    Key.startKey(builder);
    Key.addName(builder, nameOffset);
    Key.addPublicKey(builder, publicKeyOffset);
    const keyOffset = Key.endKey(builder);

    // Create repeated keys vector
    const keysVector = CoordinatorConfiguration.createSafetyKeysVector(builder, [keyOffset]);

    // Create main configuration
    const webUriOffset = builder.createString("http://192.168.1.180:4200");
    const mqttUriOffset = builder.createString("ws://192.168.1.180:8080/mqtt");

    CoordinatorConfiguration.startCoordinatorConfiguration(builder);
    CoordinatorConfiguration.addWebUri(builder, webUriOffset);
    CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset);
    CoordinatorConfiguration.addSafetyKeys(builder, keysVector);
    const configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder);

    builder.finish(configOffset);
    const data = builder.asUint8Array();
    this.generateQRCode(data).then(r => {
      console.log("Generated");
      this.hiddenDiv.nativeElement.hidden = false;
    });
  }

  async generateQRCode( data : Uint8Array) {
    const canvas = this.canvas.nativeElement;
    const qrSize = 256;
    const textHeight = 30; // Space for text
    canvas.width = qrSize;
    canvas.height = qrSize + textHeight;

    try {
      // Generate QR Code on a temporary canvas
      const tempCanvas = document.createElement('canvas');
      tempCanvas.width = qrSize;
      tempCanvas.height = qrSize;
      await QRCode.toCanvas(tempCanvas,
        [{data : data, mode: 'byte'}],
        { width: qrSize, margin: 2, errorCorrectionLevel: 'L' });

      const ctx = canvas.getContext('2d');
      // Clear canvas and draw QR code onto the main canvas
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(tempCanvas, 0, 0);

      // this.hiddenDiv.nativeElement.hidden = false;
    } catch (err) {
      console.error('Error generating QR Code:', err);
    }
  }
}
