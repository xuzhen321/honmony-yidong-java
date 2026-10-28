# 移动应用开发 · 后端 demo

Spring Boot 4.1.2 + Java 17 + Lombok + MyBatis-Plus 3.5.18 + MySQL 8。
已实现 **登录、退出登录、修改密码、修改昵称、用户管理、Token 鉴权、统一响应、全局异常处理**。

完整的接口说明见：[`docs/用户登录接口文档.md`](docs/用户登录接口文档.md)

前端联调请看：[`docs/前后端联调文档-登录模块.md`](docs/前后端联调文档-登录模块.md)

---

## 一、环境与启动

### 1.1 前置条件

- JDK 17+
- MySQL 8.x（本机 `localhost:3306`）

### 1.2 数据库配置（默认不会自动建库建表）

`src/main/resources/application.yaml` 里默认连接：

| 配置项 | 默认值 | 环境变量 |
| --- | --- | --- |
| 地址 | `jdbc:mysql://localhost:3306/harmony_test` | `DB_URL` |
| 用户名 | `root` | `DB_USERNAME` |
| 密码 | 空（必须传入） | `DB_PASSWORD` |

密码不写进仓库，启动时用环境变量传入：

```bash
# Git Bash / macOS / Linux
DB_PASSWORD=你的密码 ./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
$env:DB_PASSWORD="你的密码"; ./mvnw spring-boot:run
```

首次使用请**手动**执行初始化脚本（应用启动时不会自动改数据库）：

```bash
mysql -uroot -p < src/main/resources/db/init.sql
```

脚本会：

- 创建数据库 `harmony_test`（`utf8mb4`）
- 创建用户表 `user`（含 `role`、`deleted` 字段）
- 插入测试账号 `admin`（密码 `123456`，角色 `ADMIN`）

如果数据库里**已经有** `user` 表，`init.sql` 的 `CREATE TABLE IF NOT EXISTS` 不会补字段，
需要再执行一次迁移脚本：

```bash
mysql -uroot -p harmony_test < src/main/resources/db/migration_user_add_role_deleted.sql
```

测试账号完全由 `init.sql` 负责，应用启动时不会自动插入数据。

### 1.3 启动

```bash
./mvnw spring-boot:run
```

或者打包后运行：

```bash
./mvnw -DskipTests package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

启动后监听 `http://localhost:8080`。内置测试账号：

| 用户名 | 密码 | 昵称 | 角色 |
| --- | --- | --- | --- |
| admin | 123456 | 管理员 | ADMIN |

---

## 二、目录结构

按经典分层拆分，各层职责单一：

```
com.example.demo
├── DemoApplication.java              启动类
├── common/                           统一约定
│   ├── Result.java                   统一响应体 {code, msg, data}
│   └── BusinessException.java        业务异常（携带 HttpStatus + 错误信息）
├── config/
│   ├── WebConfig.java                拦截器注册 + 跨域
│   ├── PasswordConfig.java           BCryptPasswordEncoder Bean
│   ├── OpenApiConfig.java            Swagger / OpenAPI 文档配置
│   └── MybatisPlusConfig.java        分页插件 PaginationInnerInterceptor
├── controller/
│   ├── UserController.java           /user/login、/user/info、/user/logout、/user/password、/user/nickname
│   └── UserAdminController.java      /user/page、冻结、恢复
├── service/
│   ├── AuthService.java              登录校验、发 token、修改密码
│   └── UserAdminService.java         管理员分页、冻结、恢复
├── mapper/
│   └── UserMapper.java               MyBatis-Plus Mapper，含管理端分页/恢复 SQL
├── pojo/
│   ├── dto/
│   │   ├── LoginRequest.java         登录入参
│   │   ├── ChangePasswordRequest.java 修改自己的密码入参
│   │   ├── AdminResetPasswordRequest.java 管理员重置用户密码入参
│   │   └── UpdateNicknameRequest.java 修改自己的昵称入参
│   ├── vo/
│   │   ├── LoginResult.java          登录/当前用户返回
│   │   ├── PageResult.java           通用分页结果
│   │   └── UserListItemVO.java       用户列表项（不含密码）
│   └── entity/
│       └── User.java                 用户实体，@TableName("user")
├── security/
│   ├── TokenStore.java               token 的发放 / 校验 / 销毁
│   └── AuthInterceptor.java          登录拦截器
└── exception/
    └── GlobalExceptionHandler.java   全局异常处理
```

资源文件：

```
src/main/resources
├── application.yaml                  数据源、MyBatis-Plus、token 有效期
└── db/
    ├── init.sql                      初始化脚本：建库、建表、插入测试账号
    └── migration_user_add_role_deleted.sql  已有库补 role / deleted 字段
```

POJO 使用 Lombok 生成 getter/setter/构造方法：

- `@Data`：getter、setter、equals、hashCode、toString
- `@NoArgsConstructor` / `@AllArgsConstructor`：无参 / 全参构造

---

## 三、MyBatis-Plus 用法

`UserMapper` 是真正的 MyBatis-Plus Mapper：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {

    // 管理端分页、忽略逻辑删除查询、恢复账号等自定义 SQL
    // 具体见 src/main/java/com/example/demo/mapper/UserMapper.java
}
```

继承 `BaseMapper` 后直接拥有单表 CRUD，不需要手写 SQL：

```java
// 按用户名查询
User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
        .eq(User::getUserName, userName));

// 按主键查询
User user = userMapper.selectById(userId);

// 新增
userMapper.insert(user);
```

实体与表字段映射：

| Java 字段 | 数据库字段 | 说明 |
| --- | --- | --- |
| `id` | `id` | `@TableId(value = "id", type = IdType.AUTO)` 自增主键 |
| `userName` | `user_name` | `@TableField("user_name")` |
| `password` | `password` | `@TableField("password")`，BCrypt 密文 |
| `nickName` | `nick_name` | `@TableField("nick_name")` |
| `role` | `role` | `@TableField("role")`，字符串，存 `ADMIN` / `USER` |
| `deleted` | `deleted` | `@TableLogic`，逻辑删除：0 正常，1 已删除 |

用户表结构见 [`src/main/resources/db/init.sql`](src/main/resources/db/init.sql)，表名为 `user`。

`deleted` 字段由 MyBatis-Plus 的逻辑删除统一管理（`@TableLogic` + `application.yaml` 的 `logic-delete-field`）：
查询会自动追加 `deleted = 0`，`deleteById` 会变成 `UPDATE user SET deleted = 1`，不会真正删数据。

分页依赖 `MybatisPlusConfig` 里的 `PaginationInnerInterceptor`。
管理端用户分页用 `UserMapper.selectUserPage` 自定义 SQL，因为 `@TableLogic` 默认只查 `deleted = 0`，
而管理端需要能查已冻结用户；恢复账号也用自定义 `UPDATE`，否则会被逻辑删除条件挡住。

---

## 四、统一响应约定

```json
{ "code": 1, "msg": "", "data": { } }
```

- `code` **只有两个值**：`1 = 成功`，`0 = 失败`
- `msg` **只有失败时才有内容**，成功时为空字符串
- 失败时 `data` 为 `null`

HTTP 状态码遵循标准语义：

| 场景 | HTTP 状态码 | body.code |
| --- | --- | --- |
| 成功 | 200 | 1 |
| 参数校验失败 / JSON 格式错误 | 400 | 0 |
| 未登录 / token 失效 / 用户名或密码错误 | 401 | 0 |
| 无权限 | 403 | 0 |
| 接口路径不存在 | 404 | 0 |
| 请求方式不支持 | 405 | 0 |
| Content-Type 不支持 | 415 | 0 |
| 服务器内部错误 | 500 | 0 |

---

## 五、接口速览

| 方法 | 路径 | 是否登录 | 说明 |
| --- | --- | --- | --- |
| POST | `/user/login` | 否 | 登录，返回 token |
| GET | `/user/info` | 是 | 获取当前登录用户 |
| POST | `/user/logout` | 是 | 退出登录，使 token 立即失效 |
| PUT | `/user/password` | 是 | 修改自己的密码，需要原密码 |
| PUT | `/user/nickname` | 是 | 修改自己的昵称，不需要重新登录 |
| PUT | `/user/password/{userId}` | 是（仅管理员） | 重置用户密码，包括其他管理员和自己 |
| GET | `/user/page` | 是（仅管理员） | 分页查询用户，条件 `userName`、`deleted` |
| DELETE | `/user/{userId}` | 是（仅管理员） | 冻结用户（逻辑删除），`data=null` |
| PUT | `/user/{userId}/restore` | 是（仅管理员） | 恢复用户，`data=null` |

请求示例：

```bash
# 登录
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"userName":"admin","password":"123456"}'

# 带 token 访问
curl http://localhost:8080/user/info \
  -H "Authorization: Bearer {token}"

# 退出
curl -X POST http://localhost:8080/user/logout \
  -H "Authorization: Bearer {token}"

# 修改自己的密码
curl -X PUT http://localhost:8080/user/password \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"oldPassword":"123456","newPassword":"654321"}'

# 修改自己的昵称
curl -X PUT http://localhost:8080/user/nickname \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"nickName":"张三"}'

# 管理员重置用户密码（用户 ID 2）
curl -X PUT http://localhost:8080/user/password/2 \
  -H "Authorization: Bearer {adminToken}" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"654321"}'

# 管理员分页查询用户
curl "http://localhost:8080/user/page?pageNum=1&pageSize=10&deleted=0" \
  -H "Authorization: Bearer {adminToken}"

# 冻结用户
curl -X DELETE http://localhost:8080/user/2 \
  -H "Authorization: Bearer {adminToken}"

# 恢复用户
curl -X PUT http://localhost:8080/user/2/restore \
  -H "Authorization: Bearer {adminToken}"
```

> 修改密码成功后，目标用户的所有已登录 token 会立即失效，需要重新登录。
> 修改昵称不会使 token 失效，修改后不需要重新登录。
> 冻结是逻辑删除（`deleted=1`），不会物理删除；冻结/恢复后目标用户的所有 token 也会失效。

详细的字段说明、成功/失败响应示例、状态码对照见
[`docs/用户登录接口文档.md`](docs/用户登录接口文档.md)。

给前端同学看的联调版见
[`docs/前后端联调文档-登录模块.md`](docs/前后端联调文档-登录模块.md)。

### 自动接口文档（Swagger UI）

项目已接入 **springdoc-openapi 3.2.0**，启动后可直接在线查看和调试接口：

| 地址 | 说明 |
| --- | --- |
| http://localhost:8080/swagger-ui.html | Swagger UI（会自动跳转到 `/swagger-ui/index.html`） |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |

使用步骤：

1. 启动应用，打开 `http://localhost:8080/swagger-ui.html`
2. 在 **用户登录** 分组里调用 `POST /user/login`，拿到返回的 `data.token`
3. 点右上角 **Authorize**，把 token 填进去（不需要手动加 `Bearer ` 前缀）
4. 之后就能直接调试 `/user/info` 和 `/user/logout`

> 生产环境可以关闭文档：
> `springdoc.api-docs.enabled=false`、`springdoc.swagger-ui.enabled=false`。

### 全局登录拦截

`AuthInterceptor` 默认拦截 `/**`，只有下面这些路径不需要 token：

- `/user/login`
- `/swagger-ui.html`、`/swagger-ui/**`、`/v3/api-docs/**`
- `/error`、`/favicon.ico`

因此未登录访问任何未知路径会先返回 **401**；只有携带有效 token 时，路径写错才会返回 **404**。
这是“默认安全”策略的必然结果，客户端不要把 401 以外的未知路径当作接口不存在。

---

## 六、全局异常处理

`GlobalExceptionHandler` 用 `@RestControllerAdvice` 把所有异常统一翻译成 `Result`，
客户端永远只会收到固定格式的 JSON，不会看到 Spring 的 Whitelabel 错误页或 HTML 堆栈。

| 异常类型 | 触发场景 | HTTP |
| --- | --- | --- |
| `BusinessException` | 主动抛出的业务失败（密码错误、token 失效等） | 由异常携带的 `HttpStatus` 决定 |
| `MethodArgumentNotValidException` | `@Valid` 校验 `@RequestBody` 失败 | 400 |
| `HandlerMethodValidationException` | Controller 方法参数校验失败 | 400 |
| `ConstraintViolationException` | 旧版参数校验失败 | 400 |
| `HttpMessageNotReadableException` | 请求体不是合法 JSON | 400 |
| `MissingServletRequestParameterException` | 缺少必填查询参数 | 400 |
| `MethodArgumentTypeMismatchException` | 参数类型不匹配 | 400 |
| `HttpRequestMethodNotSupportedException` | 请求方式不对 | 405 |
| `HttpMediaTypeNotSupportedException` | Content-Type 不对 | 415 |
| `NoResourceFoundException` / `NoHandlerFoundException` | 接口路径写错 | 404 |
| `Exception`（兜底） | 任何未预料到的异常 | 500 |

设计要点：

- 业务异常只打 `warn` 日志，避免刷屏；兜底异常打 `error` + 完整堆栈
- 兜底异常不把堆栈、SQL、内网地址返回给客户端，只返回通用提示
- 参数校验失败只返回**第一条**提示，避免一次弹一堆

---

## 七、和鸿蒙端怎么对接

1. **BASE_URL 要配对**
   - 模拟器访问宿主机：`http://10.0.2.2:8080`
   - 真机：`http://<电脑的局域网IP>:8080`（手机和电脑同一 WiFi，防火墙放行 8080）

2. **在 `EntryAbility.onCreate` 里写入 BASE_URL**：

   ```ts
   AppStorage.setOrCreate<string>('BASE_URL', 'http://10.0.2.2:8080');
   ```

3. **`module.json5` 加网络权限**：

   ```json5
   "requestPermissions": [
     { "name": "ohos.permission.INTERNET" }
   ]
   ```

4. **登录成功后保存 token，后续请求带上**：

   ```ts
   const data = await login({ userName, password });
   AppStorage.setOrCreate<string>('TOKEN', data.token);
   // request.get<LoginResult>('/user/info', undefined, { header: { Authorization: `Bearer ${token}` } })
   ```

---

## 八、测试

> 测试会使用 `application.yaml` 里的 MySQL 配置启动完整 Spring 上下文，请先保证 MySQL 可连接。
> 想用独立测试库可以加环境变量：`DB_URL=jdbc:mysql://localhost:3306/harmony_test?...`。

```bash
./mvnw test
```

`UserAuthApiTests` 覆盖了：

- 登录成功 / 密码错误 / 用户不存在 / 参数校验失败 / JSON 格式错误
- 无 token、无效 token、正常 token 访问 `/user/info`
- 退出登录后 token 立即失效
- 404、405、500 的全局异常处理

---

## 九、上线前还需要补的东西

- token 换成 JWT 或存 Redis，支持多实例部署和主动踢人
- 增加登录失败次数限制、图形验证码，防暴力破解
- 密码强度校验、注册接口、找回密码
- 数据库连接池参数、索引和慢 SQL 监控
- CORS 里的 `allowedOriginPatterns("*")` 收紧成具体域名
