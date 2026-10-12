import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BtComponent } from './bt.component';

describe('BtComponent', () => {
  let component: BtComponent;
  let fixture: ComponentFixture<BtComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BtComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BtComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize forms with default empty values', () => {
    expect(component.wifiForm.get('ssid')?.value).toBe('');
    expect(component.wifiForm.get('password')?.value).toBe('');
    expect(component.commandForm.get('command')?.value).toBe('');
  });
});
