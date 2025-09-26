
## todo9喜欢模块的分类导航与关注歌手

本次实现目标
- 在客户端“喜欢”页面新增分类导航，包含：歌曲、专辑、我的关注。
- 歌曲：展示用户已点爱心的歌曲，支持分页与搜索。
- 专辑：基于已喜欢歌曲的 album 字段进行聚合展示。
- 我的关注：展示用户关注的歌手名，点击跳转歌手详情。
- 三个标签后显示实时数量徽标；关注/取关、收藏变动后能刷新数量。

一、数据库与后端

1) 表结构
- 复用 `tb_user_favorite` 表，新增关注歌手字段：
```sql
ALTER TABLE `tb_user_favorite`
  ADD COLUMN `artist_id` BIGINT NULL COMMENT '关注歌手ID' AFTER `playlist_id`;
CREATE INDEX idx_userfavorite_user_type_artist ON `tb_user_favorite` (`user_id`, `type`, `artist_id`);
```
- 收藏类型枚举扩展：`FavoriteTypeEnum` 新增 `ARTIST(2, "关注歌手")`。

2) 实体与 Mapper
- `UserFavorite` 新增字段：
```java
@TableField("artist_id")
private Long artistId;
```
- `UserFavoriteMapper` 新增：
```java
@Select("SELECT artist_id FROM tb_user_favorite WHERE user_id = #{userId} AND type = 2 ORDER BY create_time DESC")
List<Long> getUserFollowArtistIds(@Param("userId") Long userId);
```

3) 后端接口
- 控制器：`/favorite` 下新增/完善接口（关键代码）：
```20:116:src/main/java/cn/edu/seig/vibemusic/controller/UserFavoriteController.java
@GetMapping("/getFollowArtists")
public Result<PageResult<ArtistVO>> getFollowArtists(@RequestParam Integer pageNum,
                                                     @RequestParam Integer pageSize,
                                                     @RequestParam(required = false) String artistName) {
  return userFavoriteService.getFollowArtists(pageNum, pageSize, artistName);
}

@PostMapping("/followArtist")
public Result<String> followArtist(@RequestParam Long artistId) {
  return userFavoriteService.followArtist(artistId);
}

@DeleteMapping("/cancelFollowArtist")
public Result<String> cancelFollowArtist(@RequestParam Long artistId) {
  return userFavoriteService.cancelFollowArtist(artistId);
}
```
- 服务实现：按用户返回关注歌手、关注/取消关注（关键代码）：
```220:291:src/main/java/cn/edu/seig/vibemusic/service/impl/UserFavoriteServiceImpl.java
@Cacheable(key = "'followArtists-' + #pageNum + '-' + #pageSize + '-' + #artistName")
public Result<PageResult<ArtistVO>> getFollowArtists(Integer pageNum, Integer pageSize, String artistName) {
  Long userId = TypeConversionUtil.toLong(ThreadLocalUtil.get().get(JwtClaimsConstant.USER_ID));
  List<Long> artistIds = userFavoriteMapper.getUserFollowArtistIds(userId);
  if (artistIds.isEmpty()) return Result.success(new PageResult<>(0L, Collections.emptyList()));

  List<ArtistVO> all = artistIds.stream().map(id -> {
    var artist = artistMapper.selectById(id);
    if (artist == null) return null;
    ArtistVO vo = new ArtistVO();
    vo.setArtistId(artist.getArtistId());
    vo.setArtistName(artist.getArtistName());
    vo.setAvatar(artist.getAvatar());
    return vo;
  }).filter(Objects::nonNull)
    .filter(vo -> artistName == null || artistName.isBlank() || vo.getArtistName().contains(artistName))
    .toList();

  int from = Math.max(0, (pageNum - 1) * pageSize);
  int to = Math.min(all.size(), from + pageSize);
  return Result.success(new PageResult<>((long) all.size(), from >= all.size()? Collections.emptyList(): all.subList(from, to)));
}

@CacheEvict(cacheNames = {"userFavoriteCache","artistCache"}, allEntries = true)
public Result<String> followArtist(Long artistId) { /* insert type=2, artist_id */ }

@CacheEvict(cacheNames = {"userFavoriteCache","artistCache"}, allEntries = true)
public Result<String> cancelFollowArtist(Long artistId) { /* delete type=2, artist_id */ }
```

4) 修复与细节
- 收藏歌曲查询去重且按用户过滤，避免重复：
```50:115:src/main/resources/mapper/SongMapper.xml
SELECT DISTINCT ...
FROM tb_song s
LEFT JOIN tb_artist a ON s.artist_id = a.id
LEFT JOIN tb_user_favorite u ON s.id = u.song_id
WHERE
  u.user_id = #{userId}
  AND u.type = 0
  AND s.id IN (...)
ORDER BY u.create_time DESC
```

二、前端实现

1) API 封装
```163:176:vibe-music-client-main/src/api/system.ts
export const getFollowArtists = (params) => http<Result>('get', '/favorite/getFollowArtists', { params })
export const followArtist = (artistId) => http<Result>('post', '/favorite/followArtist', { params: { artistId } })
export const cancelFollowArtist = (artistId) => http<Result>('delete', '/favorite/cancelFollowArtist', { params: { artistId } })
```

2) 歌手详情页接入关注/取消
- 关键交互：点击按钮调用后端，成功后同步本地 `useFollowStore`，按钮实时切换。
```90:115:vibe-music-client-main/src/pages/artist/[id].vue
const toggleFollow = async () => {
  const id = artistInfo.value?.artistId
  const name = artistInfo.value?.artistName || '未知歌手'
  if (!id) return
  if (!isFollowing.value) {
    const res = await followArtist(id)
    if (res.code === 0) followStore.follow({ artistId: id, artistName: name })
  } else {
    const res = await cancelFollowArtist(id)
    if (res.code === 0) followStore.unfollow(id)
  }
}
```

3) 喜欢页分类导航与数据源
- tabs 与数量徽标：
```181:191:vibe-music-client-main/src/pages/like/index.vue
<button ... @click="activeTab='songs'">歌曲<span v-if="playlist.trackCount">({{ playlist.trackCount }})</span></button>
<button ... @click="activeTab='albums'">专辑<span v-if="albumCount">({{ albumCount }})</span></button>
<button ... @click="activeTab='following'">我的关注<span v-if="followingCount">({{ followingCount }})</span></button>
```
- 歌曲数据（分页、搜索）：
```55:72:.../like/index.vue
const res = await getFavoriteSongs({ pageNum, pageSize, songName: searchKeyword.value, artistName: '', album: '' })
songs.value = pageData.items
playlist.value.trackCount = pageData.total
```
- 专辑聚合：
```125:134:.../like/index.vue
const albums = computed(() => {
  const map = new Map<string, Song[]>()
  songs.value.forEach(s => (map.has(s.album || '未命名专辑') ? 0 : map.set(s.album || '未命名专辑', []), map.get(s.album || '未命名专辑')!.push(s)))
  return Array.from(map.entries()).map(([album, items]) => ({ album, items }))
})
const albumCount = computed(() => albums.value.length)
```
- 我的关注（后端拉取、数量与列表）：
```31:48:.../like/index.vue
const followArtists = ref<FollowArtist[]>([])
const followTotal = ref(0)
const fetchFollowArtists = async () => {
  const res = await getFollowArtists({ pageNum: 1, pageSize: 30 })
  followArtists.value = res.data?.items || []
  followTotal.value = res.data?.total || 0
}
const followingCount = computed(() => followTotal.value)
watch(activeTab, (tab) => { if (tab === 'following') fetchFollowArtists() })
onMounted(() => { getSongs(); fetchFollowArtists() })
```

4) 其它细节
- 统一封面兜底，避免图片灰块：在 `components/Table.vue` 中将封面 `src` 改为 `row.coverUrl || default_album`。
- 专辑容器可滚动：为专辑页签容器加 `flex-1 overflow-y-auto min-h-0`。

三、权限与异常处理

- Axios 拦截器自动附带 Bearer 前缀，并在 401/403 清空登录状态提示重登：
```1:139:vibe-music-client-main/src/utils/http.ts
if (token) config.headers.Authorization = token.startsWith('Bearer ') ? token : `Bearer ${token}`
case 401: userStore.clearUserInfo(); ...
case 403: userStore.clearUserInfo(); ElMessage.error('没有权限或登录已过期，请重新登录')
```

四、测试用例与验收点
- 登录后进入“喜欢-歌曲”，应展示收藏歌曲列表、分页和搜索；数量 == 接口 total。
- 切到“专辑”，显示按 album 聚合的数据，数量为聚合后的专辑数；列表可滚动。
- 切到“我的关注”，显示后端返回的歌手名列表，数量来自 total；点击跳转歌手详情。
- 在歌手详情关注/取关后，“我的关注”数量与列表刷新；返回“喜欢”页看到最新数量。
- 在曲库取消喜欢后，再进“喜欢-歌曲”，列表与数量刷新且无重复。

五、易错点
- MyBatis SQL 里 `u.user_id` 条件后记得加 AND 再接 `s.id IN (...)`，否则语法错误。
- 收藏歌曲去重请用 `SELECT DISTINCT`，同时确保按用户过滤。
- Authorization 必须带 `Bearer` 前缀；403 时应清理本地登录状态。

![image-20250915160924801](C:\Users\wanglei\AppData\Roaming\Typora\typora-user-images\image-20250915160924801.png)