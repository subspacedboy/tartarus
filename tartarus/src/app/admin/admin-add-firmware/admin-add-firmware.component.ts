import { Component } from '@angular/core';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';

@Component({
  selector: 'app-admin-add-firmware',
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './admin-add-firmware.component.html',
  styleUrl: './admin-add-firmware.component.scss'
})
export class AdminAddFirmwareComponent {
  firmwareForm: FormGroup;

  constructor(private tartartusCoordinatorService: TartarusCoordinatorService,
              private fb : FormBuilder) {
    this.firmwareForm = this.fb.group({
      firmwareFile: [null]
    });
  }


  onFileChange(event: any) {
    const file = event.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        const base64 = btoa(reader.result as string);
        this.firmwareForm.patchValue({ firmwareFile: base64 });
      };
      reader.readAsBinaryString(file);
    }
  }

  onSubmit() {
    const payload = this.firmwareForm.value;
    const bytesAsBase64 = payload.firmwareFile;
    this.tartartusCoordinatorService.addFirmware(bytesAsBase64).subscribe(res => {
      console.log(res);
    });
  }


}
