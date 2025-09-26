### 目标
把项目的敏感配置（数据库、Redis、邮件、对象存储等）放到 Nacos 中集中管理，并支持运行时动态刷新；参考本项目的做法，你可以在“单体 Spring Boot 项目”中按以下步骤快速集成。

### 前置准备
- 安装并启动 Nacos（单机模式）
  - Windows: 执行 `bin\startup.cmd -m standalone`
  - Linux/Mac: 执行 `bin/startup.sh -m standalone`
  - 默认控制台地址: `http://127.0.0.1:8848`（默认用户名/密码：`nacos/nacos`）
- 规划
  - 应用名：与 Spring Boot `spring.application.name` 一致，例如 `my-app`
  - Profile：`dev`/`test`/`prod` 等
  - DataId 约定：`{spring.application.name}-{spring.profiles.active}.yml`（例如：`my-app-dev.yml`）
  - Group：默认 `DEFAULT_GROUP`（也支持自定义）
  - Namespace：默认 `public`（也可以为不同环境创建独立 namespace）

### 第一步：引入依赖
建议使用 Spring Cloud Alibaba 的 BOM 管理版本（请根据你项目的 Spring Boot 版本选择合适 BOM；Boot 2.7.x 通常搭配 Spring Cloud 2021.x 与 Alibaba 2021.x）。

示例（Maven）：
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-alibaba-dependencies</artifactId>
      <version>2021.0.5.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Nacos 配置中心 -->
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
  </dependency>
  <!-- Nacos 注册中心（可选，如果你需要服务注册/发现） -->
  <dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
  </dependency>
</dependencies>
```

### 第二步：在 bootstrap.yml 中启用 Nacos
关键点：把 Nacos 的“连接信息”和“加载策略”放在 `bootstrap.yml`（它早于 `application.yml` 加载）。

本项目示例（来自 Resource 模块，等价思路适用于单体项目）：
```1:14:easylive-server/easylive-cloud/easylive-cloud-resource/src/main/resources/bootstrap.yml
spring:
  application:
    name: easylive-cloud-resource
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
      config:
        server-addr: 127.0.0.1:8848
        file-extension: yml
        shared-configs:
          - ${spring.application.name}-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}
```

你在“单体项目”里可参考写法：
```yaml
spring:
  application:
    name: my-app
  profiles:
    active: dev

  cloud:
    nacos:
      discovery:         # 若不需要注册中心，可去掉整个 discovery
        server-addr: 127.0.0.1:8848
        # username: nacos
        # password: nacos
        # namespace: public
        # group: DEFAULT_GROUP

      config:
        server-addr: 127.0.0.1:8848
        file-extension: yml
        # username: nacos
        # password: nacos
        # namespace: public
        # group: DEFAULT_GROUP
        # 推荐按“应用名-环境”加载一个主配置
        shared-configs:
          - ${spring.application.name}-${spring.profiles.active}.${spring.cloud.nacos.config.file-extension}
```

说明：
- 将 `server-addr`、`username/password`、`namespace/group` 放在 `bootstrap.yml`，使 Spring 在启动早期即可连接 Nacos。
- `shared-configs` 用来加载一个（或多个）共享配置；本项目用的是“按应用名+环境”的单一主配置，简单直观。

可选：如果你希望“主配置”直接叫 `my-app-dev.yml` 而不是 shared-configs，也可以使用：
```yaml
spring:
  cloud:
    nacos:
      config:
        extension-configs[0].data-id: ${spring.application.name}-${spring.profiles.active}.yml
        extension-configs[0].refresh: true
```

### 第三步：在 Nacos 控制台创建配置
- Data ID: `my-app-dev.yml`
- Group: `DEFAULT_GROUP`
- Namespace: `public`（或你的自定义）
- 内容（把敏感信息迁移过来）：
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/demo?useSSL=false&serverTimezone=GMT%2B8
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

  redis:
    host: 127.0.0.1
    port: 6379

  mail:
    host: smtp.qq.com
    port: 465
    username: your@qq.com
    password: your_auth_code
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: true

# 你项目的业务自定义配置
storage:
  provider: local  # 或 minio
minio:
  endpoint: http://127.0.0.1:9000
  accessKey: minioadmin
  secretKey: minioadmin
  bucket: easylive
```

提示：
- 环境切换：只需切换 `spring.profiles.active`（例如改为 `prod`），并在 Nacos 配同名 DataId：`my-app-prod.yml`。
- 你也可以把“公共配置”放到 `my-app-common.yml`，然后在 `shared-configs` 里先加载 `common` 再加载 `dev/prod` 覆盖。

### 第四步：在代码里读取配置
两种常用方式：

1) `@Value`
```java
@Value("${storage.provider:local}")
private String storageProvider;
```

2) `@ConfigurationProperties`（推荐，结构化配置）
```java
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
  private String endpoint;
  private String accessKey;
  private String secretKey;
  private String bucket;
  // getter/setter
}
```

如果希望运行时动态刷新（不重启生效）：
- 在使用处类上加 `@RefreshScope`
```java
@RefreshScope
@Component
public class SomeComponent {
  @Value("${storage.provider}")
  private String storageProvider;
}
```
- Nacos 控制台“发布”后，Spring 会自动刷新（需确保 actuator/refresh 机制或 Spring Cloud Context 正常）。

本项目里类似的集中配置读取类可参考：
```1:74:easylive-server/easylive-cloud/easylive-cloud-common/src/main/java/com/easylive/entity/config/AppConfig.java
@Configuration
public class AppConfig {
  @Value("${storage.provider:local}")
  private String storageProvider;
  @Value("${minio.endpoint:}")
  private String minioEndpoint;
  ...
  public String getStorageProvider() { return storageProvider; }
  public String getMinioEndpoint() { return minioEndpoint; }
  ...
}
```

### 第五步：配置优先级与排错
- 加载顺序（简化记忆）：`bootstrap.yml` → Nacos 配置 → `application.yml`
- 一般情况下，Nacos 的配置会覆盖本地 `application.yml` 的同名项（可通过日志确认）。
- 排错技巧：
  - 开启 debug（临时启动参数 `--debug`），会打印“加载了哪个 DataId”的日志。
  - 检查控制台 DataId、Group、Namespace 与 `bootstrap.yml` 是否一致。
  - 如果有认证，确认账号密码正确；如果是多环境，确认 NamespaceId/Group 是否一致。
  - 如果你在本机与服务器使用不同的 `server-addr`，记得在对应环境的 `bootstrap.yml` 调整。

### 第六步（可选）：服务注册发现
在单体项目中一般不需要，但如果你也想使用 Nacos 作为注册中心：
- 在主类上加 `@EnableDiscoveryClient`
- 使用 `spring.cloud.nacos.discovery.*` 配置即可（见第二步示例）

### 参考：本项目中的 Nacos 用法要点
- 每个模块的 `bootstrap.yml` 指向同一个 Nacos，定义 `shared-configs` 为 `${spring.application.name}-${spring.profiles.active}.yml`，将所有敏感与环境相关项放进 Nacos。
- 控制台里只需要维护 `easylive-cloud-xxx-dev.yml` 即可（比如 Web 模块：`easylive-cloud-web-dev.yml`），模块启动时自动合并。

---

需要我把上述“文档模板”整理成一份 `README_Nacos.md` 并放到你的另一个项目里，我可以直接生成文件并附带一个最小可运行 Demo 配置。