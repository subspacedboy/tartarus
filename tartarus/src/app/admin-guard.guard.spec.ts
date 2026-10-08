import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot } from '@angular/router';
import { adminGuard } from './admin.guard';
import { UserDataService } from './user-data.service';
import { vi } from 'vitest';

describe('adminGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
      TestBed.runInInjectionContext(() => adminGuard(...guardParameters));

  let mockUserDataService: { hasAdminSession: ReturnType<typeof vi.fn> };
  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = {} as RouterStateSnapshot;

  beforeEach(() => {
    mockUserDataService = {
      hasAdminSession: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: UserDataService, useValue: mockUserDataService }
      ]
    });
  });

  it('should return true when admin session exists', () => {
    mockUserDataService.hasAdminSession.mockReturnValue(true);
    expect(executeGuard(mockRoute, mockState)).toBe(true);
  });

  it('should return false when admin session does not exist', () => {
    mockUserDataService.hasAdminSession.mockReturnValue(false);
    expect(executeGuard(mockRoute, mockState)).toBe(false);
  });
});
