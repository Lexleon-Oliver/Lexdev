$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Execute o PowerShell como Administrador."
}

$MediaDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$InstallDir = if ($env:SYSTEMPRO_HOME) { $env:SYSTEMPRO_HOME } else { Join-Path $env:ProgramData "SystemPro" }
$ImageArchive = Join-Path $MediaDir "images\systempro-runtime-images.tar"
$ComposeSource = Join-Path $MediaDir "config\docker-compose.yml"
$CtlSource = Join-Path $MediaDir "scripts\systemproctl-windows.ps1"
$AgentSource = Join-Path $MediaDir "agent\windows\systempro-license-agent.exe"
$RootPublicSource = Join-Path $MediaDir "agent\trust\root-public.json"
$Version = (Get-Content (Join-Path $MediaDir "VERSION") -Raw).Trim()
$EnvFile = Join-Path $InstallDir ".env"
$ServiceName = "SystemProLicenseAgent"

function New-RandomBase64([int]$Count) {
    # Compatível com Windows PowerShell 5.1 / .NET Framework e PowerShell 7+.
    # RandomNumberGenerator.Fill() não existe no .NET Framework usado pelo PS 5.1.
    $bytes = New-Object byte[] $Count
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        if ($null -ne $rng) { $rng.Dispose() }
    }
    return [Convert]::ToBase64String($bytes)
}

function Read-EnvFile([string]$Path) {
    $map = @{}
    if (Test-Path $Path) {
        foreach ($line in Get-Content $Path) {
            if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
            $pair = $line.Split('=',2)
            $value = $pair[1]
            if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $map[$pair[0]] = $value
        }
    }
    return $map
}

function Ensure-Env([string]$Name, [string]$Value) {
    $current = Read-EnvFile $EnvFile
    if (-not $current.ContainsKey($Name)) {
        Add-Content -Path $EnvFile -Encoding ascii -Value ("{0}={1}" -f $Name,$Value)
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker não foi encontrado. Instale/inicie o Docker Desktop em modo Linux containers."
}
docker info | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker não está em execução." }
docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Compose v2 não está disponível." }
if (-not (Test-Path $ImageArchive)) { throw "Arquivo de imagens não encontrado: $ImageArchive" }
if (-not (Test-Path $AgentSource)) { throw "Host Agent Windows não encontrado na mídia." }
if (-not (Test-Path $RootPublicSource)) { throw "Root Public Key de licenciamento não encontrada na mídia." }

if (Test-Path (Join-Path $MediaDir "SHA256SUMS")) {
    foreach ($line in Get-Content (Join-Path $MediaDir "SHA256SUMS")) {
        if ($line -match '^([0-9a-fA-F]{64})\s+\*?(.+)$') {
            $expected = $Matches[1].ToLowerInvariant()
            $relative = $Matches[2] -replace '^\./',''
            $target = Join-Path $MediaDir $relative
            if (Test-Path $target) {
                $actual = (Get-FileHash -Algorithm SHA256 $target).Hash.ToLowerInvariant()
                if ($actual -ne $expected) { throw "Falha de integridade: $relative" }
            }
        }
    }
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item $ComposeSource (Join-Path $InstallDir "docker-compose.yml") -Force
Copy-Item $CtlSource (Join-Path $InstallDir "systemproctl.ps1") -Force

$FirstInstall = -not (Test-Path $EnvFile)
if ($FirstInstall) {
    $Port = if ($env:SYSTEMPRO_HTTP_PORT) { [int]$env:SYSTEMPRO_HTTP_PORT } else { 8080 }
    if ($Port -lt 1 -or $Port -gt 65535) { throw "SYSTEMPRO_HTTP_PORT inválida: $Port" }
    $InstanceDirNative = if ($env:SYSTEMPRO_INSTANCE_DIR) { $env:SYSTEMPRO_INSTANCE_DIR } else { Join-Path $InstallDir "instance" }
    $InstanceDirDocker = $InstanceDirNative -replace '\\','/'
    $LicenseServer = if ($env:SYSTEMPRO_LICENSE_SERVER_URL) { $env:SYSTEMPRO_LICENSE_SERVER_URL } else { "https://license.lexdev.net.br" }
    $ActivationPath = if ($env:SYSTEMPRO_LICENSE_ACTIVATION_PATH) { $env:SYSTEMPRO_LICENSE_ACTIVATION_PATH } else { "/api/v1/activations" }
    $HeartbeatPath = if ($env:SYSTEMPRO_LICENSE_HEARTBEAT_PATH) { $env:SYSTEMPRO_LICENSE_HEARTBEAT_PATH } else { "/api/v1/heartbeats" }
    $RenewalPath = if ($env:SYSTEMPRO_LICENSE_RENEWAL_PATH) { $env:SYSTEMPRO_LICENSE_RENEWAL_PATH } else { "/api/v1/leases/renewals" }

    @"
# SystemPro - configuração persistente da instalação
SYSTEMPRO_VERSION=$Version
SYSTEMPRO_HTTP_PORT=$Port
SYSTEMPRO_INSTANCE_DIR=$InstanceDirDocker

# Banco
DB_HOST=db
DB_PORT=5432
DB_NAME=systempro
DB_USER=systempro_app
DB_PASSWORD=$(New-RandomBase64 36)

# JWT
SECRET_KEY=$(New-RandomBase64 64)
SECRET_REFRESH_KEY=$(New-RandomBase64 64)
JWT_ISSUER=systempro
JWT_AUDIENCE=systempro-web
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# Storage
STORAGE_PROVIDER=local
STORAGE_LOCAL_STORAGE_LOCATION=/data/supplier-documents
STORAGE_ENDPOINT=
STORAGE_REGION=us-east-1
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
STORAGE_BUCKET=systempro-documents
STORAGE_PATH_STYLE_ACCESS=false
STORAGE_MAX_FILE_SIZE_BYTES=15728640
SYSTEMPRO_IMAGES_STORAGE_LOCATION=/data/product-images

# ClamAV
CLAMAV_HOST=clamav
CLAMAV_PORT=3310
CLAMAV_TIMEOUT_SECONDS=30

# Fiscal
FISCAL_ENABLED=true
FISCAL_PRODUCTION_READY=false
FISCAL_ENCRYPTION_KEY=$(New-RandomBase64 48)
FISCAL_CONNECT_TIMEOUT_SECONDS=10
FISCAL_REQUEST_TIMEOUT_SECONDS=30
FISCAL_SCHEMA_VERSION=4.00
FISCAL_PRODUCT_VERSION=SystemPro-$Version
FISCAL_TRUST_STORE_PATH=
FISCAL_TRUST_STORE_PASSWORD=
FISCAL_SCHEMA_LOCATION=classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd

# IA (desabilitada por padrão)
AI_ENABLED=false
AI_PROVIDER=openai
AI_MAX_OUTPUT_TOKENS=600
AI_CONNECT_TIMEOUT_SECONDS=10
AI_REQUEST_TIMEOUT_SECONDS=30
OPENAI_API_KEY=
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-6-luna
GEMINI_API_KEY=
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/v1
GEMINI_MODEL=gemini-3.5-flash-lite

# Backup
BACKUP_STORAGE_LOCATION=/data/backups
BACKUP_PG_DUMP_TIMEOUT_SECONDS=600
BACKUP_SCHEDULING_ENABLED=true
BACKUP_SCHEDULING_CRON="0 0 2 * * *"
BACKUP_SCHEDULING_ZONE=America/Sao_Paulo
BACKUP_SCHEDULING_MAX_AGE_HOURS=24
BACKUP_SCHEDULING_CHECK_INTERVAL_MILLIS=3600000
BACKUP_SCHEDULING_INITIAL_DELAY_MILLIS=60000
BACKUP_RETENTION_SCHEDULED_DAYS=30

# Licenciamento - Host Agent
SYSTEMPRO_LICENSE_SERVER_URL=$LicenseServer
SYSTEMPRO_LICENSE_ACTIVATION_PATH=$ActivationPath
SYSTEMPRO_LICENSE_HEARTBEAT_PATH=$HeartbeatPath
SYSTEMPRO_LICENSE_RENEWAL_PATH=$RenewalPath
SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS=30
SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS=21600
SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS=86400
SYSTEMPRO_LICENSE_JITTER_SECONDS=900
LICENSE_CACHE_MILLIS=5000
"@ | Set-Content -Path $EnvFile -Encoding ascii
} else {
    $lines = Get-Content $EnvFile
    $found = $false
    $updated = foreach ($line in $lines) {
        if ($line -match '^SYSTEMPRO_VERSION=') { $found = $true; "SYSTEMPRO_VERSION=$Version" } else { $line }
    }
    if (-not $found) { $updated += "SYSTEMPRO_VERSION=$Version" }
    $updated | Set-Content -Path $EnvFile -Encoding ascii

    $DefaultInstance = (Join-Path $InstallDir "instance") -replace '\\','/'
    Ensure-Env "SYSTEMPRO_INSTANCE_DIR" $DefaultInstance
    Ensure-Env "DB_HOST" "db"; Ensure-Env "DB_PORT" "5432"
    Ensure-Env "STORAGE_PROVIDER" "local"; Ensure-Env "STORAGE_LOCAL_STORAGE_LOCATION" "/data/supplier-documents"
    Ensure-Env "STORAGE_ENDPOINT" ""; Ensure-Env "STORAGE_REGION" "us-east-1"; Ensure-Env "STORAGE_ACCESS_KEY" ""; Ensure-Env "STORAGE_SECRET_KEY" ""
    Ensure-Env "STORAGE_BUCKET" "systempro-documents"; Ensure-Env "STORAGE_PATH_STYLE_ACCESS" "false"; Ensure-Env "STORAGE_MAX_FILE_SIZE_BYTES" "15728640"
    Ensure-Env "SYSTEMPRO_IMAGES_STORAGE_LOCATION" "/data/product-images"
    Ensure-Env "CLAMAV_HOST" "clamav"; Ensure-Env "CLAMAV_PORT" "3310"; Ensure-Env "CLAMAV_TIMEOUT_SECONDS" "30"
    Ensure-Env "FISCAL_CONNECT_TIMEOUT_SECONDS" "10"; Ensure-Env "FISCAL_REQUEST_TIMEOUT_SECONDS" "30"; Ensure-Env "FISCAL_SCHEMA_VERSION" "4.00"
    Ensure-Env "FISCAL_PRODUCT_VERSION" "SystemPro-$Version"; Ensure-Env "FISCAL_TRUST_STORE_PATH" ""; Ensure-Env "FISCAL_TRUST_STORE_PASSWORD" ""
    Ensure-Env "FISCAL_SCHEMA_LOCATION" "classpath:/fiscal/nfce/PL_010f/nfe_v4.00.xsd"
    Ensure-Env "AI_PROVIDER" "openai"; Ensure-Env "AI_MAX_OUTPUT_TOKENS" "600"; Ensure-Env "AI_CONNECT_TIMEOUT_SECONDS" "10"; Ensure-Env "AI_REQUEST_TIMEOUT_SECONDS" "30"
    Ensure-Env "OPENAI_API_KEY" ""; Ensure-Env "OPENAI_BASE_URL" "https://api.openai.com/v1"; Ensure-Env "OPENAI_MODEL" "gpt-6-luna"
    Ensure-Env "GEMINI_API_KEY" ""; Ensure-Env "GEMINI_BASE_URL" "https://generativelanguage.googleapis.com/v1"; Ensure-Env "GEMINI_MODEL" "gemini-3.5-flash-lite"
    Ensure-Env "BACKUP_PG_DUMP_TIMEOUT_SECONDS" "600"; Ensure-Env "BACKUP_SCHEDULING_ENABLED" "true"; Ensure-Env "BACKUP_SCHEDULING_CRON" '"0 0 2 * * *"'
    Ensure-Env "BACKUP_SCHEDULING_ZONE" "America/Sao_Paulo"; Ensure-Env "BACKUP_SCHEDULING_MAX_AGE_HOURS" "24"
    Ensure-Env "BACKUP_SCHEDULING_CHECK_INTERVAL_MILLIS" "3600000"; Ensure-Env "BACKUP_SCHEDULING_INITIAL_DELAY_MILLIS" "60000"; Ensure-Env "BACKUP_RETENTION_SCHEDULED_DAYS" "30"
    Ensure-Env "SYSTEMPRO_LICENSE_SERVER_URL" "https://license.lexdev.net.br"
    Ensure-Env "SYSTEMPRO_LICENSE_ACTIVATION_PATH" "/api/v1/activations"
    Ensure-Env "SYSTEMPRO_LICENSE_HEARTBEAT_PATH" "/api/v1/heartbeats"
    Ensure-Env "SYSTEMPRO_LICENSE_RENEWAL_PATH" "/api/v1/leases/renewals"
    Ensure-Env "SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS" "30"
    Ensure-Env "SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS" "21600"
    Ensure-Env "SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS" "86400"
    Ensure-Env "SYSTEMPRO_LICENSE_JITTER_SECONDS" "900"
    Ensure-Env "LICENSE_CACHE_MILLIS" "5000"
}

$EnvMap = Read-EnvFile $EnvFile
$InstanceDirDocker = $EnvMap["SYSTEMPRO_INSTANCE_DIR"]
$InstanceDirNative = $InstanceDirDocker -replace '/','\'
New-Item -ItemType Directory -Force -Path (Join-Path $InstanceDirNative "private") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstanceDirNative "public") | Out-Null

$AgentDir = Join-Path $InstallDir "agent"
$TrustDir = Join-Path $AgentDir "trust"
$AgentExe = Join-Path $AgentDir "systempro-license-agent.exe"
$AgentConfig = Join-Path $AgentDir "config.json"
New-Item -ItemType Directory -Force -Path $TrustDir | Out-Null

# Em atualização, o Windows mantém o executável do serviço bloqueado enquanto ele está em execução.
# Pare e aguarde o serviço encerrar antes de substituir o Host Agent. A identidade e a chave
# privada ficam em instance\ e não são removidas/recriadas por esta operação.
$Existing = Get-Service $ServiceName -ErrorAction SilentlyContinue
if ($Existing -and $Existing.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Stopped) {
    Write-Host "Parando $ServiceName para atualizar o Host Agent..."
    Stop-Service -Name $ServiceName -Force -ErrorAction Stop
    $Existing.WaitForStatus(
        [System.ServiceProcess.ServiceControllerStatus]::Stopped,
        [TimeSpan]::FromSeconds(30)
    )
    $Existing.Refresh()
    if ($Existing.Status -ne [System.ServiceProcess.ServiceControllerStatus]::Stopped) {
        throw "O serviço $ServiceName não parou dentro do prazo de 30 segundos."
    }
}

# Alguns antivírus podem manter o arquivo aberto por uma fração de segundo após o serviço parar.
# Faça poucas tentativas controladas antes de considerar a atualização inválida.
$AgentCopied = $false
for ($Attempt = 1; $Attempt -le 5; $Attempt++) {
    try {
        Copy-Item $AgentSource $AgentExe -Force -ErrorAction Stop
        $AgentCopied = $true
        break
    } catch {
        if ($Attempt -eq 5) { throw }
        Start-Sleep -Seconds 1
    }
}
if (-not $AgentCopied) { throw "Não foi possível atualizar o executável do Host Agent." }

Copy-Item $RootPublicSource (Join-Path $TrustDir "root-public.json") -Force

$config = [ordered]@{
    serverUrl = $EnvMap["SYSTEMPRO_LICENSE_SERVER_URL"]
    activationPath = $EnvMap["SYSTEMPRO_LICENSE_ACTIVATION_PATH"]
    heartbeatPath = $EnvMap["SYSTEMPRO_LICENSE_HEARTBEAT_PATH"]
    renewalPath = $EnvMap["SYSTEMPRO_LICENSE_RENEWAL_PATH"]
    systemProVersion = $Version
    instanceDir = $InstanceDirNative
    rootPublicFile = (Join-Path $TrustDir "root-public.json")
    requestTimeoutSeconds = [int]$EnvMap["SYSTEMPRO_LICENSE_REQUEST_TIMEOUT_SECONDS"]
    heartbeatIntervalSeconds = [int]$EnvMap["SYSTEMPRO_LICENSE_HEARTBEAT_INTERVAL_SECONDS"]
    renewalIntervalSeconds = [int]$EnvMap["SYSTEMPRO_LICENSE_RENEWAL_INTERVAL_SECONDS"]
    jitterSeconds = [int]$EnvMap["SYSTEMPRO_LICENSE_JITTER_SECONDS"]
}
$ConfigJson = $config | ConvertTo-Json
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($AgentConfig, $ConfigJson, $Utf8NoBom)

$PublicInstance = Join-Path $InstanceDirNative "public\instance.json"
if (-not (Test-Path $PublicInstance)) {
    $ActivationCode = $env:SYSTEMPRO_ACTIVATION_CODE
    if (-not $ActivationCode) {
        $secure = Read-Host "Código de ativação do SystemPro" -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $ActivationCode = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if (-not $ActivationCode) { throw "Código de ativação não informado." }
    $ActivationCode | & $AgentExe activate --code-stdin --config $AgentConfig
    if ($LASTEXITCODE -ne 0) { throw "Ativação do SystemPro falhou." }
    $ActivationCode = $null
    Remove-Item Env:SYSTEMPRO_ACTIVATION_CODE -ErrorAction SilentlyContinue
}

$BinPath = '"' + $AgentExe + '" service --config "' + $AgentConfig + '"'
$Existing = Get-Service $ServiceName -ErrorAction SilentlyContinue
if ($Existing) {
    & sc.exe config $ServiceName binPath= $BinPath start= auto | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Falha ao atualizar o serviço $ServiceName." }
} else {
    & sc.exe create $ServiceName binPath= $BinPath start= auto DisplayName= "SystemPro License Host Agent" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Falha ao criar o serviço $ServiceName." }
    & sc.exe description $ServiceName "Identidade, heartbeat e renewal de licenciamento do SystemPro." | Out-Null
}
Start-Service $ServiceName

Write-Host "Carregando imagens do SystemPro..."
docker load -i $ImageArchive
if ($LASTEXITCODE -ne 0) { throw "Falha ao carregar as imagens Docker." }

Write-Host "Iniciando SystemPro..."
docker compose -f (Join-Path $InstallDir "docker-compose.yml") --env-file $EnvFile up -d
if ($LASTEXITCODE -ne 0) { throw "Falha ao iniciar o SystemPro." }

$EnvMap = Read-EnvFile $EnvFile
Write-Host ""
Write-Host "SYSTEMPRO_INSTALL=OK"
Write-Host "Versão: $Version"
Write-Host "Acesso local: http://localhost:$($EnvMap['SYSTEMPRO_HTTP_PORT'])"
Write-Host "Diretório: $InstallDir"
Write-Host "Licença: PowerShell -ExecutionPolicy Bypass -File `"$InstallDir\systemproctl.ps1`" license"
if ($FirstInstall) {
    Write-Host "IMPORTANTE: .env e instance\private contêm segredos únicos. Não substitua nem copie entre clientes."
    Write-Host "Gere o Recovery Kit .sprecovery após validar a instalação."
}
