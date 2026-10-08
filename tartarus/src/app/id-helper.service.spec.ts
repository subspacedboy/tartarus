import { TestBed } from '@angular/core/testing';
import { IdHelperService } from './id-helper.service';

describe('IdHelperService', () => {
  let service: IdHelperService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(IdHelperService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate a base32 id of default length 6', () => {
    const id = service.generateBase32Id();
    expect(id).toBeTruthy();
    expect(id.length).toBe(6);
    expect(id).toMatch(/^[A-Z2-7]+$/);
  });

  it('should generate a base32 id of custom length', () => {
    const id = service.generateBase32Id(10);
    expect(id.length).toBe(10);
    expect(id).toMatch(/^[A-Z2-7]+$/);
  });

  it('should generate unique ids on subsequent calls', () => {
    const id1 = service.generateBase32Id(8);
    const id2 = service.generateBase32Id(8);
    expect(id1).not.toBe(id2);
  });
});
