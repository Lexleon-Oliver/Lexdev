import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { NotificationService } from '../../../core/services/notification-service';
import { StockSettingsService } from '../../../core/services/stock-settings-service';

@Component({ selector:'app-stock-settings', imports:[CommonModule,ReactiveFormsModule], templateUrl:'./stock-settings.html', styleUrl:'./stock-settings.scss' })
export class StockSettingsComponent implements OnInit {
  private readonly service=inject(StockSettingsService); private readonly notification=inject(NotificationService); private readonly fb=inject(FormBuilder);
  loading=true; saving=false;
  readonly form=this.fb.nonNullable.group({ minSellableShelfLifeDays:[1,[Validators.required,Validators.min(0),Validators.max(3650),Validators.pattern(/^\d+$/)]] });
  ngOnInit():void{ this.service.find().pipe(finalize(()=>this.loading=false)).subscribe({next:s=>this.form.patchValue(s)}); }
  save():void{ if(this.form.invalid){this.form.markAllAsTouched();return;} this.saving=true; this.service.save(this.form.getRawValue()).pipe(finalize(()=>this.saving=false)).subscribe({next:s=>{this.form.patchValue(s);this.notification.success('Configuração de estoque salva.');}}); }
  policyText():string{ const days=Number(this.form.controls.minSellableShelfLifeDays.value); if(days===0)return 'Com 0 dias, produtos que vencem hoje podem ser vendidos.'; if(days===1)return 'Com 1 dia, o produto precisa ter validade pelo menos até amanhã.'; return `Com ${days} dias, produtos com menos de ${days} dias restantes de validade não poderão ser vendidos.`; }
}
