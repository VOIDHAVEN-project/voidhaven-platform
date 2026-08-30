# VOIDHAVEN V0

VOIDHAVEN 是面向本校学生的竞赛、学习资源与团队招募发现平台。本仓库是独立的 V0 正式候选系统实验，不包含也不复用任何既有项目的 Git 历史、代码或运行目录。

## 当前范围

- 公开访客可浏览竞赛、学习资源和团队招募，并提交投稿/反馈或合作方申请。
- 合作方与管理员通过受控账号进入后台，处理草稿、审核、发布和归档。
- 本地环境只使用合成数据；不配置真实社群入口、真实个人联系方式或外部云资源。

## 本地启动（实施完成后）

1. 复制 `.env.example` 为 `.env`，仅填入本地测试值。
2. 在 `infra/compose` 运行 `docker compose --env-file ../../.env -f compose.local.yml up --build`。
3. 打开 `http://localhost:8080`；API 健康检查为 `http://localhost:8080/healthz`。

具体运行、备份和发布边界见 `docs/runbooks/`。生产候选配置仅是预案，未经单独授权不得部署、绑定域名或创建外部资源。
