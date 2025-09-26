## todo15轮播图「关联专辑」开发记录

本次改造目标：在管理端为轮播图设置“关联专辑”，客户端首页点击轮播图跳转到对应专辑详情；

### 一、数据库变更（DDL/数据迁移）

- 新增专辑关联列，迁移旧数据（如曾使用 `link_type/link_id`）
```sql
ALTER TABLE `tb_banner`
  ADD COLUMN `album_id` BIGINT NULL COMMENT '关联专辑ID' AFTER `status`;

-- 如历史上用 link_type=2 表示专辑，可迁移旧数据
UPDATE `tb_banner` SET `album_id` = `link_id` WHERE `link_type` = 2;

-- 清理废弃列
ALTER TABLE `tb_banner` DROP COLUMN `link_type`;
ALTER TABLE `tb_banner` DROP COLUMN `link_id`;
```

### 二、后端改造

- 实体与 VO：移除歌曲关联字段，保留专辑关联 `albumId`；为兼容 Redis 缓存旧结构，加上忽略未知字段注解。
```java
// src/main/java/cn/edu/seig/vibemusic/model/entity/Banner.java
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@TableName("tb_banner")
public class Banner implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long bannerId;

    @TableField("banner_url")
    private String bannerUrl;

    @TableField("status")
    private BannerStatusEnum bannerStatus;

    @TableField("album_id")
    private Long albumId; // 新：仅关联专辑
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/model/vo/BannerVO.java
@Data
public class BannerVO implements Serializable {
    private Long bannerId;
    private String bannerUrl;
    private Long albumId; // 返回关联专辑ID给客户端
}
```

- Service/Controller：新增/编辑接口改为接收 `albumId`（Multipart + albumId），并写库；用户端列表继续返回 VO。
```java
// src/main/java/cn/edu/seig/vibemusic/controller/BannerController.java
@PostMapping("/admin/addBanner")
public Result addBanner(@RequestParam("banner") MultipartFile banner,
                        @RequestParam(value = "albumId", required = false) Long albumId) {
    String url = minioService.uploadFile(banner, "banners");
    return bannerService.addBanner(url, albumId);
}

@PatchMapping("/admin/updateBanner/{id}")
public Result updateBanner(@PathVariable("id") Long id,
                           @RequestParam("banner") MultipartFile banner,
                           @RequestParam(value = "albumId", required = false) Long albumId) {
    String url = minioService.uploadFile(banner, "banners");
    return bannerService.updateBanner(id, url, albumId);
}
```

```java
// src/main/java/cn/edu/seig/vibemusic/service/IBannerService.java
Result addBanner(String bannerUrl, Long albumId);
Result updateBanner(Long bannerId, String bannerUrl, Long albumId);
```

```java
// src/main/java/cn/edu/seig/vibemusic/service/impl/BannerServiceImpl.java
@CacheEvict(cacheNames = "bannerCache", allEntries = true)
public Result addBanner(String url, Long albumId) {
    Banner b = new Banner().setBannerUrl(url).setBannerStatus(BannerStatusEnum.ENABLE);
    if (albumId != null) b.setAlbumId(albumId);
    return bannerMapper.insert(b) == 0 ? Result.error("新增失败") : Result.success("新增成功");
}

@CacheEvict(cacheNames = "bannerCache", allEntries = true)
public Result updateBanner(Long id, String url, Long albumId) {
    Banner b = bannerMapper.selectById(id);
    if (b == null) return Result.error("数据不存在");
    String old = b.getBannerUrl();
    if (old != null && !old.isEmpty()) minioService.deleteFile(old);
    b.setBannerUrl(url);
    b.setAlbumId(albumId); // 允许置空
    return bannerMapper.updateById(b) == 0 ? Result.error("更新失败") : Result.success("更新成功");
}
```

- 放行策略（非必须变更）：用户端获取轮播图已放行，不需要登录
```java
// src/main/java/cn/edu/seig/vibemusic/config/WebConfig.java
.excludePathPatterns(
  "/banner/getBannerList",
  "/album/getAlbumDetail/**",
  "/song/getSongsByAlbumId" /* 专辑页歌曲列表开放 */,
  ...
);
```

### 三、管理端改造（vibe-music-admin）

- 列表新增“关联专辑”列。
- 新增/编辑对话框：右侧增加“关联专辑”远程搜索选择器（按标题搜索，选中后随图片一起提交 FormData 中的 `albumId`）。

关键片段：
```ts
// vibe-music-admin-main/src/views/banner/utils/hook.tsx
import { ElSelect, ElOption } from "element-plus";
import { getBannerList, addBanner, updateBanner, updateBannerStatus, deleteBanner, deleteBanners, getAlbumList } from "@/api/system";

const albumOptions = ref<Array<{ label: string; value: number }>>([]);
const albumLoading = ref(false);

const remoteSearchAlbum = async (q: string) => {
  albumLoading.value = true;
  try {
    const res: any = await getAlbumList({ pageNum: 1, pageSize: 10, title: q || "" });
    albumOptions.value = res?.data?.items?.map((it: any) => ({
      label: `${it.albumId} - ${it.title}`, value: it.albumId
    })) ?? [];
  } finally { albumLoading.value = false; }
};

// 对话框右侧：关联专辑选择器
h(ElSelect as any, {
  filterable: true, remote: true, remoteMethod: remoteSearchAlbum, loading: albumLoading.value,
  placeholder: "输入专辑标题搜索并选择", clearable: true,
  modelValue: latestInfo.value?.albumId ?? (row?.albumId ?? undefined),
  "onUpdate:modelValue": (v: any) => { latestInfo.value = { ...(latestInfo.value || {}), albumId: v || undefined }; },
  style: "width:100%"
}, () => albumOptions.value.map(opt => h(ElOption as any, { key: opt.value, label: opt.label, value: opt.value })))

// 提交时附带 albumId
const formData = new FormData();
formData.append("banner", blob, `banner_${Date.now()}.png`);
const albumId = latestInfo.value?.albumId ?? row?.albumId;
if (albumId) formData.append("albumId", String(albumId));
```

### 四、客户端改造（vibe-music-client）

- 首页点击轮播图 → 跳转关联专辑详情。
```ts
// vibe-music-client-main/src/pages/index.vue
const bannerList = ref<{ bannerId: number; bannerUrl: string; albumId?: number }[]>([]);

<el-carousel-item v-for="item in bannerList" :key="item.bannerId">
  <img :src="item.bannerUrl" class="w-full h-full object-cover rounded-lg cursor-pointer"
       @click="() => { if(item.albumId) router.push(`/album/${item.albumId}`) }"/>
</el-carousel-item>
```

- 请求拦截：对公开接口不带 Token，避免 401
```ts
// vibe-music-client-main/src/utils/http.ts
if (
  config.url?.includes('/user/login') ||
  config.url?.includes('/captcha/generate') ||
  config.url?.includes('/banner/getBannerList')
) { return config; }
```

### 五、缓存兼容与注意事项

- 因历史缓存中可能存在 `linkType/linkId` 字段，实体上添加
```java
@JsonIgnoreProperties(ignoreUnknown = true)
```
以避免 Redis 反序列化报错。清一次 `bannerCache:*` 可立即生效。

### 六、联调与自测清单

1) 执行数据库变更 SQL，确认 `tb_banner.album_id` 存在；老数据（若有）迁移完成。  
2) 后端重启，确认接口：
   - GET `/banner/getBannerList` 返回 `albumId`
   - 管理端 POST `/admin/addBanner`、PATCH `/admin/updateBanner/{id}` 支持 `albumId`  
3) 管理端：新增/编辑轮播图 → 搜索并选择专辑 → 保存成功 → 列表“关联专辑”正确显示。  
4) 客户端首页：轮播图点击跳转到 `/album/{albumId}`，专辑详情正常渲染。  
5) 未登录时轮播图列表/专辑详情可访问；拦截器不再对公开 GET 报 401 弹窗。  

### 变更影响面与回滚

- 影响表：`tb_banner`；影响接口：管理端新增/编辑轮播图、用户端轮播图获取。  
- 回滚：恢复列 `link_type/link_id` 并下线 `album_id` 字段，恢复旧版本 Service/Controller；客户端则回退点击逻辑。



