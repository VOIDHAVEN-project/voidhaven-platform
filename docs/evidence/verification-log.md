# 本地验证记录

| 日期 | 检查项 | 结果 | 备注 |
| --- | --- | --- | --- |
| 2026-08-30 | Web `npm run test` | 通过 | 2 个组件测试：公开导航不含受控登录、详情链接使用不透明 ID。 |
| 2026-08-30 | Web `npm run build` / `npm run lint` | 通过 | Vite 生产构建与静态检查通过。 |
| 2026-08-30 | API `mvn -DskipTests compile` | 通过 | JDK 17 + Spring Boot 4.1.1 编译通过。 |
| 2026-08-30 | API `mvn test` | 通过 | 2 个领域状态机单元测试通过。 |
| 2026-08-30 | Docker Compose 启动与迁移联调 | 未执行 | Docker Desktop 已启动，但镜像构建受 Docker Hub 网络阻断；不使用未知本机 MySQL 实例，避免影响既有数据。 |
| 2026-08-30 | Docker Compose 配置解析 | 通过 | `docker compose -f compose.local.yml config` 成功；本地合成服务定义可解析。 |
| 2026-08-30 | Docker Compose 镜像构建 | 受环境阻断 | Docker Hub 认证端网络连接失败，未创建容器、数据库或数据卷。 |
| 2026-08-30 | Vite HTTP 壳检查 | 通过 | `http://127.0.0.1:5173/` 返回 200 且包含 React 根节点；浏览器自动化 CLI 在此环境不可用，未生成视觉截图。 |

本记录只保留最小命令结果，不包含凭据、真实个人资料或私密正文。
