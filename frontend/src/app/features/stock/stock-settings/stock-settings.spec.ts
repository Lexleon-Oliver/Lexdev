import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StockSettingsComponent } from './stock-settings';

describe('StockSettingsComponent',()=>{let fixture:ComponentFixture<StockSettingsComponent>;let component:StockSettingsComponent;let http:HttpTestingController;
beforeEach(async()=>{await TestBed.configureTestingModule({imports:[StockSettingsComponent],providers:[provideHttpClient(),provideHttpClientTesting()]}).compileComponents();fixture=TestBed.createComponent(StockSettingsComponent);component=fixture.componentInstance;http=TestBed.inject(HttpTestingController);fixture.detectChanges();http.expectOne('/api/stock/settings').flush({minSellableShelfLifeDays:1});fixture.detectChanges();});
afterEach(()=>http.verify());
it('shows explanatory policy for one day',()=>{expect(fixture.nativeElement.textContent).toContain('Com 1 dia, o produto precisa ter validade pelo menos até amanhã.');expect(component.policyText()).toContain('até amanhã');});
it('explains zero allows expiration today',()=>{component.form.controls.minSellableShelfLifeDays.setValue(0);expect(component.policyText()).toContain('vencem hoje podem ser vendidos');});});
