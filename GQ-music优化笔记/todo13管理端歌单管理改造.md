## todo13管理端歌单管理改造

数据库：

```
START TRANSACTION;

ALTER TABLE `tb_playlist_binding`
  DROP PRIMARY KEY,
  ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST,
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uk_playlist_song` (`playlist_id`, `song_id`);

COMMIT;
```



### 管理端歌单管理改造开发文档（第一部分：后端）

本次改造目标
- 管理端实现歌单与歌曲的可视化绑定：
  - 批量添加歌曲到歌单
  - 批量移除已绑定歌曲
  - 管理端与前台联动，新增后前台可立即回显
- 后续按需求删除了“排序”能力，简化绑定模型

总体设计
- 新增绑定表实体与服务，提供 Admin 侧统一的绑定接口；对前台读接口进行缓存驱逐，保证数据实时性
- 前端管理端做弹窗页面，支持“选择添加/已绑定列表（批量移除）”，并接入新增的 Admin API

一、数据模型与 Mapper

1) 绑定表实体 `PlaylistBinding`
- 采用单列自增主键 `id`，业务键为 `(playlist_id, song_id)`（表上有唯一约束）
- 后续已删除 `sort_no` 列，数据顺序以 `id` 为时间序近似

```1:48:src/main/java/cn/edu/seig/vibemusic/model/entity/PlaylistBinding.java
@TableName("tb_playlist_binding")
public class PlaylistBinding implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("playlist_id")
    private Long playlistId;
    @TableField("song_id")
    private Long songId;
}
```

2) Mapper XML 与接口
- 批量插入使用 INSERT IGNORE 幂等
- 查询已绑定歌曲：联表歌曲、歌手信息；按绑定 id 倒序（新增在前）

```3:37:src/main/resources/mapper/PlaylistBindingMapper.xml
<insert id="insertBatchIgnore">
    INSERT IGNORE INTO tb_playlist_binding(playlist_id, song_id)
    VALUES
    <foreach collection="list" item="it" separator="," >
        (#{it.playlistId}, #{it.songId})
    </foreach>
</insert>

<select id="selectSongsOfPlaylist" resultType="cn.edu.seig.vibemusic.model.vo.PlaylistSongVO">
    SELECT b.id,
           s.id AS songId,
           s.name AS songName,
           a.name AS artistName,
           s.album,
           s.cover_url AS coverUrl,
           s.audio_url AS audioUrl,
           s.release_time AS releaseTime
    FROM tb_playlist_binding b
    JOIN tb_song s ON b.song_id = s.id
    LEFT JOIN tb_artist a ON s.artist_id = a.id
    WHERE b.playlist_id = #{playlistId}
    <if test="keyword != null and keyword.trim() != ''">
        AND (s.name LIKE CONCAT('%', #{keyword}, '%') OR a.name LIKE CONCAT('%', #{keyword}, '%'))
    </if>
    ORDER BY b.id DESC
</select>
```

```1:27:src/main/java/cn/edu/seig/vibemusic/mapper/PlaylistBindingMapper.java
@Mapper
public interface PlaylistBindingMapper extends BaseMapper<PlaylistBinding> {
    int insertBatchIgnore(@Param("list") java.util.List<PlaylistBinding> list);
    java.util.List<PlaylistSongVO> selectSongsOfPlaylist(@Param("playlistId") Long playlistId,
                                                         @Param("keyword") String keyword);
}
```

3) 返回 VO（绑定视图）
- 用于把 SQL 结果映射到服务层，再转换为统一的 `SongAdminVO` 返回前端

```1:33:src/main/java/cn/edu/seig/vibemusic/model/vo/PlaylistSongVO.java
public class PlaylistSongVO implements Serializable {
    private Long id;
    private Long songId;
    private String songName;
    private String artistName;
    private String album;
    private String coverUrl;
    private String audioUrl;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate releaseTime;
}
```

二、Service 层

1) 绑定服务实现 `PlaylistBindingServiceImpl`
- 批量添加：INSERT IGNORE 幂等；统计“新增/忽略”数量，返回明确提示
- 批量移除：按歌单 id + songIds 删除
- 查询：调用 Mapper 查询全量，服务层做简单内存分页
- 所有写操作都进行 `@CacheEvict(cacheNames="playlistCache", allEntries = true)`，确保前台歌单详情缓存失效，能及时回显

```32:59:src/main/java/cn/edu/seig/vibemusic/service/impl/PlaylistBindingServiceImpl.java
@CacheEvict(cacheNames = "playlistCache", allEntries = true)
public Result<String> addSongs(Long playlistId, List<Long> songIds) {
    List<PlaylistBinding> rows = songIds.stream().distinct().map(id -> {
        PlaylistBinding b = new PlaylistBinding();
        b.setPlaylistId(playlistId);
        b.setSongId(id);
        return b;
    }).toList();
    int affected = 0;
    try { affected = bindingMapper.insertBatchIgnore(rows); }
    catch (Exception e) { for (PlaylistBinding r : rows) try { if (this.save(r)) affected++; } catch (Exception ignored) {} }
    int ignored = Math.max(0, rows.size() - affected);
    return Result.success(ignored == 0 ? ("添加成功，新增 " + affected + " 首")
                                       : ("已添加 " + affected + " 首，" + ignored + " 首已在歌单中"));
}

@CacheEvict(cacheNames = "playlistCache", allEntries = true)
public Result<String> removeSongs(Long playlistId, List<Long> songIds) {
    remove(new LambdaQueryWrapper<PlaylistBinding>()
        .eq(PlaylistBinding::getPlaylistId, playlistId)
        .in(PlaylistBinding::getSongId, songIds));
    return Result.success("删除成功");
}

public Result<PageResult<SongAdminVO>> getSongsOfPlaylist(PlaylistSongQueryDTO dto) {
    List<PlaylistSongVO> all = bindingMapper.selectSongsOfPlaylist(dto.getPlaylistId(), dto.getKeyword());
    List<SongAdminVO> mapped = all.stream().map(p -> {
        SongAdminVO v = new SongAdminVO();
        v.setSongId(p.getSongId());
        v.setSongName(p.getSongName());
        v.setArtistName(p.getArtistName());
        v.setAlbum(p.getAlbum());
        v.setCoverUrl(p.getCoverUrl());
        v.setAudioUrl(p.getAudioUrl());
        v.setReleaseTime(p.getReleaseTime());
        return v;
    }).toList();
    long total = mapped.size();
    int from = Math.max(0, (pageNum - 1) * pageSize);
    int to = Math.min(mapped.size(), from + pageSize);
    List<SongAdminVO> pageItems = from >= to ? List.of() : mapped.subList(from, to);
    return Result.success(new PageResult<>(total, pageItems));
}
```

三、Controller（Admin）

新增的绑定相关接口
- 批量添加：`POST /admin/playlist/addSongs`
- 批量移除：`DELETE /admin/playlist/removeSongs`
- 分页查询：`POST /admin/playlist/songs`
- 已删除排序接口

```278:305:src/main/java/cn/edu/seig/vibemusic/controller/AdminController.java
@PostMapping("/playlist/addSongs")
public Result<String> addSongsToPlaylist(@RequestBody @Valid PlaylistSongBatchDTO dto, BindingResult br) {
    String err = BindingResultUtil.handleBindingResultErrors(br);
    if (err != null) return Result.error(err);
    return playlistBindingService.addSongs(dto.getPlaylistId(), dto.getSongIds());
}

@DeleteMapping("/playlist/removeSongs")
public Result<String> removeSongsFromPlaylist(@RequestBody @Valid PlaylistSongBatchDTO dto, BindingResult br) {
    String err = BindingResultUtil.handleBindingResultErrors(br);
    if (err != null) return Result.error(err);
    return playlistBindingService.removeSongs(dto.getPlaylistId(), dto.getSongIds());
}

@PostMapping("/playlist/songs")
public Result<PageResult<SongAdminVO>> getSongsOfPlaylist(@RequestBody PlaylistSongQueryDTO dto) {
    return playlistBindingService.getSongsOfPlaylist(dto);
}
```

四、前端管理端

1) API 封装 `vibe-music-admin-main/src/api/system.ts`
```442:474:vibe-music-admin-main/src/api/system.ts
export const addSongsToPlaylist = (data: { playlistId: number; songIds: number[] }) => {
  const userData = getToken();
  return http.request<Result>("post", "/admin/playlist/addSongs", {
    headers: { "Content-Type": "application/json", Authorization: userData.accessToken },
    data
  });
};

export const removeSongsFromPlaylist = (data: { playlistId: number; songIds: number[] }) => {
  const userData = getToken();
  return http.request<Result>("delete", "/admin/playlist/removeSongs", {
    headers: { "Content-Type": "application/json", Authorization: userData.accessToken },
    data
  });
};

export const getSongsOfPlaylist = (data: { playlistId: number; pageNum: number; pageSize: number; keyword?: string }) => {
  const userData = getToken();
  return http.request<ResultTable>("post", "/admin/playlist/songs", {
    headers: { "Content-Type": "application/json", Authorization: userData.accessToken },
    data
  });
};
```

2) 弹窗页面 `views/playlist/bind.vue`
- 两个页签：“选择添加”与“已绑定”
- 选择添加：按歌手/歌名筛选，多选添加，成功后清空勾选并切到“已绑定”
- 已绑定：展示当前歌单下歌曲，支持勾选批量移除
- 已移除拖拽与排序 UI/逻辑

关键片段（部分）
```193:276:vibe-music-admin-main/src/views/playlist/bind.vue
<el-dialog v-model="dialogVisible" title="添加歌曲到歌单" ...>
  <el-tabs v-model="activeTab">
    <el-tab-pane label="选择添加" name="add">
      <!-- 条件区域/表格/分页 -->
    </el-tab-pane>
    <el-tab-pane label="已绑定" name="bound">
      <div class="mb-2 flex justify-between">
        <div>
          <el-button type="danger" @click="handleRemoveBound">移除选中</el-button>
        </div>
        <el-pagination ... />
      </div>
      <el-table ref="boundTableRef" :data="boundList" ... @selection-change="(rows:any[]) => (boundSelected = rows)">
        <el-table-column type="selection" width="50" />
        <el-table-column label="封面" width="90"> ... </el-table-column>
        <el-table-column prop="songName" label="歌曲名" min-width="180" />
        <el-table-column prop="artistName" label="歌手" width="160" />
        <el-table-column prop="album" label="专辑" min-width="180" />
      </el-table>
    </el-tab-pane>
  </el-tabs>
  <template #footer>
    <el-button @click="handleClose">关闭</el-button>
    <el-button v-if="activeTab==='add'" type="primary" :loading="submitting" @click="handleConfirm">确认添加</el-button>
  </template>
</el-dialog>
```

五、数据库变更 SQL

1) 初始约束（建议）
- 确保绑定唯一性（若尚未添加）
```sql
ALTER TABLE tb_playlist_binding
  ADD UNIQUE KEY uk_playlist_song (playlist_id, song_id);
```

2) 删除排序列
```sql
ALTER TABLE tb_playlist_binding DROP COLUMN sort_no;
```

3) 变更后的查询排序规则
- 已在 XML 改为 `ORDER BY b.id DESC`，相当于新增在前

六、联动与缓存
- 绑定写操作使用 `@CacheEvict(cacheNames="playlistCache", allEntries=true)`，使前台 `/playlist/getPlaylistDetail/{id}` 缓存失效，避免前台看到旧数据
- 客户端退出（补充）：确保 `/user/logout` Controller 在拿到 `Authorization` 时剥离 `Bearer ` 前缀（你已修复，现已正常）
- ```
     /**
       * 登出
       *
       * @param token 认证token
       * @return 结果
       */
      @PostMapping("/logout")
      public Result logout(@RequestHeader("Authorization") String token) {
          if (token != null && token.startsWith("Bearer ")) {
              token = token.substring(7);
          }
          return userService.logout(token);
      }
  
  ```

  

七、使用说明

- 在“歌单管理”页，三点菜单→添加歌曲：
  - 选择添加：勾选多首→确认添加，成功后提示“新增X首，Y首已在歌单中”
  - 已绑定：可勾选移除→“移除选中”
- 关闭弹窗回到列表，如遇极少数白屏场景，已加入“关闭后刷新”的兜底

