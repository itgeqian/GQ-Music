### GQ Music V1.0完成

一个完整的在线音乐平台二改于[Alex-LiSun/vibe-music-server: Vibe Music 服务端](https://github.com/Alex-LiSun/vibe-music-server)，包含后端服务、管理端与客户端三套应用。技术栈：Spring Boot + MySQL + Redis + MinIO + Nacos+ Vue3 + Element Plus + Vite。

- 仓库地址：[`https://github.com/itgeqian/GQ-Music.git`](https://github.com/itgeqian/GQ-Music.git)

---

## 一、项目结构

```
vibe-music-server-main/
├─ src/                          # 后端源码（Spring Boot 3 / Java 17）
├─ sql/                          # 数据库初始化
├─ img/                          # 文档配图
├─ vibe-music-admin-main/        # 管理端（Vue3 + Element Plus）
├─ vibe-music-client-main/       # 客户端（Vue3 + Element Plus）
├─ start_services.bat            # 本地一键启动依赖服务（Redis+Nacos+Minio）
├─ pom.xml
├─ GQ-music优化笔记  #记录了30多个大更新
└─ README.md V1.0版本文档
```

- 服务端端口默认：后端 8080（可在 `application.yml` 配置）
- 前端端口默认：客户端8089，管理端 8090（Vite）

---

## 二、功能总览

- 用户与权限
  - 登录/注册、JWT 鉴权、角色（管理员/用户）
  - 用户资料、头像上传、关注/粉丝
- 音乐内容
  - 歌手、歌曲、专辑、歌单的增删改查与绑定
  - 音频与封面文件上传（MinIO）
- 互动与社交
  - 评论（父子结构、回复 @、图片评论、点赞/取消点赞）
  - 收藏/取消收藏（歌曲/专辑/歌单）
  - 反馈提交、我的最近播放、听歌时段统计
- 可视化/推荐
  - 个人主页近 7 日收听走势（SVG 折线优化）
  - 歌手榜、听歌时段分布、歌单/歌曲推荐（脚本/规则）
- 运维与性能
  - Redis 缓存（歌曲详情、评论等）、缓存逐出
  - SQL 脚本分版本管理（`/sql`）
  - MinIO 对象存储，公私桶可配
  - 可接入 Nacos 做配置/注册
- 略，详情见优化笔记

---

## 三、环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- MinIO（本地或远端）
- Node.js 18+（前端）

Nacos 2.x（做配置中心与注册中心）

---

## 四、快速开始（本地）

### 1. 初始化数据库

- 创建库（字符集 UTF8MB4）：
```sql
CREATE DATABASE vibe_music CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
- 依次执行 `/sql` 目录下初始化与增量脚本（按日期顺序）。

关于表结构

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-201.png)

### 2. 准备 MinIO

- 创建桶：`vibe-music-data`
- 配置公共读或按需设置签名访问
- 资源示意见文档图：`/img/minio目录讲解.png`

### 3. 配置后端

编辑 `src/main/resources/application.yml`（仅示例字段）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/vibe_music?useUnicode=true&characterEncoding=utf-8&useSSL=false
    username: root
    password: your_mysql_pwd

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:
      database: 1

minio:
  endpoint: http://127.0.0.1:9000
  accessKey: your_access_key
  secretKey: your_secret_key
  bucket: vibe-music-data

# 开启/调整缓存空间
spring:
  cache:
    type: redis
```

使用 Nacos，请增加相应 `bootstrap.yml` 或 `application.yml` 的 Nacos 配置。

重要配置放nacos

```
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/vibe_music?useUnicode=true&characterEncoding=utf-8&useSSL=false
    username: root
    password: 123456 #换成你的密码
    driver-class-name: com.mysql.cj.jdbc.Driver

  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: #没有就留空或注释，有就填
      database: 1

  mail:
    host: smtp.qq.com
    username: 换成你的username
    password: 换成你的password
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
  endpoint: http://192.168.100.1:9000 
  accessKey: 换成你的accessKey
  secretKey: 换成你的secretKey
  bucket: vibe-music-data

# 保留本地的角色/权限和 mybatis-plus 配置，也可放 Nacos
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
      - "/theme/"
      - "/user/theme/"
    ROLE_USER:
      - "/user/"
      - "/playlist/"
      - "/artist/"
      - "/song/"
      - "/recent/"
      - "/album/"
      - "/favorite/"
      - "/comment/"
      - "/banner/"
      - "/feedback/"
      - "/theme/"
      - "/user/theme/"
ffmpeg:
  show-log: true
```



### 4. 启动后端

```bash
mvn clean package -DskipTests
java -jar target/*.jar
# 默认 http://localhost:8080
```

### 5. 启动前端（客户端）

```bash
cd vibe-music-client-main
pnpm i   # 或 npm i / yarn
pnpm run dev # 默认 http://localhost:8090
```

如需配置后端地址，检查 `src/api/index.ts` 或 `.env`（例如 `VITE_API_BASE`）。

### 6. 启动管理端

```bash
cd vibe-music-admin-main
pnpm i
pnpm run dev # 默认 http://localhost:8089
```

同样可通过 `.env` 或 `src/utils/http` 指定后端网关地址。

---

## 五、关键模块说明

- 评论（父子结构）
  
- 歌手模块+歌曲模块+专辑模块+歌单模块的整个链路

- 歌词模块

- 音频条+专辑呼吸动效

- FFmpeg转码

- 新增图形验证码

- 搜索模块支持音乐（歌曲/歌手/专辑）和用户

- 歌手详情页/专辑详情页/歌单详情页支持搜索和排序（默认/专辑/歌名/歌手）

- 管理端批量导入歌曲优化操作

- 点赞评论逻辑重写

- 轮播图绑定专辑

- 热搜榜的实现

- 我的歌单模块的实现

- 最近播放的实现

- 歌单推荐模块

- 歌曲、专辑、歌单（官方/我的）评论管理模块

- 主题模块（官方主题-图片/视频 与自定义-图片）

- 支持flac无损歌曲导入

- 优化级联删除的全链路

- 缓存策略（详解见优化笔记中的本项目缓存机制总览（Spring Cache + RedisTemplate））
  
  - `@Cacheable` + `@CacheEvict`
  - SpringCache
  
- 加入播放列表
  
- 个人设置
  
- 评论支持图片与表情
  
- 文件存储
  - MinIO 统一管理封面/音频/头像/评论图片等等
  
  ![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-200.png)
  
- 内容过多更多内容详情见详见GQ-music优化笔记

---

## 六、常用脚本

后端（Maven）：
- `mvn clean`、`mvn package -DskipTests`、`mvn spring-boot:run`

前端（Vite）：
- `pnpm dev`、`pnpm build`、`pnpm preview`

---

## 七、部署建议

- 后端：JDK17 + Docker（可选）+ Nginx 反向代理
- 静态前端：`pnpm build` 后放到 Nginx
- 依赖：MySQL/Redis/MinIO 使用持久卷
- 环境变量注入敏感信息（数据库、对象存储密钥等）

---

## 八、资料与数据

- 资源桶结构示例见仓库 `/img` 下配图
- 如需示例数据可按 `/sql` 导入
- 也可自定义导入 CSV/脚本生成

---

## 九、后续计划

- 支付模块
- AI生成音乐接口
- MV模块（利用FFmpeg）



## 十、用户端 部分页面截图

### 1.推荐页面

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-202-1.png)

- 轮播图可以跳转到管理端指定的专辑页面
- 歌单推荐和歌曲推荐走的是不同的推荐逻辑

### 2.搜索页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-203-1.png)

- 选择音乐时可以按照歌手/专辑/歌曲名进行搜索
- 选择用户时按照用户名搜索

接下来避免看不清我关掉了主题

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-204.png)

点击右箭头可以跳转用户个人详情

### 3.个人详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-205-1.png)

可以收起统计

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-206.png)

点击粉丝和关注显示自己的粉丝和关注用户，同样点击后跳转到其他用户详情

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-207.png)

若用户开启了私密模式则无法显示其喜欢的歌曲、收藏的歌单、以及创建的歌单

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-208.png)

### 4.曲库模块（所有歌曲）

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-209-1.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-210.png)

这里的更多中下拉可以看到更多的操作

### 5.歌手模块

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-211-1.png)

按分类筛选歌手，点击后可以进入歌手详情页

### 6.歌手详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-265-1.png)

歌曲、专辑、详情导航

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-213.png)

详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-216-1.png)

专辑导航显示出所有专辑懒加载展开后显示所有歌曲，也可以点击专辑名跳转到专辑详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-214.png)

### 7.专辑详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-215-1.png)

专辑信息页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-217-1.png)

专辑评论区

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-218.png)

### 8.歌单模块（官方歌单）

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-219-1.png)

点击进入歌单详情页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-220.png)

### 9.喜欢模块

显示喜欢的歌曲、专辑、歌手

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-221.png)

### 10.我的歌单-创建我的歌单

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-222.png)

创建方式

1.自己在我的歌单页创建

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-223.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-224.png)

评论管理：管理其他用户对你的歌单的评价

上传封面：就是上传封面

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-225.png)

2.歌曲更多下拉框中添加到中选择

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-226-1.png)

### 11.最近播放

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-227.png)

这里的更多多了移除按钮，可以把歌曲移出最近播放，你也可以清除全部记录

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-228.png)

### 12.个人中心

也就是别人看的个人详情页记录自己的一些数据这里不多做介绍

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-229.png)

### 13.收藏的歌单

收藏的歌单放在这里，可以滚动

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-230-344x1024.png)

### 14.个人设置

更新/修改信息

是否公开主页

自动播放/恢复进度/音频条开关

是否开启首页推荐

快捷键播放

音频条如下

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-238-1.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-231.png)

### 15.暗色模式与亮色模式

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-232.png)

### 16.主题功能

官方主题分为静态和动态

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-233.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-234.png)

当然也可以自定义图片上传，自定义不支持视频以防网站压力过大

模糊度和亮度就不做解释了

### 17.意见反馈

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-235.png)

### 18.歌曲播放页面

随机播放、循环播放、顺序播放

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-236-1.png)

呼吸光效

歌词

评论可以上传图片和回复及删除-个人删与管理员删（歌单、专辑的评论也一样）

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-237-1.png)

### 19.登录、注册、重置密码

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-239.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-240.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-241.png)

## 十一、管理端部分截图

管理端基于vue-pure-admin

### 1.首页

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-242.png)

### 2.用户管理

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-243.png)

### 3.歌手管理

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-244.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-245-1024x613.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-246-1024x906.png)

上传头像

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-247.png)

### 4.歌曲管理

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-248-1.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-249.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-250.png)

上传音频时歌词可选传

### 5.批量导入歌曲

支持拖拽

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-251.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-252.png)

### 6.专辑管理

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-253.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-254.png)

### 7.歌单管理之官方歌单

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-255-1.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-256.png)

添加歌曲界面跟用户端我的歌单一样

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-257-1.png)

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-258-1.png)

### 8.歌单管理之用户歌单

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-259.png)

用户歌单只支持读和推荐

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-260-1.png)

### 9.反馈模块

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-261-1.png)

### 10.轮播图管理

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-262-1.png)

编辑轮播图可以选择绑定专辑

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-263-1.png)

### 11.主题管理

增删改查

支持上传图片格式和视频格式

![img](https://www.legendkiller.xyz/wp-content/uploads/2025/09/image-264-1.png)

   主要大的功能大概就这多，更细小我就不截图了



---

# 未完待续