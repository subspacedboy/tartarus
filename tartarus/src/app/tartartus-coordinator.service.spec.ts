import { TestBed } from '@angular/core/testing';

import { TartarusCoordinatorService } from './tartarus-coordinator.service';

describe('KeyService', () => {
  let service: TartarusCoordinatorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TartarusCoordinatorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
