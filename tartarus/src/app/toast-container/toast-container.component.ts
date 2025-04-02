import {Component, TemplateRef} from '@angular/core';
import {ToastService} from '../toast.service';
import {NgbToast} from '@ng-bootstrap/ng-bootstrap';
import {NgForOf, NgIf, NgTemplateOutlet} from '@angular/common';

@Component({
  selector: 'app-toast-container',
  imports: [
    NgbToast,
    NgIf,
    NgTemplateOutlet,
    NgForOf
  ],
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss'
})
export class ToastContainerComponent {
  constructor(protected toastService: ToastService) { }
  isTemplate(toast: any) {
    return toast.textOrTpl instanceof TemplateRef;
  }
}
