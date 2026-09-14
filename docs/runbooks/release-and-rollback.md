# 发布与回滚手册（候选）

## 候选版本身份

- 预测试版本格式：`v0.1.0-pretest.N`；完成 R2 全部门禁后才使用 `v0.1.0-rc.N`。
- API/Web 镜像标签必须同时包含版本和完整 Git SHA，不使用 `latest`。
- annotated tag 记录源代码 SHA、Flyway 范围、镜像 ID、候选配置摘要及证据目录。
- 本地权威验证入口为 `infra/scripts/verify-release.ps1`；`main` 推送运行快速质量门，`v*` 标签运行完整候选验证、隔离全栈验收和打包。

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

## 多实例切换与回滚

公开写入限流、登录/激活限流、内容生命周期与保留清理任务、媒体删除领取均已改为 MySQL 共享状态（行锁、advisory lock 或租约）。会话继续使用 Spring Session JDBC。

水平扩容前必须采用非混合版本切换，避免新旧 API 同时运行时绕过新锁协议：

1. 备份 MySQL，并暂停全部旧 API 实例。
2. 执行向后兼容的 Flyway 迁移（当前含 V18）。
3. 启动单实例新版 API 与 Web，完成冒烟：创建普通管理员、激活、登录后台、停用/恢复、重新签发、永久撤销及撤销后会话失效。
4. 冒烟通过后再扩容多实例。

紧急回滚时保留新增表和字段，不回滚 Flyway。旧版本只能临时以单 API 实例运行，防止重新出现多实例限流放大与重复后台任务。

保数据上线的可执行步骤、隔离恢复演练、测试机切换、生产切换与回滚命令见 [data-preserving-cutover.md](data-preserving-cutover.md)。本地可用 `infra/scripts/rehearse-v11-data-safety.ps1` 在一次性 MySQL 容器上验证 V11 不删除账号/内容。

## 候选容器边界

- `compose.candidate.yml` 使用独立的非 root Web/Nginx 镜像；API 以专用非 root 用户运行。
- 容器采用只读根文件系统、临时目录、最小 Linux capability、健康检查和资源上限。
- 候选配置不含数据库容器、默认密码或管理账号；数据库、对象存储、告警、TLS 与 DNS 仍属于单独生产审批关卡。
