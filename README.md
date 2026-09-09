# VOIDHAVEN Platform

VOIDHAVEN 是面向本校学生的竞赛、学习资源与团队招募发现平台。本仓库是正式产品、规范、API 契约、数据库迁移、运行配置和发布证据的唯一来源。

## 仓库边界

- 正式产品工作区：`D:\Projects\voidhaven-platform`
- 正式远程：VOIDHAVEN 组织的私有 `voidhaven-platform`
- 个人只读灾备：`podittis10011-blip/voidhaven-platform-mirror`
- 个人实验仓库：`podittis10011-blip/voidhaven-lab`

实验仓库中的学习、原型和 PoC 不构成产品功能。任何实验成果进入本仓库前，都必须建立 Issue、移植最小实现并通过 PR 与质量门。

## 目录

- `apps/web`：Release 04 Memoir 前端。
- `apps/api`：Spring Boot API 和 Flyway 迁移。
- `contracts/openapi`：前后端唯一接口契约。
- `infra`：本地、测试、候选环境及验证/备份/发布脚本。
- `docs`：产品、标准、决策、架构、隐私、运行手册和发布证据。

## 本地验证

前置条件：Node.js 24.15+、npm 10+、JDK 17、Docker Desktop Linux 引擎。

```powershell
npm ci
npm run format:check
npm run lint
npm run test
npm run build
npm run contract:lint
cd apps\api
.\mvnw.cmd -B verify
```

完整本地环境和发布边界见 `docs/runbooks/`。本仓库不会自动部署、绑定域名或创建云资源。

## 版本与变更

- 日常开发从 GitHub Issue 建立 `feat/`、`fix/`、`docs/` 或 `ops/` 短期分支。
- 所有正式变更通过 PR 合入 `main`。
- 候选版本使用 `vX.Y.Z-rc.N`，正式版本使用 `vX.Y.Z`。
- 镜像与发布包必须同时记录版本、完整 Git SHA 和校验值，禁止使用 `latest`。

详细协作约定见 `CONTRIBUTING.md`，当前基线见 `docs/CURRENT.md`。
