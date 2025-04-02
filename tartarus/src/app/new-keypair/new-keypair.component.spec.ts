import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NewKeypairComponent } from './new-keypair.component';

describe('NewKeypairComponent', () => {
  let component: NewKeypairComponent;
  let fixture: ComponentFixture<NewKeypairComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NewKeypairComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NewKeypairComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
