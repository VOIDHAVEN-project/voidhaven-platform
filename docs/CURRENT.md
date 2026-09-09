# VOIDHAVEN 当前基线

| 字段 | 当前值 |
| --- | --- |
| 正式工作区 | `D:\Projects\voidhaven-platform` |
| 当前迁移分支 | `migration/release04-baseline` |
| 产品基线 | 2026-09-07 线上 Release 04 的实际 API 与 Memoir Web 工作副本 |
| Git 历史 | 继承 `D:\voidhaven\v0`，当前迁移基点 `dc4b820` |
| 规范基线 | S-01～S-08、DR-001～DR-004，来源提交 `5057138` |
| 本地实验仓库 | `D:\Projects\voidhaven-lab`，独立 Git 历史，无部署流程 |
| 本地归档 | `D:\Projects\_archive\voidhaven\2026-09-09`，观察至 2026-10-09 |
| 外部状态 | 组织仓库、个人镜像和个人 lab 尚待远程创建；未部署、未绑定域名 |

## 已完成验证

- Web：格式、lint、19 项单元测试和生产构建通过。
- 浏览器：22 项中 21 项通过；唯一真实 API 链路因本机未启动后端服务而未执行成功。
- API：Maven Verify 通过，50 项测试中 49 项通过、1 项 Testcontainers 测试因本机无 Docker 跳过。
- 契约与仓库：OpenAPI lint、敏感信息/仓库边界检查通过。
- 归档：五个 Git bundle 可验证，Release 01～04 发布包与原 SHA-256 一致。

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
