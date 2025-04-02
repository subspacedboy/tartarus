import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockStartComponent } from './lock-start.component';

describe('LockStartComponent', () => {
  let component: LockStartComponent;
  let fixture: ComponentFixture<LockStartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockStartComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockStartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
