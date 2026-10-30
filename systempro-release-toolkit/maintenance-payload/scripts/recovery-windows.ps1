$ErrorActionPreference = "Stop"
$BaseDir = if ($env:SYSTEMPRO_HOME) { $env:SYSTEMPRO_HOME } else { Join-Path $env:ProgramData "SystemPro" }
$MediaDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Main = Join-Path $BaseDir "docker-compose.yml"
$Overlay = Join-Path $MediaDir "config\docker-compose.maintenance.yml"
$EnvFile = Join-Path $BaseDir ".env"
if (-not (Test-Path $Main) -or -not (Test-Path $EnvFile)) { throw "Instalação SystemPro não encontrada em $BaseDir." }

$Command = if ($args.Count -gt 0) { $args[0] } else { "help" }
switch ($Command) {
    "current" {
        docker compose -f $Main -f $Overlay --env-file $EnvFile --profile maintenance run --rm --no-deps recovery current
    }
    "create" {
        $OutDir = if ($args.Count -gt 1) { (Resolve-Path -LiteralPath $args[1] -ErrorAction SilentlyContinue) } else { $null }
        if (-not $OutDir) {
            $raw = if ($args.Count -gt 1) { $args[1] } else { Join-Path (Get-Location) "recovery-kits" }
            New-Item -ItemType Directory -Force -Path $raw | Out-Null
            $OutDir = Resolve-Path -LiteralPath $raw
        }
        $Name = "systempro-recovery-$([DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')).sprecovery"
        docker compose -f $Main -f $Overlay --env-file $EnvFile --profile maintenance run --rm --no-deps -v "${OutDir}:/recovery-out" recovery create "/recovery-out/$Name"
        Write-Host "RECOVERY_KIT_HOST_FILE=$(Join-Path $OutDir $Name)"
    }
    "verify" {
        if ($args.Count -ne 2) { throw "Uso: recovery-windows.ps1 verify arquivo.sprecovery" }
        $File = Resolve-Path -LiteralPath $args[1]
        $Dir = Split-Path -Parent $File; $Name = Split-Path -Leaf $File
        docker compose -f $Main -f $Overlay --env-file $EnvFile --profile maintenance run --rm --no-deps -v "${Dir}:/recovery-in:ro" recovery verify "/recovery-in/$Name"
    }
    "extract" {
        if ($args.Count -lt 3 -or $args.Count -gt 4) {
            throw "Uso: recovery-windows.ps1 extract kit.sprecovery diretorio [--force]"
        }

        $File = Resolve-Path -LiteralPath $args[1]
        if (-not $File -or -not (Test-Path -LiteralPath $File -PathType Leaf)) {
            throw "Arquivo não encontrado: $($args[1])"
        }

        $OutRaw = $args[2]
        New-Item -ItemType Directory -Force -Path $OutRaw | Out-Null
        $OutDir = Resolve-Path -LiteralPath $OutRaw

        $Extra = @()
        if ($args.Count -eq 4) {
            if ($args[3] -ne "--force") {
                throw "Opção inválida: $($args[3]). Uso: recovery-windows.ps1 extract kit.sprecovery diretorio [--force]"
            }
            $Extra += "--force"
        }

        $Dir = Split-Path -Parent $File
        $Name = Split-Path -Leaf $File
        docker compose -f $Main -f $Overlay --env-file $EnvFile --profile maintenance run --rm --no-deps `
            -v "${Dir}:/recovery-in:ro" `
            -v "${OutDir}:/recovery-out" `
            recovery extract "/recovery-in/$Name" /recovery-out @Extra
    }
    default { Write-Host "Uso: recovery-windows.ps1 current|create [dir]|verify kit.sprecovery|extract kit.sprecovery dir [--force]" }
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
