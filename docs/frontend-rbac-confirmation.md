# AIGC Studio 前后台账号与 RBAC 实施稿（前端已确认账号模型）

> 版本：2026-11-05 · 最终版
> 状态：**账号模型已确认并已实施**。标注【新增】【调整】的接口/字段已按本稿落地；V1 仅保留新库脚本。
> 实施记录：后端、V1、管理后台表单与角色授权页已调整；单测 126 通过；新库完成 V1、超管/学校账号登录、共享角色授权、版本冲突、本人改密、组织停用联调。
> 适用：服务端管理后台、客户端管理后台、创作平台前端、Node 计费服务。

---

## 1. 统一模型

系统有两个管理后台、一个创作平台：

| 端 | 登录入口 | 账号 | 主要用途 |
|---|---|---|---|
| 服务端管理后台（Java `/admin`，内部） | `POST /api/admin/login` | 仅 `SUPER_ADMIN` | 创建组织、初始化角色、分配权限、模型/工作流/Skill/系统设置 |
| 客户端管理后台（独立前端，甲方） | `POST /api/admin/login` | 仅 `SUPER_ADMIN` | 查看学校、管理已有账号、开通功能、配置模型、积分充值/查询 |
| 创作平台前端 | `POST /api/auth/login` | 学校创作账号（`ORG_ADMIN_*`，等同创作者） | 使用已开通的创作功能 |

核心规则：

1. **只有 `SUPER_ADMIN` 是管理员账号**（`t_user.role=admin`）；两个管理后台都只允许超管登录。
2. 每所学校一个组织、一个创作账号；账号 `t_user.role=user`，只有创作权限，不能登录任何管理后台。
3. 创建组织时只初始化 **`ORG_ADMIN_{orgId}` 一个角色**，**不分配任何权限**。
4. 每所学校一个共享创作角色，就是 `ORG_ADMIN_{orgId}`；以后同组织新增账号可以绑定同一角色，沿用权限和组织余额。
5. 不再创建 `NORMAL_ADMIN_*`；之前方案里的“共享创作角色”由 `ORG_ADMIN_{orgId}` 承担。
6. 甲方后台本期**不创建组织、不新增用户**；组织开户由另一个项目/服务端后台完成。

---

## 2. 账号与角色

| 角色 | 后台显示名 | 账号显示名 | `t_user.role` | 登录入口 |
|---|---|---|---|---|
| `SUPER_ADMIN` | 超级管理员 | 超级管理员 | `admin` | `/api/admin/login` |
| `ORG_ADMIN_{orgId}` | **组织创作角色** | **学校创作账号** | `user` | `/api/auth/login`（邮箱） |
| `NORMAL_ADMIN_*` | — | — | — | **废弃，不再创建/使用** |

说明：

- `ORG_ADMIN_*` 编码保留，不再命名“组织管理员”；后台展示为“组织创作角色”，账号展示为“学校创作账号”。
- 共享角色：同一组织多个创作账号可绑定同一个 `ORG_ADMIN_{orgId}`，权限与组织余额都按组织共享。
- 组织创作角色默认**无任何权限**（既无管理权限码，也无 feature）；由超管后续开通。
- `ORG_ADMIN_*` 不再把账号提升为 `role=admin`；只有 `SUPER_ADMIN` 会。
- 本期每校只生成一个创作账号；数据模型允许以后增加账号，但甲方后台本期不增加“创建用户”入口。

---

## 3. 两个管理后台的交付范围

### 3.1 服务端管理后台（Java `/admin`，内部）

- 超管登录；
- 创建组织、初始化 `ORG_ADMIN_*` 角色、创建学校创作账号；
- 管理端权限分配：用户管理、权限、角色、组织；
- 模型连接/预设、Agent、工作流、Studio Skill、系统设置；
- 用户/角色管理、权限码启停。

### 3.2 客户端管理后台（独立前端，甲方）

本期只提供以下能力：

| 能力 | 说明 |
|---|---|
| 查看学校 | `GET /api/organizations`，跨学校查看组织编码、名称、状态 |
| 管理已有账号 | 查看/编辑/禁用/重置已有学校创作账号；**不新增用户** |
| 开通功能 | 给 `ORG_ADMIN_{orgId}` 分配 25 项创作 feature |
| 配置模型 | 模型连接/预设等（超管权限） |
| 积分 | 组织积分充值、余额、账本查询；由 **Node** 提供 |

不提供：

- **创建组织**（由另一个项目/服务端后台开户）；
- **新增用户**（本期不增加入口）；
- 系统级内部配置页面（如不需要，前端隐藏；后端仍是超管权限）。

> 两个后台共用 `POST /api/admin/login` 和同一份超管 Token；各自登录、各自保存会话。由于两边都是完整超管权限，甲方凭据理论上也能调用内部超管接口，**必须通过部署网络/访问控制限制服务端后台的暴露范围**；如果以后需要强隔离，再拆独立超管账号或 scope。

---

## 4. 登录与会话

### 4.1 超级管理员登录【已有，调整】

`POST /api/admin/login`

```json
{ "username": "admin", "password": "admin123" }
```

- 只允许 `t_user.role=admin` 且绑定 `SUPER_ADMIN`；
- 响应 `{ token, user }`：`user.superAdmin=true`，`user.scope="admin"`；
- 两个后台都调用本接口；
- 错误：
  - `401` 用户名/密码错误、账号禁用或删除；
  - `403` 非超级管理员。

> 当前实现允许 `ORG_ADMIN_*` 登录，本期改为**仅超管**。

### 4.2 学校创作账号登录【已有，调整】

`POST /api/auth/login`

```json
{ "email": "admin@school.com", "password": "******" }
```

- 只允许 `t_user.role=user` 的学校创作账号；
- 创建组织时新增 `adminEmail`，用于本登录；
- 响应 `{ token, user }`：`user.scope="creator"`，`user.superAdmin=false`；
- 错误：
  - `401` 邮箱/密码错误、账号禁用或删除；
  - `403` 组织已禁用。

### 4.3 `GET /api/admin/me`【已有，调整】

超级管理员会话：

```json
{
  "id": 1,
  "username": "admin",
  "name": "超级管理员",
  "scope": "admin",
  "superAdmin": true,
  "role": "admin",
  "status": "active",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["user:view", "org:create", "..."]
}
```

### 4.4 `GET /api/auth/me`【已有，调整】

学校创作账号会话：

```json
{
  "id": 5,
  "name": "学校创作账号",
  "email": "admin@school.com",
  "scope": "creator",
  "superAdmin": false,
  "role": "user",
  "status": "active",
  "orgId": 3,
  "orgName": "XX学校",
  "orgStatus": 1,
  "roles": ["ORG_ADMIN_3"],
  "permissions": ["script", "image"],
  "features": {
    "script": true,
    "image": true,
    "video": false,
    "...": false
  },
  "token_version": 0,
  "created_at": "2026-11-05T00:00:00Z"
}
```

| 字段 | 说明 |
|---|---|
| `id` | 稳定用户 ID |
| `scope` | `admin` / `creator`【新增】 |
| `superAdmin` | 是否超管【新增】 |
| `orgId` / `orgName` | 当前组织 |
| `orgStatus` | `1` 启用 / `0` 禁用【新增】 |
| `status` | 账号状态 `active` / `disabled` / `deleted` |
| `roles` | 角色编码，学校账号为 `["ORG_ADMIN_3"]` |
| `permissions` | 权限码，学校账号为已开通 feature 编码 |
| `features` | 25 项创作功能快照，来自 `ORG_ADMIN_{orgId}` |

行为（统一口径）：

- 账号禁用/删除 → `401`；
- **组织禁用 → 登录 `403`；已有会话下一次请求 `401`**；
- 组织删除 → 组织内账号软删除，返回 `401`；
- 学校账号 `role=user`，调用 `/api/admin/**` 返回 `403`。

### 4.5 会话与撤权

- 角色/权限变更清理相关用户缓存，下一次请求按新权限生效；
- 组织禁用时建议同时递增该组织用户的 `tokenVersion`，让旧 Token 立即失效；
- 多实例部署下本地缓存最多 5 分钟陈旧；撤权/停用的生效边界见第 14 节。

---

## 5. 创建组织与初始化【调整】

> 本期由另一个项目/服务端后台开户；**甲方后台不提供创建组织**。

`POST /api/organizations` · 仅 `SUPER_ADMIN`

```json
{
  "orgCode": "SCHOOL_001",
  "orgName": "XX学校",
  "description": "可选",
  "adminUsername": "school_admin",
  "adminEmail": "admin@school.com",
  "adminPassword": "secret123",
  "adminRealName": "学校创作账号"
}
```

新增字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `adminEmail` | string | 是 | 学校创作账号登录邮箱，全局唯一 |

行为：

1. 创建组织；
2. 只创建 `ORG_ADMIN_{orgId}` 角色（后台显示“组织创作角色”），**不分配任何权限**；
3. 创建学校创作账号：
   - `t_user.role = user`；
   - `email = adminEmail`；
   - `status = 1`；
   - 绑定 `ORG_ADMIN_{orgId}`；
4. **不再创建 `NORMAL_ADMIN_{orgId}`**；
5. 账号用 `POST /api/auth/login`（email + password）登录创作平台。

响应：`Organization`。角色 ID 通过 `GET /api/roles?orgId={orgId}` 查找 `ORG_ADMIN_{orgId}`。

---

## 6. 组织创作角色授权（客户端后台）

### 6.1 取角色与功能列表

1. `GET /api/roles?orgId={orgId}`【已有】→ 找 `roleCode = "ORG_ADMIN_{orgId}"`；
2. `GET /api/permissions?scope=feature`【新增】→ 25 项 feature：

```json
[
  { "id": 16, "permissionCode": "text-studio", "permissionName": "文本工作室", "status": 1 },
  { "id": 17, "permissionCode": "script", "permissionName": "剧本创作", "status": 1 }
]
```

3. `GET /api/roles/{roleId}/permission-snapshot`【新增】→ 当前权限 + 版本：

```json
{
  "version": 3,
  "permissions": [
    { "id": 17, "permissionCode": "script", "permissionName": "剧本创作" }
  ]
}
```

### 6.2 保存角色权限【调整】

`POST /api/roles/{roleId}/permissions`

```json
{
  "permissionIds": [17, 18, 19],
  "expectedVersion": 3
}
```

- 仅 `SUPER_ADMIN` 可调用；
- 对 `ORG_ADMIN_{orgId}` 角色**只允许 25 项 feature**；传入管理端权限码 ID 返回 `400`；
- 全量覆盖：空数组表示全部关闭；
- `expectedVersion` 与当前 `t_role.permission_version` 不一致返回 `409`；
- 保存成功后 `permission_version + 1`，清理绑定该角色用户的会话缓存；
- 兼容：旧的数组 body 仍可接受（内部服务端后台），但客户端后台/新页面使用带版本的 object body。

### 6.3 授权界面

- **服务端后台**：角色权限只展示用户管理、权限、角色、组织管理端权限；不展示 25 项创作功能；
- **客户端后台**：组织创作角色只展示 25 项创作功能，默认全关；不展示管理端权限码（`user:*`、`org:*`、`role:*`）；
- 服务端后台不提供 `ORG_ADMIN_*` 的创作功能编辑入口（列表显示“功能在客户端配置”）；客户端后台保存前重新拉 `permission-snapshot`，冲突 `409` 时刷新后重试。

### 6.4 模型配置

- 模型连接/预设、Agent、工作流、Studio Skill、系统设置：仅 `SUPER_ADMIN`；
- 本期调整后只有超管 `role=admin`，现有接口天然只允许超管。

---

## 7. 创作平台（学校创作账号）

### 7.1 登录与权限

- `POST /api/auth/login`（email + password）；
- `GET /api/auth/permissions`：

```json
{
  "features": {
    "script": true,
    "image": true,
    "video": false,
    "...": false
  }
}
```

- 有效 feature 取自 `ORG_ADMIN_{orgId}` 共享角色；
- 不再使用个人 `USER_FEATURES_{userId}`（旧账号保留但忽略）；
- 超管修改角色权限后，下一次请求生效。

### 7.2 会话

`GET /api/auth/me` 返回 `id/orgId/orgName/orgStatus/status/features`。

### 7.3 禁止的管理接口（保留本人改密例外）

学校账号 `role=user`，不能访问：

- `/api/admin/**`；
- `/api/users/**`、`/api/roles/**`、`/api/organizations/**` 等管理接口；
- 通用角色/权限管理。

**例外**：本人改密 `PUT /api/users/{id}/password`，仅当 `id = 当前用户 id` 时允许，需提供 `oldPassword`；这是已验收的自助改密能力，不能被一并挡掉。修改他人密码仍禁止。

---

## 8. Node 服务对接

### 8.1 身份

Node 用创作平台 Token 调 `GET /api/auth/me`：

- 取 `id`（userId）、`orgId`、`orgName`、`orgStatus`、`status`、`features`；
- `status != active`、`orgStatus != 1`、`orgId <= 0`：拒绝新提交；
- 已受理任务按提交时记录的 `orgId` 结算/恢复，不随账号后续变化；
- Node 必须通过 Java 身份接口校验 Token，不能只验 JWT 签名/过期时间，否则旧 Token（已撤销/降级）仍可能被接受。

### 8.2 组织列表【新增】

`GET /api/internal/organizations`

```http
Authorization: Bearer <NODE_CONFIG_READ_TOKEN>
```

```json
{
  "items": [
    { "id": 3, "orgCode": "SCHOOL_001", "orgName": "XX学校", "status": 1 }
  ],
  "total": 1
}
```

- 复用 `NODE_CONFIG_READ_TOKEN`，仅供服务端只读调用；
- 未配置 Token `503`，Token 错误 `401`；
- 供 Node 组织积分充值/账本查询使用。

### 8.3 组织积分（Node 提供）

甲方后台的积分能力由 Node 提供，Java 侧不代理、不对应业务实现：

| 能力 | 建议接口（以 Node 契约为准） | 说明 |
|---|---|---|
| 组织积分充值 | `POST /api/admin/points/recharge` | 超管跨学校充值 |
| 组织余额查询 | `GET /api/admin/points/org/{orgId}/balance` | 按组织查余额 |
| 组织账本查询 | `GET /api/admin/points/org/{orgId}/ledger` | 按组织查流水/扣费记录 |

- 客户端后台调用 Node 接口，携带超管 Token；
- Node 校验超管身份（可调 `/api/admin/me` 校验 `superAdmin=true`）；
- 跨组织充值/查询由 Node 放行；普通学校账号不能调用；
- 新组织积分账户从零开始，历史测试余额/账单不迁移。

### 8.4 撤权与多实例

- 关闭功能/停用学校后，**新任务**在 Node 下一次身份/权限校验时停止受理；
- 已受理任务继续按提交时记录的组织结算/恢复；
- Java 本地缓存最多 5 分钟陈旧；Node 每次提交前调用 Java 身份/权限接口可做到立即生效，建议按此实现；
- 组织停用建议 Java 同时 `tokenVersion++`，旧 Token 立即 `401`。

---

## 9. 数据范围与错误码

| 场景 | 结果 |
|---|---|
| 学校账号调 `/api/admin/**` | `403` |
| 学校账号调 `/api/users/**`、`/api/roles/**`、`/api/organizations/**` | `403`，**本人改密 `PUT /api/users/{id}/password` 除外** |
| 学校账号跨组织访问 | `404` |
| 非超管调 `POST /api/admin/login` | `401/403` |
| 组织禁用：登录 | `403 组织已禁用` |
| 组织禁用：已有会话下一次请求 | `401` |
| Token 过期/账号禁用/删除 | `401` |
| 角色权限版本冲突 | `409` |
| 给 `ORG_ADMIN_*` 写管理权限码 | `400` |
| 未知 feature | `400` |
| 创建组织邮箱重复 | `409` |

---

## 10. 前端对接清单

### 服务端管理后台（Java `/admin`）

- 登录：`POST /api/admin/login`，仅超管；
- 创建组织：新增 `adminEmail`；
- 角色管理：只展示用户管理、权限、角色、组织管理端权限；`ORG_ADMIN_*` 的 25 项创作功能在客户端后台配置；
- 模型/工作流/Skill/系统设置：仅超管。

### 客户端管理后台（独立前端，甲方）

1. `POST /api/admin/login`（超管）→ 保存会话；
2. `GET /api/auth/me`（或 `/api/admin/me`）；
3. `GET /api/organizations?page=0&size=200` → 查看学校；
4. `GET /api/admin/users?...` → 查看已有学校创作账号；
5. 编辑/禁用/重置密码（不新增用户）；
6. `GET /api/roles?orgId={orgId}` → 找 `ORG_ADMIN_{orgId}`；
7. `GET /api/permissions?scope=feature` → 25 项功能；
8. `GET /api/roles/{roleId}/permission-snapshot` → 当前权限 + 版本；
9. `POST /api/roles/{roleId}/permissions`（带 `expectedVersion`）→ 保存；
10. 模型配置页面（仅超管）；
11. 积分充值/余额/账本：调用 Node 接口（超管 Token）。

### 创作平台前端

1. `POST /api/auth/login`（email + password）；
2. `GET /api/auth/permissions` → 控制菜单/入口；
3. 用 Token 调创作接口；
4. `401` 清 Token 跳登录；`403` 提示组织已禁用/无权限；
5. 本人改密保留 `PUT /api/users/{id}/password`。

---

## 11. 与当前实现的差异

| 项 | 当前 | 本期 |
|---|---|---|
| 学校账号 `role` | `admin` | `user` |
| 学校账号登录 | `/api/admin/login` | `/api/auth/login`（email） |
| 管理后台登录 | 超管 + 组织管理员 | **仅超管** |
| 创建组织角色 | `ORG_ADMIN_*` + `NORMAL_ADMIN_*` | 只建 `ORG_ADMIN_*` |
| `ORG_ADMIN_*` 默认权限 | 用户/角色管理 + `org:view` | **无权限** |
| `NORMAL_ADMIN_*` | 每组织创建 | 不再创建/使用 |
| 学校账号 feature 来源 | 个人 `USER_FEATURES_*` | `ORG_ADMIN_{orgId}` 共享角色 |
| 角色权限接口 | 数组 body，无版本 | `permission-snapshot` + `expectedVersion`，冲突 `409` |
| 创建组织 | 无 `adminEmail` | 新增 `adminEmail` |
| `GET /api/auth/me` | creator 无 `orgId/orgName` | 补齐 `orgId/orgName/orgStatus/scope` |
| 内部组织列表 | 无 | `GET /api/internal/organizations` |
| 组织禁用 | 无校验 | 登录 `403`、旧会话 `401` |

---

## 12. 已确认结论（前端 2026-11-05）

| # | 确认项 | 结论 |
|---|---|---|
| 1 | 学校登录方式 | **邮箱 + 密码**；新增 `adminEmail`，沿用 `/api/auth/login` |
| 2 | 角色显示名 | 后台显示“**组织创作角色**”，账号显示“**学校创作账号**”；接口保留 `ORG_ADMIN_{orgId}`，学校前台不展示角色 |
| 3 | 两个后台登录 | **共用登录接口和 Token 契约**，各自登录/保存会话；两边都是完整超管权限，需明确内部后台部署访问范围 |
| 4 | 角色授权界面 | **服务端后台只展示用户管理、权限、角色、组织管理端权限；客户端后台只展示 25 项创作功能，默认全关**；后端拒绝给 `ORG_ADMIN_*` 写管理权限码 |
| 5 | 多创作账号 | 本期只生成一个账号；数据模型允许以后多账号绑定同一角色；甲方后台本期不增加创建用户入口 |
| 6 | Node 组织列表凭据 | 复用 `NODE_CONFIG_READ_TOKEN`，仅供服务端只读内部接口 |

---

## 13. 实施补充：甲方后台交付范围

- **不做创建组织**：组织开户由另一个项目/服务端后台完成；甲方后台只查看已有学校。
- **不做新增用户**：本期每校一个账号；甲方后台只管理已有账号（查看/编辑/禁用/重置密码）。
- **做**：查看学校、管理已有账号、开通/关闭 25 项功能、配置模型、组织积分充值/余额/账本查询（Node）。
- 组织积分相关接口由 Node 提供，Java 保持 503 占位/网关直送 Node；前端不要调 Java 的积分路径。
- 服务端后台仍负责创建组织、初始化和内部配置。

---

## 14. 实施补充：存量升级与旧会话撤销

### 14.1 推荐路径：重建（旧环境不用了）

- 旧库/联调库直接删除重建，执行新 V1；
- 新库没有旧 `ORG_ADMIN_*` 的 `role=admin`、旧管理权限和旧 Token，验收最干净。

### 14.2 需要保留数据时

新 V1 与旧 V1 不兼容（结构和角色语义变化），不能直接在同一库上跑 Flyway。需要离线升级（备份后执行）：

1. **降级账号类型**：所有绑定 `ORG_ADMIN_*` 且非 `SUPER_ADMIN` 的账号 `t_user.role='user'`；
2. **清理旧权限**：删除 `ORG_ADMIN_*` 角色上的管理权限码关联（`user:*`、`org:*`、`role:*`），只保留/重新分配 25 项 feature；
3. **处理 `NORMAL_ADMIN_*`**：删除该角色的权限关联、用户绑定和角色本身（或保留为空角色但不再使用）；
4. **补齐邮箱**：旧组织管理员没有 `adminEmail`，必须由运维按账号清单补齐（邮箱全局唯一）；无法补齐的先禁用，避免无法登录；
5. **撤销旧 Token**：对受影响账号 `token_version = token_version + 1`；
6. **角色名称**：`ORG_ADMIN_*` 的 `role_name` 改为“组织创作角色”；
7. **Flyway**：升级后的库不再走旧 V1 版本链；建议作为新库/新基线管理，或按运维流程重建。

### 14.3 旧 Token 验收

- 升级前保留一个旧 `ORG_ADMIN_*` 管理员的 Token；
- 升级/重建后：
  - Java `/api/admin/me`、`/api/admin/**` 应返回 `401/403`；
  - Node 生成接口应返回 `401`；
  - 原因：`tokenVersion` 变化 + `role=user` + Node 通过 Java 身份接口校验。
- 如果 Node 只验 JWT 签名/过期时间，旧 Token 仍可能被接受，**必须改为调用 Java 身份接口**。

---

## 15. 实施补充：权限保存、多实例与撤权

### 15.1 权限保存

- `t_role` 新增 `permission_version INT NOT NULL DEFAULT 0`；
- `GET /api/roles/{roleId}/permission-snapshot` 返回 `{version, permissions}`；
- `POST /api/roles/{roleId}/permissions` 带 `expectedVersion`，不匹配 `409`；
- 保存成功 `permission_version + 1`，清缓存；
- 两个后台同时编辑同一角色时，后保存者收到 `409`，刷新后重试。

### 15.2 多实例撤权

- Java 本地缓存最多 5 分钟陈旧；单实例下一次请求生效；
- 多实例要求严格立即生效时，需共享缓存（Redis）或每次请求查 `permission_version`；
- Node 每次新任务提交前调用 Java `/api/auth/me` 或 `/api/auth/permissions`，可做到立即停止新任务；
- 已受理任务继续按提交时记录的组织结算/恢复。

### 15.3 停用学校

- 登录 `403`；
- 已有会话下一次请求 `401`（建议停用时 `tokenVersion++`）；
- Node 新任务在身份校验时停止；
- 已受理任务不变。

---

## 16. 验收清单

1. 超管可登录两个管理后台；学校创作账号登录 `POST /api/admin/login` 被拒绝。
2. 学校创作账号可用 `adminEmail` + 密码登录创作平台，`GET /api/auth/permissions` 返回 `ORG_ADMIN_{orgId}` 的 feature。
3. 创建组织只生成 `ORG_ADMIN_*` 一个角色且无权限；不再生成 `NORMAL_ADMIN_*`。
4. 超管在客户端后台给 `ORG_ADMIN_*` 分配 25 项 feature；学校账号下一次请求生效；管理权限码写入返回 `400`；服务端后台角色页只展示管理端权限。
5. 两个后台同时保存同一角色权限，后保存者收到 `409`。
6. 学校账号访问 `/api/admin/**`、`/api/users/**`、`/api/roles/**` 被拒绝；**本人改密 `PUT /api/users/{id}/password` 成功**。
7. 组织停用：登录 `403`、旧会话 `401`；Node 新任务停止，已受理任务按原组织结算。
8. 旧管理员 Token 在升级/重建后 Java 与 Node 均拒绝。
9. 甲方后台可查看学校、管理已有账号、开通功能、配置模型、调用 Node 积分接口；不出现创建组织/新增用户入口。

> 本稿为实施依据；当前代码已按第 11~16 节调整，单测 126 通过，并在新库完成 V1、登录、共享角色授权、版本冲突、本人改密和组织停用联调。
