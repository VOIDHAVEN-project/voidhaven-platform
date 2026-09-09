# VOIDHAVEN 当前基线

| 字段         | 当前值                                                                                                                                                                              |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 正式工作区   | `D:\Projects\voidhaven-platform`                                                                                                                                                    |
| 当前主分支   | `main`（保留 `migration/release04-baseline` 作为迁移审计指针）                                                                                                                      |
| 产品基线     | 2026-09-07 线上 Release 04 的实际 API 与 Memoir Web 工作副本                                                                                                                        |
| Git 历史     | 继承 `D:\voidhaven\v0`，当前迁移基点 `dc4b820`                                                                                                                                      |
| 规范基线     | S-01～S-08、DR-001～DR-004，来源提交 `5057138`                                                                                                                                      |
| 基线标签     | `baseline-20260907-release04`（annotated tag）                                                                                                                                      |
| 本地实验仓库 | `D:\Projects\voidhaven-lab`，独立 Git 历史，无部署流程                                                                                                                              |
| 本地归档     | `D:\Projects\_archive\voidhaven\2026-09-09`，观察至 2026-10-09                                                                                                                      |
| 外部状态     | 权威仓库 `VOIDHAVEN-project/voidhaven-platform`、个人镜像 `podittis10011-blip/voidhaven-platform-mirror` 与实验仓库 `podittis10011-blip/voidhaven-lab` 均已建立；未部署、未绑定域名 |

## 已完成验证

- Web：格式、lint、19 项单元测试和生产构建通过。
- 浏览器：GitHub Actions 在真实 Compose 环境中完成 22/22 项 Playwright 验证。
- API：GitHub Actions 中 Maven Verify 通过，50/50 项测试通过，包含 MySQL 8.4 与 MinIO Testcontainers 集成测试。
- 全栈候选：提交 `6d71725` 已通过 Compose 冷启动、`/healthz`、Flyway V1～V10、候选镜像构建、干净源码打包和产物上传；[验证记录](https://github.com/VOIDHAVEN-project/voidhaven-platform/actions/runs/34386307092)。
- 契约与仓库：OpenAPI lint、敏感信息/仓库边界检查通过。
- 归档：六个 Git bundle 可验证，非 Git 目录已生成清单，Release 01～04 发布包与原 SHA-256 一致；已完成一次 `v0.bundle` 独立恢复演练。

## 当前目标

1. 完成 Release 04 源码、基础设施和规范的单仓收拢。
2. 通过干净安装、Web/API、OpenAPI、Compose 和 Secret 检查。
3. 建立 `baseline-20260907-release04` 可追溯标签。
4. 创建组织权威仓库、个人灾备镜像和独立实验仓库。
5. 旧目录冻结 30 天，只在再次批准后执行清理。

## 明确边界

- 本轮不改变 `/api/v1`、Flyway V1～V10、Cookie/CSRF、幂等、`If-Match` 或现有公开路由。
- 本轮不修改线上 Release 04，不上传真实数据或 Secret，不执行生产部署。
- `voidhaven-lab` 不是产品、需求、备份或发布真源。
