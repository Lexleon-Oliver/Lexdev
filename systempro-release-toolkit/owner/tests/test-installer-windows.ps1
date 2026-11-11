# Executar no Windows PowerShell 5.1 ou PowerShell 7; sem Docker ou instalação real.
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -and $PSVersionTable.PSVersion.Major -ge 6) {
    throw 'Este teste de ACL deve ser executado no Windows.'
}
$Toolkit = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Script = Join-Path $Toolkit 'partner-payload\scripts\install-windows.ps1'
if (-not (Test-Path -LiteralPath $Script)) { throw "Instalador não encontrado: $Script" }
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($Script, [ref]$tokens, [ref]$errors)
if ($errors.Count -gt 0) { throw ('Erros de sintaxe PowerShell: ' + ($errors | Out-String)) }
$source = [System.IO.File]::ReadAllText($Script)
if ($source -notmatch 'else\s*\{\s*"/api/v1/heartbeat"\s*\}') { throw 'Heartbeat padrão do Windows não foi corrigido.' }
if ($source -match 'Ensure-Env\s+"SYSTEMPRO_LICENSE_HEARTBEAT_PATH"\s+"/api/v1/heartbeats"') { throw 'Default incorreto no upgrade.' }
if ($source -notmatch 'Protect-SystemProSecretPath \$EnvFile' -or $source -notmatch 'Protect-SystemProSecretPath \$AgentConfig') {
    throw 'Permissões dos arquivos de segredos não são impostas.'
}

$functions = @($ast.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst]
}, $true))
foreach ($name in @('Set-EnvAssignmentPreservingBytes', 'Protect-SystemProSecretPath')) {
    $functionAst = $functions | Where-Object { $_.Name -eq $name } | Select-Object -First 1
    if ($null -eq $functionAst) { throw "Função de segurança ausente: $name" }
    Invoke-Expression $functionAst.Extent.Text
}

$testDir = Join-Path ([System.IO.Path]::GetTempPath()) ('systempro-windows-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir -Force | Out-Null
try {
    $envFile = Join-Path $testDir '.env'
    $text = "SYSTEMPRO_VERSION=simulado-r1`r`nDB_PASSWORD=CHAVE_UNICA_DE_TESTE`r`nCUSTOMER_NOTE=ação-única`r`nSYSTEMPRO_LICENSE_HEARTBEAT_PATH=/api/v1/heartbeats`r`n"
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllBytes($envFile, $utf8.GetBytes($text))
    Set-EnvAssignmentPreservingBytes $envFile 'SYSTEMPRO_VERSION' 'simulado-r2'
    Set-EnvAssignmentPreservingBytes $envFile 'SYSTEMPRO_LICENSE_HEARTBEAT_PATH' '/api/v1/heartbeat'
    $expected = $text.Replace('SYSTEMPRO_VERSION=simulado-r1', 'SYSTEMPRO_VERSION=simulado-r2').Replace('/api/v1/heartbeats', '/api/v1/heartbeat')
    $actual = [System.IO.File]::ReadAllBytes($envFile)
    if ([Convert]::ToBase64String($actual) -cne [Convert]::ToBase64String($utf8.GetBytes($expected))) {
        throw 'Atualização não preservou os demais bytes do .env.'
    }
    $privateDir = Join-Path $testDir 'private'
    New-Item -ItemType Directory -Path $privateDir | Out-Null
    $agentConfig = Join-Path $testDir 'config.json'
    [System.IO.File]::WriteAllText($agentConfig, '{"test":true}', $utf8)
    foreach ($path in @($envFile, $agentConfig, $privateDir)) {
        Protect-SystemProSecretPath $path
        $acl = Get-Acl -LiteralPath $path
        if (-not $acl.AreAccessRulesProtected) { throw "ACL herdada: $path" }
        $identities = @($acl.Access | ForEach-Object { $_.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value })
        if ($identities.Count -ne 2 -or $identities -notcontains 'S-1-5-18' -or $identities -notcontains 'S-1-5-32-544') {
            throw "ACL de $path não está limitada a SYSTEM e Administrators."
        }
    }
    Write-Host 'OK: PowerShell íntegro, bytes do .env preservados, caminho heartbeat corrigido e ACLs restritivas.'
} finally {
    Remove-Item -LiteralPath $testDir -Recurse -Force -ErrorAction SilentlyContinue
}
