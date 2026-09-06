# Compose 冷启动证据

- 使用独立 Compose 项目名和全新 MySQL/MinIO 卷。
- MySQL 8.4 从空库应用 Flyway V1–V10。
- MinIO 私有桶初始化任务正常退出。
- API、Web、Nginx、MySQL、MinIO 启动成功。
- `GET /healthz` 返回 `UP`。
- `GET /api/v1/public/home` 返回预期成功信封。
- Playwright 经同一网关访问应用。
- 测试结束后专用容器、网络和数据卷均已清理。

首次真实冷启动曾暴露 JDBC 参数 `characterEncoding=utf8mb4` 无法被 Java 识别。源提交已统一改为 `characterEncoding=UTF-8`，MySQL 服务和 V9 表迁移仍使用 `utf8mb4`。验证脚本增加禁用配置扫描，防止回归。
