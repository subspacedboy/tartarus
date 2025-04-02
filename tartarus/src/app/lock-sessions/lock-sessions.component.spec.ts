import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockSessionsComponent } from './lock-sessions.component';

describe('LockSessionsComponent', () => {
  let component: LockSessionsComponent;
  let fixture: ComponentFixture<LockSessionsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockSessionsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockSessionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
