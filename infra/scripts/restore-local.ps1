param(
  [Parameter(Mandatory = $true)] [string] $BackupFile,
  [switch] $Apply
)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$source = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not $source.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw '恢复文件必须位于 D:\voidhaven\v0 内。' }
if (-not $Apply) { Write-Output '恢复预检通过。此操作会改写本地 voidhaven 数据库；确认后请追加 -Apply。'; exit 0 }
Push-Location (Join-Path $repoRoot 'infra\compose')
try { Get-Content -Raw -LiteralPath $source | docker compose --env-file ../../.env -f compose.local.yml exec -T mysql mysql -u voidhaven_app -p$env:VOIDHAVEN_DB_PASSWORD voidhaven }
finally { Pop-Location }
Write-Output '已执行本地恢复。请立即完成迁移版本、媒体清单、权限和公开内容检查。'
