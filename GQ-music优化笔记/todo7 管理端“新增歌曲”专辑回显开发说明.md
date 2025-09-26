## todo7 管理端“新增歌曲”专辑回显开发说明

#### 目标
- 在“新增歌曲”对话框中，专辑字段支持：
  - 回显当前歌手已有的专辑列表以供选择；
  - 允许直接输入新专辑名（不在回显列表中时）。

#### 后端接口
- 新增接口：返回指定歌手的专辑列表（去重）
```java
// AdminController.java
@GetMapping("/getAlbumsByArtist/{id}")
public Result<List<String>> getAlbumsByArtist(@PathVariable("id") Long artistId) {
    return songService.getAlbumsByArtist(artistId);
}
```

- Service 定义与实现
```java
// ISongService.java
Result<List<String>> getAlbumsByArtist(Long artistId);
```

```java
// SongServiceImpl.java（片段）
@Override
public Result<List<String>> getAlbumsByArtist(Long artistId) {
    List<Song> list = songMapper.selectList(new QueryWrapper<Song>()
        .select("album")
        .eq("artist_id", artistId)
        .isNotNull("album")
        .ne("album", ""));
    List<String> albums = list.stream()
        .map(Song::getAlbum)
        .filter(Objects::nonNull)
        .filter(a -> !a.isEmpty())
        .distinct()
        .toList();
    return Result.success(albums);
}
```

#### 管理端 API
```ts
// src/api/system.ts
export const getAlbumsByArtist = (artistId: number) => {
  const userData = getToken();
  return http.request<Result>("get", `/admin/getAlbumsByArtist/${artistId}`, {
    headers: { Authorization: userData.accessToken }
  });
};
```

#### 前端表单改造（vibe-music-admin-main）
- 文件：`src/views/song/form/index.vue`
- 关键点：
  - 将专辑输入从 `el-input` 改为 `el-select`，开启 `filterable + allow-create + default-first-option`；
  - 监听 `artistId` 变化，自动调用后端接口加载专辑列表；
  - 回显列表通过 `albumOptions` 渲染；仍可手工输入新专辑名。

- 代码（核心片段）
```vue
<script setup lang="ts">
import { ref, watch } from "vue";
import { getAlbumsByArtist } from "@/api/system";

const ruleFormRef = ref();
const newFormInline = ref(props.formInline);

// 专辑回显选项
const albumOptions = ref<string[]>([]);
const albumsLoading = ref(false);

async function loadAlbums(artistId: number | null) {
  if (!artistId) { albumOptions.value = []; return; }
  albumsLoading.value = true;
  try {
    const res: any = await getAlbumsByArtist(artistId);
    albumOptions.value = res && res.code === 0 && Array.isArray(res.data) ? (res.data as string[]) : [];
  } finally {
    albumsLoading.value = false;
  }
}

// artistId 变化即刷新专辑列表
watch(() => newFormInline.value.artistId, (val) => loadAlbums(val as any), { immediate: true });
</script>

<template>
  <!-- 专辑选择：可选可输 -->
  <re-col :value="12" :xs="24" :sm="24">
    <el-form-item label="专辑" prop="album" required>
      <el-select
        v-model="newFormInline.album"
        filterable
        allow-create
        default-first-option
        :loading="albumsLoading"
        placeholder="请选择或输入专辑"
        class="w-full"
      >
        <el-option v-for="(item, idx) in albumOptions" :key="idx" :label="item" :value="item" />
      </el-select>
    </el-form-item>
  </re-col>
</template>
```

#### 交互说明
- 当“新增歌曲”弹窗打开时，如果 `artistId` 已填入（来自选中歌手），会自动加载其已有专辑，用户可直接选用。
- 若该歌曲不属于任何回显专辑，可直接在下拉框中输入新专辑名回车或选中创建项。
- 当切换 `artistId` 时，将重新请求该歌手的专辑并更新下拉选项。

#### 验收清单
- 打开“新增歌曲”，`artistId` 存在时专辑下拉出现可选项。
- 能选择已有专辑；能输入新专辑。
- 切换歌手编号，专辑回显随之刷新。
- 提交后后台能收到正确的 `album` 字段。