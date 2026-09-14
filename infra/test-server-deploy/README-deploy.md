# VOIDHAVEN test-server — 普通管理员与并发安全（保数据上线）

本发布包来自权威源码 `source/v0` 与 `source/memoir-console`，Flyway 范围为 V1–V11。不要覆盖正在运行的 `20260907-voidhaven-test-*` 目录，也不要在已有账号的库上执行 `bootstrap-admin`。

完整步骤见源码手册 `source/v0/docs/runbooks/data-preserving-cutover.md`。

## 安全边界

- 只有网关发布主机端口：现网 HTTP `80`；隔离演练默认 `18080`。
- MySQL、MinIO、API、Web 留在 Docker 网络内。
- 密钥只存在 `/opt/voidhaven/shared/.env.test-server`，权限 `0600`。
- **禁止** `docker compose down -v`、Flyway undo、`DROP COLUMN`、删除命名卷 `voidhaven-test-mysql-data` / `voidhaven-test-minio-data`。

## Windows 打包与上传

```powershell
$releaseId = '20260910-voidhaven-test-07'
$root = 'D:\Projects\voidhaven-deploy-staging'
$release = Join-Path $root $releaseId

& "$root\source\v0\infra\scripts\assemble-test-server-release.ps1"
& "$release\deploy\scripts\package-release.ps1" -ReleaseRoot $release

& 'C:\Windows\System32\OpenSSH\scp.exe' `
  "$root\voidhaven-$releaseId.tar.gz" `
  "$root\voidhaven-$releaseId.tar.gz.sha256" `
  'ubuntu@119.45.221.195:/tmp/'
```

## 服务器解压与构建（先不要 up 现网）

```bash
release_id='20260910-voidhaven-test-07'
archive="/tmp/voidhaven-$release_id.tar.gz"
checksum="$archive.sha256"
release_dir="/opt/voidhaven/releases/$release_id"
secret_file='/opt/voidhaven/shared/.env.test-server'
release_env="$release_dir/release.env"
compose_file="$release_dir/deploy/compose.test-server.yml"

cd /tmp
sha256sum -c "$(basename "$checksum")"
test ! -e "$release_dir"
sudo install -d -o ubuntu -g ubuntu /opt/voidhaven/releases /opt/voidhaven/shared /opt/voidhaven/backups
install -d -m 0750 "$release_dir"
tar -xzf "$archive" -C "$release_dir"
chmod 0600 "$secret_file"
chmod +x "$release_dir"/deploy/scripts/*.sh

docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" config --quiet
docker compose -p voidhaven-test --env-file "$secret_file" --env-file "$release_env" -f "$compose_file" build api web
```

## 隔离恢复演练

```bash
# 对现网热备（不停止容器）
sh "$release_dir/deploy/scripts/backup-mysql.sh" "$(readlink -f /opt/voidhaven/current)" "$secret_file"

# 在独立项目/卷上恢复并启动新 API。禁止指向 voidhaven-test-mysql-data。
sh "$release_dir/deploy/scripts/restore-rehearsal.sh" \
  "$release_dir" "$secret_file" \
  /opt/voidhaven/backups/<stamp>/voidhaven.sql \
  /opt/voidhaven/backups/<stamp>/baseline-counts.tsv
```

演练项目名 `voidhaven-rehearsal`，卷 `voidhaven-rehearsal-mysql-data`，网关端口 `18080`。通过后再做现网切换。

## 现网非混合切换

```bash
CONFIRM=YES sh "$release_dir/deploy/scripts/cutover-test-server.sh" "$release_dir" "$secret_file"
```

脚本会：热备 → 只停旧 API → 冷备 → 单实例新 API（Flyway V11）→ 切 Web → `verify-server.sh` → 更新 `/opt/voidhaven/current`。冒烟清单见 `deploy/scripts/smoke-admin-concurrency.md`。

不要再跑：

```bash
# 禁止：库中已有超级管理员
docker compose ... run --rm -e SPRING_PROFILES_ACTIVE=test-server,bootstrap-admin api
```

## 紧急回滚（只回应用）

保留 V11 表和字段：

```bash
CONFIRM=YES sh "$release_dir/deploy/scripts/rollback-app.sh" "$release_dir" "$secret_file" fallback
```

旧 API 只能以单实例运行。不要 undo Flyway。

## 生产切换

官方生产云仍需单独批准。候选编排不含数据库容器。默认只打印清单：

```bash
sh "$release_dir/deploy/scripts/cutover-production.sh" /path/to/v0 .env.production
```

批准且隔离复演通过后：`CONFIRM=YES EXECUTE=YES` 才启动单实例 production API。
