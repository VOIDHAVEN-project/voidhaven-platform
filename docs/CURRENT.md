# VOIDHAVEN 当前基线

| 字段         | 当前值                                                                                                                                                                              |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 正式工作区   | `D:\Projects\voidhaven\voidhaven-platform`                                                                                                                                          |
| 当前主分支   | `main`（保留 `migration/release04-baseline` 作为迁移审计指针）                                                                                                                      |
| 产品基线     | 2026-09-15 上线服务闭环候选：test08 基线加首页入口修正、核验记录闭环、双通道密码恢复及候选发布增强                                                                              |
| Git 历史     | 继承 `D:\voidhaven\v0`，当前迁移基点 `dc4b820`                                                                                                                                      |
| 规范基线     | S-01～S-08、DR-001～DR-004，来源提交 `5057138`                                                                                                                                      |
| 基线标签     | `baseline-20260907-release04`（annotated tag）                                                                                                                                      |
| 本地实验仓库 | `D:\Projects\voidhaven-lab`，独立 Git 历史，无部署流程                                                                                                                              |
| 本地归档     | `D:\Projects\_archive\voidhaven\2026-09-09`，观察至 2026-10-09                                                                                                                      |
| 外部状态     | 权威仓库 `VOIDHAVEN-project/voidhaven-platform`、个人镜像与实验仓库均已建立；本轮尚未部署生产、绑定域名或写入真实生产凭据                                                        |

## 已完成验证

- Web：格式、lint、27 项 Vitest、生产构建和 OpenAPI lint 通过。
- 浏览器：全新隔离 Compose 环境完成 25/25 Playwright；双通道密码恢复和内容核验均经过真实 API、MySQL 与本地邮件接收器闭环。
- API：从清理状态执行 Maven Verify 成功，83 项单元/集成测试及 1 项分布式安全测试通过；MySQL 8.4/MinIO、空库 V1～V18 和 V11～V18 升级均已实跑。
- 全栈候选：提交 `6d71725` 已通过 Compose 冷启动、`/healthz`、Flyway V1～V10、候选镜像构建、干净源码打包和产物上传；[验证记录](https://github.com/VOIDHAVEN-project/voidhaven-platform/actions/runs/34386307092)。
- 契约与仓库：OpenAPI lint、敏感信息/仓库边界检查通过。
- 归档：六个 Git bundle 可验证，非 Git 目录已生成清单，Release 01～04 发布包与原 SHA-256 一致；已完成一次 `v0.bundle` 独立恢复演练。

## 当前目标

1. 审核并提交当前上线服务闭环差异。
2. 通过正式 CI，建立新的 `v0.1.0-pretest.N` 可追溯候选标签。
3. 在获批生产等价环境注入真实密钥与 SMTP，完成备份/恢复、发布回滚和真实用户验收后再评估 RC。

## 明确边界

- 当前候选包含 `/api/v1` 管理、公开输入、内容核验、人工与自助密码恢复、Flyway V1～V18 及分布式并发安全实现；Cookie/CSRF、幂等和 `If-Match` 安全边界继续保留。
- 本轮只生成并验证候选提交，不修改线上 test08，不上传真实数据或 Secret，不执行生产部署。
- `voidhaven-lab` 不是产品、需求、备份或发布真源。
