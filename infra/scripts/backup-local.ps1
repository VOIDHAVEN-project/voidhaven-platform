param(
  [Parameter(Mandatory = $true)] [string] $DestinationDirectory
)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$destination = [System.IO.Path]::GetFullPath($DestinationDirectory)
if (-not $destination.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw '备份目录必须位于 D:\voidhaven\v0 内。' }
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $destination "voidhaven-local-$timestamp.sql"
Push-Location (Join-Path $repoRoot 'infra\compose')
try { docker compose --env-file ../../.env -f compose.local.yml exec -T mysql mysqldump -u voidhaven_app -p$env:VOIDHAVEN_DB_PASSWORD --single-transaction --routines --databases voidhaven | Set-Content -NoNewline -Encoding utf8 $output }
finally { Pop-Location }
Write-Output "已写入本地备份：$output"
