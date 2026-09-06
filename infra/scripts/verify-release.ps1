param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^0\.1\.0-(pretest|rc)\.\d+$')]
  [string] $ReleaseVersion,

  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[0-9a-f]{40}$')]
  [string] $GitSha,

  [ValidateRange(1024, 65535)]
  [int] $HttpPort = 18080
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$verificationRoot = Join-Path $repoRoot '.verification'
$resultPath = Join-Path $verificationRoot 'release-result.json'
$shortSha = $GitSha.Substring(0, 12)
$projectName = "voidhavenverify$($shortSha.Substring(0, 8))"
$buildCreated = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$localEnv = Join-Path $verificationRoot 'local.env'

function Invoke-Step([string] $Name, [scriptblock] $Command) {
  Write-Output "== $Name =="
  & $Command
  if ($LASTEXITCODE -ne 0) { throw "$Name 失败，候选版本不得建立标签。" }
}

function Assert-CleanSource {
  $changes = @(git status --porcelain --untracked-files=all)
  if ($LASTEXITCODE -ne 0) { throw '无法读取 Git 状态。' }
  if ($changes.Count -gt 0) { throw "发布验证必须从干净检出运行：$($changes -join ', ')" }
  $actual = (git rev-parse HEAD).Trim()
  if ($actual -ne $GitSha) { throw "Git SHA 不匹配：期望 $GitSha，实际 $actual" }
}

function Assert-NoSensitiveTrackedFiles {
  $forbidden = @(git ls-files | Where-Object { $_ -eq '.env' -or $_ -eq 'apps/api/cp.txt' })
  if ($forbidden.Count -gt 0) { throw "发现禁止提交的文件：$($forbidden -join ', ')" }

  $matches = @(git grep -n -E 'log\.(trace|debug|info|warn|error).*?(token|password|cookie|csrf)' -- 'apps/api/src/**/*.java')
  if ($LASTEXITCODE -eq 0 -and $matches.Count -gt 0) { throw "发现疑似敏感日志：$($matches -join '; ')" }
  if ($LASTEXITCODE -gt 1) { throw '敏感日志扫描执行失败。' }
}

New-Item -ItemType Directory -Force -Path $verificationRoot | Out-Null
$secretSuffix = [Guid]::NewGuid().ToString('N')
@"
VOIDHAVEN_DB_USERNAME=voidhaven_app
VOIDHAVEN_DB_PASSWORD=db-$secretSuffix
VOIDHAVEN_BOOTSTRAP_ADMIN_LOGIN=release-admin
VOIDHAVEN_BOOTSTRAP_ADMIN_PASSWORD=Admin-$secretSuffix
VOIDHAVEN_BOOTSTRAP_PARTNER_LOGIN=release-partner
VOIDHAVEN_BOOTSTRAP_PARTNER_PASSWORD=Partner-$secretSuffix
VOIDHAVEN_RATE_LIMIT_SECRET=rate-$secretSuffix
VOIDHAVEN_MEDIA_ACCESS_KEY=release-media
VOIDHAVEN_MEDIA_SECRET_KEY=media-$secretSuffix
VOIDHAVEN_MEDIA_BUCKET=voidhaven-media
VOIDHAVEN_MEDIA_REGION=us-east-1
VOIDHAVEN_MEDIA_FORCE_PATH_STYLE=true
VOIDHAVEN_HTTP_PORT=$HttpPort
"@ | Set-Content -LiteralPath $localEnv -Encoding utf8NoBOM

$env:VOIDHAVEN_RELEASE_VERSION = $ReleaseVersion
$env:VOIDHAVEN_GIT_SHA = $GitSha
$env:VOIDHAVEN_BUILD_CREATED = $buildCreated
$env:VOIDHAVEN_E2E_BASE_URL = "http://127.0.0.1:$HttpPort"
$env:VOIDHAVEN_E2E_ADMIN_LOGIN = 'release-admin'
$env:VOIDHAVEN_E2E_ADMIN_PASSWORD = "Admin-$secretSuffix"
$env:VOIDHAVEN_E2E_PARTNER_LOGIN = 'release-partner'
$env:VOIDHAVEN_E2E_PARTNER_PASSWORD = "Partner-$secretSuffix"
$env:VOIDHAVEN_E2E_EVIDENCE_DIR = Join-Path $verificationRoot 'browser'
New-Item -ItemType Directory -Force -Path $env:VOIDHAVEN_E2E_EVIDENCE_DIR | Out-Null

Push-Location $repoRoot
try {
  Assert-CleanSource
  Invoke-Step 'Docker engine' { docker info --format '{{.ServerVersion}}' }
  Invoke-Step 'Locked Node dependency install' { npm ci }
  Invoke-Step 'Formatting' { npm run format:check }
  Invoke-Step 'Web, tests, build and OpenAPI' { npm run verify:web }
  Push-Location (Join-Path $repoRoot 'apps\api')
  try { Invoke-Step 'API clean verify' { .\mvnw.cmd -B clean verify } }
  finally { Pop-Location }

  [xml] $integration = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'apps\api\target\surefire-reports\TEST-cn.voidhaven.api.integration.MySqlAndMinioIntegrationTest.xml')
  if ([int] $integration.testsuite.errors -ne 0 -or [int] $integration.testsuite.failures -ne 0 -or [int] $integration.testsuite.skipped -ne 0) {
    throw 'MySQL/MinIO 集成测试失败或被跳过。'
  }

  Invoke-Step 'Local Compose config' { docker compose --env-file $localEnv -f infra\compose\compose.local.yml config --quiet }
  Invoke-Step 'Candidate Compose config' { docker compose --env-file .env.candidate.example -f infra\compose\compose.candidate.yml config --quiet }
  Assert-NoSensitiveTrackedFiles
  Invoke-Step 'Whitespace check' { git diff --check HEAD }

  try {
    Invoke-Step 'Isolated local cold start' { docker compose --project-name $projectName --env-file $localEnv -f infra\compose\compose.local.yml up --build --detach --wait }
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$HttpPort/healthz" -TimeoutSec 15
    if ($health.status -ne 'UP') { throw 'Gateway health check did not report UP.' }
    $home = Invoke-RestMethod -Uri "http://127.0.0.1:$HttpPort/api/v1/public/home" -TimeoutSec 15
    if ($null -eq $home.data) { throw 'Public home API did not return the expected envelope.' }
    Invoke-Step 'Playwright browser workflows' { npm run web:e2e }
  } finally {
    docker compose --project-name $projectName --env-file $localEnv -f infra\compose\compose.local.yml down --volumes --remove-orphans
  }

  Invoke-Step 'Candidate image build' { docker compose --env-file .env.candidate.example -f infra\compose\compose.candidate.yml build api web }
  $apiImage = "voidhaven-api:$ReleaseVersion-$GitSha"
  $webImage = "voidhaven-web:$ReleaseVersion-$GitSha"
  $apiId = (docker image inspect --format '{{.Id}}' $apiImage).Trim()
  $webId = (docker image inspect --format '{{.Id}}' $webImage).Trim()
  if (-not $apiId -or -not $webId) { throw '无法读取候选镜像 ID。' }

  $migrationChecksums = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'apps\api\src\main\resources\db\migration') -File |
    Sort-Object Name |
    ForEach-Object { [ordered]@{ file = $_.Name; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant() } })
  $configurationFiles = @(
    'apps/api/src/main/resources/application-production.yml',
    'apps/api/Dockerfile.candidate',
    'apps/web/Dockerfile.candidate',
    'infra/compose/compose.candidate.yml',
    'infra/nginx/candidate.conf'
  )
  $configurationChecksums = @($configurationFiles | ForEach-Object {
    [ordered]@{ file = $_; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoRoot $_)).Hash.ToLowerInvariant() }
  })

  [ordered]@{
    releaseId = "v$ReleaseVersion"
    gitSha = $GitSha
    buildCreated = $buildCreated
    flyway = 'V1-V10'
    apiImage = $apiImage
    apiImageId = $apiId
    webImage = $webImage
    webImageId = $webId
    tools = [ordered]@{
      node = (node --version).Trim()
      npm = (npm --version).Trim()
      java = (& java -version 2>&1 | Select-Object -First 1).ToString().Trim()
      docker = (docker info --format '{{.ServerVersion}}').Trim()
    }
    migrations = $migrationChecksums
    configurations = $configurationChecksums
    status = 'VERIFIED'
  } | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding utf8NoBOM
  Write-Output "发布验证通过。结果：$resultPath"
} finally {
  Pop-Location
  Remove-Item -LiteralPath $localEnv -Force -ErrorAction SilentlyContinue
  Remove-Item Env:VOIDHAVEN_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:VOIDHAVEN_E2E_PARTNER_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:VOIDHAVEN_E2E_EVIDENCE_DIR -ErrorAction SilentlyContinue
}
