import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SupplierDocuments } from './supplier-documents';

describe('SupplierDocuments', () => {
  let component: SupplierDocuments;
  let fixture: ComponentFixture<SupplierDocuments>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupplierDocuments],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierDocuments);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
