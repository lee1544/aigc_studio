# AIGC Studio

AIGC Studio 的 Java 后端服务：账号与权限（RBAC）、非计费配置（模型连接/模型预设/Agent/工作流/Skill/系统设置）、普通项目数据，以及供 Node 服务读取的内部配置快照。

本服务由原 Node 后端移植拆分而来。128 个现有 `/api` 操作中，Java 负责 49 个；积分/计费/生成执行/任务/文件/模型目录等 79 个操作仍由 Node 服务提供，Java 侧对这些路径统一返回 503，由网关将请求直送 Node。

## 技术栈

| 组件 | 说明 |
|---|---|
| Java | 1.8 |
| 框架 | Spring Boot 2.6.13（Web / Security / Data JPA / Validation） |
| 安全 | Spring Security + JWT（jjwt 0.9.1，HS512，默认有效期 7 天） |
| 数据库 | MySQL 8 + Druid 连接池 1.2.20 |
| 缓存 | Guava 32.0.1（JWT 黑名单、用户权限缓存） |
| 构建 | Maven |

## 服务边界（Java / Node 归属）

| 归属 | 范围 | Java 行为 |
|---|---|---|
| **Java** | 认证/账号、组织/用户/角色/权限、模型连接与预设、Agent 配置、工作流配置、Studio Skill、系统设置、普通项目、内部配置快照 | 真实持久化，返回业务结果 |
| **Node** | 积分账户/流水/策略、报价/冻结/扣费、计价与成本、统一生成入口、任务查询、签名媒体、运行记录/概览统计、文本工作室 SSE、工作流运行、音乐、画布、数字人、公开模型目录、AutoDL 实例 | `NodeOwnedController` 统一返回 503 `{code:503, message:"该能力由 Node 服务提供，Java 不再重复实现"}` |

> 网关负责把 Node 归属路径直送 Node。联调中若在 Java 侧收到 503，说明请求路由错了，不是功能异常。详见 `static/docs/frontend-integration.md` 与 `static/docs/admin-api-handoff.md`。

## 功能概览

- **认证**：超级管理员 `POST /api/admin/login`、学校创作账号 `POST /api/auth/login`；JWT 会话隔离；`/api/auth/me`、`/api/auth/permissions` 返回当前账号、组织与权限快照
- **组织管理**：仅超级管理员可创建/修改/删除组织；创建组织时在同一事务内生成**一个** `ORG_ADMIN_{orgId}` 组织创作角色（默认无任何权限），并用 `adminEmail` + 账号信息创建学校创作账号（`role=user`）绑定该角色；默认组织受保护不可删除；删除组织级联清理用户与角色
- **用户管理**：超级管理员可跨组织管理学校创作账号；学校创作账号 `role=user`，不能登录任何管理后台
- **角色管理**：服务端后台的角色权限只分配用户管理、权限、角色、组织管理端权限；`ORG_ADMIN_{orgId}` 为组织创作角色，其 25 项创作 feature 在客户端后台维护；`NORMAL_ADMIN_*` 已废弃，不再创建
- **权限管理**：权限编码全局定义，仅超级管理员可启停；权限变更与用户功能权限批量调整均记录审计；权限变更自动清除用户权限缓存
- **模型连接与预设**：服务连接 CRUD、连接检测、模型配置 CRUD（含删除标记持久化，防止被删除预设复活）
- **Agent 配置**：canvas / comic / short-drama 三套 Agent 配置的读取与保存
- **工作流配置**：工作流及模型 API 配置 CRUD、ComfyUI 连接检查（运行由 Node 提供）
- **Studio Skill**：画布可用 Skill 的公共列表与管理端 CRUD
- **系统设置**：`GET /api/public/settings` 读取平台参数，`PUT /api/admin/system-settings` 保存完整平台参数
- **普通项目**：创作者本人项目与项目内容 CRUD
- **内部配置快照**：`GET /api/internal/node-config`、`GET /api/internal/organizations`，供 Node 服务读取非计费配置与只读组织列表，独立服务令牌校验
- **管理后台**：服务端管理后台（Java `/admin`）与客户端管理后台均为**仅超级管理员**登录；服务端后台负责组织/用户/角色/权限/模型等管理，角色授权只展示管理端权限；客户端后台负责查看学校、管理已有账号、开通 25 项创作功能、配置模型和积分

## 数据权限模型

系统仍然完全通过 RBAC 管理权限，分为“角色 → 权限码”和“数据范围”两层：

### 角色层级

| 角色 | 范围 | 说明 |
|---|---|---|
| `SUPER_ADMIN` | 全局（`org_id = NULL`） | 超级管理员：唯一的管理员账号，拥有全部启用权限，可查看和管理所有组织的数据 |
| `ORG_ADMIN_{orgId}` | 组织 | 组织创作角色（学校创作账号共享角色）：账号 `role=user`，默认无权限，由超管分配 25 项创作 feature，只能登录创作平台 |
| `NORMAL_ADMIN_{orgId}` | 组织 | 已废弃，不再创建/使用 |
| `USER_FEATURES_{userId}` | 个人 | 旧创作者个人 feature 载体，仅历史兼容；绑定组织创作角色的账号不再使用 |

### 初始化不变量

启动时 Flyway 先执行 `V1__init.sql` 种子数据，随后 `RbacInitializer`（`RbacBootstrapService`）幂等补齐：

1. 默认组织 `DEFAULT_ORG`（“默认组织”），平台级账号归属该组织；
2. 超级管理员角色 `SUPER_ADMIN`，并分配**所有启用中**的权限（后续新增权限在下次启动时自动补齐，不会清空已有授权）；
3. 默认超级管理员账号 `admin / admin123`（仅不存在时创建，不会重置已有密码），并绑定 `SUPER_ADMIN`；
4. 每个组织的 `ORG_ADMIN_{orgId}` 组织创作角色（缺失时创建，默认无权限）。

### 数据范围

- **超级管理员**：组织、角色、用户、功能权限等列表均不限制组织，可看到全部组织的数据；可创建/修改/删除组织。
- **学校创作账号**：`role=user`，只能访问创作平台和本人数据；不能访问 `/api/admin/**`、`/api/users/**`、`/api/roles/**`、`/api/organizations/**` 等管理接口；本人改密 `PUT /api/users/{id}/password` 例外。
- 模型连接/模型配置、Agent/工作流/Skill、系统设置、权限码启停均为仅超级管理员可用的全局配置。
- 创建组织时**必须**提交学校创作账号信息（`adminUsername` / `adminEmail` / `adminPassword` / `adminRealName`）；组织、`ORG_ADMIN_{orgId}` 组织创作角色、账号与角色绑定在同一事务内完成，默认不分配任何权限。
- 权限校验链路：`用户 → t_user_role → 角色 → t_role_permission → 权限`；`@SuperAdminOnly` 负责平台级操作边界，服务层再按 `org_id` 做数据范围校验。
- `t_user.role`（`admin` / `user`）只由 `SUPER_ADMIN` 决定；`ORG_ADMIN_*`、`NORMAL_ADMIN_*`（已废弃）与用户自建角色都保持 `user`，不能登录管理后台。
- 任何非计费配置变更都会 bump 配置版本（`ConfigVersion`，单行 id=1），供内部 `node-config` 快照使用。

## 快速开始

### 1. 初始化数据库

创建数据库 `aigc_studio`（utf8mb4，MySQL >= 8.0.16）：

```sql
CREATE DATABASE aigc_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

新库初始化：直接启动应用。Flyway 会执行唯一的 `src/main/resources/db/migration/V1__init.sql`，一次建全 19 张表、索引，以及 RBAC/权限/平台设置/模型配置种子。Flyway 完成后，`RbacInitializer` 会幂等核对默认组织、超级管理员角色的全量启用权限、默认超管账号与各组织内置角色（缺失才创建，不覆盖已有配置）。

> 当前只保留 `V1__init.sql`，且 V1 面向**全新空库**：原有的 V2~V6 迁移和独立全量脚本已删除，旧库不再兼容，请重建数据库。
> V1 含 Flyway 占位符 `${tencent_tokenhub_key}` 等，mysql CLI 不会替换它们，因此不要用 CLI 直接执行 V1；初始化只通过启动应用由 Flyway 执行。

任何手工执行的 SQL（包括历史脚本）都要显式指定 utf8mb4，否则 Windows 中文环境会按 gbk 解码，报 `Data too long for column 'role_name'`：

```bash
mysql --default-character-set=utf8mb4 -u root -p aigc_studio < your-script.sql
```

厂商密钥：通过 `spring.flyway.placeholders.*` 注入，映射到环境变量 `TENCENT_TOKENHUB_KEY`、`DEEPSEEK_KEY`、`ALIYUN_BAILIAN_KEY_1`、`ALIYUN_BAILIAN_KEY_2`。`application.properties` 中默认值为空，未注入时对应模型会被置为 `enabled=0`；`application-dev.properties` / `application-prod.properties` 已填入实际 key，直接按 profile 启动即可。

> 这四个占位符**只在 V1 第一次执行时生效**。如果新库已经启动过一次（V1 已记录到 `flyway_schema_history`），之后再设置环境变量不会回填数据库。此时请通过管理后台修改模型连接/模型配置，或新增一个迁移来补 key；开发环境也可以直接删库重建。

### 2. 配置并启动（本地开发）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 数据库连接、JWT 密钥等通过环境变量注入：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`
- `application-dev.properties` 已配置本地默认值（含厂商 key），该文件被 `.gitignore` 忽略，不进入 Git
- 开发模式 bypass token：`DEV_BYPASS_TOKEN`（设为 `DEV-BYPASS-DEBUG-TOKEN` 后可用 `Authorization: Bearer` 头直接以 developer 身份访问，生产环境留空禁用）
- 内部配置快照令牌：`NODE_CONFIG_READ_TOKEN`（留空则 `GET /api/internal/node-config` 返回 503；该值必须与用户/管理员登录 token 完全独立，仅经受控内网或 HTTPS 交付，不得进入前端或 Git）

### 2.1 正式环境启动

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
java -jar aigc-studio.jar --spring.profiles.active=prod
```

- `application-prod.properties` 含生产数据库默认值、独立 JWT 密钥、内部配置令牌和厂商 key，同样被 `.gitignore` 忽略
- 该文件**不会随 Git 发布**，部署时需单独拷贝到目标机器的 `classpath`（打成 jar 时放在 jar 同级 `config/` 目录，或直接打进产物），或全部改用环境变量覆盖
- 生产环境 `dev.bypass-token` 为空，禁用开发 bypass；`MEDIA_*_DIR` 默认指向 `/data/aigc/media/*`
- 其余变量优先级：环境变量 > `application-prod.properties` 中的默认值

### 3. 访问

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost:8089/admin |
| 管理员登录 | POST `/api/admin/login` |
| 创作者登录 | POST `/api/auth/login`（用户名 + 密码） |

默认超级管理员：`admin / admin123`（首次启动自动创建，归属默认组织；生产环境请立即修改密码）。

## 主要 API（Java 归属）

> 完整接口文档（请求/响应字段、错误码、分页、权限模型、Node 占位路由）见 [`docs/API.md`](docs/API.md)。
> 两个管理后台（均仅超管）+ 创作平台的账号与 RBAC 前端对接确认稿见 [`docs/frontend-rbac-confirmation.md`](docs/frontend-rbac-confirmation.md)。

认证（免 JWT）：`POST /api/admin/login`、`POST /api/auth/login`、`POST /api/auth/register`

| 模块 | 路径 | 所需权限 |
|---|---|---|
| 用户 | `GET/POST /api/users`，`GET/PUT/DELETE /api/users/{id}`，`/{id}/status`、`/{id}/password`、`/{id}/reset-password`、`/{id}/roles`（旧版，建议改用 `/api/admin/users`） | `user:*`（仅超管；学校账号仅本人 `/{id}/password`） |
| 组织 | `GET/POST /api/organizations`，`GET/PUT/DELETE /api/organizations/{id}` | `org:*`；创建/修改/删除仅超管 |
| 角色 | `GET/POST /api/roles`，`PUT/DELETE /api/roles/{id}`，`/{id}/permissions`、`/{id}/permission-snapshot` | `role:*`（仅超管） |
| 权限 | `GET /api/permissions`（`scope=feature` 返回 25 项 feature），`PUT /api/permissions/{id}/status` | `role:view` / `role:update`（仅超管） |
| 功能权限 | `GET /api/admin/users/{id}/permissions`，`PUT /api/admin/user-permissions`，`GET /api/admin/permission-audits` | `user:view` / `user:update`（仅超管；组织创作角色的功能在角色权限中维护） |
| 模型连接 | `GET/POST/PUT/DELETE /api/admin/model-connections[/{id}]`，`POST /{id}/check`，`GET /model-connection-checks` | 超级管理员 |
| 模型预设 | `GET/POST /api/admin/model-configs`，`PUT/DELETE /api/admin/model-configs/{feature}/{model}` | 超级管理员 |
| Agent 配置 | `GET/PUT /api/agent/{canvas\|comic\|short-drama}/config` | 读：公开/登录；写：超级管理员 |
| 工作流配置 | `GET/POST/PUT/DELETE /api/admin/workflows[/{id}]`，`POST /{id}/check` | 超级管理员 |
| Studio Skill | `GET /api/studio-skills`，`GET/POST/PUT/DELETE /api/admin/studio-skills[/{id}]` | 公开 / 超级管理员 |
| 系统设置 | `GET /api/public/settings`，`GET /api/admin/apis`，`PUT /api/admin/system-settings` | 公开 / 管理员 |
| 项目 | `GET/POST /api/projects`，`GET/DELETE /api/projects/{id}`，`GET/PUT /{id}/content`，`PATCH /{id}/module` | 会话 |
| 内部配置快照 | `GET /api/internal/node-config` | 服务令牌 `NODE_CONFIG_READ_TOKEN` |

> 积分/计费/生成/执行/任务/文件/模型目录等 Node 归属路由在 Java 侧统一返回 503，不在上表。完整 128 路由清单见 `static/docs/backend-handoff/route-index.md`。

成功响应通常直接返回业务 JSON；失败统一为 `{code, message, data, timestamp}`。分页封装见 `dto/PageResponse`。财务/权限/模型配置响应头 `Cache-Control: no-store`。

分页：`GET /api/projects` 与 `GET /api/admin/workflows` 支持可选 `page` / `size`。不传时保持原数组响应；传入时返回 Spring `Page` 结构（`content/totalElements/totalPages/...`），`size` 上限分别为 200 / 100。

会话撤销：改密、禁用、软删除、退出都会递增 `t_user.token_version`，旧 token 立即失效；退出会同时登出该账号的其他会话（当前为单会话模型）。角色权限变更在事务提交后失效相关用户的会话缓存。

## 目录结构

```
src/main/java/com/example/aigc
├── annotation       # @RequirePermission
├── config           # Security / CORS / 缓存头
├── constant         # 角色编码、默认密码等常量
├── dto              # 请求/响应对象、分页封装、contract DTO
├── entity           # JPA 实体（组织/用户/角色/权限/模型配置/项目/配置版本）
├── exception        # 业务异常体系 + 全局处理器 + ApiResponse
├── filter           # JWT 过滤器、权限切面、Guava 缓存
├── repository       # Spring Data JPA
├── service          # 认证/组织/用户/角色/权限/模型连接/配置版本/项目等服务
├── utils            # JWT 工具、请求上下文工具、Schema 诊断
└── web              # Controller（/api/**、/admin 重定向）
```

```
src/main/resources
├── application.properties          # 环境变量占位，不含密钥
├── application-dev.properties      # 本地开发默认值（含厂商 key，Git 忽略）
├── application-prod.properties     # 正式环境默认值（含厂商 key，Git 忽略）
├── db/migration/V1__init.sql       # 唯一初始化迁移：全新库建全表 + RBAC/权限/平台设置/模型配置种子
└── static
    ├── admin/                      # 管理后台静态页面与资源
    └── docs/                       # 移植契约、路由索引、OpenAPI、前端联调文档
```

## 部署

```bash
mvn clean package
java -jar target/aigc_studio-0.0.1-SNAPSHOT.jar
# 环境变量：DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET / NODE_CONFIG_READ_TOKEN
# 可选：TENCENT_TOKENHUB_KEY / DEEPSEEK_KEY / ALIYUN_BAILIAN_KEY_1 / ALIYUN_BAILIAN_KEY_2
# 跨域：CORS_ALLOWED_ORIGINS（逗号分隔；同源部署可忽略，分域部署务必显式配置）
```

网关需将 Node 归属路径（`/api/admin/points/**`、`/api/points/**`、`/api/billing/**`、`/api/generate/**`、`/api/text-studio/**`、`/api/workflows/**` 运行、`/api/music/**`、`/api/canvas/**`、`/api/digital-humans/**`、`/api/models`、签名媒体等）直送 Node，不经过本 Java 服务。

## 本地构建与测试

项目当前以 Java 8 + Spring Boot 2.6.13 编译。若本机 Maven 仍是 3.0.x，`mvn test` 可能因 surefire 与 Maven 版本不兼容、或离线仓库缺少 `surefire-junit-platform` 而失败；这只影响本地测试执行，不影响 `mvn -o -DskipTests compile`。

建议本地/CI 使用 Maven 3.9.x 与 JDK 8u202+（或 JDK 11/17，需同步升级 Spring Boot），并尽快补上 Maven Wrapper（`mvnw`）以固定构建工具版本。后续升级 Spring Boot 3.x 时需同时处理 `javax.* → jakarta.*` 与 jjwt 0.9.x → 0.11+。
