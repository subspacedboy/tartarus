import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewFullContractComponent } from './new-full-contract.component';

describe('NewFullContractComponent', () => {
  let component: NewFullContractComponent;
  let fixture: ComponentFixture<NewFullContractComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewFullContractComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NewFullContractComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
