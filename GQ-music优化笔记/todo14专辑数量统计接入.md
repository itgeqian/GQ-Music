## todo14专辑数量统计接入

### 背景
- 在管理端首页统计页新增“专辑数量”卡片，与现有“用户/歌手/歌曲/歌单”统计保持一致。
- 后端提供统计总量接口；前端新增 API、接入到首页数据源，并增加卡片展示。

### 后端改动

- 新增接口：`GET /admin/getAllAlbumsCount`（返回 Long）
```259:262:src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java
@GetMapping("/getAllAlbumsCount")
public Result<Long> getAllAlbumsCount() {
    return albumService.getAllAlbumsCount();
}
```

- `IAlbumService` 新增统计方法
```25:26:src/main/java/cn/edu/seig/vibemusic/service/IAlbumService.java
// 统计所有专辑数量
Result<Long> getAllAlbumsCount();
```

- `AlbumServiceImpl` 实现统计逻辑（直接统计专辑表总数）
```81:85:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
@Override
public Result<Long> getAllAlbumsCount() {
    Long count = this.lambdaQuery().count();
    return Result.success(count == null ? 0L : count);
}
```

### 前端改动

- 新增前端 API：`getAllAlbumsCount`
```52:61:vibe-music-admin-main/src/api/data.ts
/** 获取专辑数量 */
export const getAllAlbumsCount = () => {
  const userData = getToken();
  return http.request<Result>("get", "/admin/getAllAlbumsCount", {
    headers: {
      "Content-Type": "application/json",
      Authorization: userData.accessToken
    }
  });
};
```

- 首页数据源 `useChartData.ts` 接入
  - 引入 API：
```1:7:vibe-music-admin-main/src/views/welcome/hooks/useChartData.ts
import {
  getAllUsersCount,
  getAllArtistsCount,
  getAllSongsCount,
  getAllPlaylistsCount,
  getAllAlbumsCount
} from "@/api/data";
```
  - 新增 `albumCount`：  
```13:18:vibe-music-admin-main/src/views/welcome/hooks/useChartData.ts
const userCount = ref<number>(0);
const artistCount = ref<number>(0);
const songCount = ref<number>(0);
const playlistCount = ref<number>(0);
const albumCount = ref<number>(0);
```
  - 并行请求加入专辑数量：
```71:79:vibe-music-admin-main/src/views/welcome/hooks/useChartData.ts
const allResponses = await Promise.all([
  getAllUsersCount(),
  getAllArtistsCount(),
  getAllSongsCount(),
  getAllPlaylistsCount(),
  getAllAlbumsCount(),
  ...songTypePromises,
  ...artistAreasPromises,
  ...artistGendersPromises
]);
```
  - 计数器映射加入 `albumCount`：
```116:123:vibe-music-admin-main/src/views/welcome/hooks/useChartData.ts
const counts = [
  userCount,
  artistCount,
  songCount,
  playlistCount,
  albumCount,
  ...
];
```
  - 导出 `albumCount`：
```211:217:vibe-music-admin-main/src/views/welcome/hooks/useChartData.ts
return {
  userCount,
  artistCount,
  songCount,
  playlistCount,
  albumCount,
  ...
};
```

### 页面展示（首页卡片）
- 脚本引入图标与 `albumCount`：
```11:13:vibe-music-admin-main/src/views/welcome/index.vue
import Album from "@iconify-icons/ri/album-fill";
import CompactDisc from "@iconify-icons/ri/disc-line";
```

```41:44:vibe-music-admin-main/src/views/welcome/index.vue
maleCount,
femaleCount,
albumCount
```

- “专辑数量”卡片（样式可按需调整）
```142:172:vibe-music-admin-main/src/views/welcome/index.vue
<el-card class="line-card" shadow="never">
  <div class="flex justify-between">
    <span class="text-md font-medium">
      {{ "专辑数量" }}
    </span>
    <div
      class="w-8 h-8 flex justify-center items-center rounded-md"
      :style="{
        backgroundColor: isDark ? 'transparent' : '#fff8e6'
      }"
    >
      <IconifyIconOffline :icon="CompactDisc" :color="'#f59e0b'" width="18" />
    </div>
  </div>
  <div class="flex justify-between items-start mt-3">
    <div class="w-1/2">
      <ReNormalCountTo
        :duration="2200"
        :fontSize="'2.5em'"
        :startVal="100"
        :endVal="albumCount"
      />
    </div>
    <ChartLine
      class="!w-1/2"
      :color="'#f59e0b'"
      :data="[120, 220, 330, 440, 520, 610, 720]"
    />
  </div>
</el-card>
```

### 校验与联调建议
- 接口鉴权：调用时通过 `http` 工具自动附带 `Authorization`，需确保已登录。
- 期望返回结构：`{ code: 0, data: number }`。`useChartData.ts` 内已做统一校验与降级（失败则重置为 0）。
- 并行请求：专辑数量加入 `Promise.all`，不会影响其他统计的并行性能。

### 布局说明
- 当前你已将卡片的 `:value` 调整为 `8/12/12` 等，以适配你的行数布局。如果需要五卡同排，可把前五个卡片 `:value` 统一设为 `4`；也可根据断点设置 `:md/:sm/:xs` 控制在小屏自动换行。

### 变更影响面
- 新增后端只读统计接口，无数据写操作。
- 前端仅统计页相关文件改动，未影响其他页面逻辑。

![image-20250917151425823](C:\Users\wanglei\AppData\Roaming\Typora\typora-user-images\image-20250917151425823.png)