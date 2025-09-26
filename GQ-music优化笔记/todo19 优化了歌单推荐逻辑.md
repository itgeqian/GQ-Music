## todo19 优化了歌单推荐逻辑


这里是一份“推荐逻辑”专项文档，覆盖歌单与歌曲两条链路，并附关键代码引用。

### 一、推荐目标与一致性约束
- 推荐分为“歌单推荐”和“歌曲推荐”两条。
- 均遵循“固定推荐优先，个性化/随机补足”的一致性策略，保证条数充足且顺序稳定。
- 已固定（手动置顶）的推荐必须“常驻不变”，刷新只影响非固定部分。

---

### 二、歌单推荐逻辑

#### 2.1 核心流程
- 总数固定为 10。
- 第一步取固定推荐（Pinned）：来自 `tb_playlist_recommendation`，按 `weight` 降序取前 N。
- 第二步补齐：
  - 未登录：随机补齐。
  - 已登录：基于用户收藏歌单的风格偏好做“个性化”补齐，再不足随机补齐。
- 去重合并：固定推荐优先，不与个性化/随机重复。

关键实现（节选）：
```145:225:src/main/java/cn/edu/seig/vibemusic/service/impl/PlaylistServiceImpl.java
List<PlaylistVO> pinned = playlistMapper.getPinnedRecommendedPlaylists(10);
int remain = Math.max(0, 10 - pinned.size());

if (userId == null) {
  if (remain == 0) return Result.success(pinned);
  List<PlaylistVO> random = playlistMapper.getRandomPlaylists(remain);
  LinkedHashMap<Long, PlaylistVO> mapVo = new LinkedHashMap<>();
  for (PlaylistVO vo : pinned) mapVo.put(vo.getPlaylistId(), vo);
  for (PlaylistVO vo : random) mapVo.putIfAbsent(vo.getPlaylistId(), vo);
  return Result.success(new ArrayList<>(mapVo.values()));
}

// 登录用户：根据收藏风格统计偏好
List<Long> favoritePlaylistIds = userFavoriteMapper.getFavoritePlaylistIdsByUserId(userId);
...
List<String> favoriteStyles = playlistMapper.getFavoritePlaylistStyles(favoritePlaylistIds);
List<Long> favoriteStyleIds = userFavoriteMapper.getFavoriteIdsByStyle(favoriteStyles);
Map<Long, Long> styleFrequency = favoriteStyleIds.stream().collect(groupingBy(identity(), counting()));
List<Long> sortedStyleIds = styleFrequency.entrySet().stream()
  .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
  .map(Map.Entry::getKey).toList();

List<PlaylistVO> personalized = playlistMapper.getRecommendedPlaylistsByStyles(sortedStyleIds, favoritePlaylistIds, Math.max(0, 10 - pinned.size()));

LinkedHashMap<Long, PlaylistVO> mapVo = new LinkedHashMap<>();
for (PlaylistVO vo : pinned) mapVo.put(vo.getPlaylistId(), vo);
for (PlaylistVO vo : personalized) { if (mapVo.size() >= 10) break; mapVo.putIfAbsent(vo.getPlaylistId(), vo); }
// 若仍不足再随机填充
while (mapVo.size() < 10) { List<PlaylistVO> random = playlistMapper.getRandomPlaylists(10); ... }
```

#### 2.2 管理端的“常驻推荐”与只读用户歌单
- 管理端“歌单管理”只显示官方歌单：在所有管理端列表接口加 `user_id IS NULL` 条件，用户歌单不混入官方管理。
- 管理端/用户歌单（只读）中可手动“设为推荐/取消推荐”，推荐状态保存在 `tb_playlist_recommendation`，对客户端生效且持久。

相关接口（节选）：
```24:84:src/main/java/cn/edu/seig/vibemusic/service/IPlaylistService.java
Result<String> recommendPlaylist(Long playlistId, Integer weight);
Result<String> cancelRecommendPlaylist(Long playlistId);
Result<List<PlaylistVO>> getPinnedRecommendedPlaylists(Integer limit);
```

---

### 三、歌曲推荐逻辑（未进行固定推荐，不合适）

#### 3.1 核心流程
- 总数目标为 20。
- 未登录：直接返回随机 20 首（由 `SongMapper.getRandomSongsWithArtist()` 提供）。
- 已登录：
  1) 基于用户收藏歌曲的“风格”做加权：统计用户收藏歌曲的风格出现频次，得到 `sortedStyleIds`。
  2) 从 Redis 读取该用户的“候选推荐池”缓存（Key: `recommended_songs:{userId}`，有效期 30 分钟）。若无则按风格偏好查库生成 80 条候选，并写入缓存。
  3) 从候选池随机抽取 20 首。若不足 20，再从随机歌曲中补齐，避免重复。

关键实现（节选）：
```168:242:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
// 未登录：随机
if (map == null) {
  return Result.success(songMapper.getRandomSongsWithArtist());
}

// 登录用户：按收藏风格加权
Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
List<Long> favoriteSongIds = userFavoriteMapper.getFavoriteSongIdsByUserId(userId);
if (favoriteSongIds.isEmpty()) {
  return Result.success(songMapper.getRandomSongsWithArtist());
}
List<Long> favoriteStyleIds = songMapper.getFavoriteSongStyles(favoriteSongIds);
Map<Long, Long> styleFrequency = favoriteStyleIds.stream().collect(groupingBy(identity(), counting()));
List<Long> sortedStyleIds = styleFrequency.entrySet().stream()
  .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
  .map(Map.Entry::getKey).toList();

// Redis 缓存候选推荐池（80）
String redisKey = "recommended_songs:" + userId;
List<SongVO> cachedSongs = redisTemplate.opsForList().range(redisKey, 0, -1);
if (cachedSongs == null || cachedSongs.isEmpty()) {
  cachedSongs = songMapper.getRecommendedSongsByStyles(sortedStyleIds, favoriteSongIds, 80);
  redisTemplate.opsForList().rightPushAll(redisKey, cachedSongs);
  redisTemplate.expire(redisKey, 30, TimeUnit.MINUTES);
}

// 从候选池随机取 20，不足用随机歌补齐且去重
Collections.shuffle(cachedSongs);
List<SongVO> recommendedSongs = cachedSongs.subList(0, Math.min(20, cachedSongs.size()));
if (recommendedSongs.size() < 20) { List<SongVO> randomSongs = songMapper.getRandomSongsWithArtist(); ... }
```

#### 3.2 推荐与“喜欢状态”的统一
- 在分页/推荐等返回的 `SongVO` 上，默认 `likeStatus=0`，若用户已登录则批量补充已收藏歌曲的 `likeStatus=1`，保证前端心跳一致。
```86:146:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
songVOList = songPage.getRecords().stream().peek(v -> v.setLikeStatus(DEFAULT)).toList();
if (user) { 查询收藏 → 批量将对应 songId 的 likeStatus 置为 1 }
```

---

### 四、客户端消费与用户体验

- 歌单推荐（首页）：`getRecommendedPlaylists()` → 推荐数据用于“今日为你推荐”卡片，固定推荐常驻，刷新的只是非固定部分。
- 歌曲推荐（首页“相似推荐”）：`getRecommendedSongs()` → 推荐列表支持刷新按钮，始终是 20 首，随缓存与偏好变化随机。
- 若登录状态发生变化，会重新调用推荐接口以获取更符合当前用户的结果。

客户端关键消费（节选）：
```54:81:vibe-music-client-main/src/pages/index.vue
const getRecommendedData = async () => {
  const result = await getRecommendedPlaylists()
  if (result.code === 0) {
    recommendedPlaylist.value = result.data.map(i => ({ playlistId: i.playlistId, title: i.title, coverUrl: i.coverUrl ?? coverImg }))
  }
  handleRefreshSongs()
}
```

```83:109:vibe-music-client-main/src/pages/index.vue
const handleRefreshSongs = async () => {
  const result = await getRecommendedSongs()
  if (result.code === 0) {
    recommendedSongList.value = result.data.map(item => ({
      id: item.songId, name: item.songName, artists: [{ name: item.artistName }],
      album: { name: item.album, picUrl: item.coverUrl }, duration: item.duration,
      audioUrl: item.audioUrl, likeStatus: item.likeStatus || 0
    }))
  }
}
```

---

### 五、边界与优化
- 固定推荐容量可配置（当前 10）；若固定推荐过多，会优先占满并保持顺序。
- 缓存策略：歌曲推荐候选池 30 分钟缓存，减少高频刷新压力；可按用户行为（如新收藏）主动失效。
- 去重策略在歌单与歌曲均使用集合/映射去重，确保展示无重复。
- 后续可加入“协同过滤”或“Embedding 相似度”进一步提升个性化质量。

如需，我可以把本说明整理为 `docs/推荐逻辑说明.md` 放入仓库，并附上时序图（固定→个性化→随机）与 SQL/Mapper 方法说明。