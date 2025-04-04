import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAddFirmwareComponent } from './admin-add-firmware.component';

describe('AdminAddFirmwareComponent', () => {
  let component: AdminAddFirmwareComponent;
  let fixture: ComponentFixture<AdminAddFirmwareComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAddFirmwareComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminAddFirmwareComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
