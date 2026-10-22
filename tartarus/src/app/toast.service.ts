import { Injectable } from '@angular/core';
import { toast as sonnerToast } from 'ngx-sonner';

export interface ToastInfo {
  header: string;
  body: string;
  delay?: number;
  classname?: string;
  textOrTpl?: any;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {

  toasts: ToastInfo[] = [];

  constructor() { }

  show(header: string, body: string) {
    this.toasts.push({ header, body });
    sonnerToast(header, { description: body });
  }

  showInfo(body: string) {
    const toast: ToastInfo = {
      header: '',
      body: '',
      textOrTpl: body,
      classname: ''
    };

    this.toasts.push(toast);
    sonnerToast.info(body, { duration: 4000 });
  }

  showSuccess(body: string) {
    const toast: ToastInfo = {
      header: '',
      body: '',
      textOrTpl: body,
      classname: 'bg-success text-light',
      delay: 2000
    };

    this.toasts.push(toast);
    sonnerToast.success(body, { duration: 3000 });
  }

  showError(body: string) {
    const toast: ToastInfo = {
      header: '',
      body: '',
      textOrTpl: body,
      classname: 'bg-danger text-light'
    };

    this.toasts.push(toast);
    sonnerToast.error(body, { duration: 6000 });
  }

  remove(toast: ToastInfo) {
    this.toasts = this.toasts.filter(t => t != toast);
  }
}
