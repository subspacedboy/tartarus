import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { ConfigService } from '../config.service';
import * as flatbuffers from 'flatbuffers';
import { Key } from '../club/subjugated/fb/message/configuration/key';
import { CoordinatorConfiguration } from '../club/subjugated/fb/message/configuration/coordinator-configuration';
import * as QRCode from 'qrcode';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { firstValueFrom, Subject } from 'rxjs';
import { SafetyKey } from '../models/safety-key';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AppConfig } from '../models/app-config';
import { QrcodeService } from '../qrcode.service';
import { HlmAlertImports } from '../shared/ui/ui-alert-helm';
import { HlmButtonDirective } from '../shared/ui/ui-button-helm';
import { HlmCardImports } from '../shared/ui/ui-card-helm';
import { HlmInputDirective } from '../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../shared/ui/ui-label-helm';

@Component({
  selector: 'app-coordinator-configuration',
  imports: [
    ReactiveFormsModule,
    ...HlmAlertImports,
    HlmButtonDirective,
    ...HlmCardImports,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './coordinator-configuration.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './coordinator-configuration.component.scss'
})
export class CoordinatorConfigurationComponent implements OnInit, AfterViewInit {
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  configurationForm;
  configFromApi? : AppConfig;

  backendSelection;

  private dataLoaded = new Subject<void>();

  constructor(private configService: ConfigService,
              private tartarusCoordinatorService: TartarusCoordinatorService,
              private fb: FormBuilder,
              private qrService: QrcodeService) {
    this.configurationForm = this.fb.group({
      webUri: ['', [Validators.required]],
      wsUri: ['', [Validators.required]],
      apiUri: ['', [Validators.required]],
      mqttUri: ['', [Validators.required]],
      includeSafetyKeys: [false],
    });

    this.backendSelection = this.fb.group({
      apiUri: ['', Validators.required],
      wsUri: ['', [Validators.required]]
    });
  }

  async ngOnInit() {
    console.log("ngOnInit");

    this.configFromApi = await firstValueFrom(this.tartarusCoordinatorService.getConfigurationFromCoordinator());

    console.log("configFromApi", this.configFromApi);
    this.configurationForm.get('webUri')!.setValue(this.configFromApi!.webUri!);
    this.configurationForm.get('wsUri')!.setValue(this.configFromApi!.wsUri!);
    this.configurationForm.get('apiUri')!.setValue(this.configFromApi!.apiUri!);
    this.configurationForm.get('mqttUri')!.setValue(this.configFromApi.mqttUri!);

    this.backendSelection.get('wsUri')!.setValue(this.configFromApi!.wsUri!);
    this.backendSelection.get('apiUri')!.setValue(this.configFromApi!.apiUri!);

    this.dataLoaded.next();  // Signal that data is ready
    this.dataLoaded.complete();
  }

  async ngAfterViewInit() {
    console.log("ngAfterViewInit");
    await firstValueFrom(this.dataLoaded);
    await this.buildConfiguration();
  }

  async chooseBackend() {
    localStorage.clear();
    this.configService.setOverride(
      String(this.backendSelection.get('apiUri')!.value),
      String(this.backendSelection.get('wsUri')!.value)
    );
  }

  async resetBackend() {
    localStorage.clear();
    this.configService.clearOverride();
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
    const wsUriOffset = builder.createString(this.configurationForm.get('wsUri')!.value);
    const mqttUriOffset = builder.createString(this.configurationForm.get('mqttUri')!.value);
    const apiUriOffset = builder.createString(this.configurationForm.get('apiUri')!.value);

    CoordinatorConfiguration.startCoordinatorConfiguration(builder);
    CoordinatorConfiguration.addWebUri(builder, webUriOffset);
    CoordinatorConfiguration.addWebUri(builder, wsUriOffset);
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
    let binaryString = '';
    for (let i = 0; i < data.length; i++) {
      binaryString += String.fromCharCode(data[i]);
    }
    const base64Data = btoa(binaryString);
    console.log("QR Data (Base64):\n" + base64Data);

    this.qrService.generateQRCode(data, this.canvas).then(r => {
      console.log("Generated");
      this.hiddenDiv.nativeElement.hidden = false;
    });
  }
}
