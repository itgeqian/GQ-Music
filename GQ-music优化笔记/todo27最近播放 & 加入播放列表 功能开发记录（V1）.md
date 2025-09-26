## todo27最近播放 & 加入播放列表 功能开发记录（V1）

本记录说明了“最近播放”模块与“加入播放列表（插入到当前曲目下一位）”的端到端实现，涵盖后端表结构/接口、前端 API/页面/播放器与列表交互的关键改动，并附关键代码参考。



## 一、后端

- 目标
  - 记录用户最近播放（去重置顶、每用户最多200条）
  - 提供分页查询、删除单条、清空接口
  - 返回歌曲基础信息（含秒级时长、歌手名）

- 新表
  - `tb_user_recent_play`（用户最近播放）
  - 字段：`id`、`user_id`、`song_id`、`create_time`
  - 约束：`idx_user_time`、`idx_user_song`

```1:11:sql/2025-09-23_user_recent_play.sql
-- 用户最近播放表（每用户最多保留200条，业务层控制）
CREATE TABLE IF NOT EXISTS `tb_user_recent_play` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `song_id` BIGINT NOT NULL COMMENT '歌曲ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_user_song` (`user_id`, `song_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户最近播放';
```

- 实体/Mapper/XML

```1:23:src/main/java/cn/edu/seig/vibemusic/model/entity/UserRecentPlay.java
@TableName("tb_user_recent_play")
public class UserRecentPlay implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("song_id")
    private Long songId;
    @TableField("create_time")
    private LocalDateTime createTime;
}
```

```1:15:src/main/java/cn/edu/seig/vibemusic/mapper/UserRecentPlayMapper.java
@Mapper
public interface UserRecentPlayMapper extends BaseMapper<UserRecentPlay> {
    @Select("SELECT COUNT(1) FROM tb_user_recent_play WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);

    @Select("SELECT id FROM tb_user_recent_play WHERE user_id = #{userId} ORDER BY create_time ASC LIMIT #{limit}")
    List<Long> findOldestIds(@Param("userId") Long userId, @Param("limit") int limit);
}
```

- Service 实现（去重、截断为200、分页查询联表补齐歌手与时长秒数）

```1:43:src/main/java/cn/edu/seig/vibemusic/service/impl/RecentPlayServiceImpl.java
public void reportRecent(Long userId, Long songId) {
    // 去重 → 删除已有
    recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>()
        .eq(UserRecentPlay::getUserId, userId)
        .eq(UserRecentPlay::getSongId, songId));
    // 插入新记录
    UserRecentPlay rec = new UserRecentPlay();
    rec.setUserId(userId);
    rec.setSongId(songId);
    rec.setCreateTime(LocalDateTime.now());
    recentMapper.insert(rec);
    // 超限截断
    int count = recentMapper.countByUser(userId);
    if (count > MAX_RECENT) {
        List<Long> ids = recentMapper.findOldestIds(userId, count - MAX_RECENT);
        if (ids != null && !ids.isEmpty()) {
            recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>().in(UserRecentPlay::getId, ids));
        }
    }
}
```

```52:87:src/main/java/cn/edu/seig/vibemusic/service/impl/RecentPlayServiceImpl.java
public PageResult<?> page(Long userId, Integer pageNum, Integer pageSize) {
    Page<UserRecentPlay> res = recentMapper.selectPage(
        new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize),
        new LambdaQueryWrapper<UserRecentPlay>().eq(UserRecentPlay::getUserId, userId)
            .orderByDesc(UserRecentPlay::getCreateTime));

    List<Long> songIds = res.getRecords().stream().map(UserRecentPlay::getSongId).collect(Collectors.toList());
    List<Song> songs = songIds.isEmpty() ? java.util.Collections.emptyList() : songMapper.selectBatchIds(songIds);
    Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getSongId, s -> s));
    // 批量查歌手
    Set<Long> artistIds = songs.stream().map(Song::getArtistId).filter(Objects::nonNull).collect(Collectors.toSet());
    List<Artist> artists = artistIds.isEmpty() ? Collections.emptyList() : artistMapper.selectBatchIds(artistIds);
    Map<Long, String> artistNameMap = artists.stream().collect(Collectors.toMap(Artist::getArtistId, Artist::getArtistName));

    List<Map<String, Object>> items = res.getRecords().stream().map(r -> {
        Song s = songMap.getOrDefault(r.getSongId(), null);
        HashMap<String, Object> m = new HashMap<>();
        m.put("songId", r.getSongId());
        m.put("songName", s != null ? s.getSongName() : "");
        m.put("artistName", s != null ? artistNameMap.getOrDefault(s.getArtistId(), "") : "");
        m.put("album", s != null ? s.getAlbum() : "");
        // 将小数秒四舍五入为整数秒
        long seconds = 0;
        try { if (s != null && s.getDuration() != null) seconds = Math.round(Double.parseDouble(s.getDuration())); } catch (Exception ignore) {}
        m.put("duration", String.valueOf(seconds));
        m.put("coverUrl", s != null ? s.getCoverUrl() : null);
        m.put("audioUrl", s != null ? s.getAudioUrl() : null);
        m.put("likeStatus", 0);
        return m;
    }).collect(Collectors.toList());
    return new PageResult<>(res.getTotal(), items);
}
```

- Controller（从 ThreadLocal 取用户ID；列表/上报/删一条/清空）

```11:24:src/main/java/cn/edu/seig/vibemusic/controller/RecentPlayController.java
private Long currentUserId() {
    try {
        Map<String, Object> map = ThreadLocalUtil.get();
        return TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
    } catch (Exception ignored) {}
    return null;
}
```

```25:43:src/main/java/cn/edu/seig/vibemusic/controller/RecentPlayController.java
@PostMapping("/play")
public Result<?> report(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    Long userId = currentUserId();
    if (userId == null) return Result.error("未登录");
    Long songId = body.get("songId") == null ? null : Long.valueOf(String.valueOf(body.get("songId")));
    recentPlayService.reportRecent(userId, songId);
    return Result.success();
}
```

```35:52:src/main/java/cn/edu/seig/vibemusic/controller/RecentPlayController.java
@PostMapping("/list")
public Result<PageResult<?>> list(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    Long userId = currentUserId();
    if (userId == null) return Result.error("未登录");
    Integer pageNum = body.get("pageNum") == null ? 1 : Integer.valueOf(String.valueOf(body.get("pageNum")));
    Integer pageSize = body.get("pageSize") == null ? 20 : Integer.valueOf(String.valueOf(body.get("pageSize")));
    return Result.success(recentPlayService.page(userId, pageNum, pageSize));
}
```

```54:73:src/main/java/cn/edu/seig/vibemusic/controller/RecentPlayController.java
@DeleteMapping("/one")
public Result<?> removeOne(@RequestParam("songId") Long songId) {
    Long userId = currentUserId();
    if (userId == null) return Result.error("未登录");
    recentPlayService.removeOne(userId, songId);
    return Result.success();
}

@DeleteMapping("/clear")
public Result<?> clearAll() {
    Long userId = currentUserId();
    if (userId == null) return Result.error("未登录");
    recentPlayService.clearAll(userId);
    return Result.success();
}
```

- 权限白名单（避免403）

```81:94:src/main/resources/application.yml
role-path-permissions:
  permissions:
    ROLE_USER:
      - "/user/"
      - "/playlist/"
      - "/artist/"
      - "/song/"
      - "/album/"
      - "/recent/"
      - "/favorite/"
```



## 二、前端

### 2.1 API 封装

```354:399:vibe-music-client-main/src/api/system.ts
export const reportRecentPlay = (data: { songId: number }) =>
  http<Result>('post', '/recent/play', { data })

export const getRecentPlays = (data: { pageNum: number; pageSize: number }) =>
  http<ResultTable>('post', '/recent/list', { data })

export const removeRecentPlay = (params: { songId: number }) =>
  http<Result>('delete', '/recent/one', { params })

export const clearRecentPlays = () =>
  http<Result>('delete', '/recent/clear')
```

### 2.2 播放器上报 + 实时刷新

- 在 `play()` 成功后上报最近播放；上报成功派发全局事件 `recent:added`，最近播放页监听该事件后自动刷新服务端列表（“服务端共 x 首”实时变化）。

```120:129:vibe-music-client-main/src/hooks/useAudioPlayer.ts
if (Number.isFinite(idNum)) {
  reportRecentPlay({ songId: idNum })
    .then(() => { try { window.dispatchEvent(new CustomEvent('recent:added')) } catch {} })
    .catch(() => {})
}
```

- 在切歌时重置插入游标（服务“加入播放列表”插入行为，详见 2.4）

```171:173:vibe-music-client-main/src/hooks/useAudioPlayer.ts
await loadTrack()
play()
try { audioStore.resetNextInsertIndex() } catch {}
```

### 2.3 最近播放页面

- 获取服务端分页 + 回退到本地 store；支持“清除全部记录”（确认弹窗）与“移除单条”，并用 `:show-remove="true"` 开启“移除”菜单项。
- 监听 `recent:added` 事件做实时刷新。

```31:52:vibe-music-client-main/src/pages/recent/index.vue
const fetchServer = async () => {
  const res: any = await getRecentPlays({ pageNum: pageNum.value, pageSize: pageSize.value })
  if (res?.code === 0) {
    const data = res.data || { items: [], total: 0 }
    serverSongs.value = (data.items || []).map((s: any) => ({
      songId: s.songId, songName: s.songName, artistName: s.artistName, album: s.album,
      duration: String(Math.floor(Number(s.duration) || 0)), coverUrl: s.coverUrl || coverImg,
      audioUrl: s.audioUrl, likeStatus: s.likeStatus || 0,
    }))
    total.value = data.total || serverSongs.value.length
  } else {
    serverSongs.value = []; total.value = 0
  }
}
```

```54:75:vibe-music-client-main/src/pages/recent/index.vue
onMounted(fetchServer)
// 监听新增最近播放事件，实时刷新服务端数据
onMounted(() => {
  const handler = () => fetchServer()
  window.addEventListener('recent:added', handler)
  onBeforeUnmount(() => window.removeEventListener('recent:added', handler))
})
```

```86:101:vibe-music-client-main/src/pages/recent/index.vue
<button class="h-9 px-4 rounded-md hover:bg-hoverMenuBg" @click="onClearAll">清除全部记录</button>
...
<Table :data="serverSongs.length ? serverSongs : localSongs"
  :clickable-title="false" :show-remove="true" @row-more-command="onRowMore" />
```

```56:66:vibe-music-client-main/src/pages/recent/index.vue
const onClearAll = async () => {
  try { await ElMessageBox.confirm('确定要清除全部最近播放记录吗？此操作不可撤销。', '清除确认', { type: 'warning' }) } catch { return }
  await clearRecentPlays()
  ElMessage.success('已清空最近播放')
  serverSongs.value = []; total.value = 0; recent.clear()
}
```

```69:75:vibe-music-client-main/src/pages/recent/index.vue
const onRowMore = async (cmd: string, row: any) => {
  if (cmd !== 'remove') return
  await removeRecentPlay({ songId: Number(row.songId) })
  ElMessage.success('已从最近播放移除'); fetchServer()
}
```

### 2.4 “加入播放列表”（插入到当前曲目下一位，连续添加依次排在后面）

- AudioStore 新增插入游标与方法
  - `nextInsertIndex?: number` 记录“下次插入到哪”
  - `insertNext(track)` 将歌曲插入到 `nextInsertIndex`（或 `currentSongIndex + 1`），并自增游标
  - `resetNextInsertIndex()` 切歌时重置游标

```8:18:vibe-music-client-main/src/stores/interface/index.ts
export interface AudioState {
  trackList: trackModel[]
  currentSongIndex: number
  currentPageSongs: []
  volume: number
  quality: string
  nextInsertIndex?: number // 下次插入到播放列的位置（可选，持久化）
}
```

```20:37:vibe-music-client-main/src/stores/modules/audio.ts
insertNext(track: trackModel) {
  if (!track) return
  const existingIndex = this.trackList.findIndex(t => t.id === track.id)
  if (existingIndex !== -1) {
    this.trackList.splice(existingIndex, 1)
  }
  const baseIndex = typeof this.nextInsertIndex === 'number'
    ? this.nextInsertIndex
    : (this.currentSongIndex + 1)
  const insertIndex = Math.min(Math.max(baseIndex, 0), this.trackList.length)
  this.trackList.splice(insertIndex, 0, track)
  // 下次插入位置递增
  this.nextInsertIndex = insertIndex + 1
}
```

- Table“更多”菜单新增“加入播放列表”，调用 `audio.insertNext`

```416:424:vibe-music-client-main/src/components/Table.vue
<el-dropdown-menu>
  <el-dropdown-item command="like">{{ row.likeStatus===1 ? '取消喜欢' : '我喜欢' }}</el-dropdown-item>
  <el-dropdown-item divided command="add">添加到</el-dropdown-item>
  <el-dropdown-item divided command="enqueueNext">加入播放列表</el-dropdown-item>
  <el-dropdown-item divided command="download">下载</el-dropdown-item>
  <el-dropdown-item v-if="props.showRemove" divided command="remove">移除</el-dropdown-item>
</el-dropdown-menu>
```

```221:224:vibe-music-client-main/src/components/Table.vue
if (command === 'enqueueNext') {
  audio.insertNext(convertToTrackModel(row))
  ElMessage.success('已加入播放列表')
  return
}
```

- “移除”仅在最近播放页开启

```23:25:vibe-music-client-main/src/components/Table.vue
// 仅在“最近播放”页面展示“移除”项
showRemove: { type: Boolean, default: false },
```

### 2.5 侧边栏与路由（仅登录可见）

```17:31:vibe-music-client-main/src/layout/components/aside/data.ts
{ title: '最近播放', icon: 'ri:history-line', router: '/recent' },
```

```18:25:vibe-music-client-main/src/layout/components/aside/index.vue
const filteredMenu = computed(() =>
  MenuData.map((group) => ({
    ...group,
    children: group.children.filter((c) => !(c.router === '/recent' && !user.isLoggedIn)),
  }))
)
```

```44:59:vibe-music-client-main/src/routers/index.ts
{ path: '/recent', component: () => import('@/pages/recent/index.vue') },
```



## 三、交互要点与测试清单

- 最近播放
  - 登录态下播放歌曲 → 后端 `/recent/play` 成功；页面接收到 `recent:added` 事件自动刷新，统计“服务端共 x 首”实时变化
  - 删除单条：更多 → 移除
  - 清空全部：右上角“清除全部记录”（二次确认）
  - 歌手名、时长：均由后端分页结果返回（时长为四舍五入的秒，前端格式化）

- 加入播放列表
  - 任意列表更多 → “加入播放列表”：插入到“当前曲目下一位”
  - 连续添加多首：依次排列在上一首插入之后
  - 切歌或上下曲：自动重置插入游标到当前曲目后一位

