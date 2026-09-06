# 浏览器验收摘要

Playwright 以 Chromium 验证以下场景：

- 桌面及移动公开首页、稳定详情路由与受控入口；
- 桌面及移动未认证访客拒绝进入管理后台；
- 合作方 Markdown 草稿、图片上传、送审；
- 管理员审核详情查看完整原文、退回、通过；
- 发布配额检查、发布、公开读取、隐藏；
- 超级管理员停用合作方、既有会话失效及账号恢复。

结果为 6 项通过、2 项设计性跳过。合成数据截图由本次干净验证生成在受忽略的 `.verification/browser` 目录；大体积浏览器报告、trace 和动态截图不提交 Git。既有公开页证据 `docs/evidence/admin-remediation-public-smoke.png` 与本次生成文件 SHA-256 一致。

专用 `agent-browser` 命令在当前 Windows 环境不可用，因此没有附加第二套人工视觉工具结果；Playwright 浏览器验收不受影响。
