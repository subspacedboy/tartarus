import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WifiHelperComponent } from './wifi-helper.component';

describe('WifiHelperComponent', () => {
  let component: WifiHelperComponent;
  let fixture: ComponentFixture<WifiHelperComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WifiHelperComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WifiHelperComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
