import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContractDetailComponent } from './contract-detail.component';

describe('ContractDetailComponent', () => {
  let component: ContractDetailComponent;
  let fixture: ComponentFixture<ContractDetailComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContractDetailComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ContractDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
