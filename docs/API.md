# AIGC Studio API 接口文档

> **2026-11-05 账号模型变更**：两个管理后台（服务端/客户端）均仅允许 `SUPER_ADMIN` 登录；`ORG_ADMIN_{orgId}` 改为“组织创作角色”，账号 `role=user`，只能登录创作平台；`NORMAL_ADMIN_*` 废弃，不再创建；创建组织新增 `adminEmail`，组织创作角色默认无任何权限；新增 `GET /api/permissions?scope=feature`、`GET /api/roles/{id}/permission-snapshot`、带 `expectedVersion` 的角色权限保存、`GET /api/internal/organizations`。本文中涉及旧 `ORG_ADMIN` / `NORMAL_ADMIN` 的章节以 [`docs/frontend-rbac-confirmation.md`](frontend-rbac-confirmation.md) 为准。

> 适用范围：本仓库 Java 服务（Spring Boot 2.6 / Java 8）。
> 文档基于当前 `main` 分支代码整理，覆盖 Java 归属的全部 `/api/**` 接口与 `/admin` 静态入口。
> Node 归属能力（积分、计费、生成执行、任务、文件、模型目录等）不在本文档范围内，Java 侧统一返回 `503`，见 [第 14 节](#14-node-归属占位接口)。

---

## 目录

1. [概述](#1-概述)
2. [认证与会话](#2-认证与会话)
3. [管理端：创作者账号](#3-管理端创作者账号)
4. [功能权限（25 项 feature）](#4-功能权限25-项-feature)
5. [组织管理](#5-组织管理)
6. [角色与权限码](#6-角色与权限码)
7. [模型连接与模型配置](#7-模型连接与模型配置)
8. [Agent 配置](#8-agent-配置)
9. [工作流配置](#9-工作流配置)
10. [系统设置与 Skill](#10-系统设置与-skill)
11. [项目](#11-项目)
12. [旧版用户管理（已废弃）](#12-旧版用户管理已废弃)
13. [内部接口](#13-内部接口)
14. [Node 归属占位接口](#14-node-归属占位接口)
15. [公共数据结构](#15-公共数据结构)
16. [附录](#16-附录)

---

## 1. 概述

### 1.1 Base URL

| 环境 | 地址 |
|---|---|
| 本地默认 | `http://localhost:8089` |
| 生产 | 由网关/反向代理决定，接口路径不变 |

管理后台静态页：`GET /admin` → 302 重定向到 `/admin/login.html`。

### 1.2 认证

除公开接口外，请求需携带 JWT：

```http
Authorization: Bearer <token>
```

Token 由登录/注册接口返回，载荷包含：

| Claim | 说明 |
|---|---|
| `sub` | 用户名（创作者为 email） |
| `userId` | 用户 ID |
| `orgId` | 组织 ID |
| `role` | `admin` / `user` |
| `tokenVersion` | 会话版本，与 `t_user.token_version` 不一致时立即失效 |
| `iat` / `exp` | 签发/过期时间（默认 7 天） |

会话撤销规则：

- 改密、重置密码、禁用、软删除、修改邮箱、退出登录都会递增 `token_version`，旧 token 立即 `401`。
- 改密（`PUT /api/users/{id}/password`）必须先校验 `oldPassword`，校验失败不写库、不撤销会话；管理员通过 `/reset-password` 或 `PATCH /api/admin/users/{id}` 修改密码属于强制覆盖，不需要旧密码。
- 角色权限变更、权限码启停会在事务提交后失效相关用户的权限缓存。
- 退出登录会同时登出该账号的其他会话（当前为单会话模型）。
- 账号 `status < 1`（禁用/删除）时，即使 token 未过期也立即失效。

`dev.bypass-token` 仅用于本地开发：把该值作为 Bearer token 可直接获得 `role=admin` 身份；生产环境必须留空。

### 1.3 响应约定

**成功响应**：绝大多数接口直接返回业务 JSON（对象/数组），不包 `code/message/data` 外壳。

**失败响应**：统一为 `ApiResponse`：

```json
{
  "code": 400,
  "message": "参数验证失败",
  "data": { "email": "must be a well-formed email address" },
  "timestamp": 1793674181000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | integer | HTTP 状态码语义 |
| `message` | string | 可直接展示的错误信息 |
| `data` | any | 可选，参数校验错误时为 `{字段: 错误}` |
| `timestamp` | long | 服务器毫秒时间戳 |

请求体统一使用 DTO + Bean Validation 校验。字段缺失、类型错误或超长时返回 `400`：类型/JSON 语法错误为 `message="请求体格式错误"`，约束不满足为 `message="参数验证失败"` 且 `data` 给出字段级错误。

少数接口返回 `ApiResponse` 作为成功体，例如删除组织返回 `{"code":200,"message":"组织已成功删除","timestamp":...}`。

### 1.4 分页

项目内存在三种分页结构，按接口区分：

**A. `PageResponse<T>`**（组织、旧版用户列表）

```json
{
  "items": [],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "empty": true
}
```

**B. `Page<T>`（Spring Data）**（项目、工作流的分页模式）

```json
{
  "content": [],
  "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20,
  "numberOfElements": 0,
  "first": true,
  "last": true,
  "empty": true
}
```

**C. 管理端用户列表专用**

```json
{ "items": [], "total": 0, "page": 0, "pageSize": 20 }
```

说明：

- `page` 一律从 `0` 开始。
- `GET /api/projects`、`GET /api/admin/workflows` 的 `page`/`size` 均为可选：**不传时保持原数组响应**，传入后返回 Spring `Page`。

### 1.5 时间与枚举

- `ContractUserDto.created_at`、`AdminUserListItemDto.created_at/last_login_at` 使用 ISO-8601 UTC 字符串，例如 `2026-11-03T10:49:13Z`。数据库存的是服务端本地时区（JVM `systemDefault`）的墙上时间，返回前会先按本地时区解释再转换为 UTC，不会把本地时间直接标成 `Z`。
- 其余实体（`Organization`、`Project`、`StudioSkill`、`ModelConnectionCheck` 等）的 `LocalDateTime` 字段按 Jackson 默认格式输出，例如 `2026-11-03T10:49:13`。
- 用户状态：内部 `1=active`、`0=disabled`、`-1=deleted`；对外统一 `active` / `disabled` / `deleted`。
- 用户角色：`admin`（管理端） / `user`（创作者）。
- 角色编码约定：
  - `SUPER_ADMIN`：全局超级管理员（`org_id = NULL`）
  - `ORG_ADMIN_{orgId}`：组织管理员
  - `NORMAL_ADMIN_{orgId}`：普通管理员
  - `USER_FEATURES_{userId}`：创作者个人功能权限载体，不作为业务角色展示

### 1.6 权限模型

| 层级 | 规则 |
|---|---|
| Spring Security | `anyRequest().authenticated()`；公开路径见下表 |
| 管理端拦截器 | `/api/admin/**` 要求 JWT `role=admin`（`/api/admin/login` 除外），否则 `403` |
| 方法级权限 | `@RequirePermission("xxx")` 校验会话权限码；缺失返回 `403` |
| 超级管理员专属 | `@SuperAdminOnly` 标注的接口仅 `SUPER_ADMIN` 可访问，其余管理员返回 `403`。当前包括：组织创建/修改/删除。模型连接与模型配置、Agent/工作流/Skill 管理端接口、系统设置、权限码启停对管理员（含组织管理员）开放 |
| 数据范围 | 超级管理员不受组织限制，可查看所有组织的数据；组织管理员只能访问 `org_id = 当前账号 orgId` 的数据，跨组织读写按 `404`（资源不存在）或 `403`（超级管理员专属）处理 |

**初始化不变量**（Flyway 种子数据 + 启动时 `RbacInitializer` 幂等补齐）：

- 创建默认组织 `DEFAULT_ORG` / “默认组织”，作为平台级账号的归属组织；
- 创建全局超级管理员角色 `SUPER_ADMIN`（`org_id = NULL`），并分配**所有启用中**的权限码（含后续新增权限，启动时会自动补齐）；
- 创建默认超级管理员账号 `admin / admin123`（仅在账号不存在时创建，不会重置已有密码），并绑定 `SUPER_ADMIN`；
- 为每个组织补齐内置的 `ORG_ADMIN_{orgId}` / `NORMAL_ADMIN_{orgId}` 角色（缺失时创建，已有角色不重置授权）。

**完全公开（无需 token）的路径**：

| 方法 | 路径 |
|---|---|
| POST | `/api/admin/login`、`/api/auth/login`、`/api/auth/register` |
| GET | `/api/public/settings` |
| GET | `/api/studio-skills` |
| GET | `/api/models`、`/api/published-models`、`/api/canvas/models`、`/api/canvas/identity`、`/api/agent/canvas/config` |
| GET | `/api/generated/*`、`/api/reference-input/*`、`/api/digital-human-media/*`（由签名参数自行鉴权，当前 Java 侧返回 503） |
| GET | `/api/internal/node-config`（控制器内用独立 `NODE_CONFIG_READ_TOKEN` 鉴权） |
| ALL | `/api/auth/**`（Security 层放行，但 `me/permissions` 等控制器内部仍要求登录态） |
| ALL | `/admin`、`/admin/**`、`/favicon.ico` |

### 1.7 通用状态码

| HTTP | 场景 |
|---|---|
| `200` | 成功 |
| `400` | 参数校验失败、业务参数非法、请求体格式错误 |
| `401` | 未登录、token 无效/过期/已撤销、账号禁用或删除、密码错误 |
| `403` | 非管理员访问 `/api/admin/**`、缺少权限码、注册已关闭 |
| `404` | 资源不存在（含跨组织访问） |
| `405` | 请求方法不支持 |
| `409` | 唯一键冲突、权限版本冲突、连接被模型引用 |
| `415` | Content-Type 不支持 |
| `500` | 服务器内部错误（不返回堆栈细节） |
| `503` | Node 归属能力、内部配置读取未启用 |

---

## 2. 认证与会话

### 2.1 管理员登录

`POST /api/admin/login` · 公开

请求：

```json
{ "username": "admin", "password": "admin123" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | 是 | 管理员用户名 |
| `password` | string | 是 | 密码 |

响应 `200`：

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "user": {
    "id": 1,
    "name": "超级管理员",
    "email": "admin@example.invalid",
    "role": "admin",
    "status": "active",
    "created_at": "2026-11-03T10:49:13Z",
    "token_version": 0,
    "features": { "text-studio": false, "...": false },
    "username": "admin",
    "realName": "超级管理员",
    "orgId": 1,
    "orgName": "默认组织",
    "firstLogin": false,
    "roles": ["SUPER_ADMIN"],
    "permissions": ["user:view", "user:create", "..."]
  }
}
```

错误：`401` 用户名或密码错误 / 非管理员账号 / 账号已被禁用或删除。

> 登录型管理员由 `t_user.role=admin` 判定。只有绑定 `SUPER_ADMIN` 或 `ORG_ADMIN_*` 角色时系统才会自动同步 `role=admin`；`NORMAL_ADMIN_*` 与用户自建角色只增加权限集合，账号仍为 `user`，不能登录管理端。组织管理员与超级管理员都走本入口。

### 2.2 管理员当前会话

`GET /api/admin/me` · 管理员

响应 `200`：`ContractUserDto`（结构同上 `user`）。

错误：普通账号访问返回 `403`。

### 2.3 创作者登录

`POST /api/auth/login` · 公开

请求：

```json
{ "email": "creator@example.com", "password": "secret123" }
```

响应 `200`：`{ "token": "...", "user": ContractUserDto }`，`user.features` 为完整 25 项快照。

错误：`401` 邮箱或密码错误 / 非创作者账号 / 账号已被禁用或删除。

### 2.4 创作者注册

`POST /api/auth/register` · 公开（受平台注册开关控制）

请求：

```json
{ "name": "创作者", "email": "creator@example.com", "password": "secret123" }
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `name` | string | 是 | ≤ 80 |
| `email` | string | 是 | 合法邮箱，≤ 50，全局唯一 |
| `password` | string | 是 | 6–256 |

行为：

- `allowRegistration=false` 时直接 `403 平台已关闭注册`，不写库。
- 注册成功默认 **25 项 feature 全 false**；请求体不接收 `features`。
- `username` 自动设为 email，`orgId=0`，`accountSource=自助注册`。
- 返回与登录相同的 `{token, user}`，可直接进入会话。

错误：`403` 注册已关闭；`409` 邮箱已注册；`400` 参数校验失败。

### 2.5 当前会话用户

`GET /api/auth/me` · 需登录

响应 `200`：`ContractUserDto`，包含 `token_version`、`features`、`roles`、`permissions`（管理员）。

### 2.6 旧版会话用户

`GET /api/auth/userinfo` · 需登录 · 兼容旧管理后台

与 `/api/auth/me` 返回完全一致，保留给 `page.js` 等旧前端。

### 2.7 当前账号功能权限

`GET /api/auth/permissions` · 需登录

响应 `200`：

```json
{ "features": { "text-studio": true, "script": false, "...": false } }
```

### 2.8 退出登录

`POST /api/auth/logout` · 可选 token

行为：

- 有 `Authorization: Bearer <token>` 时：加入内存黑名单 + 递增该账号 `token_version` + 清理会话缓存。
- 无 token 时也返回成功（幂等）。
- 成功后旧 token 立即 `401`，同账号其他会话一并失效。

响应 `200`：

```json
{ "ok": true }
```

---

## 3. 管理端：创作者账号

> 所有接口要求 `role=admin`，并额外校验括号中的 `user:*` 权限码。
> 仅能操作 `role=user` 的创作者账号；管理员账号返回 `400 仅可操作创作者账号`。
> 数据范围：超级管理员可查看/管理全部组织的创作者；组织管理员只能看到并管理本组织的创作者，跨组织按 `404` 处理。

### 3.1 创作者列表

`GET /api/admin/users` · `user:view`

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | int | 0 | 0 基页码，负数按 0 |
| `pageSize` | int | 20 | 有效 1–100，越界/非正数按 20 |
| `keyword` | string | — | 模糊匹配 `name/realName/email/username/phone`（不区分大小写） |
| `status` | string | — | `active` / `disabled` / `deleted` / `all`；缺省排除已删除 |
| `orgId` | int | — | 组织筛选；仅超级管理员生效，组织管理员传入会被忽略并强制本组织 |

响应 `200`：

```json
{
  "items": [
    {
      "id": 2,
      "name": "创作者",
      "email": "creator@example.com",
      "username": "creator@example.com",
      "realName": null,
      "phone": null,
      "role": "user",
      "status": "active",
      "created_at": "2026-11-03T10:49:13Z",
      "last_login_at": "2026-11-03T10:50:00Z",
      "orgId": 0,
      "orgName": null,
      "roleIds": [],
      "roleNames": [],
      "features": { "text-studio": false, "...": false }
    }
  ],
  "total": 1,
  "page": 0,
  "pageSize": 20
}
```

说明：

- 列表项不含 `password` / `permissions`。
- `roleIds/roleNames` 已排除个人角色 `USER_FEATURES_*`。
- 每页批量补齐组织名、角色、feature，避免 N+1。
- 非法 `status` 返回 `400`。
- 超级管理员返回全部组织的账号（`orgId/orgName` 用于区分）；组织管理员只返回本组织账号。

### 3.2 创建创作者

`POST /api/admin/users` · `user:create`

请求：

```json
{
  "name": "创作者",
  "email": "creator@example.com",
  "password": "secret123",
  "features": { "script": true, "textToImage": true },
  "orgId": 2
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `name` | string | 是 | ≤ 80 |
| `email` | string | 是 | 合法邮箱，≤ 50，全局唯一；同时作为 `username` |
| `password` | string | 是 | 6–256 |
| `features` | object | 否 | 已知 25 项 feature 的布尔值；省略时全 false |
| `orgId` | int | 否 | 目标组织；仅超级管理员生效（按组织筛选后创建），组织管理员传入会被忽略 |

响应 `200`：`ContractUserDto`（`id`/`name`/`email`/`role`/`status`/`created_at`，不含密码、角色与 feature 明细）。

行为：默认 `orgId` 取调用方所属组织，并建立个人 feature 角色；超级管理员可传 `orgId` 把创作者建到指定组织。组织管理员创建的创作者只在本组织可见。

错误：`409` 邮箱已存在；`400` feature 名称未知或值非布尔、组织不存在。

### 3.3 修改创作者

`PATCH /api/admin/users/{id}` · `user:update`

请求（未传字段沿用旧值）：

```json
{
  "name": "新名字",
  "email": "new@example.com",
  "phone": "13800000000",
  "status": "active",
  "password": "newpass123"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | ≤ 80 |
| `email` | string | 合法邮箱，≤ 50；变更时同步 `username` 并撤销会话 |
| `phone` | string | ≤ 32 |
| `status` | string | `active` / `disabled`；修改后撤销会话 |
| `password` | string | 空串不修改；非空至少 6 位；修改后撤销会话 |

行为：邮箱、状态、密码任一变更都会递增 `token_version`；接口始终清理会话缓存。

> 管理员在此修改 `password` 属于强制覆盖，不需要旧密码；账号本人改密请使用 `PUT /api/users/{id}/password`（必须携带 `oldPassword`）。

响应 `200`：`ContractUserDto`。

错误：`404` 用户不存在或不在当前数据范围；`400` 仅可操作创作者账号 / 状态非法 / 密码过短；`409` 邮箱已存在。

### 3.4 启用/禁用创作者

`PATCH /api/admin/users/{id}/status` · `user:update`

请求：

```json
{ "status": "disabled" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | int | 否 | 兼容旧客户端；若传入且与路径不一致返回 `400` |
| `status` | string | 是 | `active` / `disabled` |

行为：始终递增 `token_version` 并清理会话缓存，旧 token 立即失效。

响应 `200`：`ContractUserDto`。

### 3.5 软删除创作者

`DELETE /api/admin/users/{id}` · `user:delete`

行为：`status=-1`、递增 `token_version`、清理会话缓存；保留历史数据，不物理删除。

响应 `200`：空响应体。

---

## 4. 功能权限（25 项 feature）

> 管理端接口要求 `role=admin`，并校验括号中的权限码（读取 `user:view`、写入 `user:update`）。
> 数据范围：超级管理员可操作全部组织账号；组织管理员只能操作本组织账号，跨组织按 `404` 处理，审计记录同样按组织过滤。
> 歌词识别（`lyricRecognition`）已随前端功能下线，不再出现在 feature 快照和写入校验中。
> 管理后台的“权限管理”页与角色授权弹窗只展示后端 RBAC 权限码，不展示这些 feature；feature 仅通过本节接口按账号维护。

### 4.1 读取账号功能权限

`GET /api/admin/users/{id}/permissions`

响应 `200`：

```json
{
  "id": 2,
  "features": { "text-studio": true, "script": false, "...": false },
  "version": "3"
}
```

| 字段 | 说明 |
|---|---|
| `id` | 创作者用户 ID |
| `features` | 25 项布尔快照 |
| `version` | 权限版本（`t_user.permission_version`），用于 `expectedVersions` 乐观锁 |

错误：`404` 账号不存在；`400` 管理员账号不适用 feature 权限。

### 4.2 批量写入功能权限

`PUT /api/admin/user-permissions`

请求：

```json
{
  "userIds": [2, 3],
  "mode": "replace",
  "features": { "script": true, "textToImage": true },
  "expectedVersions": { "2": "3", "3": "1" }
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userIds` | int[] | 是 | 1–500 个，正整数，不可重复 |
| `mode` | string | 是 | `replace` / `grant` / `revoke` |
| `features` | object | 否 | 已知 25 项 feature 布尔值 |
| `expectedVersions` | object | `replace` 必填 | `{userId: 版本字符串}`，任意冲突整批 `409` 不写 |

`mode` 语义：

| mode | 行为 |
|---|---|
| `replace` | 以 `features` 为准整体替换；未传项视为 false |
| `grant` | 仅把值为 `true` 的项打开，其余保持不变 |
| `revoke` | 仅把值为 `true` 的项关闭，其余保持不变 |

响应 `200`：

```json
{
  "ok": true,
  "updated": 2,
  "versions": { "2": "4", "3": "2" }
}
```

行为：

- 每账号写入一条 `permission_audits` 审计。
- 仅递增 `permission_version`，**不**递增 `token_version`，不撤销登录会话；但会清理权限缓存使新权限立即生效。
- 事务原子：任一账号不存在、版本冲突、参数非法都会整批回滚。

错误：`400` 参数非法/管理员账号/已删除账号；`404` 账号不存在；`409` 权限版本冲突。

### 4.3 权限审计记录

`GET /api/admin/permission-audits?userId=2`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | int | 否 | 省略时返回全部账号最近 100 条 |

响应 `200`：数组（按创建时间倒序，最多 100 条）：

```json
[
  {
    "id": "8f1c...",
    "operator_id": 1,
    "user_id": 2,
    "mode": "replace",
    "created_at": "2026-11-03T11:00:00",
    "before": { "script": false },
    "after": { "script": true }
  }
]
```

---

## 5. 组织管理

> 组织生命周期属于平台级操作：**创建 / 修改 / 删除仅超级管理员可执行**（`@SuperAdminOnly`，否则 `403`）。
> 组织管理员只能查看自己的组织（列表只返回 1 条，详情跨组织按 `404`）。

### 5.1 组织列表

`GET /api/organizations` · `org:view`

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | int | 0 | 0 基页码 |
| `size` | int | 10 | 每页条数 |
| `keyword` | string | — | 模糊匹配组织编码/名称 |

响应 `200`：`PageResponse<Organization>`，按 `createTime desc, id desc` 排序。

- 超级管理员：返回全部组织。
- 组织管理员：只返回当前账号所属组织。

### 5.2 组织详情

`GET /api/organizations/{id}` · `org:view`

响应 `200`：`Organization`。

错误：`404` 组织不存在；组织管理员访问其他组织同样返回 `404`。

### 5.3 创建组织

`POST /api/organizations` · `org:create` + **仅超级管理员**

请求：

```json
{
  "orgCode": "DEMO_ORG",
  "orgName": "演示组织",
  "description": "可选描述",
  "adminUsername": "demo_admin",
  "adminPassword": "secret123",
  "adminRealName": "演示管理员"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `orgCode` | string | 是 | ≤ 50，全局唯一 |
| `orgName` | string | 是 | ≤ 100，全局唯一 |
| `description` | string | 否 | ≤ 255 |
| `adminUsername` | string | 是 | 6–50，全局唯一 |
| `adminPassword` | string | 是 | 6–100，BCrypt 存储 |
| `adminRealName` | string | 是 | 同时写入 `name` 与 `realName` |

行为（同一事务）：

1. 校验组织编码/名称/管理员用户名唯一，任一冲突直接 `400`，不会留下半成品组织。
2. 创建 `ORG_ADMIN_{orgId}` 角色并分配默认权限（用户/角色管理 + `org:view`，不包含组织创建/修改/删除）。
3. 创建 `NORMAL_ADMIN_{orgId}` 角色并分配默认权限（`user:view`、`user:update`）。
4. 使用请求中的账号信息创建组织管理员：`role=admin`、`status=1`、`accountSource=组织管理员`，绑定 `ORG_ADMIN_{orgId}`，可直接登录 `/api/admin/login`。

响应 `200`：`Organization`。

错误：`403` 非超级管理员；`400` 组织编码/名称/管理员用户名已存在、密码过短。

### 5.4 修改组织

`PUT /api/organizations/{id}` · `org:update` + **仅超级管理员**

请求（未传字段沿用）：

```json
{ "orgCode": "DEMO_ORG", "orgName": "新名称", "description": "新描述", "status": 1 }
```

响应 `200`：`Organization`。

错误：`403` 非超级管理员；`404` 组织不存在；`400` 编码/名称已存在、默认组织编码不可修改、默认组织不允许禁用。

### 5.5 删除组织

`DELETE /api/organizations/{id}` · `org:delete` + **仅超级管理员**

行为：

- 默认组织 `DEFAULT_ORG` 不允许删除（`400`）。
- 组织内用户改为软删除（`status=-1`）并递增 `token_version`，立即撤销会话。
- 清理用户角色、个人 feature 角色及其权限关联。
- 删除组织角色与权限关联，最后删除组织记录。

响应 `200`：

```json
{ "code": 200, "message": "组织已成功删除", "timestamp": 1793674181000 }
```

错误：`403` 非超级管理员；`404` 组织不存在；`400` 默认组织不允许删除。

---

## 6. 角色与权限码

> 数据范围：超级管理员可查看/维护全部组织的角色（全局 `SUPER_ADMIN` 角色显示 `orgName=全局`）；
> 组织管理员只能查看/维护本组织角色，全局角色、其他组织角色与创作者个人角色 `USER_FEATURES_*` 一律按 `404` 处理。

### 6.1 角色列表

`GET /api/roles` · `role:view`

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `orgId` | int | — | 组织筛选；仅超级管理员生效，组织管理员传入会被忽略并强制本组织 |

响应 `200`：`Role[]`，每个角色内联 `permissions` 数组和 `orgName`。

- 超级管理员：不传 `orgId` 时返回全部组织的业务角色 + 全局 `SUPER_ADMIN`；传 `orgId` 时只返回该组织的角色。
- 组织管理员：仅本组织角色。
- 个人 role（`USER_FEATURES_{userId}`）不作为业务角色返回。

### 6.2 角色详情

`GET /api/roles/{id}` · `role:view`

响应 `200`：`Role`。

错误：`404` 角色不存在或不在当前数据范围。

### 6.3 创建角色

`POST /api/roles` · `role:create`

请求：

```json
{
  "roleCode": "EDITOR",
  "roleName": "编辑",
  "description": "可选描述"
}
```

行为：`orgId` 取自当前会话（超级管理员可用 body 中的 `orgId` 指定组织），`status=1`，`createTime/updateTime` 自动填充。

响应 `200`：`Role`。

错误：`400` 角色编码格式非法（2–50 位字母/数字/下划线/短横线）、系统保留编码（`SUPER_ADMIN`、`ORG_ADMIN_*`、`NORMAL_ADMIN_*`、`USER_FEATURES_*`）、角色编码已存在、角色名称非法。

### 6.4 修改角色

`PUT /api/roles/{id}` · `role:update`

请求：

```json
{ "roleName": "高级编辑", "description": "新描述" }
```

行为：仅更新 `roleName`、`description`（`roleCode`/`orgId` 不可改）。

响应 `200`：`Role`。

错误：`404` 角色不存在或不在当前数据范围；`400` 角色名称非法。

### 6.5 删除角色

`DELETE /api/roles/{id}` · `role:delete`

限制：`SUPER_ADMIN`、`ORG_ADMIN_*`、`NORMAL_ADMIN_*` 不允许删除（`400`）。

行为：删除前捕获受影响用户，删除角色与权限关联，并失效这些用户的会话缓存。

响应 `200`：空响应体。

错误：`404` 角色不存在或不在当前数据范围；`400` 内置角色不可删除。

### 6.6 覆盖角色权限

`POST /api/roles/{id}/permissions` · `role:assign-permission`

请求：权限 ID 数组（空数组表示清空）：

```json
[1, 2, 5]
```

行为：

- ID 自动去空、去重，重复提交幂等。
- 传入不存在的权限 ID 返回 `400`，不会部分写入。
- 事务提交后失效该角色下所有用户的权限缓存。
- 组织管理员只能给本组织角色授权；`SUPER_ADMIN` 角色仅超级管理员可维护。

响应 `200`：空响应体。

错误：`404` 角色不存在或不在当前数据范围。

### 6.7 读取角色权限

`GET /api/roles/{id}/permissions` · `role:view`

响应 `200`：`Permission[]`。

错误：`404` 角色不存在或不在当前数据范围。

### 6.8 权限码列表

`GET /api/permissions` · `role:view`

响应 `200`：`Permission[]`，按 `permissionCode` 升序，包含启用与停用项。

> 仅返回管理后台 RBAC 权限码（`user:*`、`org:*`、`role:*` 等）。创作者 feature 权限不在此列表返回，角色授权弹窗也不会展示，统一通过 [第 4 节](#4-功能权限25-项-feature)的账号权限接口维护。

### 6.9 启停权限码

`PUT /api/permissions/{id}/status?status=1` · `role:update`

| 参数 | 位置 | 说明 |
|---|---|---|
| `id` | path | 权限 ID |
| `status` | query | `0` 停用 / `1` 启用 |

行为：变更后失效全部会话权限缓存。

响应 `200`：`Permission`。

错误：`400` 状态非 0/1 或权限不存在；`403` 缺少 `role:update`。

> 组织管理员默认拥有 `role:update`，因此可以启停权限码；该操作是全局的，会影响所有组织。

### 6.10 内置权限码

| 权限码 | 说明 |
|---|---|
| `user:view` / `user:create` / `user:update` / `user:delete` | 用户查看/创建/更新/删除 |
| `user:assign-role` / `user:reset-password` | 分配角色/重置密码 |
| `org:view` / `org:create` / `org:update` / `org:delete` | 组织管理 |
| `role:view` / `role:create` / `role:update` / `role:delete` / `role:assign-permission` | 角色管理 |
| 25 项 feature 权限码 | 见 [16.1](#161-25-项-feature) |

内置角色默认授权：

| 角色 | 默认权限 |
|---|---|
| `SUPER_ADMIN` | 全部启用权限（启动时自动补齐新增的启用权限） |
| `ORG_ADMIN_{orgId}` | `user:view/create/update/delete/assign-role/reset-password`、`org:view`、`role:view/create/update/delete/assign-permission` |
| `NORMAL_ADMIN_{orgId}` | `user:view`、`user:update` |

> `org:create` / `org:update` / `org:delete` 虽然仍是权限码，但对应接口同时受 `@SuperAdminOnly` 保护，只有超级管理员能真正执行；组织管理员即使被授予该权限码也会得到 `403`。

---

## 7. 模型连接与模型配置

> 以下接口均要求 `role=admin`（含组织管理员）。连接与模型配置属于平台级全局配置，不属于任何组织；变更会递增配置版本号，供 Node 快照感知。

### 7.1 连接列表

`GET /api/admin/model-connections`

响应 `200`：

```json
[
  {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "name": "DeepSeek",
    "provider": "deepseek",
    "baseUrl": "https://api.deepseek.com",
    "hasKey": true,
    "models": [
      { "feature": "script", "model": "deepseek-chat", "enabled": 1 }
    ]
  }
]
```

说明：密钥永不回显，仅返回 `hasKey`；关联模型为 `enabled` 原始值 `0/1`。

### 7.2 创建连接

`POST /api/admin/model-connections`

请求：

```json
{
  "name": "DeepSeek",
  "provider": "deepseek",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-..."
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `name` | string | 是 | ≤ 100 |
| `provider` | string | 是 | ≤ 100 |
| `baseUrl` | string | 是 | 非空 |
| `apiKey` | string | 是 | 非空，禁止换行，≤ 8192 |

响应 `200`：`ConnectionDto`（`models` 为空数组）。

错误：`400` 缺少有效 apiKey / apiKey 含换行。

### 7.3 更新连接

`PUT /api/admin/model-connections/{id}`

请求同创建；`apiKey` 省略或空串时保留旧密钥。

行为：

- 关联模型若自身 `endpoint` 为空，回填为连接 `baseUrl`。
- 不把密钥复制进模型，读取时按“模型自身密钥优先，其次连接密钥”解析。
- 清除该连接旧的检测结果。

响应 `200`：`ConnectionDto`（含关联模型）。

错误：`404` 连接不存在；`400` apiKey 含换行。

### 7.4 删除连接

`DELETE /api/admin/model-connections/{id}`

限制：仍有模型引用该连接时返回 `409 连接被模型引用，不能删除`。

响应 `200`：空响应体。

### 7.5 检测连接

`POST /api/admin/model-connections/{id}/check`

行为：向 `{baseUrl}/v1/models` 发起 GET（Bearer 密钥），连接超时 3s、读取超时 10s，不生成内容。

响应 `200`：

```json
{
  "connectionId": "f47ac10b-...",
  "checkedAt": "2026-11-03T11:05:00",
  "status": "passed",
  "message": "目录认证通过",
  "latencyMs": 187
}
```

| status | 含义 |
|---|---|
| `passed` | 2xx，目录认证通过 |
| `failed` | 401/403 或其他 4xx/5xx |
| `unsupported` | 404、非 2xx/4xx，或网络异常（不返回内部异常细节） |

### 7.6 检测记录列表

`GET /api/admin/model-connection-checks`

响应 `200`：`ModelConnectionCheck[]`（每个连接最多一条最新记录）。

### 7.7 模型配置列表

`GET /api/admin/model-configs`

响应 `200`：

```json
[
  {
    "connectionId": "f47ac10b-...",
    "feature": "script",
    "model": "deepseek-chat",
    "provider": "deepseek",
    "adapter": "chat",
    "endpoint": "https://api.deepseek.com",
    "requestTemplate": "",
    "enabled": true,
    "hasKey": true,
    "supported": true,
    "custom": false,
    "modes": []
  }
]
```

`hasKey` 按执行时是否拿得到密钥判定：自身密钥优先，其次绑定连接密钥。

### 7.8 新增自定义模型

`POST /api/admin/model-configs`

请求：

```json
{
  "feature": "textToImage",
  "model": "my-model-v1",
  "provider": "my-provider",
  "adapter": "dashscope-image",
  "endpoint": "https://example.com",
  "requestTemplate": ""
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `feature` | string | 是 | 必须在适配器矩阵内（含别名） |
| `model` | string | 是 | 2–120，正则 `^[A-Za-z0-9][A-Za-z0-9._:+/-]{1,119}$` |
| `provider` | string | 是 | ≤ 80 |
| `adapter` | string | 是 | 必须与 feature 匹配 |
| `endpoint` | string | 否 | 默认空串 |
| `requestTemplate` | string | 否 | 默认空串 |

行为：新模型 `enabled=false`、`custom=true`、`connectionId=null`、`apiKey=""`。

限制：每个 feature 最多 50 个自定义模型。

错误：`400` feature/adapter 不支持、模型已存在、已被删除不可复活、超过数量上限。

### 7.9 更新模型配置

`PUT /api/admin/model-configs/{feature}/{model}`

请求（未传字段沿用）：

```json
{
  "adapter": "dashscope-image",
  "endpoint": "https://example.com",
  "apiKey": "sk-...",
  "connectionId": "f47ac10b-...",
  "enabled": true,
  "requestTemplate": ""
}
```

| 字段 | 说明 |
|---|---|
| `adapter` | 非空时校验与 feature 匹配 |
| `endpoint` | 非 null 时覆盖 |
| `apiKey` | 空串沿用旧值；非空覆盖 |
| `connectionId` | 非 null 时写入；空串清除绑定；非空需已存在 |
| `enabled` | **仅 `true` 为启用**；未传或 false 都会停用 |
| `requestTemplate` | 非 null 时覆盖 |

启用前置校验（不满足返回 `400`）：

1. 适配器受支持；
2. `endpoint` 非空；
3. 自身或绑定连接存在有效密钥。

响应 `200`：`ModelConfigDto`。

### 7.10 删除模型配置

`DELETE /api/admin/model-configs/{feature}/{model}`

行为：删除配置并写入 `deleted_model_configs` 墓碑，之后不能直接重建同名模型。

响应 `200`：空响应体。

错误：`404` 模型配置不存在。

### 7.11 适配器矩阵

| feature | 可用 adapter |
|---|---|
| `textGeneration` / `script` / `storyboard` / `videoReverse` | `chat`、`autodl-chat` |
| `imageEdit` | `qwen-image-edit`、`wanx-image-edit` |
| `lyricRecognition` | `tencent-asr`、`dashscope-asr` |
| `image` | `autodl-api`、`autodl-workflow`、`tencent-image`、`autodl-image`、`dashscope-image`、`dashscope-image-sync`、`qwen-image`、`wanx-image-edit`、`ark-image` |
| `video` | `tencent-vega`、`dashscope-videoretalk`、`autodl-api`、`autodl-workflow`、`fal-lipsync`、`tencent-video`、`dashscope-video`、`dashscope-image-video`、`dashscope-s2v`、`ark-video`、`kling-video` |
| `voice` | `autodl-api`、`autodl-workflow`、`dashscope-tts` |
| `music` | `autodl-api`、`autodl-workflow`、`tencent-mureka`、`tencent-music`、`dashscope-music`、`minimax-music`、`hf-audio`、`ace-step-music` |
| `threeD` | `autodl-api`、`autodl-workflow`、`tripo-3d`、`dashscope-3d`、`tencent-3d` |

别名：`textToImage`/`imageToImage` → `image`；`textToVideo`/`imageToVideo`/`firstLastFrameVideo` → `video`。

---

## 8. Agent 配置

Agent 配置保存的是“原始选择”，不计算最终有效模型。

### 8.1 读取配置

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/agent/short-drama/config` | 需登录 |
| GET | `/api/agent/comic/config` | 需登录 |
| GET | `/api/agent/canvas/config` | 公开 |

响应 `200`（short-drama / comic）：

```json
{
  "agent": "shortDrama",
  "systemPrompt": "",
  "selections": { "script": "", "storyboard": "", "image": "", "video": "", "voice": "" },
  "models": { "script": "", "storyboard": "", "image": "", "video": "", "voice": "" }
}
```

响应 `200`（canvas）：

```json
{
  "agent": "canvas",
  "systemPrompt": "",
  "selection": "",
  "model": ""
}
```

### 8.2 保存配置

| 方法 | 路径 | 权限 |
|---|---|---|
| PUT | `/api/admin/agent/short-drama/config` | 管理员 |
| PUT | `/api/admin/agent/comic/config` | 管理员 |
| PUT | `/api/admin/agent/canvas/config` | 管理员 |

请求（short-drama）：

```json
{
  "systemPrompt": "你是一个短剧助手",
  "models": {
    "script": "deepseek-chat",
    "storyboard": "deepseek-chat",
    "image": "wanx-v1",
    "video": "kling-v1",
    "voice": "cosyvoice"
  }
}
```

请求（comic）：`models` 仅 `script/storyboard/image/video`。

请求（canvas）：`{ "systemPrompt": "...", "model": "..." }`。

约束：

- `systemPrompt` 最长 20000 字符；只传 `systemPrompt` 时不改模型选择。
- `models` 为 `step -> model` 映射，值最长 128 字符；只更新已知 step。
- canvas 的 `model` 最长 128 字符。
- 未知 `agent` 返回 `400`；未知 step 会被忽略。
- 字段超长返回 `400`，响应 `data` 中给出字段级错误（如 `{"systemPrompt":"systemPrompt 最多 20000 字符"}`）。
- 保存后递增配置版本号并返回最新配置。

---

## 9. 工作流配置

> Java 只持久化配置；工作流运行、任务、输出归 Node。
> 管理端配置接口（列表/增删改/校验）要求管理员（含组织管理员），属于平台级全局配置。

### 9.1 工作流列表

`GET /api/admin/workflows` · 管理员

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | int | 可选；与 `size` 同时决定是否分页 |
| `size` | int | 可选；有效 1–100，缺省 20 |

- 不传 `page` 且不传 `size`：返回数组，按 `createdAt desc`。
- 传入任一参数：返回 Spring `Page` 结构。

响应元素：

```json
{
  "id": "9f2c...",
  "name": "文生图工作流",
  "description": "",
  "provider": "comfyui",
  "workflowId": "remote-123",
  "purpose": "general",
  "baseUrl": "http://comfy:8188",
  "hasKey": true,
  "graph": {},
  "promptNode": "6",
  "promptInput": "text",
  "mediaBindings": {},
  "enabled": true
}
```

### 9.2 创建工作流

`POST /api/admin/workflows` · 管理员

请求：

```json
{
  "name": "文生图工作流",
  "description": "可选",
  "provider": "comfyui",
  "baseUrl": "http://comfy:8188",
  "remoteWorkflowId": "remote-123",
  "purpose": "general",
  "promptNode": "6",
  "promptInput": "text",
  "graph": {},
  "mediaBindings": {},
  "apiKey": "",
  "enabled": false
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 非空，≤ 100 |
| `baseUrl` | 是 | 非空，≤ 255 |
| `provider` | 否 | ≤ 32，缺省 `comfyui` |
| `purpose` | 否 | ≤ 32，缺省 `general` |
| `remoteWorkflowId` | 否 | ≤ 64；响应中字段名为 `workflowId` |
| `promptNode` | 否 | ≤ 64，缺省空串 |
| `promptInput` | 否 | ≤ 64，缺省 `text` |
| `graph` | 否 | 对象或 JSON 字符串；缺省 `{}` |
| `mediaBindings` | 否 | 对象或 JSON 字符串；缺省 `{}` |
| `apiKey` | 否 | ≤ 8192；空串不保存 |
| `enabled` | 否 | 仅 `true` 启用 |
| `description` | 否 | 原样保存 |

响应 `200`：工作流视图（同列表元素）。

错误：`400` name/baseUrl 为空；字段超长返回 `400`，`data` 中给出字段级错误。

### 9.3 更新工作流

`PUT /api/admin/workflows/{id}` · 管理员

请求字段同创建，未传字段沿用；`apiKey` 空串沿用旧值。

响应 `200`：工作流视图。

错误：`404` 工作流不存在；字段超长返回 `400`。

### 9.4 停用工作流

`DELETE /api/admin/workflows/{id}` · 管理员

行为：仅 `enabled=0`，不物理删除，不影响 Node 侧运行记录/文件。

响应 `200`：

```json
{ "ok": true }
```

### 9.5 配置校验

`POST /api/admin/workflows/{id}/check` · 管理员

行为：仅校验 `name/baseUrl/graph/promptNode` 非空，不发起真实连通检测。

响应 `200`：

```json
{
  "ok": true,
  "configurationOnly": true,
  "message": "配置校验通过，未执行真实生成",
  "id": "9f2c...",
  "passed": true
}
```

错误：`400` 配置字段为空；`404` 工作流不存在。

---

## 10. 系统设置与 Skill

### 10.1 公开平台设置

`GET /api/public/settings` · 公开

响应 `200`：

```json
{
  "platformName": "AI 创作平台",
  "allowRegistration": false,
  "defaultVideoResolution": "480P",
  "maxVideoDuration": 15,
  "taskConcurrency": 4,
  "jwtHours": 24,
  "retentionDays": 30,
  "autoCleanup": false
}
```

未初始化时返回上述产品默认值；`features` 不属于平台设置，读取时会被移除。

### 10.2 修改系统设置

`PUT /api/admin/system-settings` · 管理员

请求：可只传部分字段，未传字段保留；`features` 会被忽略。

```json
{
  "platformName": "AIGC Studio",
  "allowRegistration": true,
  "defaultVideoResolution": "720P",
  "maxVideoDuration": 30,
  "taskConcurrency": 8,
  "jwtHours": 168,
  "retentionDays": 60,
  "autoCleanup": true
}
```

校验规则：

| 字段 | 规则 |
|---|---|
| `platformName` | 字符串，1–60 |
| `allowRegistration` / `autoCleanup` | 布尔 |
| `defaultVideoResolution` | `480P` / `720P` / `1080P` |
| `maxVideoDuration` | `5` / `10` / `15` / `30` |
| `taskConcurrency` | 整数 1–20 |
| `jwtHours` | `24` / `168` |
| `retentionDays` | 整数 1–365 |

响应 `200`：保存后的完整设置（同公开设置结构）。

错误：`400` 不支持的字段 / 类型或范围非法。

### 10.3 旧版 API 列表

`GET /api/admin/apis` · 管理员

兼容旧管理后台，固定返回：

```json
{ "apis": [] }
```

### 10.4 公开 Skill 列表

`GET /api/studio-skills` · 公开

响应 `200`：仅 `enabled=1` 的 `StudioSkill[]`。

### 10.5 管理端 Skill 列表

`GET /api/admin/studio-skills` · 管理员

响应 `200`：全部 `StudioSkill[]`。

### 10.6 创建 Skill

`POST /api/admin/studio-skills` · 管理员

请求：

```json
{
  "name": "分镜助手",
  "description": "可选描述",
  "content": "提示词内容",
  "enabled": true
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | 是 | 非空，≤ 100 |
| `description` | 否 | 默认空串 |
| `content` | 否 | 默认空串 |
| `enabled` | 否 | 仅 `true` 启用，缺省启用 |

响应 `200`：`StudioSkill`。

错误：`400` name 为空或超过 100 字符。

### 10.7 更新 Skill

`PUT /api/admin/studio-skills/{id}` · 管理员

请求字段同创建，未传沿用。

响应 `200`：`StudioSkill`。

错误：`404` Skill 不存在；`name` 传入空白或超过 100 字符返回 `400`。

### 10.8 删除 Skill

`DELETE /api/admin/studio-skills/{id}` · 管理员

响应 `200`：空响应体；错误 `404` Skill 不存在。

---

## 11. 项目

> 所有接口按当前登录用户过滤，不信任请求体中的 `userId`。
> 访问非本人项目统一返回 `404 项目不存在`。

### 11.1 项目列表

`GET /api/projects` · 需登录

| 参数 | 类型 | 说明 |
|---|---|---|
| `page` | int | 可选；负值按 0 |
| `size` | int | 可选；有效 1–200，缺省 20 |

- 不传 `page` 且不传 `size`：返回 `Project[]`，按 `updatedAt desc`。
- 传入任一参数：返回 Spring `Page<Project>`。

### 11.2 创建项目

`POST /api/projects` · 需登录

请求：

```json
{
  "title": "我的短剧",
  "type": "short-drama",
  "style": "anime",
  "duration": 60,
  "module": "script"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `title` | string | 否 | ≤ 100 |
| `type` | string | 否 | ≤ 32 |
| `style` | string | 否 | ≤ 32 |
| `duration` | int | 否 | 整数；类型错误返回 `400` |
| `module` | string | 否 | 缺省 `script`，≤ 64；可直接使用 feature 编码（如 `short-drama-agent`） |

> 超长字段返回 `400`（如 `title 长度不能超过 100 个字符`），不会落到数据库再报 500。

行为：`status` 固定为 `draft`，并初始化空正文（`script=""`、`scenes=[]`、`media={}`）。

响应 `200`：`Project`。

### 11.3 读取项目正文

`GET /api/projects/{id}/content` · 需登录

响应 `200`：

```json
{
  "projectId": 1,
  "script": "",
  "scenes": [],
  "media": {},
  "updatedAt": "2026-11-03T11:10:00"
}
```

`scenes`/`media` 保存的是 JSON 字符串，响应时解析为对象/数组；解析失败时原样返回字符串。

### 11.4 保存项目正文

`PUT /api/projects/{id}/content` · 需登录

请求：

```json
{
  "script": "第一幕……",
  "scenes": [{ "id": 1, "prompt": "..." }],
  "media": { "images": [] }
}
```

| 字段 | 说明 |
|---|---|
| `script` | 字符串，缺省空串 |
| `scenes` | 数组/对象，或已是 JSON 字符串；缺省 `[]` |
| `media` | 对象，或已是 JSON 字符串；缺省 `{}` |

行为：同时刷新项目 `updatedAt`；正文不存在时自动创建。

响应 `200`：同读取正文结构。

### 11.5 更新项目模块

`PATCH /api/projects/{id}/module` · 需登录

请求：

```json
{ "module": "storyboard" }
```

响应 `200`：更新后的 `Project`；`module` 为空或超过 64 个字符返回 `400`。

### 11.6 删除项目

`DELETE /api/projects/{id}` · 需登录

行为：删除项目及其正文记录。

响应 `200`：空响应体。

---

## 12. 旧版用户管理（已废弃）

> `/api/users` 为旧管理后台接口，代码中已标记 `@Deprecated`，新接入请使用 [第 3 节](#3-管理端创作者账号) 的 `/api/admin/users`。
> 两者共享同一套会话撤销逻辑；旧接口按数据范围隔离：组织管理员只能访问本组织数据（跨组织 `404`），超级管理员可访问全部组织。
> 旧接口创建的用户不会初始化个人 feature 角色，功能权限默认全 false。

### 12.1 创建用户

`POST /api/users` · `user:create`

请求：

```json
{ "username": "editor01", "password": "secret123", "realName": "编辑一号", "orgId": 2 }
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `username` | string | 是 | 6–50，全局唯一 |
| `password` | string | 是 | 6–100 |
| `realName` | string | 是 | 姓名 |
| `orgId` | int | 否 | 目标组织；仅超级管理员生效（管理后台按组织筛选后创建用户），组织管理员传入会被忽略 |

行为：默认 `orgId` 取自当前会话；超级管理员传 `orgId` 时可把用户建到指定组织。`status=1`，角色使用实体默认值 `user`。

响应 `200`：`User`。

错误：`400` 用户名已存在、组织不存在。

### 12.2 用户列表

`GET /api/users` · `user:view`

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | int | 0 | 0 基页码 |
| `size` | int | 10 | 每页条数 |
| `realname` | string | — | 姓名模糊匹配 |
| `status` | int | — | 内部状态 `1/0/-1` |
| `orgId` | int | — | 组织筛选；仅超级管理员生效，组织管理员传入会被忽略并强制本组织 |

响应 `200`：`PageResponse<User>`，按 `createTime desc, id desc`。

- 若当前会话拥有 `role:view`，额外填充 `roleIds/roleNames`。
- 每条记录填充 `orgName`。
- 超级管理员不传 `orgId` 返回全部组织的用户，传 `orgId` 只返回该组织；组织管理员只返回本组织用户。

### 12.3 用户详情

`GET /api/users/{id}` · `user:view`

响应 `200`：`User`；用户不在当前数据范围内时 `404`。

### 12.4 用户角色 ID

`GET /api/users/{id}/roles` · `user:view`

响应 `200`：`int[]`。

### 12.5 修改用户姓名

`PUT /api/users/{id}` · `user:update`

请求：

```json
{ "realName": "新姓名" }
```

响应 `200`：`User`。

### 12.6 修改用户状态

`PUT /api/users/{id}/status?status=0` · `user:update`

| 参数 | 位置 | 说明 |
|---|---|---|
| `id` | path | 用户 ID |
| `status` | query | `1` 启用 / `0` 禁用 |

行为：递增 `token_version` 并清理会话缓存。

响应 `200`：`User`。

### 12.7 修改用户密码

`PUT /api/users/{id}/password` · 本人改密需登录；修改他人需 `user:update`

请求：

```json
{ "oldPassword": "current123", "password": "newpass123" }
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `oldPassword` | string | 是 | 该账号当前密码，≤ 256 |
| `password` | string | 是 | 新密码，6–50 |

行为：

- 本人改密（`id` 等于当前登录用户）属于自助通道，只要求登录并提供正确的 `oldPassword`，不要求 `user:update`；普通创作者可直接使用。
- 修改他人密码需要 `user:update`，且目标用户必须与调用方同组织，跨组织或无权访问按 `403/404` 返回。
- 先校验目标账号的 `oldPassword`，不匹配返回 `400 原密码不正确`，且不写库、不撤销会话。
- 校验通过后更新密码、递增 `token_version` 并清理会话缓存，旧 token 立即失效。
- 管理员免验证地强制重置仍使用 [12.8 重置用户密码](#128-重置用户密码)，或 [3.3 修改创作者](#33-修改创作者) 的 `password` 字段。

响应 `200`：`User`。

错误：`400` 原密码不正确 / 新密码长度非法；`403` 无权修改他人密码；`404` 用户不存在（含跨组织）。

### 12.8 重置用户密码

`PUT /api/users/{id}/reset-password` · `user:reset-password`

行为：重置为系统初始密码 `12345678`；清空 `lastLoginTime`（下次登录 `firstLogin=true`）；递增 `token_version`。

响应 `200`：`User`。

### 12.9 删除用户

`DELETE /api/users/{id}` · `user:delete`

行为：**物理删除**用户及其角色关联（与新版软删除不同），并清理会话缓存。

响应 `200`：空响应体。

### 12.10 分配角色

`POST /api/users/{id}/roles` · `user:assign-role`

请求：角色 ID 数组（空数组表示清空）：

```json
[3, 7]
```

约束：

- 目标账号必须在调用方数据范围内（组织管理员跨组织按 `404`）。
- 组织角色必须属于目标账号所在组织，否则 `400 角色不属于该用户所在组织`。
- `SUPER_ADMIN` 只有超级管理员可分配，且目标账号必须是管理员账号（`role=admin`）；组织管理员尝试分配返回 `403`。
- 其他全局角色（含创作者个人角色 `USER_FEATURES_*`）不允许通过该接口分配（`400`）。
- 分配 `SUPER_ADMIN` / `ORG_ADMIN_*` 后，系统自动把 `t_user.role` 同步为 `admin`，账号即可登录 `/api/admin/login`；移除这些角色后同步回 `user`。
- `NORMAL_ADMIN_*` 与用户自建角色只授予权限集合，不会改变 `t_user.role`（保持 `user`），因此不会获得管理端登录入口。
- 默认超级管理员账号 `admin` 不允许移除管理员角色，否则返回 `400`。

行为：覆盖用户角色、同步账号类型并清理会话缓存。

响应 `200`：空响应体。

### 12.11 按角色编码查用户

`GET /api/users/by_role_code?roleCode=ORG_ADMIN_1` · `user:view`

响应 `200`：`User[]`，自动排除当前登录用户；并按数据范围过滤（超级管理员返回全部组织的成员，组织管理员只返回本组织成员）；角色不存在返回空数组。

### 12.12 旧版 `User` 响应字段

`password` 被 `@JsonIgnore`，不会返回。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 用户 ID |
| `username` | string | 用户名 |
| `realName` | string | 真实姓名 |
| `name` | string | 展示名 |
| `email` | string | 邮箱 |
| `phone` | string | 手机号 |
| `role` | string | `admin` / `user` |
| `tokenVersion` | int | 会话版本 |
| `permissionVersion` | int | 权限版本 |
| `accountSource` | string | 账号来源 |
| `orgId` | int | 组织 ID |
| `status` | int | `1` / `0` / `-1` |
| `lastLoginTime` | string | 最后登录时间 |
| `createTime` / `updateTime` | string | 时间 |
| `orgName` | string | 组织名称 |
| `roleIds` | int[] | 角色 ID（列表按需填充） |
| `roleNames` | string[] | 角色名称（列表按需填充） |

---

## 13. 内部接口

### 13.1 非计费配置快照

`GET /api/internal/node-config` · 独立服务令牌

请求头：

```http
Authorization: Bearer <NODE_CONFIG_READ_TOKEN>
```

说明：

- 不复用用户/管理员登录 token；未配置 `NODE_CONFIG_READ_TOKEN` 时返回 `503`，凭据缺失/错误返回 `401`。
- 响应 `Cache-Control: no-store`。
- 响应包含真实厂商密钥，仅限内网/受控链路调用。

响应 `200`：

```json
{
  "version": "12",
  "updatedAt": "2026-11-03T11:12:00Z",
  "connections": [
    { "id": "...", "name": "DeepSeek", "provider": "deepseek", "baseUrl": "https://api.deepseek.com", "apiKey": "sk-..." }
  ],
  "modelConfigs": [
    {
      "feature": "script",
      "model": "deepseek-chat",
      "provider": "deepseek",
      "adapter": "chat",
      "endpoint": "https://api.deepseek.com",
      "apiKey": "sk-...",
      "enabled": true,
      "connectionId": "...",
      "requestTemplate": ""
    }
  ],
  "agents": {
    "shortDrama": { "agent": "shortDrama", "systemPrompt": "", "selections": {}, "models": {} },
    "comic": { "agent": "comic", "systemPrompt": "", "selections": {}, "models": {} },
    "canvas": { "agent": "canvas", "systemPrompt": "", "selection": "", "model": "" }
  },
  "skills": [
    {
      "id": "...",
      "name": "分镜助手",
      "description": "",
      "content": "",
      "enabled": 1,
      "created_at": "2026-11-03T11:00:00Z",
      "updated_at": "2026-11-03T11:00:00Z"
    }
  ],
  "workflows": [
    {
      "id": "...",
      "name": "文生图工作流",
      "description": "",
      "provider": "comfyui",
      "workflowId": "remote-123",
      "purpose": "general",
      "baseUrl": "http://comfy:8188",
      "hasKey": true,
      "apiKey": "sk-...",
      "graph": {},
      "promptNode": "6",
      "promptInput": "text",
      "mediaBindings": {},
      "enabled": true
    }
  ],
  "settings": { "platformName": "AI 创作平台", "...": "..." },
  "deletedModelConfigs": [
    { "feature": "textToImage", "model": "old-model" }
  ]
}
```

快照在同一只读事务内读取，避免半次提交的配置混入。

---

## 14. Node 归属占位接口

以下路径由 Node 服务实现，Java 侧保留占位路由并统一返回：

```json
{ "code": 503, "message": "该能力由 Node 服务提供，Java 不再重复实现", "timestamp": 1793674181000 }
```

| 分类 | 路径 |
|---|---|
| 积分（管理端） | `/api/admin/points`、`/api/admin/points/**` |
| 积分（用户端） | `/api/points`、`/api/points/**` |
| 计费报价 | `/api/billing`、`/api/billing/**` |
| 生成执行 | `/api/generate`、`/api/generate/**` |
| 运行记录与统计 | `/api/admin/generation-records/**`、`/api/admin/generation-filters`、`/api/admin/generation-summary`、`/api/admin/overview`、`/api/admin/stats`、`/api/admin/generations` |
| 签名媒体 | `/api/generated/**`、`/api/reference-input/**`、`/api/digital-human-media/**` |
| 执行类能力 | `/api/text-studio/**`、`/api/workflows/**`（运行）、`/api/music/**`、`/api/canvas/**`、`/api/digital-humans/**`、`/api/admin/autodl/instances` |
| 模型目录 | `/api/models`、`/api/published-models` |

网关应将这些路径直接转发到 Node，不经过 Java 服务。签名媒体 GET 请求在 Security 层放行，由 Node 校验 `expires + signature`。

---

## 15. 公共数据结构

### 15.1 `ContractUserDto`

登录响应中的 `user`、`/api/auth/me`、`/api/admin/me` 共用。空值字段不输出（`@JsonInclude(NON_NULL)`）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 用户 ID |
| `name` | string | 展示名（优先 `name`，其次 `realName/email/username`） |
| `email` | string | 邮箱 |
| `role` | string | `admin` / `user` |
| `status` | string | `active` / `disabled` / `deleted` |
| `created_at` | string | ISO-8601 UTC |
| `token_version` | int | 会话版本 |
| `features` | object | 25 项 feature 快照 |
| `phone` | string | 手机号（如有） |
| `username` | string | 管理员兼容字段 |
| `realName` | string | 管理员兼容字段 |
| `orgId` | int | 组织 ID |
| `orgName` | string | 组织名称 |
| `firstLogin` | boolean | 管理员是否首次登录 |
| `roles` | string[] | 角色编码列表（管理员） |
| `permissions` | string[] | 权限码列表（管理员） |

### 15.2 `AdminUserListItemDto`

仅 `GET /api/admin/users` 列表项使用。创建（`POST`）、修改（`PATCH`）、状态（`PATCH /status`）接口返回的是 `ContractUserDto`，字段更少。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 用户 ID |
| `name` | string | 展示名 |
| `email` | string | 邮箱 |
| `username` | string | 用户名 |
| `realName` | string | 真实姓名 |
| `phone` | string | 手机号 |
| `role` | string | `user` |
| `status` | string | `active` / `disabled` / `deleted` |
| `created_at` | string | 创建时间 |
| `last_login_at` | string | 最后登录时间 |
| `orgId` | int | 组织 ID（0 表示未分配） |
| `orgName` | string | 组织名称，未分配时为 null |
| `roleIds` | int[] | 业务角色 ID（不含个人角色） |
| `roleNames` | string[] | 业务角色名称 |
| `features` | object | 25 项 feature 快照 |

### 15.3 `Organization`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 组织 ID |
| `orgCode` | string | 组织编码，唯一 |
| `orgName` | string | 组织名称，唯一 |
| `description` | string | 描述 |
| `status` | int | `1` 启用 / `0` 停用 |
| `createTime` | string | 创建时间 |
| `updateTime` | string | 更新时间 |

### 15.4 `Role`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 角色 ID |
| `roleCode` | string | 角色编码，组织内唯一 |
| `roleName` | string | 角色名称 |
| `description` | string | 描述 |
| `orgId` | int | 组织 ID；`null` 表示全局角色 |
| `orgName` | string | 所属组织名称；全局角色为“全局”，列表接口返回 |
| `status` | int | `1` 启用 |
| `createTime` / `updateTime` | string | 时间 |
| `permissions` | Permission[] | 仅角色列表返回时内联 |

### 15.5 `Permission`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 权限 ID |
| `permissionCode` | string | 权限码 |
| `permissionName` | string | 中文名称 |
| `status` | int | `1` 启用 / `0` 停用 |
| `createTime` / `updateTime` | string | 时间 |

### 15.6 `Project`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | int | 项目 ID |
| `title` | string | 标题 |
| `type` | string | 类型 |
| `style` | string | 风格 |
| `duration` | int | 时长 |
| `status` | string | 固定 `draft` |
| `updatedAt` | string | 更新时间 |
| `module` | string | 当前模块，默认 `script`，最长 64 字符 |

`userId` 为内部字段，不返回。

### 15.7 `StudioSkill`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | string | UUID |
| `name` | string | 名称 |
| `description` | string | 描述 |
| `content` | string | 内容 |
| `enabled` | int | `1` 启用 / `0` 停用 |
| `createdAt` / `updatedAt` | string | 时间 |

### 15.8 `ModelConnectionCheck`

| 字段 | 类型 | 说明 |
|---|---|---|
| `connectionId` | string | 连接 ID |
| `checkedAt` | string | 检测时间 |
| `status` | string | `passed` / `failed` / `unsupported` |
| `message` | string | 可展示信息 |
| `latencyMs` | int | 耗时（毫秒） |

---

## 16. 附录

### 16.1 25 项 feature

| 顺序 | 权限码 | 中文名 |
|---|---|---|
| 1 | `text-studio` | 文本工作室 |
| 2 | `script` | 剧本创作 |
| 3 | `storyboard` | 分镜 |
| 4 | `grid` | 九宫格入口 |
| 5 | `textToImage` | 文生图 |
| 6 | `imageToImage` | 图生图 |
| 7 | `imageEdit` | 图片编辑 |
| 8 | `image` | 图片生成 |
| 9 | `textToVideo` | 文生视频 |
| 10 | `imageToVideo` | 图生视频 |
| 11 | `firstLastFrameVideo` | 首尾帧视频 |
| 12 | `video` | 视频生成 |
| 13 | `voice` | 配音 |
| 14 | `music` | 音乐 |
| 15 | `digital-human` | 数字人 |
| 16 | `edit` | 本地剪辑 |
| 17 | `3d` | 3D Studio |
| 18 | `short-drama-agent` | 短剧 Agent |
| 19 | `comic-agent` | 漫剧 Agent |
| 20 | `canvas` | 画布 |
| 21 | `director` | 本地 3D 导演台 |
| 22 | `workflows` | 通用工作流 |
| 23 | `transform` | 素材转换 |
| 24 | `recreate` | 参考视频再创作 |
| 25 | `files` | 我的生成文件 |

### 16.2 调用示例

**管理员登录并调用受保护接口**

```bash
TOKEN=$(curl -s -X POST http://localhost:8089/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

curl -s http://localhost:8089/api/admin/users?page=0&pageSize=20 \
  -H "Authorization: Bearer $TOKEN"
```

**创建创作者并授权**

```bash
curl -s -X POST http://localhost:8089/api/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"创作者","email":"creator@example.com","password":"secret123"}'

curl -s -X PUT http://localhost:8089/api/admin/user-permissions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"userIds":[2],"mode":"grant","features":{"script":true,"textToImage":true}}'
```

**退出登录**

```bash
curl -s -X POST http://localhost:8089/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

**内部配置快照**

```bash
curl -s http://localhost:8089/api/internal/node-config \
  -H "Authorization: Bearer $NODE_CONFIG_READ_TOKEN"
```

### 16.3 网关配置提醒

- `/api/admin/**` 中积分、统计、AutoDL 等 Node 路径应优先转发到 Node，其余管理端接口转发到 Java。
- `/api/workflows/**` 需区分：管理端工作流配置是 `/api/admin/workflows`（Java），运行接口是 `/api/workflows/**`（Node）。
- 签名媒体 GET 请求由 Node 校验签名，Java 侧仅占位。
- 跨域白名单通过 `cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` 配置；生产不建议保留 `*`。

---

## 变更记录

| 日期 | 说明 |
|---|---|
| 2026-11-04 | 首版：覆盖 Java 归属全部接口、权限模型、分页/错误约定、Node 占位路由与 feature/适配器附录 |
| 2026-11-04 | `PUT /api/users/{id}/password` 改为必须提供 `oldPassword` 并通过 BCrypt 校验，失败返回 400 且不撤销会话 |
| 2026-11-04 | 修复 `POST /api/projects` 因 `module` 超过 `VARCHAR(16)` 触发的 500：`projects.module` 加宽到 64，创建/切换模块前置校验长度；数据库数据类异常统一返回 400 |
| 2026-11-04 | 请求体 DTO 化：`/api/admin/studio-skills`、`/api/projects`（创建/保存正文/切换模块）、`/api/admin/agent/*/config`、`/api/admin/workflows` 改用带 Bean Validation 的 DTO，超长/缺失字段返回字段级 400；工作流请求字段统一为 `remoteWorkflowId` |
| 2026-11-04 | `PUT /api/users/{id}/password` 支持本人自助改密：本人不再要求 `user:update`，凭 `oldPassword` 校验即可；修改他人仍要求同组织 + `user:update` |
| 2026-11-04 | 歌词识别功能下线：删除 `lyricRecognition` feature 权限码，feature 快照由 26 项调整为 25 项；管理后台权限管理与角色授权仅展示后端 RBAC 权限码 |
| 2026-11-04 | RBAC 重构：启动时幂等初始化默认组织、`SUPER_ADMIN` 角色（全部启用权限）、默认超管账号 `admin/admin123`，并为每个组织补齐内置角色；组织创建/修改/删除改为仅超级管理员，创建组织在同一事务内生成组织管理员角色、默认权限与管理员账号并绑定；组织/角色/用户/功能权限接口按组织隔离（超级管理员可见全部组织），跨组织按 404；模型连接、Agent、工作流、Skill、系统设置、权限启停仍对管理员（含组织管理员）开放 |
| 2026-11-04 | 管理后台角色/用户列表新增组织筛选：`GET /api/roles`、`GET /api/users`、`GET /api/admin/users` 支持可选 `orgId`（超级管理员生效，组织管理员强制本组织）；`POST /api/users` 支持超级管理员把新用户建到所选组织 |
| 2026-11-04 | 修复 `t_user.role` 与 RBAC 管理员角色不一致：仅 `SUPER_ADMIN`/`ORG_ADMIN_*` 同步 `role=admin`，移除后回 `user`；`NORMAL_ADMIN_*` 与用户自建角色只授予权限、保持 `user`；默认超管不可移除管理员角色；JWT 过滤器改用数据库中的账号类型，降级后旧 token 立即失效 |
| 2026-11-04 | 初始化脚本合并：`db/migration` 仅保留 `V1__init.sql`，包含原 V1~V6 的最终表结构、权限/组织/管理员种子、平台设置与模型配置；删除 V2~V6 与独立全量脚本。V1 仅面向全新空库，旧库不再兼容，需重建数据库 |
| 2026-11-05 | 修复 `created_at`/`last_login_at` 时区错误：数据库本地墙上时间（如 `14:45`）此前被直接标成 `14:45:25Z`，前端按 UTC 再转北京时区后变为 `22:45`；现改为按 JVM 时区解释后转换为 UTC（`06:45:25Z`） |
