# 普通管理员与并发安全版本：保数据上线

权威源码在本仓库。测试机发布包使用新的 dated release 目录，禁止覆盖已在跑的 `20260907-voidhaven-test-*`。本手册不自动连接腾讯云或改线上库。

Flyway 范围：V1–V11。V11 只加列/表/索引，并删除幂等缓存中含 `activationToken` 的响应；不删除账号、内容、合作方、会话或媒体对象。Hibernate `ddl-auto: validate`，应用进程不会改表。

禁止：`docker compose down -v`、`DROP COLUMN`、Flyway undo、`DROP DATABASE`、切窗中新旧 API 混跑、对已有账号库再跑 `bootstrap-admin`。

## 数据影响

| 变更 | 对已有数据 |
| --- | --- |
| `accounts.version` 默认 `0` | 现有账号继续可用 |
| `idempotency_records.response_replayable` 默认 `true` | 普通幂等重放仍可用 |
| 删除含 `activationToken` 的幂等行 | 只清一次性令牌缓存；重复开户仍被 `login_id` 唯一约束挡住 |
| `rate_limit_locks` / `public_write_rate_limit_events` | 新空表 |
| `media_deletion_jobs.locked_*` | 未完成删除任务可被新租约领取 |

切窗内公开站可能短暂 502。Spring Session JDBC 使同一库上的登录态在重启后仍在。

## 阶段 0：打新发布包

在 Windows 打包机、仓库根目录 `D:\Projects\voidhaven-deploy-staging`：

```powershell
.\source\v0\infra\scripts\assemble-test-server-release.ps1
.\20260910-voidhaven-test-07\deploy\scripts\package-release.ps1 -ReleaseRoot '.\20260910-voidhaven-test-07'
```

将 `voidhaven-20260910-voidhaven-test-07.tar.gz` 与 `.sha256` 拷到服务器 `/tmp/`。解压到 `/opt/voidhaven/releases/20260910-voidhaven-test-07`，禁止解压进 `/opt/voidhaven/current`。先 `compose config --quiet` 并构建镜像，先不要对现网项目 `up`。

## 阶段 1：隔离恢复演练

1. 热备（API 仍在跑）后记录基线行数，见发布包 `deploy/scripts/backup-mysql.sh`。
2. 用 `deploy/compose.rehearsal.yml` 与项目名 `voidhaven-rehearsal`、卷 `voidhaven-rehearsal-mysql-data` 恢复 dump。禁止指向 `voidhaven-test-mysql-data`。
3. 只对副本启动新 API。确认 `flyway_schema_history` 有 V11；`accounts`/`content_items` 行数与切前一致；含 `activationToken` 的幂等行数为 0。校验 SQL：`infra/scripts/verify-v11-data-safety.sql`。
4. 副本冒烟通过后再碰现网库。

本机无现网 dump 时，运行 `infra/scripts/rehearse-v11-data-safety.ps1`：一次性 MySQL 容器顺序执行 V1–V10、插入夹具、再执行 V11，断言业务行仍在。容器名 `voidhaven-v11-rehearsal`，结束即删除，不使用测试机命名卷。

## 阶段 2：测试机非混合切换

项目名 `voidhaven-test`，密钥 `/opt/voidhaven/shared/.env.test-server`。由 `deploy/scripts/cutover-test-server.sh` 执行：

1. 通知窗口；停 API 后冷备。
2. 只停 API（MySQL/MinIO/Web/Nginx 与命名卷不动）。
3. 用新 compose 启动**一个** API；Flyway 在启动时应用 V11；等待 readiness UP。
4. 切新 Web；运行 `verify-server.sh`。
5. 原子更新 `/opt/voidhaven/current`。
6. 不要跑 `bootstrap-admin`。

冒烟（超级管理员，勿把激活令牌写入日志或证据）：

- 创建普通管理员 → 固定 `ADMIN` + `PENDING` → 人工激活 → 登录后台
- 未激活不能登录；普通 `ADMIN` 调创建接口 403
- `PENDING` 重新签发；刷新后面板令牌消失
- 停用 / 恢复；永久撤销后旧会话下一写请求失效
- 拒绝停用/恢复/撤销 `SUPER_ADMIN`
- 公开投稿 200 或 `PUBLIC_WRITE_RATE_LIMITED`；生命周期任务不双跑

## 阶段 3：生产切换

官方生产云仍受 ADR-V0-004 单独批准。`infra/compose/compose.candidate.yml` 不含数据库容器。命令见发布包 `deploy/scripts/cutover-production.sh`（默认只打印清单；设置 `EXECUTE=YES` 且已批准才执行）。

1. 窗口、责任人、回滚镜像（版本 + 完整 SHA，不用 `latest`）。
2. 托管 MySQL 快照 + 逻辑 dump；对象存储按现有策略留点；隔离副本先跑 V11 + 冒烟。
3. 暂停全部旧 API。禁止混跑。
4. 单实例新 API：`SPRING_PROFILES_ACTIVE=production`。validate 通过后才对外。
5. 发布新 Web；冒烟同阶段 2。
6. 首次生产保持 1 个 API。

## 阶段 4：紧急回滚

保留 V11 表和字段。`deploy/scripts/rollback-app.sh` 把 API/Web 切回 `VOIDHAVEN_*_FALLBACK_IMAGE`，并强制 `--scale api=1`。

不要 Flyway undo，不要删 `version` 列。仅当 dump 证明数据损坏时，才在隔离副本恢复备份并核对行数后再替换数据目录。前端可单独回滚；已写入的 `ADMIN` 账号保留。
