# 旧位置与迁移登记

本页防止继续误用旧目录。2026-09-09 起，产品代码、规范、契约和运维脚本只在 `D:\Projects\voidhaven-platform` 继续维护。

| 原路径 | 原用途 | 冻结提交/状态 | 新归属 | 处置状态 |
| --- | --- | --- | --- | --- |
| `D:\voidhaven\v0` | v0 API、Web 与基础设施 | `dc4b820` | 产品仓库 Git 历史 | 冻结，2026-10-09 后复核 |
| `D:\Projects\docs` | 产品规范与决策 | `5057138` | `docs/` | 冻结，2026-10-09 后复核 |
| `D:\Projects\voidhaven` | 早期前端 | `0069e1f` | 仅作历史参考 | 冻结，2026-10-09 后复核 |
| `D:\Projects\voidhaven-project\voidhaven-frontend` | 原型前端 | `03cea65` + 未提交 `fixtures.ts` | 实验/历史参考 | 已保存 bundle、补丁和文件副本 |
| `D:\Projects\voidhaven-phase1` | 第一阶段工作树 | detached `75f6577` | 仅作历史参考 | 冻结，2026-10-09 后复核 |
| `D:\Projects\voidhaven-deploy-staging` | Release 01～04 发布暂存 | Release 04 | `infra/`、归档发布包与证据 | 冻结，2026-10-09 后复核 |

## 归档位置

`D:\Projects\_archive\voidhaven\2026-09-09`

归档包含五个 Git bundle、原型未提交改动、Release 01～04 发布包、Release 04 证据及 SHA-256 清单。所有 bundle 均已通过 `git bundle verify`，四个历史发布包均与原校验文件一致。

## 使用规则

- 不在旧路径继续开发、修复或补写规范。
- 发现遗漏时先登记来源与校验值，再通过产品仓库 PR 导入。
- 观察期结束不代表自动删除；清理仍需仓库、CI、线上稳定性和异地备份检查，并再次获得所有者明确批准。
