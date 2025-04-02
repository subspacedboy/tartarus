import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminContractDetailComponent } from './admin-contract-detail.component';

describe('AdminContractDetailComponent', () => {
  let component: AdminContractDetailComponent;
  let fixture: ComponentFixture<AdminContractDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminContractDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminContractDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
