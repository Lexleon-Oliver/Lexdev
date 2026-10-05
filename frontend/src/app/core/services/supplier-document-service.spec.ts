import { TestBed } from '@angular/core/testing';
import { SupplierDocumentService } from './supplier-document-service';

describe('SupplierDocumentService', () => {
  let service: SupplierDocumentService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SupplierDocumentService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
