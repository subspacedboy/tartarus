import {Component, ElementRef, ViewChild} from '@angular/core';
import {QrcodeService} from '../qrcode.service';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';

@Component({
  selector: 'app-wifi-helper',
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './wifi-helper.component.html',
  styleUrl: './wifi-helper.component.scss'
})
export class WifiHelperComponent {
  wifiForm;
  @ViewChild('canvas', { static: false }) canvas!: ElementRef;
  @ViewChild('hiddenDiv', { static: false }) hiddenDiv!: ElementRef;

  constructor(private qrcodeService: QrcodeService,
              private fb: FormBuilder,) {
    this.wifiForm = this.fb.group({
      ssid: ['', [Validators.required]],
      password: ['', [Validators.required]],
    });
  }

  generate() {
    // WIFI:T:<encryption>;S:<ssid>;P:<password>;H:<hidden>;;

    const ssid = String(this.wifiForm.get('ssid')!.value);
    const password = String(this.wifiForm.get('password')!.value);

    const toEncode = `WIFI:T:WPA;S:${ssid};P:${password};H:false;;`;

    const asArray = new TextEncoder().encode(toEncode);
    this.qrcodeService.generateQRCode(asArray, this.canvas).then(r => {
      console.log("Generated");
      this.hiddenDiv.nativeElement.hidden = false;
    });
  }
}
