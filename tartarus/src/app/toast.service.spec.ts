import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(service.toasts.length).toBe(0);
  });

  it('should add toast via show()', () => {
    service.show('Header 1', 'Body 1');
    expect(service.toasts.length).toBe(1);
    expect(service.toasts[0].header).toBe('Header 1');
    expect(service.toasts[0].body).toBe('Body 1');
  });

  it('should add info toast via showInfo()', () => {
    service.showInfo('Info text');
    expect(service.toasts.length).toBe(1);
    expect(service.toasts[0].textOrTpl).toBe('Info text');
  });

  it('should add success toast with proper styling and delay via showSuccess()', () => {
    service.showSuccess('Operation successful');
    expect(service.toasts.length).toBe(1);
    expect(service.toasts[0].textOrTpl).toBe('Operation successful');
    expect(service.toasts[0].classname).toBe('bg-success text-light');
    expect(service.toasts[0].delay).toBe(2000);
  });

  it('should add error toast with proper styling via showError()', () => {
    service.showError('Operation failed');
    expect(service.toasts.length).toBe(1);
    expect(service.toasts[0].textOrTpl).toBe('Operation failed');
    expect(service.toasts[0].classname).toBe('bg-danger text-light');
  });

  it('should remove specific toast via remove()', () => {
    service.show('Header 1', 'Body 1');
    service.show('Header 2', 'Body 2');
    expect(service.toasts.length).toBe(2);

    const firstToast = service.toasts[0];
    service.remove(firstToast);

    expect(service.toasts.length).toBe(1);
    expect(service.toasts[0].header).toBe('Header 2');
  });
});
