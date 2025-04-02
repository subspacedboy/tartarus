import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LogoutAuthorComponent } from './logout-author.component';

describe('LogoutAuthorComponent', () => {
  let component: LogoutAuthorComponent;
  let fixture: ComponentFixture<LogoutAuthorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LogoutAuthorComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LogoutAuthorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
