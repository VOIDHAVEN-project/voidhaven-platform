# 本地运行手册

## 前置条件

- Node.js 24+、JDK 17、Maven（或仓库内 Maven Wrapper）和 Docker Desktop Linux 引擎。
- 只使用合成数据。不要将真实联系人、社群入口、生产 Secret 或真实媒体填入 `.env`。

## 启动

1. 将仓库根目录 `.env.example` 复制为 `.env`，为所有 `change-me` 值生成本地测试值。
2. 在 `infra/compose` 执行：

   ```powershell
   docker compose --env-file ../../.env -f compose.local.yml up --build
   ```

3. 访问 `http://localhost:8080`；健康检查为 `/healthz`。本地管理员和本地合作方分别由 `VOIDHAVEN_BOOTSTRAP_ADMIN_*`、`VOIDHAVEN_BOOTSTRAP_PARTNER_*` 创建，仅用于验收。使用非默认端口时，同时设定 `VOIDHAVEN_HTTP_PORT`；合成来源链接会使用同一端口。

## 质量门

```powershell
cd D:\voidhaven\v0\apps\web; npm run test; npm run build; npm run lint
cd D:\voidhaven\v0\apps\api; .\mvnw verify
```

或从仓库根目录一次执行：

```powershell
.\infra\scripts\verify-local.ps1
```

在 Docker 引擎可用时，再执行 `mvn -Pintegration test` 和 Compose 启动后的公开、认证、审核 Smoke Test。绝不为测试连接未知的本机业务数据库。

## 生产候选配置（不部署）

`infra/compose/compose.candidate.yml` 不包含 MySQL、媒体存储、默认密码或 bootstrap 账号。它只能连接已获批的受控测试/生产依赖；未得到单独授权时，不得执行 `up`。使用 `.env.candidate.example` 仅验证变量完整性，真实 Secret 必须由受控 Secret 管理注入。
