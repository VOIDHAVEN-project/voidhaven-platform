param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^\d+\.\d+\.\d+(-(pretest|rc)\.\d+)?$')]
  [string] $ReleaseVersion,

  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[0-9a-f]{40}$')]
  [string] $GitSha
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$releaseRoot = Join-Path $repoRoot '.release'
$shortSha = $GitSha.Substring(0, 12)
$releaseId = "v$ReleaseVersion-$shortSha"
$staging = Join-Path $releaseRoot "$releaseId-staging"
$archive = Join-Path $releaseRoot "$releaseId.tar.gz"
$checksum = "$archive.sha256"
$sourceTar = Join-Path $releaseRoot "$releaseId-source.tar"

Push-Location $repoRoot
try {
  $actualSha = (git rev-parse HEAD).Trim()
  if ($actualSha -ne $GitSha) { throw "Git SHA 不匹配：期望 $GitSha，实际 $actualSha" }
  $changes = @(git status --porcelain --untracked-files=all)
  if ($changes.Count -gt 0) { throw "发布必须来自干净检出：$($changes -join ', ')" }
  & (Join-Path $PSScriptRoot 'check-repository.ps1')

  New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
  foreach ($path in @($staging, $archive, $checksum, $sourceTar)) {
    if (Test-Path -LiteralPath $path) { throw "发布输出已存在，拒绝覆盖：$path" }
  }
  New-Item -ItemType Directory -Path $staging | Out-Null

  git archive --format=tar --output=$sourceTar $GitSha
  if ($LASTEXITCODE -ne 0) { throw 'git archive 失败。' }
  tar -xf $sourceTar -C $staging
  if ($LASTEXITCODE -ne 0) { throw '解包 Git archive 失败。' }

  $buildCreated = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  $apiImage = "voidhaven-api:$ReleaseVersion-$GitSha"
  $webImage = "voidhaven-web:$ReleaseVersion-$GitSha"
  @(
    "VOIDHAVEN_RELEASE_VERSION=$ReleaseVersion"
    "VOIDHAVEN_GIT_SHA=$GitSha"
    "VOIDHAVEN_BUILD_CREATED=$buildCreated"
    "VOIDHAVEN_API_IMAGE=$apiImage"
    "VOIDHAVEN_WEB_IMAGE=$webImage"
  ) | Set-Content -LiteralPath (Join-Path $staging 'release.env') -Encoding ascii

  $migrations = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'apps\api\src\main\resources\db\migration') -File |
    Sort-Object Name |
    ForEach-Object { [ordered]@{ file = $_.Name; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } })
  $configurationFiles = @(
    'contracts/openapi/voidhaven-v0.yaml',
    'infra/compose/compose.local.yml',
    'infra/compose/compose.test-server.yml',
    'infra/compose/compose.candidate.yml',
    'infra/docker/Dockerfile.api',
    'infra/docker/Dockerfile.web'
  )
  $configurations = @($configurationFiles | ForEach-Object {
    [ordered]@{ file = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $repoRoot $_)).Hash.ToLowerInvariant() }
  })

  [ordered]@{
    releaseId = $releaseId
    version = $ReleaseVersion
    gitSha = $GitSha
    buildCreated = $buildCreated
    apiImage = $apiImage
    webImage = $webImage
    migrations = $migrations
    configurations = $configurations
    status = 'PACKAGED_FROM_CLEAN_GIT'
  } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $staging 'release-manifest.json') -Encoding utf8NoBOM

  tar -czf $archive -C $staging .
  if ($LASTEXITCODE -ne 0) { throw '创建发布压缩包失败。' }
  $entries = @(tar -tzf $archive)
  $unsafe = @($entries | Where-Object {
    $_ -match '(^|/)(\.git|node_modules|dist|target|coverage|test-results|playwright-report|\.verification)(/|$)' -or
    $_ -match '(^|/)(\.env|id_rsa[^/]*|[^/]+\.(pem|key|p12|pfx))$'
  })
  if ($unsafe.Count -gt 0) { throw "发布包包含禁止项：$($unsafe -join ', ')" }

  $hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
  "$hash  $([IO.Path]::GetFileName($archive))" | Set-Content -LiteralPath $checksum -Encoding ascii
  [pscustomobject]@{ ReleaseId = $releaseId; Archive = $archive; Checksum = $checksum; Sha256 = $hash; EntryCount = $entries.Count }
} finally {
  Pop-Location
  if (Test-Path -LiteralPath $sourceTar) { Remove-Item -LiteralPath $sourceTar -Force }
  if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
}
