# ISSUE：正式上线前产品服务闭环（V12–V16）

| 字段 | 内容 |
| --- | --- |
| 日期 | 2026-09-14 |
| 仓库 | `D:\Projects\voidhaven\voidhaven-platform` |
| 分支 | `main` @ `23b695c`，工作树含未提交改动 |
| 状态 | P0 骨架与关键路径已验证；P1 主缺口已补；**不建议上线**。剩余：Spring/JPA validate、DistributedSafetyIT、P2 主题/无障碍/完整契约核对 |
| 依据 | `docs/product/launch-service-closure-implementation.md`、交接 `docs/handoffs/cursor-launch-service-closure-handoff-2026-09-14.md` |

## 目标

在不引入学生账号、站内聊天、简历或站内投递的前提下，把公开提交、状态查询、社群门禁、招聘行动入口、密码重置和首页编排补成可运营、可审计、可关闭的轻闭环。

## 验收

- 隔离 MySQL 完成 Flyway V1–V16 空库与 V11→V16 升级，应用能以 `ddl-auto: validate` 启动。
- 公共事项状态与内容审核 `PENDING_REVIEW` 分离；OpenAPI、后端、前端、数据库与测试一致。
- 激活令牌与密码重置令牌按 `token_type` 隔离。
- 首页编排可重复保存同一内容，幂等回放使用明确 DTO。
- 私密联系人缺密钥时 fail closed；接口不回显密文或明文。
- 提交有回执、状态可追踪、管理员能处理、人工交付可记录。本轮不部署、不推送、不绑定域名、不写入真实密钥。

## 影响

- API：公开事项、社群、首页、密码、招聘行动相关路径。
- 数据库：仅新增/修正未发布的 V12–V16；已发布 V1–V11 不改。
- 权限：社群与密码重置仍仅超级管理员。
- 隐私：查询码/邀请码只存摘要；联系人 AES-256-GCM。
- 部署：功能开关默认关闭；本 Issue 不执行上线。
