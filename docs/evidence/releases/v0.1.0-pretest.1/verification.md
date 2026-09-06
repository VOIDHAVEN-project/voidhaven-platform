# v0.1.0-pretest.1 验证结论

- 状态：`PRETEST_ONLY`
- 源提交：`af20036452b819d1fef906bb5f8516a3e618f7ca`
- 验证时间：2026-09-06 13:09–13:12（Asia/Shanghai）
- 验证来源：独立干净检出 `D:\voidhaven\v0\.verification\v0.1.0-pretest.1`
- 结论：本地预测试质量门通过，可以进入受控上线测试；尚不是正式上线许可，也不创建 `rc` 标签。

验证入口为 `infra/scripts/verify-release.ps1`。它从干净检出安装锁定依赖，执行格式、静态检查、组件测试、构建、OpenAPI、后端测试、MySQL/MinIO 集成测试、Compose 冷启动、真实网关探测、Playwright 和候选镜像构建。

验证过程中发现并修复了三个可复现问题：Linux Maven Wrapper 换行、JDBC 将 MySQL 字符集名误用作 Java 编码名、网关探测早于 API 就绪。另修复了验证脚本自身的扫描误命中和 PowerShell 保留变量冲突。失败尝试均未创建标签，专用 Compose 资源均已清理。

未使用远程仓库、云资源、域名、真实账号、真实联系方式或真实生产数据。
