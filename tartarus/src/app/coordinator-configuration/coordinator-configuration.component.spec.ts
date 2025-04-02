import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CoordinatorConfigurationComponent } from './coordinator-configuration.component';

describe('CoordinatorConfigurationComponent', () => {
  let component: CoordinatorConfigurationComponent;
  let fixture: ComponentFixture<CoordinatorConfigurationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoordinatorConfigurationComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CoordinatorConfigurationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
