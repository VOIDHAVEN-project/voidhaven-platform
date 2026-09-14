# Rehearse Flyway V11 against a disposable MySQL container.
# Does not touch voidhaven-test-* named volumes or any live database.

param(
    [string]$MysqlImage = 'mysql:8.4',
    [string]$ContainerName = 'voidhaven-v11-rehearsal'
)

$ErrorActionPreference = 'Stop'
$scriptDir = $PSScriptRoot
$verifySqlPath = Join-Path $scriptDir 'verify-v11-data-safety.sql'
$fixtureSqlPath = Join-Path $scriptDir 'rehearse-v11-fixture.sql'
$migrationCandidates = @(
    (Join-Path $scriptDir '..\..\apps\api\src\main\resources\db\migration'),
    (Join-Path $scriptDir '..\..\work\api\src\main\resources\db\migration')
)
$migrationDir = $migrationCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $migrationDir) { throw 'Cannot find Flyway migration directory.' }
$migrationDir = (Resolve-Path -LiteralPath $migrationDir).Path

function Invoke-Native([string]$Command) {
    cmd.exe /c $Command
    return $LASTEXITCODE
}

function Invoke-Mysql([string]$Sql) {
    $tempSql = Join-Path $env:TEMP ('voidhaven-v11-' + [Guid]::NewGuid().ToString('N') + '.sql')
    [IO.File]::WriteAllText($tempSql, $Sql, [Text.UTF8Encoding]::new($false))
    try {
        $cp = Invoke-Native ('docker cp "' + $tempSql + '" ' + $ContainerName + ':/tmp/stmt.sql')
        if ($cp -ne 0) { throw 'docker cp failed' }
        $output = cmd.exe /c ('docker exec ' + $ContainerName + ' sh -c "mysql -uroot -prehearse --database=voidhaven --batch --raw --default-character-set=utf8mb4 < /tmp/stmt.sql" 2>&1')
        if ($LASTEXITCODE -ne 0) { throw "MySQL failed: $output" }
        return @($output | Where-Object { $_ -and ($_ -notmatch 'Using a password') })
    } finally {
        Remove-Item -LiteralPath $tempSql -Force -ErrorAction SilentlyContinue
    }
}

function Get-Count([string]$Sql) {
    $rows = Invoke-Mysql $Sql
    return ($rows | Select-Object -Last 1).ToString().Trim()
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker is not on PATH; isolated rehearsal cannot run locally.'
}

[void](Invoke-Native "docker rm -f $ContainerName >nul 2>&1")
$runExit = Invoke-Native "docker run -d --name $ContainerName -e MYSQL_ROOT_PASSWORD=rehearse -e MYSQL_DATABASE=voidhaven $MysqlImage >nul"
if ($runExit -ne 0) { throw "docker run failed with exit $runExit" }

$deadline = (Get-Date).AddSeconds(90)
do {
    $ping = Invoke-Native "docker exec $ContainerName mysqladmin ping -h 127.0.0.1 -uroot -prehearse --silent >nul 2>&1"
    if ($ping -eq 0) { break }
    if ((Get-Date) -gt $deadline) { throw 'Timed out waiting for disposable MySQL.' }
    Start-Sleep -Seconds 2
} while ($true)

Invoke-Mysql @'
CREATE TABLE flyway_schema_history (
  installed_rank INT NOT NULL,
  version VARCHAR(50),
  description VARCHAR(200) NOT NULL,
  type VARCHAR(20) NOT NULL,
  script VARCHAR(1000) NOT NULL,
  checksum INT NULL,
  installed_by VARCHAR(100) NOT NULL,
  installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  execution_time INT NOT NULL,
  success TINYINT(1) NOT NULL,
  PRIMARY KEY (installed_rank)
);
'@

$files = Get-ChildItem -LiteralPath $migrationDir -Filter 'V*.sql' | Sort-Object { [int]($_.BaseName -replace '^V(\d+).*','$1') }
if (-not ($files | Where-Object { $_.Name -like 'V11__*' })) { throw 'V11 migration file is missing.' }

$rank = 0
foreach ($file in $files) {
    $version = [int]($file.BaseName -replace '^V(\d+).*','$1')
    if ($version -eq 11) {
        Invoke-Mysql (Get-Content -LiteralPath $fixtureSqlPath -Raw -Encoding utf8)
        $beforeAccounts = Get-Count 'SELECT COUNT(*) FROM accounts;'
        $beforeIdempotency = Get-Count 'SELECT COUNT(*) FROM idempotency_records;'
        if ($beforeAccounts -ne '2') { throw "Fixture account count expected 2, got $beforeAccounts" }
        if ($beforeIdempotency -ne '2') { throw "Fixture idempotency count expected 2, got $beforeIdempotency" }
    }

    Invoke-Mysql (Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8)
    $rank += 1
    $description = ($file.BaseName -replace '^V\d+__', '').Replace('_', ' ')
    Invoke-Mysql "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) VALUES ($rank, '$version', '$description', 'SQL', '$($file.Name)', 0, 'rehearsal', 1, 1);"
}

$afterAccounts = Get-Count 'SELECT COUNT(*) FROM accounts;'
$afterTokenCache = Get-Count "SELECT COUNT(*) FROM idempotency_records WHERE CAST(response_body AS CHAR) LIKE '%activationToken%';"
$afterHarmless = Get-Count "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key='harmless-write';"
$versionCol = Get-Count "SELECT version FROM accounts WHERE login_id='super.admin';"
$flyway11 = Get-Count "SELECT success FROM flyway_schema_history WHERE version='11';"

if ($afterAccounts -ne '2') { throw "Account count changed after V11: $afterAccounts" }
if ($afterTokenCache -ne '0') { throw 'activationToken idempotency cache was not purged.' }
if ($afterHarmless -ne '1') { throw 'Non-token idempotency row was deleted.' }
if ($versionCol -ne '0') { throw "accounts.version expected 0, got $versionCol" }
if ($flyway11 -ne '1') { throw 'flyway_schema_history is missing V11.' }

Invoke-Mysql (Get-Content -LiteralPath $verifySqlPath -Raw -Encoding utf8)
[void](Invoke-Native "docker rm -f $ContainerName >nul 2>&1")
Write-Output 'PASS: V11 kept accounts and harmless idempotency rows; purged activationToken cache; disposable container removed.'
