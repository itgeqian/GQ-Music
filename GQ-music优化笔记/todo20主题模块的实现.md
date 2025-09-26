## todo20主题模块的实现（FFmpeg）

### 主题模块开发文档（客户端/管理端/服务端一体化）

本模块实现“官方主题图集 + 用户自定义（图/视频）”，并提供模糊度与亮度调节、视频预览、权限控制、MinIO 文件同步删除、FFmpeg 处理等能力。下文按“数据结构 → 服务端 → 客户端 → 管理端 → 权限与部署 → 常见问题”进行说明，穿插关键代码与调用点。

## 一、数据库与数据模型

- **核心表**
  - `tb_theme`：官方主题（图片或视频）
  - `tb_user_theme`：用户当前选择的主题（官方/自定义）

  ```
  
  SET NAMES utf8mb4;
  SET FOREIGN_KEY_CHECKS = 0;
  
  -- ----------------------------
  -- Table structure for tb_theme
  -- ----------------------------
  DROP TABLE IF EXISTS `tb_theme`;
  CREATE TABLE `tb_theme`  (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `url_1080` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `url_1440` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `thumb_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `type` tinyint NOT NULL DEFAULT 0 COMMENT '0=图片 1=视频',
    `video_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `poster_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `duration` int NULL DEFAULT NULL,
    `blurhash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `need_vip` tinyint(1) NOT NULL DEFAULT 0,
    `status` tinyint NOT NULL DEFAULT 1,
    `sort` int NOT NULL DEFAULT 0,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE
  ) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;
  
  SET FOREIGN_KEY_CHECKS = 1;
  
  ```
  
  ```
  SET NAMES utf8mb4;
  SET FOREIGN_KEY_CHECKS = 0;
  
  -- ----------------------------
  -- Table structure for tb_user_theme
  -- ----------------------------
  DROP TABLE IF EXISTS `tb_user_theme`;
  CREATE TABLE `tb_user_theme`  (
    `user_id` bigint NOT NULL,
    `theme_type` enum('official','custom') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `theme_id` bigint NULL DEFAULT NULL,
    `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `blurhash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `color_primary` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`) USING BTREE,
    INDEX `fk_user_theme_theme`(`theme_id` ASC) USING BTREE,
    CONSTRAINT `fk_user_theme_theme` FOREIGN KEY (`theme_id`) REFERENCES `tb_theme` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
  ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;
  
  SET FOREIGN_KEY_CHECKS = 1;
  
  ```
  
  
  
- **视频增强字段迁移**
```sql
ALTER TABLE `tb_theme`
  ADD COLUMN `type` TINYINT NOT NULL DEFAULT 0 COMMENT '0-图片 1-视频' AFTER `name`,
  ADD COLUMN `video_url` VARCHAR(512) NULL COMMENT '视频地址' AFTER `type`,
  ADD COLUMN `poster_url` VARCHAR(512) NULL COMMENT '视频封面图地址' AFTER `video_url`,
  ADD COLUMN `duration` INT NULL COMMENT '视频时长（秒）' AFTER `poster_url`;

ALTER TABLE `tb_theme`
  MODIFY `url_1080` VARCHAR(512) NULL,
  MODIFY `url_1440` VARCHAR(512) NULL,
  MODIFY `thumb_url` VARCHAR(512) NULL;
```

- `Theme`/`UserTheme` 实体对应上述字段，注意与 `mybatis-plus` 的 `table-prefix: tb_` 保持一致。

## 二、服务端设计（Spring Boot）

### 2.1 API 列表（示例）
- 用户侧
  - `GET /theme/list`：获取官方主题列表
  - `GET /user/theme/my`：获取我的主题
  - `POST /user/theme/official/{id}`：应用官方主题
  - `POST /user/theme/custom/upload`：上传自定义图/视频
  - `POST /user/theme/custom/set`：设置自定义主题（入库）
  - `POST /user/theme/reset`：恢复默认主题
- 管理侧
  - `POST /admin/theme`：新增主题（图/视频）
  - `PUT /admin/theme/{id}`：编辑主题元数据（名称/类型/状态等）
  - `DELETE /admin/theme/{id}`：删除主题（含 MinIO 文件联动删除）
  - `PUT /admin/theme/sort`：拖拽排序

### 2.2 FFmpeg/MinIO 能力

- 统一进程执行器（避免 IO 死锁）
```java
// ProcessUtils.execute(String cmd, boolean showLog)
// - 同时消费 stdout/stderr，返回 exitCode/stdout/stderr，避免阻塞
```

- 视频工具
```java
// FFmpegUtils
public static String getVideoCodec(String videoPath) { ... }
public static Integer getDurationSeconds(String videoPath) { ... }
public static String createVideoFirstFrame(String videoPath, String jpgOut, int width) { ... } // 生成首帧海报
```

- MinIO 扩展
```java
// MinioService
String uploadStream(String objectName, InputStream in, long size, String contentType);
void removeObject(String objectUrl);
```

- 典型上传流程（管理端“视频主题”）
  1) `MultipartFile` 落本地临时文件 → `ffprobe` 获取时长 → `ffmpeg` 生成首帧海报
  2) 海报 `InputStream` 直传 MinIO，拿到 `posterUrl`
  3) 原视频按策略可转码（HEVC→H.264，可选）→ MinIO
  4) 入库：`type=1, videoUrl, posterUrl, duration`

- 删除主题的联动删除
```java
// 删除主题前，解析并删除所有关联 object（url_1080/url_1440/thumbUrl/videoUrl/posterUrl）
```

### 2.3 返回结构与细节
- 统一 `Result`/`PageResult`
- 自定义背景上传成功：
```java
// 避免 URL 出现在 toast message
return Result.success("上传成功", url); // message="上传成功"，data=实际 URL
```

### 2.4 权限与配置
```yaml
ffmpeg:
  show-log: true   # 后端控制台打印 FFmpeg 命令行日志

role-path-permissions:
  permissions:
    ROLE_ADMIN:
      - "/admin/"
      - "/theme/"
    ROLE_USER:
      - "/user/"
      - "/theme/"
      - "/playlist/"
      - "/artist/"
      - "/song/"
      - "/favorite/"
      - "/comment/"
      - "/banner/"
      - "/feedback/"

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      table-prefix: tb_
```
- `RolePermissionManager` 增加兜底：已登录用户允许访问 `/theme/**`、`/user/theme/**`，防止 Nacos 未同步导致 403。

## 三、客户端设计（Vue 3 + Pinia + Element Plus + Tailwind）

### 3.1 主题状态与动作
```ts
// stores/interface/index.ts
export interface ThemeState {
  isDark: boolean
  primary: string
  backgroundUrl?: string
  videoUrl?: string
  themeType?: 'official' | 'custom'
  themeId?: number
  blurStrength?: number         // 背景模糊(px)
  brightness?: number           // 背景亮度(%) 50~150，默认 100
}
```

```ts
// stores/modules/theme.ts
export const themeStore = defineStore({
  id: 'themeStore',
  state: (): ThemeState => ({
    isDark: false,
    primary: '#7E22CE',
    backgroundUrl: '',
    videoUrl: '',
    themeType: 'official',
    themeId: undefined,
    blurStrength: 0,
    brightness: 100,
  }),
  actions: {
    setDark(isDark: boolean) { this.isDark = isDark },
    applyOfficialTheme(themeId: number, url?: string) { ... }, // 自动识别视频/图片
    applyCustomTheme(url: string) { ... },
    setBlurStrength(px: number) { this.blurStrength = Math.max(0, Math.min(20, px)) },
    setBrightness(percent: number) { this.brightness = Math.max(50, Math.min(150, Math.round(percent))) },
  },
  persist: piniaPersistConfig('ThemeStore'),
})
```

### 3.2 全局背景渲染（仅背景层受影响）
```vue
<!-- layout/index.vue 片段：视频优先，模糊与亮度仅作用于背景 -->
<video v-if="user.isLoggedIn && theme.videoUrl" :src="theme.videoUrl" autoplay muted loop playsinline
       style="width:100%;height:100%;object-fit:cover;"
       :style="{
         filter: `${theme.blurStrength ? `blur(${theme.blurStrength}px)` : ''} ${theme.brightness !== undefined ? `brightness(${theme.brightness}%)` : ''}`.trim()
       }"></video>

<div v-else :style="(user.isLoggedIn && theme.backgroundUrl) ? {
  backgroundImage: `url(${theme.backgroundUrl})`,
  backgroundSize: 'cover',
  backgroundPosition: 'center center',
  filter: `${theme.blurStrength ? `blur(${theme.blurStrength}px)` : ''} ${theme.brightness !== undefined ? `brightness(${theme.brightness}%)` : ''}`.trim()
} : {}" class="w-full h-full"></div>
```
- 未登录时不应用用户自定义主题，避免遮挡登录弹窗。
- 前景容器 `relative z-10`，不受模糊/亮度影响。

### 3.3 主题选择弹窗（用户端）
- 功能：官方主题列表（静态/动态切换）、名称展示、视频卡片“首帧图 + 悬浮播放”、模糊度滑块、亮度滑块、上传自定义、恢复默认。
```vue
<!-- theme-switcher.vue 片段 -->
<div class="flex items-center gap-3 text-sm">
  <span class="text-inactive">模糊度</span>
  <el-slider :min="0" :max="20" v-model="theme.blurStrength" style="width: 240px" />
  <span class="w-8 text-center">{{ theme.blurStrength }}px</span>
</div>
<div class="flex items-center gap-3 text-sm">
  <span class="text-inactive">亮度</span>
  <el-slider :min="50" :max="150" v-model="theme.brightness" style="width: 240px" />
  <span class="w-10 text-center">{{ theme.brightness }}%</span>
</div>

<!-- 视频卡片：默认显示 poster，hover 播放 -->
<div class="relative w-full h-28 rounded-md border overflow-hidden">
  <img v-if="item.posterUrl" :src="item.posterUrl" class="w-full h-full object-cover" />
  <video v-else :src="item.videoUrl" autoplay muted loop playsinline class="w-full h-full object-cover"></video>
  <video v-if="item.posterUrl" :src="item.videoUrl" muted loop playsinline
         class="absolute inset-0 w-full h-full object-cover opacity-0 group-hover:opacity-100 transition-opacity"></video>
</div>
<div class="mt-1 text-xs text-center truncate" :title="item.name || '官方主题'">{{ item.name || '官方主题' }}</div>
```

### 3.4 全局透明化与融合
- Header/Aside/Main、各列表页（曲库/歌手/歌单/喜欢/我的歌单/专辑详情/歌手详情/用户中心）统一移除不必要的背景色、边框、毛玻璃，只在组件局部需要时加轻量边框/阴影。
- `Table.vue` 对 Element Plus 表格做透明覆盖，避免“黑色底”的割裂感。

### 3.5 登录可见性保障
- 登录弹窗 `el-dialog` 加 `append-to-body`
- 未登录时不渲染自定义背景，避免遮挡弹窗

## 四、管理端（主题管理）

- CRUD、上下架状态、编辑元数据、拖拽排序
- 新增主题：
  - 图片：直传 MinIO，尺寸/质量压缩可按需扩展
  - 视频：本地临时文件 → FFmpeg 生成海报（poster）与时长（duration）→ 上传海报与视频 → 入库
- 列表卡片与用户端同样的“海报优先、悬浮播放预览”
- 删除主题：先删 MinIO 文件再删数据库

## 五、部署与参数

- 安装 `ffmpeg/ffprobe` 并加入 `PATH`
- `application.yml` 开启 `ffmpeg.show-log: true` 以便排查视频处理
- MinIO 访问三元组与 `bucket` 正确配置，服务端和前端访问域同源/可跨域

## 六、常见问题与排查

- 403：Nacos 未同步或丢失 `/theme/**`、`/user/theme/**`；或使用了历史配置。已在 `RolePermissionManager` 做兜底放行。
- 表名不一致：`mybatis-plus` 配了 `table-prefix: tb_`，表需命名为 `tb_theme`、`tb_user_theme`。
- 插入异常：`name`、`url_1080` 非空导致失败。视频主题已将相关字段允许 `NULL`。
- 自定义上传 URL 出现在消息里：后端改为 `Result.success("上传成功", url)`。
- 登录弹窗被遮挡：未登录不应用用户主题 + `append-to-body`。
- MinIO 未同步删除：后端删除前统一解析并删除所有相关对象。

## 七、可扩展方向

- 视频转码：HEVC 自动转 H.264（兼容 Safari/移动端）
- 自适应亮度：依据背景图亮度/对比度自动给出默认亮度建议
- 主题收藏/热度排序：客户端埋点 + 管理端排序策略
- 主题权限：会员主题/限时主题/地区主题
- 预加载与渐进显示：BlurHash/低清图占位，避免首屏闪烁

——

如果需要，我可以将本说明拆分为“管理端开发笔记/客户端开发笔记/服务端开发笔记”三篇，并补充类图/时序图及完整接口清单。