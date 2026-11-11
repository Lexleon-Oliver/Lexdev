import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

interface AiOutputTokensSettings {
  effectiveValue: number;
  installationDefault: number;
  overridden: boolean;
}

@Component({
  selector: 'app-support-ai-configuration',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './ai-configuration.html',
  styleUrl: './ai-configuration.scss',
})
export class SupportAiConfigurationComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly url = '/api/support/configuration/ai-output-tokens';

  readonly form = this.fb.nonNullable.group({
    value: [600, [Validators.required, Validators.min(128), Validators.max(2048), Validators.pattern(/^\d+$/)]],
  });
  settings: AiOutputTokensSettings | null = null;
  loading = false;
  saving = false;
  error = '';
  success = '';

  ngOnInit(): void { this.reload(); }

  reload(): void {
    if (this.loading || this.saving) return;
    this.loading = true;
    this.error = '';
    this.success = '';
    this.http.get<AiOutputTokensSettings>(this.url)
      .pipe(finalize(() => { this.loading = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: s => this.apply(s),
        error: () => { this.settings = null; this.error = 'Não foi possível consultar a configuração da IA.'; },
      });
  }

  save(): void {
    if (!this.settings || this.loading || this.saving) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving = true;
    this.error = '';
    this.success = '';
    this.http.put<AiOutputTokensSettings>(this.url, { value: this.form.controls.value.value })
      .pipe(finalize(() => { this.saving = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: s => { this.apply(s); this.success = 'Limite de tokens atualizado com auditoria.'; },
        error: () => { this.error = 'Não foi possível salvar o limite de tokens.'; },
      });
  }

  reset(): void {
    if (!this.settings?.overridden || this.loading || this.saving) return;
    this.saving = true;
    this.error = '';
    this.success = '';
    this.http.delete<AiOutputTokensSettings>(this.url)
      .pipe(finalize(() => { this.saving = false; this.cdr.detectChanges(); }))
      .subscribe({
        next: s => { this.apply(s); this.success = 'Padrão da instalação restaurado.'; },
        error: () => { this.error = 'Não foi possível restaurar o padrão da instalação.'; },
      });
  }

  private apply(settings: AiOutputTokensSettings): void {
    this.settings = settings;
    this.form.patchValue({ value: settings.effectiveValue });
    this.form.markAsPristine();
  }
}
