import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ConfigService} from '../config.service';
import * as flatbuffers from 'flatbuffers';
import {Key} from '../club/subjugated/fb/message/configuration/key';
import {CoordinatorConfiguration} from '../club/subjugated/fb/message/configuration/coordinator-configuration';
import * as QRCode from 'qrcode';
import {TartarusCoordinatorService} from '../tartarus-coordinator.service';
import {firstValueFrom, Subject} from 'rxjs';
import {SafetyKey} from '../models/safety-key';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {AppConfig} from '../models/app-config';
import {QrcodeService} from '../qrcode.service';

@Component({
  selector: 'app-coordinator-configuration',
  imports: [ReactiveFormsModule],
  templateUrl: './coordinator-configuration.component.html',
  styleUrl: './coordinator-configuration.component.scss'
})
export class CoordinatorConfigurationComponent implements OnInit, AfterViewInit {
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  configurationForm;
  configFromApi? : AppConfig;

  private dataLoaded = new Subject<void>();

  constructor(private configService: ConfigService,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private fb: FormBuilder,
              private qrService: QrcodeService) {
    this.configurationForm = this.fb.group({
      webUri: ['', [Validators.required]],
      apiUri: ['', [Validators.required]],
      mqttUri: ['', [Validators.required]],
      includeSafetyKeys: [false],
    });
  }

  async ngOnInit() {
    console.log("ngOnInit");

    this.configFromApi = await firstValueFrom(this.tartarusCoordinatorService.getConfigurationFromCoordinator());

    console.log("configFromApi", this.configFromApi);
    this.configurationForm.get('webUri')!.setValue(this.configFromApi!.webUri!);
    this.configurationForm.get('apiUri')!.setValue(this.configFromApi!.apiUri!);
    this.configurationForm.get('mqttUri')!.setValue(this.configFromApi.mqttUri!);

    this.dataLoaded.next();  // Signal that data is ready
    this.dataLoaded.complete();
  }

  async ngAfterViewInit() {
    console.log("ngAfterViewInit");
    await firstValueFrom(this.dataLoaded);
    await this.buildConfiguration();
  }

  async regenerate() {
    await this.buildConfiguration();
  }

  async buildConfiguration() {
    console.log(this.configFromApi);
    const builder = new flatbuffers.Builder(1024);

    const includeSafetyKeys = !!this.configurationForm.get('includeSafetyKeys')!.value;
    let keysVector;

    if(includeSafetyKeys) {
      const keyOffsets = [];
      for(let k of this.configFromApi!.safetyKeys!) {
        const nameOffset = builder.createString(k.name!);
        const publicKeyData = Uint8Array.from(atob(k.publicKey!), c => c.charCodeAt(0))
        const publicKeyOffset = Key.createPublicKeyVector(builder, publicKeyData);

        Key.startKey(builder);
        Key.addName(builder, nameOffset);
        Key.addPublicKey(builder, publicKeyOffset);
        const keyOffset = Key.endKey(builder);
        keyOffsets.push(keyOffset);
      }
      keysVector = CoordinatorConfiguration.createSafetyKeysVector(builder, keyOffsets);
    }

    // Create main configuration
    const webUriOffset = builder.createString(this.configurationForm.get('webUri')!.value);
    const mqttUriOffset = builder.createString(this.configurationForm.get('mqttUri')!.value);
    const apiUriOffset = builder.createString(this.configurationForm.get('apiUri')!.value);

    CoordinatorConfiguration.startCoordinatorConfiguration(builder);
    CoordinatorConfiguration.addWebUri(builder, webUriOffset);
    CoordinatorConfiguration.addMqttUri(builder, mqttUriOffset);
    CoordinatorConfiguration.addApiUri(builder, apiUriOffset);

    console.log(includeSafetyKeys);
    if(includeSafetyKeys) {
      CoordinatorConfiguration.addSafetyKeys(builder, keysVector!);
    }
    const configOffset = CoordinatorConfiguration.endCoordinatorConfiguration(builder);

    builder.finish(configOffset);
    const data = builder.asUint8Array();
    console.log("Data size: " + data.length);
    this.qrService.generateQRCode(data, this.canvas).then(r => {
      console.log("Generated");
      this.hiddenDiv.nativeElement.hidden = false;
    });
  }
}
