import { Injectable } from '@angular/core';

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
  }

  showInfo(body: string) {
    const toast: ToastInfo = {
      header: '',
      body: '',
      textOrTpl: body,
      classname: ''
    };

    this.toasts.push(toast);
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
  }

  showError(body: string) {
    const toast: ToastInfo = {
      header: '',
      body: '',
      textOrTpl: body,
      classname: 'bg-danger text-light'
    };

    this.toasts.push(toast);
  }

  remove(toast: ToastInfo) {
    this.toasts = this.toasts.filter(t => t != toast);
  }
}
