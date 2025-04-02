import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockUserContractDetailComponent } from './lock-user-contract-detail.component';

describe('LockUserContractDetailComponent', () => {
  let component: LockUserContractDetailComponent;
  let fixture: ComponentFixture<LockUserContractDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockUserContractDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockUserContractDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
