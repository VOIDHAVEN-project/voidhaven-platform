# Assemble a new test-server release from authoritative source trees.
# Never writes into 20260907-voidhaven-test-* directories.

param(
    [string]$ReleaseId = '20260910-voidhaven-test-07',
    [string]$PreviousReleaseId = '20260910-voidhaven-test-06',
    [string]$WorkspaceRoot = ''
)

$ErrorActionPreference = 'Stop'
if (-not $WorkspaceRoot) {
    $WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
}

$sourceRoot = Join-Path $WorkspaceRoot 'source'
$previous = Join-Path $WorkspaceRoot $PreviousReleaseId
$release = Join-Path $WorkspaceRoot $ReleaseId

if (-not (Test-Path -LiteralPath $previous)) { throw "Missing previous release directory: $previous" }
if (Test-Path -LiteralPath $release) { throw "Refusing to overwrite existing release directory: $release" }

function Assert-MemoirConsoleReleaseReady([string]$FrontendRoot) {
    $requiredFiles = @(
        'src\lib\partner-applications.ts',
        'src\lib\partner-applications.test.ts',
        'src\lib\secure-id.ts',
        'src\lib\secure-id.test.ts',
        'src\lib\public-submissions.ts',
        'src\lib\public-submissions.test.ts',
        'src\lib\copy-text.ts',
        'src\lib\copy-text.test.ts',
        'src\App.tsx',
        'src\lib\api.ts',
        'src\components\workspace-panels.tsx',
        'e2e\site.spec.ts'
    )
    foreach ($rel in $requiredFiles) {
        $path = Join-Path $FrontendRoot $rel
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Frontend release check failed: missing $rel. Do not assemble an unmerged memoir-console."
        }
    }
    $app = Get-Content -LiteralPath (Join-Path $FrontendRoot 'src\App.tsx') -Raw -Encoding utf8
    if ($app -notmatch 'RouteErrorBoundary') { throw 'Frontend release check failed: App.tsx is missing RouteErrorBoundary.' }
    if ($app -notmatch '/workspace/\*') { throw 'Frontend release check failed: App.tsx is missing /workspace/* nested routes.' }
    $api = Get-Content -LiteralPath (Join-Path $FrontendRoot 'src\lib\api.ts') -Raw -Encoding utf8
    if ($api -notmatch 'adminCreateAdmin') { throw 'Frontend release check failed: api.ts is missing adminCreateAdmin.' }
    if ($api -notmatch 'createIdempotencyKey') { throw 'Frontend release check failed: api.ts is not using createIdempotencyKey.' }
    $panels = Get-Content -LiteralPath (Join-Path $FrontendRoot 'src\components\workspace-panels.tsx') -Raw -Encoding utf8
    if ($panels -notmatch 'PENDING_REVIEW') { throw 'Frontend release check failed: workspace-panels.tsx is missing PENDING_REVIEW.' }
    if ($panels -notmatch 'isReviewablePublicSubmission') { throw 'Frontend release check failed: submissions are not using PENDING_REVIEW reviewability.' }
    if ($panels.Contains("state === 'PENDING'") -or $panels.Contains('state === "PENDING"')) {
        throw 'Frontend release check failed: submissions/partner rows still compare state to PENDING.'
    }
    if ($panels -notmatch 'partnerApplicationStatusOptions') { throw 'Frontend release check failed: partner applications are not using backend status options.' }
    if ($panels -notmatch 'copyText') { throw 'Frontend release check failed: activation-link copy is not using the HTTP-safe helper.' }
    if ($panels -match 'event\.currentTarget\.reset') { throw 'Frontend release check failed: create-admin still resets the form after await via currentTarget.' }
    if ($panels -notmatch 'adminCreateAdmin') { throw 'Frontend release check failed: AccountsPanel is missing admin create.' }
}

Assert-MemoirConsoleReleaseReady (Join-Path $sourceRoot 'memoir-console')

function Assert-ApiReleaseReady([string]$ApiRoot) {
    $requiredFiles = @(
        'src\main\resources\db\migration\V11__account_concurrency_and_distributed_limits.sql',
        'src\main\java\cn\voidhaven\api\service\AccountAdministrationService.java',
        'src\main\java\cn\voidhaven\api\service\ActivationService.java',
        'src\main\java\cn\voidhaven\api\service\AuthenticationRateLimiter.java',
        'src\main\java\cn\voidhaven\api\service\RateLimitLockService.java',
        'src\main\java\cn\voidhaven\api\service\MediaOrphanCleanupService.java',
        'src\main\java\cn\voidhaven\api\web\ApiExceptionHandler.java',
        'src\test\java\cn\voidhaven\api\web\ApiExceptionHandlerTest.java'
    )
    foreach ($rel in $requiredFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $ApiRoot $rel))) {
            throw "API release check failed: missing $rel."
        }
    }
    $activationRepository = Get-Content -LiteralPath (Join-Path $ApiRoot 'src\main\java\cn\voidhaven\api\persistence\ActivationTokenRepository.java') -Raw -Encoding utf8
    if ($activationRepository -match 'clearAutomatically\s*=\s*true') {
        throw 'API release check failed: activation token bulk updates still clear the persistence context.'
    }
    $activationService = Get-Content -LiteralPath (Join-Path $ApiRoot 'src\main\java\cn\voidhaven\api\service\ActivationService.java') -Raw -Encoding utf8
    if ($activationService -notmatch 'activateWithPassword') {
        throw 'API release check failed: ActivationService is missing activateWithPassword.'
    }
    $rateLimitLocks = Get-Content -LiteralPath (Join-Path $ApiRoot 'src\main\java\cn\voidhaven\api\service\RateLimitLockService.java') -Raw -Encoding utf8
    if ($rateLimitLocks -notmatch 'completeLoginAttempt') {
        throw 'API release check failed: login rate-limit completion is not serialized on the subject lock.'
    }
    $cleanup = Get-Content -LiteralPath (Join-Path $ApiRoot 'src\main\java\cn\voidhaven\api\service\MediaOrphanCleanupService.java') -Raw -Encoding utf8
    if ($cleanup -notmatch 'claimId') {
        throw 'API release check failed: media deletion claims are missing claimId.'
    }
    $exceptionHandler = Get-Content -LiteralPath (Join-Path $ApiRoot 'src\main\java\cn\voidhaven\api\web\ApiExceptionHandler.java') -Raw -Encoding utf8
    if ($exceptionHandler -notmatch 'AccessDeniedException' -or $exceptionHandler -notmatch 'FORBIDDEN') {
        throw 'API release check failed: access denied may still be wrapped as 500.'
    }
}

Assert-ApiReleaseReady (Join-Path $sourceRoot 'v0\apps\api')

function Copy-Tree([string]$From, [string]$To) {
    New-Item -ItemType Directory -Force -Path $To | Out-Null
    & robocopy $From $To /E /NFL /NDL /NJH /NJS /nc /ns /np `
        /XD node_modules target dist test-results playwright-report coverage .git .vite `
        /XF *.tsbuildinfo .env .env.test-server
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE): $From -> $To" }
}

function Get-TreeSha256([string]$Path) {
    $files = Get-ChildItem -LiteralPath $Path -Recurse -File |
        Where-Object { $_.FullName -notmatch '\\(node_modules|target|dist|test-results|playwright-report|coverage|\.git|\.vite)(\\|$)' } |
        Sort-Object FullName
    $sha = [System.Security.Cryptography.SHA256]::Create()
    foreach ($file in $files) {
        $bytes = [IO.File]::ReadAllBytes($file.FullName)
        $rel = $file.FullName.Substring((Resolve-Path $Path).Path.Length).TrimStart('\') -replace '\\', '/'
        $nameBytes = [Text.Encoding]::UTF8.GetBytes($rel)
        [void]$sha.TransformBlock($nameBytes, 0, $nameBytes.Length, $null, 0)
        [void]$sha.TransformBlock($bytes, 0, $bytes.Length, $null, 0)
    }
    [void]$sha.TransformFinalBlock([byte[]]::new(0), 0, 0)
    return ([BitConverter]::ToString($sha.Hash) -replace '-', '').ToLowerInvariant()
}

New-Item -ItemType Directory -Force -Path $release | Out-Null
Copy-Tree (Join-Path $previous 'deploy') (Join-Path $release 'deploy')
Copy-Tree (Join-Path $sourceRoot 'v0') (Join-Path $release 'sources\v0')
Copy-Tree (Join-Path $sourceRoot 'memoir-console') (Join-Path $release 'sources\memoir-console')
Copy-Tree (Join-Path $sourceRoot 'v0\apps\api') (Join-Path $release 'work\api')
Copy-Tree (Join-Path $sourceRoot 'memoir-console') (Join-Path $release 'work\memoir-console')
Assert-MemoirConsoleReleaseReady (Join-Path $release 'work\memoir-console')

$bootstrap = Join-Path $release 'work\api\src\main\java\cn\voidhaven\api\config\BootstrapAdminCommand.java'
$bootstrapText = Get-Content -LiteralPath $bootstrap -Raw -Encoding utf8
$bootstrapText = $bootstrapText.Replace(
    ' * This component is never active in the normal production web process, so activation',
    " * production or isolated test-server environment. This component is never active in the normal web process, so activation"
)
$bootstrapText = [regex]::Replace(
    $bootstrapText,
    'if \(Arrays\.stream\(environment\.getActiveProfiles\(\)\)\.noneMatch\("production"::equals\)\) \{\s+throw new IllegalStateException\("bootstrap-admin must run together with the production profile"\);\s+\}',
    "if (!isApprovedEnvironment(environment.getActiveProfiles())) {`n            throw new IllegalStateException(`"bootstrap-admin must run together with the production or test-server profile`");`n        }"
)
if ($bootstrapText -notmatch 'static boolean isApprovedEnvironment') {
    $bootstrapText = $bootstrapText.TrimEnd()
    if ($bootstrapText.EndsWith('}')) {
        $bootstrapText = $bootstrapText.Substring(0, $bootstrapText.Length - 1).TrimEnd() + @"

    static boolean isApprovedEnvironment(String[] profiles) {
        return Arrays.stream(profiles).anyMatch(profile -> "production".equals(profile) || "test-server".equals(profile));
    }
}
"@
    }
}
[IO.File]::WriteAllText($bootstrap, $bootstrapText, [Text.UTF8Encoding]::new($false))

$apiSourceSha = Get-TreeSha256 (Join-Path $release 'sources\v0\apps\api')
$apiAdaptedSha = Get-TreeSha256 (Join-Path $release 'work\api')
$frontendSha = Get-TreeSha256 (Join-Path $release 'work\memoir-console')
$v0WebSha = Get-TreeSha256 (Join-Path $release 'sources\v0\apps\web')
$buildCreated = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$apiTag = $apiAdaptedSha.Substring(0, 12)
$webTag = $frontendSha.Substring(0, 12)

@(
    "VOIDHAVEN_RELEASE_ID=$ReleaseId"
    "VOIDHAVEN_BUILD_CREATED=$buildCreated"
    "VOIDHAVEN_PREVIOUS_RELEASE_ID=$PreviousReleaseId"
    "VOIDHAVEN_API_SOURCE_SHA=$apiSourceSha"
    "VOIDHAVEN_API_ADAPTED_SHA=$apiAdaptedSha"
    "VOIDHAVEN_FRONTEND_SHA=$frontendSha"
    "VOIDHAVEN_V0_WEB_SHA=$v0WebSha"
    "VOIDHAVEN_API_IMAGE=voidhaven-api:$ReleaseId-$apiTag"
    "VOIDHAVEN_API_FALLBACK_IMAGE=voidhaven-api:20260910-voidhaven-test-06-359c8a2f03bd"
    "VOIDHAVEN_WEB_IMAGE=voidhaven-web:$ReleaseId-memoir-$webTag"
    "VOIDHAVEN_WEB_FALLBACK_IMAGE=voidhaven-web:20260910-voidhaven-test-06-memoir-9889c99ee743"
    "VOIDHAVEN_V0_WEB_IMAGE=voidhaven-web:20260907-voidhaven-test-01-v0-a7b05802128a"
) | Set-Content -LiteralPath (Join-Path $release 'release.env') -Encoding ascii

Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'assemble-test-server-release.ps1') -Destination (Join-Path $release 'deploy\scripts\assemble-release.ps1') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'verify-v11-data-safety.sql') -Destination (Join-Path $release 'deploy\scripts\verify-v11-data-safety.sql') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'rehearse-v11-data-safety.ps1') -Destination (Join-Path $release 'deploy\scripts\rehearse-v11-data-safety.ps1') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'rehearse-v11-fixture.sql') -Destination (Join-Path $release 'deploy\scripts\rehearse-v11-fixture.sql') -Force

$overlay = Join-Path $PSScriptRoot '..\test-server-deploy'
Copy-Item -LiteralPath (Join-Path $overlay 'backup-mysql.sh') -Destination (Join-Path $release 'deploy\scripts\backup-mysql.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'restore-rehearsal.sh') -Destination (Join-Path $release 'deploy\scripts\restore-rehearsal.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'cutover-test-server.sh') -Destination (Join-Path $release 'deploy\scripts\cutover-test-server.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'rollback-app.sh') -Destination (Join-Path $release 'deploy\scripts\rollback-app.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'cutover-production.sh') -Destination (Join-Path $release 'deploy\scripts\cutover-production.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'cutover-production.sh') -Destination (Join-Path $PSScriptRoot 'cutover-production.sh') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'smoke-admin-concurrency.md') -Destination (Join-Path $release 'deploy\scripts\smoke-admin-concurrency.md') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'compose.rehearsal.yml') -Destination (Join-Path $release 'deploy\compose.rehearsal.yml') -Force
Copy-Item -LiteralPath (Join-Path $overlay 'README-deploy.md') -Destination (Join-Path $release 'deploy\README-deploy.md') -Force

Write-Output "Assembled $release"
Get-Content -LiteralPath (Join-Path $release 'release.env')
exit 0
