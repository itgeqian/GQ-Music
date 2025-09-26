### Vibe Music Server 集成 Nacos 配置中心开发文档

以下文档记录本项目将敏感配置从本地 `application.yml` 迁移到 Nacos 配置中心的完整步骤与要点，适用于 Spring Boot 3.3.x + Spring Cloud Alibaba 2023.0.1.0。

### 一、引入依赖

在 `pom.xml` 中引入 Spring Cloud Alibaba Nacos Config 和 BOM。

```xml
<properties>
  <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>${spring-cloud-alibaba.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Nacos Config -->
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
  </dependency>
</dependencies>
```

说明：
- 不再使用 `spring-cloud-starter-bootstrap`。改用 Spring Boot 官方推荐的 `spring.config.import` 方式导入 Nacos 配置。

### 二、应用配置入口

采用 application.yml 导入 Nacos 配置，避免占位符解析时机问题。

文件：`src/main/resources/application.yml` 顶部需包含：

```yaml
spring:
  profiles:
    active: dev
  config:
    import:
      - optional:nacos:vibe-music-server-dev.yml
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yml
```

要点：
- DataId 固定为 `vibe-music-server-dev.yml`，与 `spring.profiles.active: dev` 对应。
- 使用 `optional:nacos:` 确保即使 Nacos 未就绪也不影响本地启动（会退回到本地默认值）。
- 若使用自定义 `group/namespace`，请追加：
  - `spring.cloud.nacos.config.group: DEFAULT_GROUP`
  - `spring.cloud.nacos.config.namespace: <namespaceId>`（注意是 ID）

可选：`bootstrap.yml` 不是必需，若存在也不影响。

### 三、在 Nacos 控制台创建配置

- Data ID: `vibe-music-server-dev.yml`
- Group: `DEFAULT_GROUP`
- Namespace: `public`（或你的自定义）
- 配置格式：YAML
- 内容示例（将敏感信息放入这里）：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/vibe_music?useUnicode=true&characterEncoding=utf-8&useSSL=false
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:
      database: 1

  mail:
    host: smtp.qq.com
    username: your@qq.com
    password: your_app_password
    protocol: smtp
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          ssl:
            enable: true
          socketFactory:
            port: 465
            class: javax.net.ssl.SSLSocketFactory
          connectiontimeout: 5000
          timeout: 3000
          writetimeout: 5000

minio:
  endpoint: http://127.0.0.1:9000
  accessKey: minioadmin
  secretKey: minioadmin
  bucket: vibe-music-data

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      table-prefix: tb_

role-path-permissions:
  permissions:
    ROLE_ADMIN:
      - "/admin/"
    ROLE_USER:
      - "/user/"
      - "/playlist/"
      - "/artist/"
      - "/song/"
      - "/favorite/"
      - "/comment/"
      - "/banner/"
      - "/feedback/"
```

说明：
- 生产环境请分环境维护：例如 `vibe-music-server-prod.yml` 对应 `spring.profiles.active: prod`。

### 四、精简本地 application.yml

我们已将敏感配置迁移到 Nacos，本地只保留默认值与非敏感项；敏感值以占位符兜底，防止 Nacos 不可用时无法启动。

- 数据源、Redis、邮件、MinIO 已使用占位符（你当前仓库已更新）。
- 若需要完全本地化测试，可暂时在本地填入明文或通过环境变量注入。

### 五、动态刷新（可选）

为了在不重启的情况下刷新 MinIO 等配置，已对相关 Bean 增加 `@RefreshScope`。

- `cn.edu.seig.vibemusic.config.MinioConfig` 上添加了 `@RefreshScope`。
- `cn.edu.seig.vibemusic.service.impl.MinioServiceImpl` 上添加了 `@RefreshScope`。

当你在 Nacos 修改 MinIO endpoint/accessKey 等配置并发布后，Bean 会自动刷新生效（前提是 Spring Cloud Context 正常）。

### 六、常见问题与排错

- 启动报错 “No spring.config.import property has been defined”
  - 确认 `application.yml` 顶部已添加 `spring.config.import: optional:nacos:...`。
  - 确认 Nacos 服务地址 `server-addr` 正确。
- 报错 `http://null/...-${spring.profiles.active}.yml`
  - 原因：占位符解析时机导致。已改为固定 DataId，或在 `application.yml` 明确设置 `spring.profiles.active`。
- 依赖下载失败（如 spring-cloud-starter-bootstrap）
  - 本项目未使用该依赖；请不要添加。统一用 `spring.config.import` 即可。
- 切换环境
  - 修改 `spring.profiles.active`，并在 Nacos 创建同名 DataId（如 `vibe-music-server-prod.yml`）。

### 七、可选安全优化建议

- MinIO：生产建议走“预签名 URL”而非公开读；数据库只存对象键（如 `banners/xxx.png`），由后端动态生成时效访问地址下发。
- 邮件/数据库密码：强烈建议仅在 Nacos 存放，且 Nacos 自身要有鉴权与网络隔离策略。
- 访问控制：为 Nacos 控制台与 API 配置强密码、IP 白名单或内网访问。

### 八、完成状态

- 依赖已接入
- application.yml 已导入 Nacos 配置
- 敏感配置已迁移（示例已给出）
- MinIO 模块支持动态刷新
- 启动与加载路径已验证通过

若需要，我可以把本说明整理为 `docs/nacos-integration.md` 文件加入仓库。