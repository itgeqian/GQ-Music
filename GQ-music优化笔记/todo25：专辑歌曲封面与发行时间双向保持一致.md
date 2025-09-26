## todo25：专辑/歌曲封面与发行时间双向保持一致

本记录说明了“封面一致性”和“发行时间一致性”的落地方案、核心流程与关键代码位置，便于后续维护与扩展。

--------------------------------
## 一、封面一致性

目标
- 专辑上传封面后：该专辑下“无封面”的歌曲自动继承专辑封面。
- 歌曲上传封面后：当歌曲已绑定专辑且“专辑封面为空”时，自动补齐专辑封面。
- 前端上传裁剪图不再出现 MinIO 对象名带 “-blob” 导致 404 的问题；封面展示失败时有兜底图。

实现要点
- 后端在专辑/歌曲更新接口里执行“条件回填”。
- 前端上传时将裁剪得到的 Blob 包装成 File，并显式文件名与扩展名，避免 MinIO 生成 “...-blob”。
- 列表展示加 fallback，空值或加载失败回退到占位图。

关键改动
1) 前端上传歌曲封面：裁剪 Blob → File（带后缀）
```394:442:vibe-music-admin-main/src/views/song/utils/hook.tsx
addDialog({
  title: "裁剪、上传封面",
  // ...
  beforeSure: async done => {
    // ...
    // 创建 FormData（为 Blob 指定文件名与扩展名，避免 MinIO 生成 -blob 路径）
    const formData = new FormData();
    const blob = coverInfo.value.blob as Blob;
    const mime = blob?.type || "image/png";
    const ext = mime.includes("png") ? "png" : mime.includes("jpeg") || mime.includes("jpg") ? "jpg" : "png";
    const filename = `${row.songId}-${Date.now()}.${ext}`;
    const file = new File([blob], filename, { type: mime });
    formData.append("cover", file, filename);
    formData.append("songId", row.songId);
    // 调用上传接口...
  }
});
```

2) 前端封面列兜底：空/异常/fallback 回退到默认图
```67:93:vibe-music-admin-main/src/views/song/utils/hook.tsx
cellRenderer: ({ row }) => (
  (() => {
    const normalizeCover = (u: any) => {
      const s = (u ?? "").toString().trim();
      if (!s || s === "null" || s === "undefined") return songCover as any;
      return s as any;
    };
    const safe = normalizeCover(row.cover);
    return (
      <el-image
        fit="cover"
        preview-teleported={true}
        src={safe}
        preview-src-list={Array.of(safe)}
        fallback={songCover as any}
        class="w-[72px] h-[72px] rounded-lg align-middle"
      />
    );
  })()
),
```

3) 后端：歌曲上传封面后同步专辑封面（专辑封面为空时）
```610:636:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
public Result<String> updateSongCover(Long songId, String coverUrl) {
    Song song = songMapper.selectById(songId);
    String cover = song.getCoverUrl();
    if (cover != null && !cover.isEmpty()) {
        minioService.deleteFile(cover);
    }
    song.setCoverUrl(coverUrl);
    if (songMapper.updateById(song) == 0) {
        return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
    }
    // 同步：若歌曲所属专辑存在且专辑未设置封面，则回填专辑封面
    try {
        if (song.getAlbumId() != null) {
            Album album = albumMapper.selectById(song.getAlbumId());
            if (album != null && (album.getCoverUrl() == null || album.getCoverUrl().isEmpty())) {
                album.setCoverUrl(coverUrl);
                albumMapper.updateById(album);
            }
        }
    } catch (Exception ignored) {}
    return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
}
```

4) 后端：专辑更新封面后回填“无封面歌曲”
```174:183:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
if (a.getCoverUrl() != null && !a.getCoverUrl().isEmpty()) {
    UpdateWrapper<Song> uw = new UpdateWrapper<>();
    uw.eq("album_id", a.getAlbumId())
      .and(w -> w.isNull("cover_url").or().eq("cover_url", ""))
      .set("cover_url", a.getCoverUrl());
    songMapper.update(null, uw);
}
```

接口路径
- 歌曲封面上传：PATCH `/admin/updateSongCover/{id}`（保存于 `songCovers/`）
- 专辑封面上传：PATCH `/admin/updateAlbumCover/{id}`（保存于 `albums/`）

可选策略
- 如需“强制保持一致（无条件覆盖专辑或歌曲封面）”，将上面两处“仅当为空再同步”的条件去掉或改为“当不一致则覆盖”。

--------------------------------
## 二、发行时间一致性

目标
- 新增/修改歌曲与新增/修改专辑时，二者的发行时间保持一致：
  - 若歌曲未填而专辑有时间 → 填入歌曲。
  - 若专辑未填而歌曲有时间 → 反向补齐专辑。
  - 新增专辑时若未提供时间，尝试从该专辑下已存在歌曲中取最早一首的日期回显。
- 前端“新增歌曲表单”选择专辑时自动回显该专辑发行日期（若表单内为空）。

关键改动
1) 后端：新增歌曲时的时间同步
```409:428:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
if (resolvedAlbumId != null) {
    Album album = albumMapper.selectById(resolvedAlbumId);
    // 歌曲未填而专辑有 → 回显到歌曲
    if (song.getReleaseTime() == null && album != null && album.getReleaseDate() != null) {
        song.setReleaseTime(album.getReleaseDate());
    }
    // 歌曲已填而专辑空 → 反向补齐专辑
    if (song.getReleaseTime() != null && album != null && album.getReleaseDate() == null) {
        album.setReleaseDate(song.getReleaseTime());
        albumMapper.updateById(album);
    }
}
```

2) 后端：修改歌曲时的时间同步
```504:522:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
if (resolvedAlbumId != null) {
    Album album = albumMapper.selectById(resolvedAlbumId);
    if (song.getReleaseTime() == null && album != null && album.getReleaseDate() != null) {
        song.setReleaseTime(album.getReleaseDate());
    }
    if (song.getReleaseTime() != null && album != null && album.getReleaseDate() == null) {
        album.setReleaseDate(song.getReleaseTime());
        albumMapper.updateById(album);
    }
    // 同时处理封面继承（见上一节）
}
```

3) 后端：新增/更新专辑时的时间推断与回显
```143:158:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
// 新增专辑：若未传时间，尝试从同名歌曲取最早一首的 release_time
if (a.getReleaseDate() == null) {
    Song one = songMapper.selectOne(new QueryWrapper<Song>()
            .eq("artist_id", a.getArtistId())
            .eq("album", a.getTitle())
            .isNotNull("release_time")
            .orderByAsc("release_time")
            .last("limit 1"));
    if (one != null) {
        a.setReleaseDate(one.getReleaseTime());
        albumMapper.updateById(a);
    }
}
```

```185:199:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
// 更新专辑：若仍为空，保存后再次从歌曲取最早日期补齐
Album latest = albumMapper.selectById(albumUpdateDTO.getAlbumId());
if (latest != null && latest.getReleaseDate() == null) {
    Song one = songMapper.selectOne(new QueryWrapper<Song>()
            .eq("album_id", latest.getAlbumId())
            .isNotNull("release_time")
            .orderByAsc("release_time")
            .last("limit 1"));
    if (one != null) {
        latest.setReleaseDate(one.getReleaseTime());
        albumMapper.updateById(latest);
    }
}
```

4) 前端：新增歌曲表单选择专辑后，自动回显该专辑发行日期
```71:97:vibe-music-admin-main/src/views/song/form/index.vue
async function echoReleaseFromAlbum() {
  const artistId = newFormInline.value.artistId as any
  const title = newFormInline.value.album as any
  if (!artistId || !title) return
  if (newFormInline.value.releaseTime) return
  const res: any = await http.get('/admin/getAlbumReleaseDate', { params: { artistId, title } })
  if (res && res.code === 0 && res.data) {
    newFormInline.value.releaseTime = res.data
    ;(ruleFormRef.value as any)?.validateField?.('releaseTime')
  }
}
watch(() => newFormInline.value.album, () => echoReleaseFromAlbum())
watch(() => newFormInline.value.artistId, () => echoReleaseFromAlbum())
```

接口路径
- 回显发行日期：GET `/admin/getAlbumReleaseDate?artistId=&title=`
- 后端同步逻辑由 `SongServiceImpl.addSong/updateSong` 与 `AlbumServiceImpl.addAlbum/updateAlbum` 自动执行，无需前端介入。

--------------------------------
## 三、边界与策略说明

- 覆盖策略：
  - 目前采用“空则补”的保守策略，避免非预期覆盖。若业务需要“始终保持一致（以最后一次上传/修改为准）”，可将判断条件放宽为“当不一致时覆盖”。
- 缓存一致性：
  - 涉及封面与时间的写操作已在相应服务上使用 `@CacheEvict` 清空 `songCache`、`albumCache` 等，同时通过 `CachePurger` 清理自定义 Redis 前缀，规避读到旧缓存。
- MinIO 路径规范：
  - 专辑封面：`albums/`；歌曲封面：`songCovers/`。前端统一生成带扩展名的文件名，避免 `-blob`。

--------------------------------
## 四、回归测试要点

- 在专辑页上传封面 → 歌曲列表中该专辑下“无封面歌曲”应显示专辑封面。
- 在歌曲列表裁剪并上传封面 → 若专辑封面为空，专辑列表应显示该封面；网络面板无 `-blob` 路径。
- 新增歌曲选择已有专辑且未填日期 → 自动回显专辑发行日期。
- 新增专辑不填日期，但该专辑名下已有歌曲有日期 → 保存后专辑日期自动补齐。
- 修改歌曲/专辑日期后，刷新后端缓存（已自动），前端展示一致。

如需把封面与时间改为“总是保持一致（双向强制覆盖）”，告诉我，我会提交对应的后端条件调整。