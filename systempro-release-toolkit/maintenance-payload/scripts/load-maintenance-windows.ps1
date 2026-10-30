$ErrorActionPreference = "Stop"
$MediaDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Archive = Join-Path $MediaDir "images\systempro-maintenance-images.tar"
if (-not (Test-Path $Archive)) { throw "Arquivo não encontrado: $Archive" }
docker info | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker indisponível." }
docker load -i $Archive
if ($LASTEXITCODE -ne 0) { throw "Falha ao carregar imagens de manutenção." }
Write-Host "SYSTEMPRO_MAINTENANCE_IMAGES=OK"
