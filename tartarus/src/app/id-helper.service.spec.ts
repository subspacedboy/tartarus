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
});
