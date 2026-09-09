# VOIDHAVEN 当前基线

| 字段 | 当前值 |
| --- | --- |
| 正式工作区 | `D:\Projects\voidhaven-platform` |
| 当前迁移分支 | `migration/release04-baseline` |
| 产品基线 | 2026-09-07 线上 Release 04 的实际 API 与 Memoir Web 工作副本 |
| Git 历史 | 继承 `D:\voidhaven\v0`，当前迁移基点 `dc4b820` |
| 规范基线 | S-01～S-08、DR-001～DR-004，来源提交 `5057138` |
| 外部状态 | 组织仓库、个人镜像和个人 lab 尚待远程创建；未部署、未绑定域名 |

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
