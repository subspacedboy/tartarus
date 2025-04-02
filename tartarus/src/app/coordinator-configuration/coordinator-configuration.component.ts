import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ConfigService} from '../config.service';
import * as flatbuffers from 'flatbuffers';
import {Key} from '../club/subjugated/fb/message/configuration/key';
import {CoordinatorConfiguration} from '../club/subjugated/fb/message/configuration/coordinator-configuration';
import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {firstValueFrom} from 'rxjs';
import {SafetyKey} from '../models/safety-key';

@Component({
  selector: 'app-coordinator-configuration',
  imports: [],
  templateUrl: './coordinator-configuration.component.html',
  styleUrl: './coordinator-configuration.component.scss'
})
export class CoordinatorConfigurationComponent implements AfterViewInit {
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private configService: ConfigService,
              private tartarusCoordinatorService: TartarusCoordinatorService) {
  }

  ngAfterViewInit() {
    this.buildConfiguration().then(r => {

    });
  }

  async buildConfiguration() {
    const config = this.configService.getConfig();

    const configFromApi = await firstValueFrom(this.tartarusCoordinatorService.getConfigurationFromCoordinator());

    console.log(configFromApi);
    const builder = new flatbuffers.Builder(1024);

    // const keyOffsets = [];
    // for(let k of configFromApi.safetyKeys!) {
    //   const nameOffset = builder.createString(k.name!);
    //   const publicKeyData = Uint8Array.from(atob(k.publicKey!), c => c.charCodeAt(0))
    //   const publicKeyOffset = Key.createPublicKeyVector(builder, publicKeyData);
    //
    //   Key.startKey(builder);
    //   Key.addName(builder, nameOffset);
    //   Key.addPublicKey(builder, publicKeyOffset);
    //   const keyOffset = Key.endKey(builder);
    //   keyOffsets.push(keyOffset);
    // }


    // Create repeated keys vector
    // const keysVector = CoordinatorConfiguration.createSafetyKeysVector(builder, keyOffsets);

    // Create main configuration
    const webUriOffset = builder.createString(configFromApi.webUri);
    const mqttUriOffset = builder.createString(configFromApi.mqttUri);
    const apiUriOffset = builder.createString(configFromApi.apiUri);

    CoordinatorConfiguration.startCoordinatorConfiguration(builder);
    CoordinatorConfiguration.addWebUri(builder, webUriOffset);
    CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset);
    CoordinatorConfiguration.addApiUri(builder, apiUriOffset);
    // CoordinatorConfiguration.addSafetyKeys(builder, keysVector);
    const configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder);

    builder.finish(configOffset);
    const data = builder.asUint8Array();
    console.log("Data size: " + data.length);
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
