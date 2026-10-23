import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { LockSessionsComponent } from './lock-sessions.component';
import { TartarusCoordinatorService } from '../tartarus-coordinator.service';
import { ToastService } from '../toast.service';
import { KnownToken } from '../models/known-token';
import { vi } from 'vitest';

describe('LockSessionsComponent', () => {
  let component: LockSessionsComponent;
  let fixture: ComponentFixture<LockSessionsComponent>;
  let coordinatorServiceSpy: any;
  let toastServiceSpy: any;
  let router: Router;

  beforeEach(async () => {
    coordinatorServiceSpy = {
      getKnownTokensForAuthor: vi.fn().mockReturnValue(of([
        new KnownToken({ name: 'kt-1', shareableToken: 'tok-1', notes: 'Lock 1' }),
        new KnownToken({ name: 'kt-2', shareableToken: 'tok-2', notes: '' })
      ])),
      deleteKnownToken: vi.fn().mockReturnValue(of(true))
    };

    toastServiceSpy = {
      showSuccess: vi.fn(),
      showError: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [LockSessionsComponent],
      providers: [
        provideRouter([]),
        { provide: TartarusCoordinatorService, useValue: coordinatorServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ]
    })
    .compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(LockSessionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load known tokens', () => {
    expect(component).toBeTruthy();
    expect(component.knownTokens.length).toBe(2);
    expect(component.knownTokens[0].name).toBe('kt-1');
    expect(component.labeledCount).toBe(1);
  });

  it('should filter known tokens by search query across notes, shareableToken, and name', () => {
    expect(component.filteredKnownTokens.length).toBe(2);

    component.searchQuery = 'Lock 1';
    expect(component.filteredKnownTokens.length).toBe(1);
    expect(component.filteredKnownTokens[0].name).toBe('kt-1');

    component.searchQuery = 'tok-2';
    expect(component.filteredKnownTokens.length).toBe(1);
    expect(component.filteredKnownTokens[0].name).toBe('kt-2');

    component.clearSearch();
    expect(component.filteredKnownTokens.length).toBe(2);
  });

  it('should extract session token from either raw token or full URL and navigate', () => {
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    expect(component.extractSessionToken('QAAAB3XYZ')).toBe('QAAAB3XYZ');
    expect(component.extractSessionToken('https://tartarus.example.com/lock-sessions/QAAAB3XYZ?foo=bar')).toBe('QAAAB3XYZ');
    expect(component.extractSessionToken('/lock-sessions/TOK-999/')).toBe('TOK-999');

    component.connectInput = 'https://tartarus.example.com/lock-sessions/QAAAB3XYZ';
    component.connectToSession();
    expect(navSpy).toHaveBeenCalledWith(['/lock-sessions', 'QAAAB3XYZ']);
  });

  it('should delete a token and show success toast', () => {
    const token = component.knownTokens[0];
    component.deleteToken(token);
    expect(coordinatorServiceSpy.deleteKnownToken).toHaveBeenCalledWith('kt-1');
    expect(component.knownTokens.length).toBe(1);
    expect(toastServiceSpy.showSuccess).toHaveBeenCalledWith('Lock session deleted');
  });
});
