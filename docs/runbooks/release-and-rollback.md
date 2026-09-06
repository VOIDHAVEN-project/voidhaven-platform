# 发布与回滚手册（候选）

## 候选版本身份

- 预测试版本格式：`v0.1.0-pretest.N`；完成 R2 全部门禁后才使用 `v0.1.0-rc.N`。
- API/Web 镜像标签必须同时包含版本和完整 Git SHA，不使用 `latest`。
- annotated tag 记录源代码 SHA、Flyway 范围、镜像 ID、候选配置摘要及证据目录。
- 当前唯一权威验证入口为 `infra/scripts/verify-release.ps1`；远程 CI 未获授权，仓库只保存不自动运行的模板。

## 首个超级管理员

普通 production Web 进程不会创建管理员，也不会向应用日志输出激活令牌。仅在空数据库且获得操作者授权后执行一次：

```powershell
docker compose --env-file .env.production -f infra/compose/compose.candidate.yml run --rm `
  -e SPRING_PROFILES_ACTIVE=production,bootstrap-admin `
  -e VOIDHAVEN_BOOTSTRAP_ADMIN_LOGIN=<approved-login-id> `
  api --spring.main.web-application-type=none
```

一次性激活 URL 仅显示在当前终端。不得重定向、复制到聊天、截图、普通日志或验收证据；应立即完成激活并关闭终端。若数据库已有任意账号，命令会拒绝执行。

1. 从通过 Web/API 质量门的 Git 提交构建不可变镜像标签。
2. 在测试环境执行 Flyway 迁移、公开读取、受控登录、越权拒绝、投稿幂等与审核发布 Smoke Test。
3. 发布前确认：最近可恢复备份、回滚镜像、数据库迁移兼容性、窗口、责任人、通知范围。
4. 生产迁移只采用“扩展后收缩”；常规发布不得执行不可逆数据删除。
5. 内容或隐私风险优先隐藏内容、关闭受控入口、撤销会话；应用故障回滚到上一验证镜像。数据恢复必须先在隔离副本校验。

腾讯云、域名、备案、TLS、监控、告警和公网发布均需要单独批准，不由本仓库脚本自动触发。

## 单副本运行边界（禁止扩容）

当前实现只能以单实例运行；在完成以下剩余改造之前，任何形式的水平扩容（包括 `docker compose up --scale`、多机部署或蓝绿双活）都会直接导致生产问题，禁止执行：

1. `PublicWriteRateLimiter` 是进程内 `ConcurrentHashMap`：多实例下公开投稿/合作方申请的限流阈值等效放大为「实例数 × 阈值」。
2. `ContentLifecycleService` 与 `RetentionService` 的 `@Scheduled` 定时任务没有分布式锁：多实例下会重复执行，产生重复审计记录与重复的媒体清理尝试。

已完成：会话已改为 Spring Session JDBC（MySQL），单实例重启/滚动发布不会再清空登录态。扩容前仍须先完成上述两项改造并补充相应测试，再解除此限制。

## 候选容器边界

- `compose.candidate.yml` 使用独立的非 root Web/Nginx 镜像；API 以专用非 root 用户运行。
- 容器采用只读根文件系统、临时目录、最小 Linux capability、健康检查和资源上限。
- 候选配置不含数据库容器、默认密码或管理账号；数据库、对象存储、告警、TLS 与 DNS 仍属于单独生产审批关卡。
