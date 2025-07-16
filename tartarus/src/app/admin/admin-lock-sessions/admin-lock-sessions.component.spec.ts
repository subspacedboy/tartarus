import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminLockSessionsComponent } from './admin-lock-sessions.component';

describe('AdminLockSessionsComponent', () => {
  let component: AdminLockSessionsComponent;
  let fixture: ComponentFixture<AdminLockSessionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLockSessionsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminLockSessionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
