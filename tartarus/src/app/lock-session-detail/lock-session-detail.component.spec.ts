import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockSessionDetailComponent } from './lock-session-detail.component';

describe('LockSessionDetailComponent', () => {
  let component: LockSessionDetailComponent;
  let fixture: ComponentFixture<LockSessionDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockSessionDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockSessionDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
