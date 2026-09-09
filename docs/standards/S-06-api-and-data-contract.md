# S-06：前后端 API 与数据契约规范

| 字段 | 内容 |
| --- | --- |
| 文档 ID | S-06 |
| 版本 | 0.3 |
| 状态 | APPROVED；2026-08-30 随第 3 批合入 docs/main 生效 |
| 起草日期 | 2026-08-30 |
| 最近修订 | 2026-08-30 |
| 批准日期 | 2026-08-30 |
| 最终确认人 | 项目负责人 |
| 影响标签 | D、I、P、Q、O |
| 主要来源 | S-01 至 S-05、DR-001 至 DR-004、SRC-FE-02、SRC-FE-05、SRC-CODE-01、SRC-USER-04 至 SRC-USER-06 |
| 关联文件 | S-03、S-04、S-05、S-07、S-08、CURRENT.md |

## 1. 目的与边界

本规范定义浏览器前端、未来服务端和 Mock 层之间的**可替换业务契约**。它要解决的是：前端看到什么形状的数据、提交时如何防重、什么错误能安全显示、哪些字段绝不进入公开响应，以及合作方/管理员如何在最小权限下完成内容闭环。

它不是数据库表设计、ORM 设计、Spring Boot 控制器实现、云数据库采购、对象存储配置或身份供应商选择。数据库迁移、密钥、备份、日志清理和生产网络边界留给 S-08；代码质量与自动化测试留给 S-07。本规范是后续模块的接口与数据边界依据，但不单独授权建库、接入外部服务或修改代码；这些动作仍须遵循 S-02 的模块开工与批准流程。

## 2. 契约总原则

1. **API 是边界，不是后端内部对象的透传。** 前端组件不直接认识数据库实体、审核记录、私密联系人或框架异常。
2. **公开读取模型与私密管理模型分离。** 同一内容可有公开 DTO、合作方 DTO 和管理员 DTO；不能为省事返回一个“全字段对象”。
3. **业务状态、可见性、存放状态和账号资格独立。** S-04 的四个维度必须能被内部契约表达；公开接口只暴露用户理解公开内容所需的最小状态。
4. **一次逻辑提交只能产生一条业务记录。** 公共投稿、合作方申请、合作方内容提交和社群验证均使用服务端等价幂等保护；仅禁用前端按钮不够。
5. **无普通学生账户。** 公开投稿、公开浏览和校内社群验证不能因为 API 方便而引入学生身份档案、简历、聊天或普通登录 token。
6. **契约版本可演进。** 破坏性变更必须新开 API 大版本或按兼容期迁移，不能让前端同时猜两套字段。
7. **Mock 先实现同一前端领域模型。** 旧 `Competition*` / `Team*` Mock 只作迁移证据；未来 UI 通过 Service/Mapper 消费 S-06 对应的稳定前端模型，而不是直接绑定 HTTP JSON。

## 3. 通用协议约定

### 3.1 路径、编码与命名

| 项目 | 规范 |
| --- | --- |
| 基础路径 | `/api/v1`；公开读取与公开写入位于 `/public`，合作方位于 `/partner`，管理端位于 `/admin`，会话位于 `/auth`。 |
| 内容类型 | 请求和成功响应默认使用 `application/json; charset=utf-8`；图片上传使用明确的 `multipart/form-data`，不把二进制塞入 JSON。 |
| 字段命名 | JSON 使用 `lowerCamelCase`；枚举使用 `UPPER_SNAKE_CASE`；不在同一契约混用数据库列名、中文字段名和临时缩写。 |
| 标识 | `id` 是对前端和用户不透明的稳定公开标识，不能从中推断数据库、自增顺序、账号或内容数量；首发公开详情 URL 只使用此 `id`，不引入 `slug`。 |
| 未知/空集合 | 已定义但暂无可靠值的可选标量返回 `null`；集合返回 `[]`；不能用省略字段、`"N/A"` 或魔法值改变类型。 |
| 文本与 URL | 服务端在验证后保存规范化文本；外部 URL 只接受安全的 `https`/`http` 方案，并在公开前经过来源审核。 |

### 3.2 时间、语言和枚举

业务日期和精确时刻不能混写为无含义字符串。公开竞赛/招募等对人类重要的时间使用 `BusinessTime`；创建、更新、审核和审计时间使用 UTC ISO 8601 时刻。

```ts
type EntityId = string
type IsoDateTime = string // ISO 8601 UTC，例如 2026-08-30T04:10:00Z

interface BusinessTime {
  value: string // DATE: YYYY-MM-DD；DATETIME: ISO 8601
  precision: 'DATE' | 'DATETIME'
  timeZone: string | null // DATE 时可为 null；未知时不能伪造时区
}

interface ContentLanguage {
  code: 'ZH' | 'EN' | 'ZH_EN' | 'OTHER'
  otherLanguageName: string | null // 仅 code=OTHER 时必填
}
```

`ContentLanguage` 是公开元数据。对 `EN`、`ZH_EN` 或 `OTHER` 的公开内容，中文导读、原始标题和原始来源的发布前检查由 S-04/DR-004 决定；API 不能用空字符串绕过。

### 3.3 成功、分页与错误信封

所有 JSON 成功响应使用一致包裹，便于前端安全处理请求关联号和分页；HTTP 状态码仍表达真正的协议结果。

```ts
interface ApiSuccess<T> {
  data: T
  meta?: {
    requestId: string
    nextCursor?: string | null
    total?: number // 仅在实际可廉价、准确返回时提供；不得伪造
  }
}

interface ApiFailure {
  error: {
    code: string
    message: string // 面向前端映射的安全中文或稳定语义，不含堆栈/私密值
    fieldErrors?: Record<string, string[]> // 只返回当前提交者可修正的字段错误
    retryable: boolean
  }
  meta: { requestId: string }
}
```

集合读取统一使用 `cursor` 与 `pageSize`：默认 20，最大 50；`cursor` 是服务端不透明字符串。首发不为“看起来企业化”而实现任意页跳转、复杂搜索或热度推荐。默认排序必须由端点写清，例如竞赛按报名状态/截止时间，招募按公开状态/截止时间，资源按最近有效更新；不能由客户端本机时间私自改写服务端状态。

### 3.4 幂等、并发与缓存

- 任何会创建或改变业务记录的表单请求必须携带 `Idempotency-Key` 请求头。键由客户端为一次逻辑提交生成并在安全重试中复用；服务端至少保存关联结果 24 小时。
- 首次成功创建返回 `201`；同键、同语义请求返回原业务结果及 `200`，`data.outcome = "DUPLICATE"`；同键不同载荷返回 `409 IDEMPOTENCY_KEY_REUSED`。不得因网络超时让用户猜测是否需要重复填写。
- 合作方/管理员编辑现有内容时，读取模型携带 `revision`；更新请求携带 `If-Match` 或等价 `revision`。版本冲突返回 `409 CONTENT_VERSION_CONFLICT`，前端提示刷新/比较，不能无声覆盖他人修改。
- 包含受控社群入口、私密联系方式、会话或审核数据的响应设置 `Cache-Control: no-store`；公开详情可按后续实现设置安全的短缓存，但隐藏内容不得被旧缓存继续暴露。

### 3.5 认证与授权基线

公开 API 不需要普通学生账号。合作方和管理员使用同一受限会话契约：`POST /api/v1/auth/session`、`DELETE /api/v1/auth/session`、`GET /api/v1/auth/me`。推荐采用同源 `HttpOnly`、`Secure`、`SameSite=Lax`（或更严格）的会话 Cookie，并配合 CSRF 防护；前端不得把长期访问 token 写入 `localStorage` 或公开日志。

身份供应商、初始激活链接、密码重置、邮件/SMS/第三方登录和生产密钥管理不在本批定案。无论最终实现为何，服务端都必须以 Account 的角色和归属做最终授权，不能相信前端传来的 `role`、`partnerProfileId` 或 `ownerId`。

## 4. 共享业务 DTO

### 4.1 内容生命周期与公开投影

内部管理 DTO 必须能表达下列状态；公开 DTO 不返回待审核、被拒原因、逻辑删除原因或内部审核意见。

```ts
type WorkflowStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'CLOSED'
  | 'REJECTED'

type Visibility = 'PUBLIC' | 'HIDDEN'
type RecordState = 'ACTIVE' | 'ARCHIVED' | 'DELETED'
type QualificationStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'REVOKED'

interface InternalLifecycle {
  workflowStatus: WorkflowStatus
  visibility: Visibility
  recordState: RecordState
  publishedAt: IsoDateTime | null
  closedAt: IsoDateTime | null
  revision: number
}

interface PublicLifecycle {
  displayStatus: 'OPEN' | 'CLOSED' | 'ONGOING' | 'UPDATED'
  publishedAt: IsoDateTime
  updatedAt: IsoDateTime
}
```

公开查询只返回符合 S-04 公开规则的记录：通常为 `PUBLISHED + PUBLIC + ACTIVE`，以及三天公开期内的 `CLOSED + PUBLIC` 竞赛/招募。隐藏、待审、已拒绝和删除记录一律不因猜到 ID 而泄露存在性。

### 4.2 来源、作者、权利、富文本与媒体

```ts
interface SourceAttribution {
  label: string
  url: string | null
  sourceKind: 'OFFICIAL' | 'ORIGINAL' | 'AUTHORIZED' | 'EXTERNAL_REFERENCE'
}

interface RightsDeclaration {
  rightsSource: 'ORIGINAL' | 'AUTHORIZED' | 'EXTERNAL_REFERENCE'
  readingScope: 'PUBLIC'
  repostPermission: 'NOT_GRANTED' | 'ATTRIBUTION_ALLOWED' | 'CONTACT_REQUIRED'
  adaptationPermission: 'NOT_GRANTED' | 'ATTRIBUTION_ALLOWED' | 'CONTACT_REQUIRED'
  note: string | null
}

interface RichTextDocument {
  format: 'RICH_TEXT_V1'
  document: object // 受白名单节点/属性约束的结构化文档，不接受任意 HTML
}

interface MediaRef {
  id: EntityId
  url: string
  altText: string
  caption: string | null
  width: number | null
  height: number | null
}
```

资源或文章的公开 DTO 必须同时携带可见的 `authorDisplayName`、`sourceAttribution` 和 `rightsDeclaration`。对权利不明的外部内容，`rightsSource` 只能为 `EXTERNAL_REFERENCE`，`repostPermission` 与 `adaptationPermission` 均不得表示平台授予复用许可。

`RichTextDocument` 的具体编辑器实现由模块选择，但存储/传输格式必须版本化、服务端校验并安全渲染。禁止将未经处理的 HTML、脚本、事件属性、危险 URL 或任意嵌入原样返回给浏览器。

### 4.3 公开读取 DTO

下表定义公共页面需要的最小字段。前端可将其映射为更稳定的展示模型，但不能删掉产品所需的来源、语言或状态信息。

| DTO | 最小字段 | 特别边界 |
| --- | --- | --- |
| `PublicCompetitionSummary` | `id`、`title`、`category`、`language`、`registrationEndsAt`、`lifecycle`、`relatedRecruitmentCount`、`updatedAt` | 列表不含完整规则正文或内部审核信息。 |
| `PublicCompetitionDetail` | Summary 全部字段、`organizerLabel`、`officialLinks[]`、`eligibility`、`introduction`、`ruleSummary`、`sourceAttribution`、可选 `body` | 发布前必须存在可核验官方来源；站内摘要不替代官方规则。 |
| `PublicResourceSummary` | `id`、`title`、`resourceType`、`language`、`summary`、`authorDisplayName`、`updatedAt` | 不在列表返回完整富文本或私密来源材料。 |
| `PublicResourceDetail` | Summary、`originalTitle`、`chineseGuide`、`body`、`media[]`、`externalLinks[]`、可选 `relatedCompetition`、`sourceAttribution`、`rightsDeclaration`、`lifecycle` | 非中文内容的中文导读和原始来源是公开前必要条件。 |
| `PublicRecruitmentSummary` | `id`、`title`、`partnerDisplayName`、`language`、`requirementSummary`、可选 `relatedCompetition`、`applicationEndsAt`、`lifecycle` | 不含合作方私密联系人、申请记录或学生资料。 |
| `PublicRecruitmentDetail` | Summary、`body`、`roleRequirements[]`、`collaborationExpectation`、`followUpMode`、`sourceAttribution`、`updatedAt` | `followUpMode` 仅说明“校内社群入口”或“已审核外部渠道”，不直接下发群二维码或私人联系信息。 |
| `PublicPartnerProfile` | `id`、`displayName`、`subjectType`、`publicBio` | 不包含 `affiliationLabel`、账号状态、运营联系方式或共同编辑关系。 |

`resourceType` 取值为 `ARTICLE`、`EXTERNAL_LINK`、`PROJECT`、`VIDEO`、`DOCUMENT`、`EXPERIENCE`。其中 `VIDEO`、`DOCUMENT` 在首发表示外部链接内容，不代表本平台托管文件。

### 4.4 内部内容候选 DTO

合作方和管理员使用带判别字段的 `ContentCandidate`。该对象服务于编辑和审核，而不是公开页面的万能 DTO。

```ts
type ContentKind = 'COMPETITION' | 'RECRUITMENT' | 'RESOURCE'

interface ContentCandidateBase {
  id: EntityId
  kind: ContentKind
  title: string
  language: ContentLanguage
  originalTitle: string | null
  chineseGuide: string | null
  summary: string | null
  body: RichTextDocument | null
  media: MediaRef[]
  sourceAttribution: SourceAttribution
  authorDisplayName: string | null
  rightsDeclaration: RightsDeclaration | null
  lifecycle: InternalLifecycle
  ownerPartnerProfileId: EntityId | null
  createdByAccountId: EntityId
  lastModifiedByAccountId: EntityId
}
```

- `COMPETITION` 额外包含分类、主办/来源、报名/比赛关键时间、资格信息、官方链接；
- `RECRUITMENT` 额外包含角色/能力要求、截止时间、可选关联竞赛、后续入口模式；其 `ownerPartnerProfileId` 是合作方归属，合作方创建时由服务端从会话确定；
- `RESOURCE` 额外包含资源类型、外部链接、可选关联竞赛；发布前按资源形态校验中文导读、权利和来源；
- 管理员迁移代录必须额外携带只在内部可见的 `migrationRecord`：来源、实际归属/来源主体、代录原因、操作人和时间。它不能被合作方伪造或作为公开字段返回。

## 5. 公开读取端点

| 方法与路径 | 查询/输入 | 成功返回 | 规则 |
| --- | --- | --- | --- |
| `GET /api/v1/public/home` | 无；服务端固定小数量 | `HomeFeed`：近期竞赛、团队招募、学习资源三个数组 | 仅作首页读取聚合，不创建第四种领域对象；三个数组独立为空或失败可解释，前端展示顺序按 S-05 第 5.1 节。 |
| `GET /api/v1/public/competitions` | `category`、`status`、`language`、`cursor`、`pageSize` | `PublicCompetitionSummary[]` | 默认按公开报名状态与截止紧迫度；首发不承诺全文搜索或热度。 |
| `GET /api/v1/public/competitions/{id}` | 公开 `id` | `PublicCompetitionDetail` | 隐藏/不存在统一 404。 |
| `GET /api/v1/public/competitions/{id}/recruitments` | `cursor`、`pageSize` | `PublicRecruitmentSummary[]` | 仅返回该竞赛关联且当前可公开的招募；关联缺失不影响两类内容独立发布。 |
| `GET /api/v1/public/resources` | `resourceType`、`language`、`cursor`、`pageSize` | `PublicResourceSummary[]` | 首发不预设大型主题树或跨语言搜索。 |
| `GET /api/v1/public/resources/{id}` | 公开 `id` | `PublicResourceDetail` | 公开阅读无需账户，来源/权利/语言信息必有。 |
| `GET /api/v1/public/recruitments` | `relatedCompetitionId`、`status`、`language`、`cursor`、`pageSize` | `PublicRecruitmentSummary[]` | 只含已核验合作方发布的团队招募，不含学生找队帖。 |
| `GET /api/v1/public/recruitments/{id}` | 公开 `id` | `PublicRecruitmentDetail` | 不下发私密联系方式、群二维码或站内申请记录。 |

首页聚合端点是为稳定首页排序、减少三次读取耦合而设的读模型；前端仍将三个数组分别映射、分别渲染状态。若服务端不能独立报告某一分区错误，前端须在模块契约层补足可解释状态，不能把全部首页伪装成成功。

## 6. 公开写入与受控社群端点

### 6.1 内容投稿/反馈

`POST /api/v1/public/submissions` 接收公开访客的候选内容或反馈，必须携带 `Idempotency-Key`。它只创建 `ContentSubmission` 或内部反馈记录，绝不直接创建公开 Competition、Resource 或 Recruitment。

```ts
type PublicSubmissionKind =
  | 'COMPETITION_LEAD'
  | 'RESOURCE'
  | 'ARTICLE_OR_EXPERIENCE'
  | 'PROJECT'
  | 'GENERAL_FEEDBACK'
  | 'RIGHTS_REPORT'

interface CreatePublicSubmissionRequest {
  kind: PublicSubmissionKind
  title: string | null
  language: ContentLanguage | null
  originalTitle: string | null
  summary: string
  imageDescription: string | null // 可描述图片信息，但不接收文件或站内 MediaRef
  sourceUrl: string | null
  body: RichTextDocument | null // 公开投稿不得内嵌二进制或本站 MediaRef
  authorDisplayName: string | null
  privateContact: {
    channel: 'EMAIL' | 'PHONE' | 'WECHAT' | 'QQ' | 'OTHER'
    value: string
  } | null
  privacyAcknowledgement: true
  rightsTarget: { contentKind: ContentKind; contentId: EntityId } | null
}
```

`privateContact` 只在确有审核/必要沟通目的时收集，永不出现在成功响应、公开 DTO、错误回显或通用日志中。`RIGHTS_REPORT` 可用 `rightsTarget` 指向公开内容；它仍进入受控处理，而不是直接删除内容。公开访客不得调用图片上传端点；其投稿只接收文本、来源/外部链接和可选图片说明。经审核需要站内图片时，由管理员在受限候选内容编辑器补入或请求补充，不以缺图阻止贡献。

成功返回只包含 `receiptId`、`outcome: "ACCEPTED" | "DUPLICATE"`、安全的中文下一步和 `submittedAt`；不创建公开投稿进度页，也不让无账号用户查询其他人的记录。

### 6.2 合作方申请

`POST /api/v1/public/partner-applications` 同样必须携带 `Idempotency-Key`。请求包含申请主体类型、公开展示名称候选、能力/资源说明、意向内容类型、核验所需私密联系方式和隐私确认。响应只确认“已进入人工核验”，不得自动创建 Account、PartnerProfile 或发布权限。

申请批准后，管理员创建一对一的受限 `Account` 与 `PartnerProfile`，再通过一次性、短时激活链接或等价受控人工交付方式完成首次登录。具体邮件、短信、身份供应商、生产凭据和交付渠道由 S-08 决定；公开申请接口和成功响应均不暴露这些信息。

### 6.3 校内社群入口

`POST /api/v1/public/community-gate/verify` 是低门槛验证请求，使用短期匿名会话和 `Idempotency-Key`。请求体只含当前问题的回答与匿名会话关联；服务端不得在业务数据或异常日志保存完整答案。

响应 `data.result` 仅可为：

- `PASSED`：返回短时、不可缓存的 `entryUrl` 与 `expiresAt`；前端不得将其写入静态内容、历史记录或日志；
- `INCORRECT`：中文提示可再次尝试，不透露答案；
- `COOLDOWN`：中文提示稍后重试，可给出可读结束时间，但不展示完整风控阈值；
- `RATE_LIMITED`：中文提示“请稍后再试”，不暴露精确规则；
- `RETRYABLE_ERROR`：明确本次未确认成功，可安全重试且不计入答题错误。

同一匿名会话的 24 小时内第 8 次答案不匹配后冷却 30 分钟，网络/服务异常和用户安全重试不计错；短时高频限制的精确阈值不对外暴露。相关最小事件和清理期限严格按 S-04/DR-002 执行。

## 7. 受限会话、合作方与管理员端点

### 7.1 受限会话

| 方法与路径 | 成功返回 | 权限/边界 |
| --- | --- | --- |
| `POST /api/v1/auth/session` | 当前受限会话摘要 | 仅已获批 Account；认证失败不得透露账号是否存在。 |
| `POST /api/v1/auth/activation/consume` | 消费一次性、短时激活凭据并完成首次受限会话/凭据设置 | 仅管理员已创建的 Account 可用；凭据单次使用、过期失效且不得写入公开页面、通用日志或缓存。 |
| `DELETE /api/v1/auth/session` | 无内容或成功信封 | 只注销当前会话。 |
| `GET /api/v1/auth/me` | `accountId`、角色、账号状态、合作方时的本人 PartnerProfile 摘要 | 不返回私密联系人、其他账号或后台备注。 |

### 7.2 合作方内容管理

| 方法与路径 | 用途 | 必须的服务端限制 |
| --- | --- | --- |
| `GET /api/v1/partner/dashboard` | 本人公开计数、当前配额、候选内容摘要、需处理审核结论 | 只返回会话对应 PartnerProfile；不能用前端参数改归属。 |
| `GET /api/v1/partner/content-items` | 分页读取本人候选内容 | 可查看草稿/待审/退回/本人已公开记录，但不返回他人内容。 |
| `POST /api/v1/partner/content-items` | 创建 `COMPETITION`、`RECRUITMENT` 或 `RESOURCE` 草稿 | 服务器从会话绑定 `ownerPartnerProfileId`；创建不占活跃公开配额。 |
| `GET` / `PATCH /api/v1/partner/content-items/{id}` | 读取或更新本人可编辑候选内容 | 必须校验归属和 `revision`；合作方修改已公开内容后回到审核链路。 |
| `POST /api/v1/partner/content-items/{id}/submit-review` | 将可提交候选内容送审 | 必须进行类型完整性检查、来源/语言/权利检查及幂等保护；不能直接改为 PUBLIC。 |

合作方不能调用归属转交、配额配置、直接发布、直接隐藏、管理员审计或其他合作方的私密资料端点。共享编辑、组织成员关系和公开组织主页不在此 API 版本中出现。

### 7.3 管理员审核与运营

| 方法与路径 | 用途 | 最小审计要求 |
| --- | --- | --- |
| `GET /api/v1/admin/review-queue` | 读取投稿、合作方申请和内容候选审核队列 | 限职责角色；私密材料按最小可见原则返回。 |
| `POST /api/v1/admin/reviews/{id}/decision` | `APPROVE`、`REQUEST_CHANGES` 或 `REJECT` | 记录审核人、对象/版本、结论、必要说明、时间。 |
| `POST /api/v1/admin/content-items/{id}/publish` | 将已满足条件的内容公开 | 发布前再次检查来源、语言、权利、完整性和 PartnerProfile 活跃公开配额。 |
| `POST /api/v1/admin/content-items/{id}/hide`、`/archive`、`/restore` | 按 S-04 处理可见性/存放状态 | 记录操作人、前后状态、原因和时间；物理删除不作为常规操作。 |
| `POST /api/v1/admin/content-items` | 首批迁移期代录或管理员自有内容录入 | 代录必填 `migrationRecord`；不因管理员身份绕过来源、权利、审核/审计和生命周期。 |
| `POST /api/v1/admin/content-ownership-transfers` | 经核验后转交 PartnerProfile 归属 | 记录原归属、新归属、操作人、原因和时间。 |
| `PATCH /api/v1/admin/partner-profiles/{id}/content-quota` | 设置 0–5 的例外配额 | 记录旧值、新值、原因、操作人、时间；超过 5 必须先有新 DR/明确运营决定。 |
| `POST /api/v1/admin/partner-applications/{id}/decision` | 批准/拒绝合作方申请并触发受控账号开通流程 | 账号开通与申请批准可关联，但不等于公开注册；私密材料遵守留存。 |

管理员编辑管理员创建的草稿或公开内容可直接生效，但仍须记录创建人、最后修改人、时间和前后状态摘要。`APPROVE` 与 `PUBLISH` 在 API 上保留为不同动作，避免把审核通过偷偷等同于公开发布。

## 8. 图片上传与富文本安全

只有已认证的合作方和管理员能在候选内容编辑器使用 `POST /api/v1/media/uploads`（`multipart/form-data`）上传候选图片，或使用同等安全的两步上传意图/完成契约。公开访客调用该端点必须被拒绝。成功只返回处理后的 `MediaRef`；图片二进制不进入 MySQL 正文、不放入公开内容 JSON，也不通过任意文件接口提供。

服务端必须在“可引用到正文”前完成：真实类型检测、JPEG/PNG/WebP 白名单、单张不超过 5 MB、每篇最多 10 张、每篇总量不超过 20 MB、尺寸限制、去除不必要的 EXIF 位置元数据、安全存储和可安全渲染状态。SVG、视频、压缩包、可执行文件、任意文档和未知 MIME 类型默认拒绝。外部视频、文档、网盘和项目链接只作为经过审核的外部链接保存。

媒体存储供应商、病毒扫描/异步处理实现、备份、恢复、删除、过期清理与生产 CDN 规则由 S-08 决定。S-06 只冻结“上传后得到媒体引用、公开读取不带二进制、正文不接收危险 HTML”的数据边界。

## 9. 隐私、权限与错误码边界

### 9.1 绝不进入公开响应或前端 Mock 的字段

- 私人微信、QQ、手机号、私人邮箱、群二维码和受控社群原始入口；
- 投稿/合作方申请的私密联系方式、证明材料、完整未审核正文；
- Account 凭据、会话 token、密码摘要、账号恢复材料；
- `affiliationLabel`、内部审核意见、AuditLog 详情、完整匿名答案、原始 IP 或网络标识；
- 其他合作方的私密资料、学生报名/简历/聊天/面试记录。

### 9.2 稳定错误语义

| HTTP | 稳定错误码示例 | 前端行为 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 显示通用中文请求错误，不显示内部详情。 |
| 401 | `AUTHENTICATION_REQUIRED` | 仅受限页面引导重新登录；公开页面不把它转成学生注册。 |
| 403 | `FORBIDDEN` | 显示无权限，不泄露资源归属或私密字段。 |
| 404 | `NOT_FOUND` | 对不存在、隐藏或无权公开内容使用同一安全表现。 |
| 409 | `IDEMPOTENCY_KEY_REUSED`、`CONTENT_VERSION_CONFLICT`、`ACTIVE_CONTENT_QUOTA_EXCEEDED` | 提示复用结果、刷新比较或联系管理员；不静默覆盖。 |
| 422 | `VALIDATION_FAILED`、`PUBLISH_REQUIREMENT_MISSING`、`MEDIA_REJECTED` | 显示字段级中文可修正提示；不能回显私密值。 |
| 429 | `COMMUNITY_COOLDOWN`、`RATE_LIMITED` | 按 DR-002 显示安全重试提示，不暴露精确限制细节。 |
| 500/503 | `TEMPORARY_UNAVAILABLE` | 明确未确认成功、允许复用幂等键安全重试。 |

错误消息的最终中文文案由前端统一映射；服务端可以提供稳定语义码和有限字段路径，但不应把数据库异常、堆栈、SQL、完整请求正文或密钥返回给浏览器。

## 10. 前端 Service 与 Mock 迁移规则

前端的展示组件、路由和筛选控件不能直接 `fetch` 或导入 fixture。建议层次为：

```text
Page / Feature
  → PublicContentService | PartnerContentService | AdminReviewService
  → Mock 实现或 HTTP 实现
  → HTTP DTO Mapper
  → 稳定的前端展示模型
```

旧 `CompetitionService` 的“列表/详情/官方链接”可以迁移到 `PublicCompetitionService`；旧 `TeamService` 不能原样保留，因为它含学生找队联合类型，应拆除或改造为 `PublicRecruitmentService`。新增 `PublicResourceService`、`PublicSubmissionService`、`PartnerApplicationService` 和 `CommunityGateService`。Mock 必须覆盖：公开/隐藏边界、资源外语导读、来源/权利缺口、到期三天、配额冲突、重复提交、网络失败、社群冷却、空列表和 404；不得以 Mock 伪造学生账户或私人联系方式。

## 11. 第 3 批已批准的答辩结论

以下结论已由项目负责人于 2026-08-30 确认，并随本规范在第 3 批整体批准后成为 docs/main 的正式基线。后续变更必须遵循 S-01 的版本与决策流程。

| ID | 已批准结论 | 实施约束 | 影响 |
| --- | --- | --- | --- |
| S06-O1 | R1/R2 只允许已认证合作方和管理员在候选内容编辑器直接上传受限图片；公开访客投稿只提交文本、链接和图片说明。 | 匿名请求不得调用媒体上传端点；未来如需匿名图片上传，必须先以 DR 明确滥用、防病毒、费用和删除责任。 | 投稿体验、媒体安全、成本、审核和 S-08。 |
| S06-O2 | 合作方申请批准后，由管理员创建受限 Account 与 PartnerProfile，并通过一次性、短时激活链接或等价受控人工交付完成首次登录。 | 邮件/短信/身份供应商、生产凭据与实际交付方式由 S-08 决定，不在页面或公开 API 暴露。 | 会话、运营流程、外部服务、成本。 |
| S06-O3 | 首发公开详情 URL 使用稳定不透明 `id`，不引入随标题变化的 `slug`。 | 如运营以后确有 SEO/可读链接需求，先新增 `slug` 与重定向策略，并通过 DR 评估迁移。 | 路由、公开链接、数据迁移。 |

S05-O1 已裁决公开名称为“团队招募”、规范路径为 `/recruitments`；本文件中的公开 API 路径与该结论一致。

## 12. 批准状态与维护条件

本规范已于 2026-08-30 通过第 3 批整体批准。以下条件是该次批准时已满足的基线要求，也是后续维护时必须继续保持的约束：

1. 第 11 节答辩结论已全部回填，并已与 S-05 的路由、名称和公开入口结论复核一致；
2. 公开读取、公开提交、社群验证、合作方编辑和管理员审核都能映射到 S-03/S-04 的用户、权限、状态和留存边界；
3. 不存在把私密字段、学生账户、聊天/报名数据或媒体二进制偷渡进公开 DTO 的捷径；
4. Mock/HTTP 替换、字段校验、幂等、并发冲突、错误码和版本演进已有可测试的书面定义；
5. 本规范现在可作为 B-001 或后续功能模块 M0 开工卡的依据；任何代码、建库或外部服务动作仍须遵循 S-02 的模块开工、验证与明确批准流程。
