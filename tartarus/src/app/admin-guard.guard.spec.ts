import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';

import { adminGuard } from './admin.guard';

describe('adminGuardGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
      TestBed.runInInjectionContext(() => adminGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
