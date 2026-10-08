import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { UserDataService } from './user-data.service';
import { vi } from 'vitest';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
      TestBed.runInInjectionContext(() => authGuard(...guardParameters));

  let mockUserDataService: { hasAuthorSession: ReturnType<typeof vi.fn> };
  const mockRoute = {} as ActivatedRouteSnapshot;
  const mockState = {} as RouterStateSnapshot;

  beforeEach(() => {
    mockUserDataService = {
      hasAuthorSession: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: UserDataService, useValue: mockUserDataService }
      ]
    });
  });

  it('should allow access if author session exists', () => {
    mockUserDataService.hasAuthorSession.mockReturnValue(true);
    const result = executeGuard(mockRoute, mockState);
    expect(result).toBe(true);
  });

  it('should redirect to root if no author session exists', () => {
    mockUserDataService.hasAuthorSession.mockReturnValue(false);
    const result = executeGuard(mockRoute, mockState);
    expect(result instanceof UrlTree).toBe(true);
    expect((result as UrlTree).toString()).toBe('/');
  });
});
