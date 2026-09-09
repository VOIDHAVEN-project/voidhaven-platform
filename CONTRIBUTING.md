# 贡献指南

## 工作流

1. 在 GitHub Project 的 `Inbox` 建立 Issue，补齐目标、验收条件和影响检查。
2. Issue 进入 `Ready` 后创建短期分支：`feat/<issue>-<name>`、`fix/<issue>-<name>`、`docs/<issue>-<name>` 或 `ops/<issue>-<name>`。
3. 保持提交小而可审计，推荐使用 `feat:`、`fix:`、`docs:`、`test:`、`chore:` 前缀。
4. 创建 PR，填写测试证据和风险；CI 通过后合入 `main`。
5. 架构、权限、数据、隐私、部署或付费服务的跨模块决定必须补充 ADR。

## Definition of Done

- 验收条件有自动测试或可复现的人工证据。
- 格式、lint、单元测试、构建、OpenAPI 和 API 验证通过。
- API/数据库变更有兼容策略，文档和示例配置同步更新。
- 未引入 Secret、真实数据、未经许可的第三方资产或不可恢复的数据操作。
- 需要发布时已准备版本、备份、回滚和已知限制。

## 实验成果

个人学习和 PoC 放在 `voidhaven-lab`。产品化时创建新的正式 Issue，只移植最小代码、测试和结论；实验仓库的状态、标签和 README 不代表本产品状态。
