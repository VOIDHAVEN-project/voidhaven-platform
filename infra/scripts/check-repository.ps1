$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

Push-Location $repoRoot
try {
  $tracked = @(git ls-files)
  if ($LASTEXITCODE -ne 0) { throw '无法读取 Git 跟踪文件。' }

  $forbiddenFiles = @($tracked | Where-Object {
    $leaf = [IO.Path]::GetFileName($_)
    $forbiddenEnvironment = $leaf -eq '.env' -or ($leaf.StartsWith('.env.') -and -not $leaf.EndsWith('.example'))
    $forbiddenEnvironment -or
    $_ -match '(^|/)(id_rsa[^/]*|credentials?\.(json|yml|yaml)|[^/]+\.(pem|key|p12|pfx))$' -or
    $_ -match '(^|/)(node_modules|dist|target|coverage|test-results|playwright-report|\.verification)(/|$)'
  })
  if ($forbiddenFiles.Count -gt 0) {
    throw "发现禁止跟踪的文件：$($forbiddenFiles -join ', ')"
  }

  $secretPatterns = 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}'
  $matches = @(git grep -n -I -E $secretPatterns -- . ':!docs/evidence/**')
  if ($LASTEXITCODE -eq 0 -and $matches.Count -gt 0) {
    throw "发现高置信度 Secret：$($matches -join '; ')"
  }
  if ($LASTEXITCODE -gt 1) { throw 'Secret 内容扫描执行失败。' }

  git diff --check HEAD
  if ($LASTEXITCODE -ne 0) { throw '发现空白字符或补丁格式问题。' }
} finally {
  Pop-Location
}

Write-Output '仓库边界和高置信度 Secret 扫描通过。'
