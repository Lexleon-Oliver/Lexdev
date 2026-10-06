import { TestBed } from '@angular/core/testing';
import { ProductFiscalProfileService } from './product-fiscal-profile-service';

describe('ProductFiscalProfileService', () => {
  let service: ProductFiscalProfileService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ProductFiscalProfileService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
