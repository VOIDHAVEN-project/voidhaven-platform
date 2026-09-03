# 本地验证记录

| 日期 | 检查项 | 结果 | 备注 |
| --- | --- | --- | --- |
| 2026-08-30 | Web `npm run test` | 通过 | 2 个组件测试：公开导航不含受控登录、详情链接使用不透明 ID。 |
| 2026-08-30 | Web `npm run build` / `npm run lint` | 通过 | Vite 生产构建与静态检查通过。 |
| 2026-08-30 | API `mvn -DskipTests compile` | 通过 | JDK 17 + Spring Boot 4.1.1 编译通过。 |
| 2026-08-30 | API `mvn test` | 通过 | 2 个领域状态机单元测试通过。 |
| 2026-08-30 | Docker Compose 启动与迁移联调 | 未执行 | Docker Desktop 已启动，但镜像构建受 Docker Hub 网络阻断；不使用未知本机 MySQL 实例，避免影响既有数据。 |
| 2026-08-30 | Docker Compose 配置解析 | 通过 | `docker compose -f compose.local.yml config` 成功；本地合成服务定义可解析。 |
| 2026-08-30 | Docker Compose 镜像构建 | 受环境阻断 | Docker Hub 认证端网络连接失败，未创建容器、数据库或数据卷。 |
| 2026-08-30 | Vite HTTP 壳检查 | 通过 | `http://127.0.0.1:5173/` 返回 200 且包含 React 根节点；浏览器自动化 CLI 在此环境不可用，未生成视觉截图。 |
| 2026-08-31 | 空库 Compose 与 Flyway 回归 | 通过 | 使用独立 `voidhavenr0` 临时项目和空卷启动 Web、API、MySQL、MinIO、Nginx；Flyway V1/V2 成功，`/healthz`、首页、公开 API、合成来源页与直接详情 SPA 路由均返回成功。仅使用合成数据。 |
| 2026-08-31 | R0/R3 Web 质量门 | 通过 | `npm run lint`、`npm run test`（4 项）和 `npm run build` 通过；包含来源夹具、公开分页界面、受控后台组件及两种异步表单安全重置回归。 |
| 2026-08-31 | R0/R3 API 验证与可运行包 | 通过 | `.\mvnw.cmd verify` 通过（10 项），并生成 Spring Boot 可运行 JAR；覆盖状态机、来源拒绝、一次性激活与富文本白名单。 |
| 2026-09-03 | R0 生产来源与 Cookie 回归 | 通过 | `.\mvnw.cmd verify` 通过（10 项）。生产 profile 显式强制会话 Cookie `Secure`；本地合成来源迁移已移除；公开投影对遗留的 localhost、保留域名及非 HTTPS 来源 fail-closed。 |
| 2026-09-03 | R0 干净 Compose 与浏览器 Smoke Test | 通过 | 在隔离项目 `voidhavenr0clean0903` 的新建 MySQL/MinIO 卷执行 Compose 构建。Flyway 仅从空库应用 V1；`/healthz`、`/api/v1/public/home`、首页、竞赛详情直达与刷新均返回成功。公开响应包含本地来源说明而不含 `example.invalid`。 |
| 2026-09-03 | R1 候选/公开生命周期分离单元回归 | 通过 | `.\mvnw.cmd verify` 通过（10 项）；状态机测试验证已发布内容创建候选草稿后，到期关闭只改变 `publicationStatus`，不覆盖候选修订的 `workflowStatus`。 |
| 2026-09-03 | R3 账号资格与会话失效单元回归 | 通过 | `.\mvnw.cmd verify` 通过（13 项）。超级管理员可审计地停用或撤销其他账号；过滤器回归验证既有会话会在失效账号的下一次受限请求被清除并返回 `ACCOUNT_INACTIVE`。 |
| 2026-09-03 | R2 公开输入审核状态机单元回归 | 通过 | `.\mvnw.cmd verify` 通过（14 项）。公开投稿、反馈、版权请求可由管理员审批、退回或拒绝；合作方申请可退回或拒绝，批准仍采用一次性激活凭据流程。所有后台列表排除私密联系方式。 |
| 2026-08-31 | Local/Candidate Compose 配置 | 通过 | 本地 Compose 与生产候选 Compose 均通过 `docker compose config --quiet`；候选验证仅使用示例变量，不创建容器、数据库、云资源或 Secret。 |
| 2026-08-31 | Docker 联调复跑 | 待执行 | Docker Desktop Linux 引擎当前未运行，无法在本次后续变更后重建临时空库、执行 E2E 或恢复演练。此项不是通过状态。 |

正式上线候选的阻断项与已知限制见 [known-limitations.md](known-limitations.md)。

本记录只保留最小命令结果，不包含凭据、真实个人资料或私密正文。
