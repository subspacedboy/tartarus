import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LogoutLockUserComponent } from './logout-lock-user.component';

describe('LogoutLockUserComponent', () => {
  let component: LogoutLockUserComponent;
  let fixture: ComponentFixture<LogoutLockUserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LogoutLockUserComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LogoutLockUserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
