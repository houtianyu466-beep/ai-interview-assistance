# Load local secrets from config/secrets.env and start the backend.
# Usage: powershell -ExecutionPolicy Bypass -File scripts/run-local.ps1
# The secrets file is gitignored; this script never prints secret values.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root 'config\secrets.env'
$jar = Join-Path $root 'target\ai-interview-platform.jar'

if (-not (Test-Path $envFile)) {
    Write-Host "Missing config\secrets.env" -ForegroundColor Yellow
    Write-Host "Copy config\secrets.example.env to config\secrets.env, fill in the keys, then run again."
    exit 1
}

$loaded = 0
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $idx = $line.IndexOf('=')
    if ($idx -le 0) { return }
    $name = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    if ($value -eq '' -or $value.StartsWith('sk-请')) {
        Write-Host "Skip placeholder: $name" -ForegroundColor DarkYellow
        return
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $loaded++
}

Write-Host "Loaded $loaded variables from config\secrets.env"
if (-not (Test-Path $jar)) {
    Write-Host "Jar not found, building..."
    Push-Location $root
    mvn -B -ntp -DskipTests package
    Pop-Location
}
java -jar $jar
