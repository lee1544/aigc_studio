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

- **认证**：管理员 `POST /api/admin/login`、创作者 `POST /api/auth/login`；JWT 会话隔离；`/api/auth/me`、`/api/auth/permissions` 返回当前账号与权限快照
- **组织管理**：组织增删改查；创建组织时自动生成内置角色和初始管理员账号；删除组织级联清理用户与角色
- **用户管理**：组织内用户增删改查、启用/禁用、角色分配、密码修改与重置（重置为 `12345678` 并标记下次登录为首次登录）
- **角色管理**：组织内角色增删改查、角色-权限分配；内置角色（SUPER_ADMIN / ORG_ADMIN_* / NORMAL_ADMIN_*）受保护不可删除
- **权限管理**：权限编码全局定义，仅超级管理员可启用/禁用；权限变更与用户功能权限批量调整均记录审计；权限变更自动清除用户权限缓存
- **模型连接与预设**：服务连接 CRUD、连接检测、模型配置 CRUD（含删除标记持久化，防止被删除预设复活）
- **Agent 配置**：canvas / comic / short-drama 三套 Agent 配置的读取与保存
- **工作流配置**：工作流及模型 API 配置 CRUD、ComfyUI 连接检查（运行由 Node 提供）
- **Studio Skill**：画布可用 Skill 的公共列表与管理端 CRUD
- **系统设置**：`GET /api/public/settings` 读取平台参数，`PUT /api/admin/system-settings` 保存完整平台参数
- **普通项目**：创作者本人项目与项目内容 CRUD
- **内部配置快照**：`GET /api/internal/node-config`，供 Node 服务读取 Java 已保存的非计费配置（含真实密钥），独立服务令牌校验
- **管理后台**：`/admin` 静态页面（登录 / 概览 / 组织 / 权限 / 角色 / 用户），菜单按权限显隐

## 数据权限模型

- 数据按 `org_id` 隔离，请求上下文中的组织来自 JWT claim
- 权限编码形如 `user:view`、`org:create`、`role:assign-permission`，由 `@RequirePermission("...")` 注解 + AOP 切面校验
- 用户权限链路：`用户 → t_user_role → 角色 → t_role_permission → 权限`
- 任何非计费配置变更都会 bump 配置版本（`ConfigVersion`，单行 id=1），供内部 `node-config` 快照使用

## 快速开始

### 1. 初始化数据库

创建数据库 `aigc_studio`（utf8mb4，MySQL >= 8.0.16）：

```sql
CREATE DATABASE aigc_studio CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

新库：直接启动应用，Flyway 会自动执行 `src/main/resources/db/migration/V1__init.sql`，建表、建索引并写入种子数据，不需要再手工执行其他 SQL。

老库：先备份，再启动应用。Flyway 会先建立 version 0 基线，然后执行 V1；V1 对已存在的表只做幂等补列/补索引，不删除既有数据。若老库存在重复 `username`/`email`，唯一索引会创建失败并中止迁移，需要先人工清理。

V1 含 Flyway 占位符 `${tencent_tokenhub_key}` 等，mysql CLI 不会替换它们，因此**不要用 CLI 直接执行 V1 做正式初始化**；正式初始化只通过启动应用由 Flyway 执行。若确需离线执行，请先手动把占位符替换为实际值（或空串），并自行补写 `flyway_schema_history`。

任何手工执行的 SQL（包括历史脚本）都要显式指定 utf8mb4，否则 Windows 中文环境会按 gbk 解码，报 `Data too long for column 'role_name'`：

```bash
mysql --default-character-set=utf8mb4 -u root -p aigc_studio < your-script.sql
```

厂商密钥不写进 SQL，通过 Flyway 占位符注入：`spring.flyway.placeholders.*` 映射到环境变量 `TENCENT_TOKENHUB_KEY`、`DEEPSEEK_KEY`、`ALIYUN_BAILIAN_KEY_1`、`ALIYUN_BAILIAN_KEY_2`。`application.properties` 中默认值为空，未注入时对应模型会被置为 `enabled=0`；`application-dev.properties` / `application-prod.properties` 已填入实际 key，直接按 profile 启动即可。

> 这四个变量**只在 V1 第一次执行时生效**。如果新库已经启动过一次（V1 已记录到 `flyway_schema_history`），之后再设置环境变量不会回填数据库。此时请通过管理后台修改模型连接/模型配置，或新增一个 V2 迁移来补 key；开发环境也可以直接删库重建。

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
| 创作者登录 | POST `/api/auth/login` |

默认超级管理员：`admin / admin123`

## 主要 API（Java 归属）

认证（免 JWT）：`POST /api/admin/login`、`POST /api/auth/login`、`POST /api/auth/register`

| 模块 | 路径 | 所需权限 |
|---|---|---|
| 用户 | `GET/POST /api/users`，`GET/PUT/DELETE /api/users/{id}`，`/{id}/status`、`/{id}/password`、`/{id}/reset-password`、`/{id}/roles` | `user:*` |
| 组织 | `GET/POST /api/organizations`，`GET/PUT/DELETE /api/organizations/{id}` | `org:*` |
| 角色 | `GET/POST /api/roles`，`PUT/DELETE /api/roles/{id}`，`/{id}/permissions` | `role:*` |
| 权限 | `GET /api/permissions`，`PUT /api/permissions/{id}/status` | `role:view` / 超管 |
| 功能权限 | `GET /api/admin/users/{id}/permissions`，`PUT /api/admin/user-permissions`，`GET /api/admin/permission-audits` | `role:*` |
| 模型连接 | `GET/POST/PUT/DELETE /api/admin/model-connections[/{id}]`，`POST /{id}/check`，`GET /model-connection-checks` | 管理员 |
| 模型预设 | `GET/POST /api/admin/model-configs`，`PUT/DELETE /api/admin/model-configs/{feature}/{model}` | 管理员 |
| Agent 配置 | `GET/PUT /api/agent/{canvas\|comic\|short-drama}/config` | 管理员 |
| 工作流配置 | `GET/POST/PUT/DELETE /api/admin/workflows[/{id}]`，`POST /{id}/check` | 管理员 |
| Studio Skill | `GET /api/studio-skills`，`GET/POST/PUT/DELETE /api/admin/studio-skills[/{id}]` | 公开 / 管理员 |
| 系统设置 | `GET /api/public/settings`，`GET /api/admin/apis`，`PUT /api/admin/system-settings` | 公开 / 管理员 |
| 项目 | `GET/POST /api/projects`，`GET/DELETE /api/projects/{id}`，`GET/PUT /{id}/content`，`PATCH /{id}/module` | 会话 |
| 内部配置快照 | `GET /api/internal/node-config` | 服务令牌 `NODE_CONFIG_READ_TOKEN` |

> 积分/计费/生成/执行/任务/文件/模型目录等 Node 归属路由在 Java 侧统一返回 503，不在上表。完整 128 路由清单见 `static/docs/backend-handoff/route-index.md`。

统一响应体：`{code, message, data, timestamp}`；分页封装见 `dto/PageResponse`。财务/权限/模型配置响应头 `Cache-Control: no-store`。

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
├── db/migration/V1__init.sql       # Flyway 版本化迁移（新库建全表，老库幂等补列/索引）
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
```

网关需将 Node 归属路径（`/api/admin/points/**`、`/api/points/**`、`/api/billing/**`、`/api/generate/**`、`/api/text-studio/**`、`/api/workflows/**` 运行、`/api/music/**`、`/api/canvas/**`、`/api/digital-humans/**`、`/api/models`、签名媒体等）直送 Node，不经过本 Java 服务。
