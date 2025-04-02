import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewSimpleContractComponent } from './new-simple-contract.component';

describe('NewSimpleContractComponent', () => {
  let component: NewSimpleContractComponent;
  let fixture: ComponentFixture<NewSimpleContractComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewSimpleContractComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NewSimpleContractComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
