import { TestBed } from '@angular/core/testing';
import { FiscalEstablishmentService } from './fiscal-establishment-service';

describe('FiscalEstablishmentService', () => {
  let service: FiscalEstablishmentService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FiscalEstablishmentService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
