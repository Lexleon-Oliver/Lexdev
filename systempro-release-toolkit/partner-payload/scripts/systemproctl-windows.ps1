$ErrorActionPreference = "Stop"
$BaseDir = if ($env:SYSTEMPRO_HOME) { $env:SYSTEMPRO_HOME } else { Join-Path $env:ProgramData "SystemPro" }
$Compose = Join-Path $BaseDir "docker-compose.yml"
$EnvFile = Join-Path $BaseDir ".env"
$Agent = Join-Path $BaseDir "agent\systempro-license-agent.exe"
$AgentConfig = Join-Path $BaseDir "agent\config.json"
$ServiceName = "SystemProLicenseAgent"
if (-not (Test-Path $Compose) -or -not (Test-Path $EnvFile)) { throw "SystemPro não está instalado em $BaseDir." }
$Command = if ($args.Count -gt 0) { $args[0] } else { "status" }
switch ($Command) {
  "start" { docker compose -f $Compose --env-file $EnvFile up -d; Start-Service SystemProLicenseAgent -ErrorAction SilentlyContinue }
  "stop" { docker compose -f $Compose --env-file $EnvFile stop }
  "restart" { docker compose -f $Compose --env-file $EnvFile restart; Restart-Service SystemProLicenseAgent -ErrorAction SilentlyContinue }
  "status" { docker compose -f $Compose --env-file $EnvFile ps; Get-Service SystemProLicenseAgent -ErrorAction SilentlyContinue | Format-Table -AutoSize }
  "logs" { $svc = if ($args.Count -gt 1) { $args[1] } else { "" }; if ($svc) { docker compose -f $Compose --env-file $EnvFile logs --tail=200 $svc } else { docker compose -f $Compose --env-file $EnvFile logs --tail=200 } }
  "version" { (Get-Content $EnvFile | Where-Object { $_ -match '^SYSTEMPRO_VERSION=' } | Select-Object -Last 1) -replace '^SYSTEMPRO_VERSION=','' }
  "license" { & $Agent status --config $AgentConfig }
  "license-once" {
    $service = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    $restartService = $false
    $agentExitCode = 0

    try {
      if ($null -ne $service) {
        $service.Refresh()

        if ($service.Status -eq [System.ServiceProcess.ServiceControllerStatus]::StartPending) {
          $service.WaitForStatus(
            [System.ServiceProcess.ServiceControllerStatus]::Running,
            [TimeSpan]::FromSeconds(30)
          )
          $service.Refresh()
        }

        if ($service.Status -eq [System.ServiceProcess.ServiceControllerStatus]::Running) {
          $principal = New-Object Security.Principal.WindowsPrincipal(
            [Security.Principal.WindowsIdentity]::GetCurrent()
          )
          if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            throw "O comando license-once requer PowerShell como Administrador enquanto o serviço $ServiceName estiver em execução."
          }

          $restartService = $true
          Stop-Service -Name $ServiceName -ErrorAction Stop
          (Get-Service -Name $ServiceName -ErrorAction Stop).WaitForStatus(
            [System.ServiceProcess.ServiceControllerStatus]::Stopped,
            [TimeSpan]::FromSeconds(30)
          )
        }
        elseif ($service.Status -eq [System.ServiceProcess.ServiceControllerStatus]::StopPending) {
          $service.WaitForStatus(
            [System.ServiceProcess.ServiceControllerStatus]::Stopped,
            [TimeSpan]::FromSeconds(30)
          )
        }
      }

      & $Agent once --config $AgentConfig
      $agentExitCode = $LASTEXITCODE
    }
    finally {
      if ($restartService) {
        $currentService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
        if ($null -ne $currentService -and $currentService.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Running) {
          Start-Service -Name $ServiceName -ErrorAction Stop
          (Get-Service -Name $ServiceName -ErrorAction Stop).WaitForStatus(
            [System.ServiceProcess.ServiceControllerStatus]::Running,
            [TimeSpan]::FromSeconds(30)
          )
        }
      }
    }

    if ($agentExitCode -ne 0) { exit $agentExitCode }
  }
  default { Write-Host "Uso: systemproctl.ps1 start|stop|restart|status|logs [serviço]|version|license|license-once"; exit 2 }
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
