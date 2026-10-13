import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StockSettingsService } from './stock-settings-service';

describe('StockSettingsService', () => {
  let service: StockSettingsService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]}); service=TestBed.inject(StockSettingsService); http=TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('loads settings', () => { service.find().subscribe(); const req=http.expectOne('/api/stock/settings'); expect(req.request.method).toBe('GET'); req.flush({minSellableShelfLifeDays:1}); });
  it('saves settings', () => { service.save({minSellableShelfLifeDays:2}).subscribe(); const req=http.expectOne('/api/stock/settings'); expect(req.request.method).toBe('PUT'); expect(req.request.body).toEqual({minSellableShelfLifeDays:2}); req.flush({minSellableShelfLifeDays:2}); });
});
