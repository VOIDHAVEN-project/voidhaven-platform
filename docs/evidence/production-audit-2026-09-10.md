# 线上生产环境排查记录（2026-09-10）

## 范围与结论

本次对 `http://101.42.53.14/` 仅执行 HTTP、SSH、容器状态、日志与数据库的只读检查，未发送审核决定，未修改数据，未替换镜像。

“公共投稿处理审核”无法使用的直接原因是线上前端仍以 `PENDING` 判断是否显示通过/拒绝按钮，而 API 和数据库的待审核状态是 `PENDING_REVIEW`。列表能加载并显示“审核中”，但操作表单不会渲染。

## 线上实证

- `/opt/voidhaven/current` 指向 `20260907-voidhaven-test-04`。
- Web 镜像为 `voidhaven-web:20260907-voidhaven-test-04-memoir-b27ec4ec3e77`。
- API 镜像为 `voidhaven-api:20260907-voidhaven-test-02-ff5dbb30dacc`。
- 线上 JS `index-DcEpdzAN.js` 的 SHA-256 为 `65691c42413f25bc4a111d4f41d15d6c2bf6b6f142709912b65ebd3afe7dfb29`，与本地 `test-04` 构建产物完全相同。该产物包含 `state === PENDING` 的旧审核按钮判断。
- 数据库中 3 条公开输入全部为 `PENDING_REVIEW`，`APPROVED`/`REJECTED` 总数为 0。
- Nginx 访问日志记录了多次成功的 `GET /api/v1/admin/submissions`，没有对 `/api/v1/admin/submissions/{id}/decision` 的 POST。
- API、Web、Nginx、MySQL 和 MinIO 容器均为 healthy，且重启计数为 0。因此该故障不是服务挂掉、权限拒绝或审核 API 返回错误。

## 回滚残留状态

线上数据库已执行 Flyway V11，但当前 API 镜像只携带 V1–V10。API 启动日志明确警告：数据库版本 11 高于应用可见最新版本 10。V11 为增量字段、锁表和限流事件表，当前旧 API 仍可运行，但这是一个不应长期保留的混合版本状态。下次发布必须同时上线携带 V11 的新 API，不得回退或重复执行破坏性迁移。

## 候选版必须通过的回归

1. 后端返回 `PENDING_REVIEW` 时，前端必须显示处理原因、通过和拒绝操作。
2. 点击通过必须发出 `/api/v1/admin/submissions/{id}/decision`，成功后刷新列表。
3. 同一投稿被两名管理员并发处理时，数据库条件更新只允许一个决定成功，后到请求返回 `SUBMISSION_NOT_ACTIONABLE`。
4. 组包脚本必须拒绝任何重新出现的 `item.state === "PENDING"` 公开投稿判断。
