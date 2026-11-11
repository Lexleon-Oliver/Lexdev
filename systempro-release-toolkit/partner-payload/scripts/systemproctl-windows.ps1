$ErrorActionPreference = "Stop"
$BaseDir = if ($env:SYSTEMPRO_HOME) { $env:SYSTEMPRO_HOME } else { Join-Path $env:ProgramData "SystemPro" }
$Compose = Join-Path $BaseDir "docker-compose.yml"
$EnvFile = Join-Path $BaseDir ".env"
$Agent = Join-Path $BaseDir "agent\systempro-license-agent.exe"
$AgentConfig = Join-Path $BaseDir "agent\config.json"
if (-not (Test-Path $Compose) -or -not (Test-Path $EnvFile)) { throw "SystemPro não está instalado em $BaseDir." }
$Command = if ($args.Count -gt 0) { $args[0] } else { "status" }
switch ($Command) {
  "start" { docker compose -f $Compose --env-file $EnvFile up -d; Start-Service SystemProLicenseAgent -ErrorAction SilentlyContinue }
  "stop" { docker compose -f $Compose --env-file $EnvFile stop }
  "restart" { docker compose -f $Compose --env-file $EnvFile restart; Restart-Service SystemProLicenseAgent -ErrorAction SilentlyContinue }
  "status" { docker compose -f $Compose --env-file $EnvFile ps; Get-Service SystemProLicenseAgent -ErrorAction SilentlyContinue | Format-Table -AutoSize }
  "logs" { $svc = if ($args.Count -gt 1) { $args[1] } else { "" }; if ($svc) { docker compose -f $Compose --env-file $EnvFile logs --tail=200 $svc } else { docker compose -f $Compose --env-file $EnvFile logs --tail=200 } }
  "version" { (Get-Content $EnvFile | Where-Object { $_ -match '^SYSTEMPRO_VERSION=' } | Select-Object -Last 1) -replace '^SYSTEMPRO_VERSION=','' }
  # --quiet impede a impressão do YAML renderizado (que incluiria segredos).
  "config-check" {
    docker compose -f $Compose --env-file $EnvFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Configuração inválida. Nenhum serviço foi alterado." }
    Write-Host "SYSTEMPRO_CONFIG=OK"
  }
  "config-apply" {
    $Target = if ($args.Count -gt 1) { $args[1] } else { "" }
    $Confirmation = if ($args.Count -gt 2) { $args[2] } else { "" }
    if ($Target -notin @("backend", "proxy") -or $Confirmation -ne "--confirm") {
      throw "Uso: systemproctl.ps1 config-apply backend|proxy --confirm"
    }
    docker compose -f $Compose --env-file $EnvFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Configuração inválida. Nenhum serviço foi alterado." }
    # Não atualiza dependências, banco, volumes ou Host Agent.
    docker compose -f $Compose --env-file $EnvFile up -d --no-deps --no-build --pull never $Target
    if ($LASTEXITCODE -ne 0) { throw "Falha ao reaplicar a configuração do serviço $Target." }
    Write-Host "SYSTEMPRO_CONFIG_APPLIED=$Target"
    Write-Host "ATENÇÃO: mudanças em licenciamento/Host Agent não são aplicadas por este comando."
  }
  "license" { & $Agent status --config $AgentConfig }
  "license-once" { & $Agent once --config $AgentConfig }
  default { Write-Host "Uso: systemproctl.ps1 start|stop|restart|status|logs [serviço]|version|config-check|config-apply backend|proxy --confirm|license|license-once"; exit 2 }
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
