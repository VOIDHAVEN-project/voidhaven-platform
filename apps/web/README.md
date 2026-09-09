# VOIDHAVEN · Memoir Edition

独立的整站前端，基于 Vite + React + React Router。公开官网采用顶部导航和纵向叙事，详情页采用自适应文档式阅读；投稿反馈、合作申请与受控工作台共享 Memoir 风格和三态主题。

实现目录：`D:\Projects\voidhaven-memoir-console`。原 `D:\voidhaven\v0` 不作修改；所有接口沿用运行中的 v0 服务。

## 本地运行

1. 确认已有 v0 服务在 `http://localhost:8080` 运行。不同地址可在 `.env` 中配置 `VITE_API_PROXY_TARGET`，未配置时自动使用默认地址。
2. 在此项目目录运行 `npm install`。
3. 运行 `npm run dev -- --host 127.0.0.1`。
4. 首页：`http://127.0.0.1:4173/`；受控入口：`http://127.0.0.1:4173/auth/sign-in`。

预览仅绑定本机。开发服务关闭后，本地链接也会停止服务；重新运行第 3 步即可恢复。

新前端将 `/api` 请求代理到配置的 API 地址，因此 Cookie 会话、CSRF 校验和原有请求协议保持不变。

## 页面与交互

- `/`：官网式纵向首页；`/competitions`、`/resources`、`/recruitments`：服务端搜索、游标分页。
- `/search?q=`：基于三个现有公开接口的聚合搜索，支持 `Ctrl/Command + K` 打开。
- `/content/:publicId`：自适应双栏/三栏正文、页内目录、关键信息、同类内容、来源与授权、阅读进度和复制 / 原生分享。
- `/feedback`：内容线索、产品反馈、版权/下线反馈；`/join`：合作方申请；`/about`：平台说明。
- `/auth/sign-in`、`/auth/activate`：登录与一次性激活。
- `/workspace?section=overview|content|reviews|submissions|applications|partners|accounts|audit`：管理员工作区；账号治理仅超级管理员可见。合作方只显示概览与自有内容。
- 公开分类从 `/public/content-modules` 读取模块定义；资源页可在“学习资源”和“文章 / 经验”之间切换，并使用后端支持的单字段精确筛选。
- 内容编辑使用 Markdown 写作、分屏和阅读预览；草稿首次保存后可上传受控图片并插入 `media:ID` 引用。
- 未匹配路径显示 404，原有公开 URL 及内容标识保持不变。

## 明暗主题：持续开发要求

主题支持浅色、深色和跟随系统，使用 `voidhaven-theme` 保存选择；兼容已有 light / dark 值。后台侧栏偏好继续使用 `voidhaven-sidebar`。
页面首次渲染前应用主题；跨页面、刷新和同源标签页同步。主题和侧栏是唯一持久化的界面偏好，不缓存受控正文或凭据。

新增页面必须复用主题组件与语义颜色变量。开发约定见 `AGENTS.md`。字体通过本地 npm 包打包，不依赖运行时 Google Fonts 请求。

公开页面的中文正文和辅助信息采用 Windows / macOS 系统优化中文字体，正文与页脚可见中文最低 13px；小号等宽字体只用于纯英文标签、编号和日期。首页采用内容驱动高度，页脚使用品牌说明、三组导航与版权行组成的紧凑网格。

## 验证

```text
npm run build
npm run lint
npm test
npm run test:e2e
```

浏览器测试首次运行若没有 Chromium，请先执行 `npx playwright install chromium`。真实公开测试需要 v0 服务在线；其他测试以隔离的浏览器接口拦截运行，不会写入真实服务。

验证边界和回归清单见 `docs/verification.md`。本项目不进行公网部署。
