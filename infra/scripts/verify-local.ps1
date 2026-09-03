$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Invoke-QualityStep([string] $Name, [scriptblock] $Command) {
  Write-Output "== $Name =="
  & $Command
  if ($LASTEXITCODE -ne 0) { throw "$Name 失败，已停止后续质量门。" }
}

Push-Location $repoRoot
try {
  Invoke-QualityStep 'Web lint' { npm run lint }
  Invoke-QualityStep 'Web test' { npm run test }
  Invoke-QualityStep 'Web build' { npm run build }
  Push-Location (Join-Path $repoRoot 'apps\api')
  try { Invoke-QualityStep 'API verify' { .\mvnw.cmd verify } }
  finally { Pop-Location }
  Invoke-QualityStep 'Local Compose config' { docker compose -f infra\compose\compose.local.yml config --quiet }
  Invoke-QualityStep 'Candidate Compose config' { docker compose --env-file .env.candidate.example -f infra\compose\compose.candidate.yml config --quiet }
  Invoke-QualityStep 'Whitespace check' { git diff --check }
} finally {
  Pop-Location
}

Write-Output '本地质量门全部通过。Docker 引擎启动、空库迁移、E2E 与恢复演练仍须按手册单独执行。'
