import { ComponentFixture, TestBed } from '@angular/core/testing';

import { KeyHelperComponent } from './key-helper.component';

describe('KeyHelperComponent', () => {
  let component: KeyHelperComponent;
  let fixture: ComponentFixture<KeyHelperComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KeyHelperComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(KeyHelperComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
