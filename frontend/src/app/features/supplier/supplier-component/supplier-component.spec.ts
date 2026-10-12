import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '../../../core/services/notification-service';
import { SupplierService } from '../../../core/services/supplier-service';
import { SupplierComponent } from './supplier-component';

describe('SupplierComponent', () => {
  let component: SupplierComponent;
  let fixture: ComponentFixture<SupplierComponent>;
  let supplierService: {
    findAll: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    getAddressByCep: ReturnType<typeof vi.fn>;
  };

  const emptyPage = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0,
  };

  beforeEach(async () => {
    supplierService = {
      findAll: vi.fn().mockReturnValue(of(emptyPage)),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
      getAddressByCep: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [SupplierComponent],
      providers: [
        provideRouter([]),
        { provide: SupplierService, useValue: supplierService },
        {
          provide: NotificationService,
          useValue: {
            show: vi.fn(),
            success: vi.fn(),
            error: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('should load the first supplier page on init', () => {
    supplierService.findAll.mockReturnValue(of({
      ...emptyPage,
      totalElements: 25,
      totalPages: 3,
      content: [],
    }));

    fixture.detectChanges();

    expect(supplierService.findAll).toHaveBeenCalledWith(0, 10, '');
    expect(component.totalElements()).toBe(25);
    expect(component.totalPages()).toBe(3);
    expect(component.currentPage()).toBe(0);
  });

  it('should search on the server after debounce and reset to page zero', () => {
    vi.useFakeTimers();
    component.currentPage.set(2);

    component.onSearchInput('Tech');

    expect(supplierService.findAll).not.toHaveBeenCalled();

    vi.advanceTimersByTime(349);
    expect(supplierService.findAll).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);

    expect(supplierService.findAll).toHaveBeenCalledWith(0, 10, 'Tech');
    expect(component.currentPage()).toBe(0);
  });
});
