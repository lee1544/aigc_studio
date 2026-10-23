# AIGC Studio

AIGC Studio 应用基础模块：提供组织管理、用户管理、角色管理、权限管理一套完整的 RBAC 后台能力（移植自 ask_the_wall 用户权限模块）。

## 技术栈

| 组件 | 说明 |
|---|---|
| Java | 1.8 |
| 框架 | Spring Boot 2.6.13（Web / Security / Data JPA / Validation） |
| 安全 | Spring Security + JWT（jjwt 0.9.1，HS512，有效期 7 天） |
| 数据库 | MySQL 8 + Druid 连接池 1.2.20 |
| 缓存 | Guava（JWT 黑名单、用户权限缓存） |
| 构建 | Maven |

## 功能概览

- **组织管理**：组织增删改查；创建组织时自动生成「组织管理员」「普通管理员」角色和初始管理员账号；删除组织级联清理用户与角色
- **用户管理**：组织内用户增删改查、启用/禁用、角色分配、密码修改、重置密码（重置为 `12345678` 并标记下次登录为首次登录）
- **角色管理**：组织内角色增删改查、角色-权限分配；内置角色（SUPER_ADMIN / ORG_ADMIN_* / NORMAL_ADMIN_*）受保护不可删除
- **权限管理**：权限编码全局定义，仅超级管理员可启用/禁用；权限变更自动清除用户权限缓存
- **管理后台**：`/admin` 静态页面（登录 / 概览 / 组织 / 权限 / 角色 / 用户），菜单按权限显隐

## 数据权限模型

- 数据按 `org_id` 隔离，请求上下文中的组织来自 JWT claim
- 权限编码形如 `user:view`、`org:create`、`role:assign-permission`，由 `@RequirePermission("...")` 注解 + AOP 切面校验
- 用户权限链路：`用户 → t_user_role → 角色 → t_role_permission → 权限`

## 快速开始

### 1. 初始化数据库

创建数据库 `aigc_studio`（utf8mb4），执行幂等脚本：

```sql
source src/main/resources/static/system_init.sql
```

### 2. 配置并启动（本地开发）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- 数据库连接默认 `jdbc:mysql://localhost:3306/aigc_studio`，可通过环境变量 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 覆盖
- `application-*.properties` 不入库，生产配置通过环境变量注入：`DB_URL`、`JWT_SECRET`、`DEV_BYPASS_TOKEN` 等
- 开发模式 bypass token：`DEV-BYPASS-DEBUG-TOKEN`（`Authorization: Bearer` 头直接以 developer 身份访问，生产环境留空禁用）

### 3. 访问

| 入口 | 地址 |
|---|---|
| 管理后台 | http://localhost:8081/admin |
| 登录接口 | POST `/api/auth/login` |

默认超级管理员：`admin / admin123`

## 主要 API

认证（免 JWT）：`POST /api/auth/login`、`POST /api/auth/logout`

| 模块 | 路径 | 所需权限 |
|---|---|---|
| 用户 | `GET/POST /api/users`，`GET/PUT/DELETE /api/users/{id}`，`/{id}/status`、`/{id}/password`、`/{id}/reset-password`、`/{id}/roles` | `user:*` |
| 组织 | `GET/POST /api/organizations`，`GET/PUT/DELETE /api/organizations/{id}` | `org:*` |
| 角色 | `GET/POST /api/roles`，`PUT/DELETE /api/roles/{id}`，`/{id}/permissions` | `role:*` |
| 权限 | `GET /api/permissions`，`PUT /api/permissions/{id}/status` | `role:view` / `role:update`（超管） |

统一响应体：`{code, message, data, timestamp}`；分页封装见 `dto/PageResponse`。

## 目录结构

```
src/main/java/com/example/aigc
├── annotation   # @RequirePermission
├── config       # Security / CORS
├── constant     # 角色编码、默认密码等常量
├── dto          # 请求/响应对象、分页封装
├── entity       # JPA 实体（t_ 表）
├── exception    # 业务异常体系 + 全局处理器 + ApiResponse
├── filter       # JWT 过滤器、权限切面、Guava 缓存
├── repository   # Spring Data JPA
├── service      # 组织/用户/角色/权限/认证服务
├── utils        # JWT 工具、请求上下文工具
└── web          # Controller（/api/**）
```

## 部署

```bash
mvn clean package
java -jar target/aigc_studio-0.0.1-SNAPSHOT.jar
# 环境变量：DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET 等，无需 dev profile
```
