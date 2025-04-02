import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MyLockComponent } from './my-lock.component';

describe('MyLockComponent', () => {
  let component: MyLockComponent;
  let fixture: ComponentFixture<MyLockComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyLockComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MyLockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
