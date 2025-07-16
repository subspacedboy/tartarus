import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminLandingPageComponent } from './admin-landing-page.component';

describe('AdminLandingPageComponent', () => {
  let component: AdminLandingPageComponent;
  let fixture: ComponentFixture<AdminLandingPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminLandingPageComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminLandingPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
