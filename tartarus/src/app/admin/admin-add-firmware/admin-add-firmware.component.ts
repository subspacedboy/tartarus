import { Component, ChangeDetectionStrategy } from '@angular/core';
import {TartarusCoordinatorService} from '../../tartarus-coordinator.service';
import {FormBuilder, FormGroup, ReactiveFormsModule} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HlmCardImports } from '../../shared/ui/ui-card-helm';
import { HlmButtonDirective } from '../../shared/ui/ui-button-helm';
import { HlmInputDirective } from '../../shared/ui/ui-input-helm';
import { HlmLabelDirective } from '../../shared/ui/ui-label-helm';

@Component({
  selector: 'app-admin-add-firmware',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ...HlmCardImports,
    HlmButtonDirective,
    HlmInputDirective,
    HlmLabelDirective,
  ],
  templateUrl: './admin-add-firmware.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
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
