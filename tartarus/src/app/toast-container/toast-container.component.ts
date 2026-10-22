import { ChangeDetectionStrategy, Component, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ToastService } from '../toast.service';
import { HlmToasterComponent } from '../shared/ui/ui-sonner-helm';

@Component({
  selector: 'app-toast-container',
  imports: [
    HlmToasterComponent,
    NgTemplateOutlet
  ],
  templateUrl: './toast-container.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './toast-container.component.scss'
})
export class ToastContainerComponent {
  constructor(protected toastService: ToastService) { }
  isTemplate(toast: any) {
    return toast.textOrTpl instanceof TemplateRef;
  }
}
