## TODO 11专辑模块

### 第一批：数据设计与建表、后端基础接口与实体、专辑查询/分页与联动、歌曲与专辑的关联落库（album_id 自动维护）

一、数据设计与建表
1) 新表 tb_album
- 字段建议
  - id BIGINT AUTO_INCREMENT 主键
  - artist_id BIGINT 所属歌手ID
  - title VARCHAR(255) 专辑名
  - cover_url VARCHAR(512) 封面
  - release_date DATE 发行日期
  - category VARCHAR(50) 分类/类型
  - introduction TEXT 简介
  - create_time DATETIME
  - update_time DATETIME
- 关键 SQL（示例）
```sql
CREATE TABLE tb_album (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  artist_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  cover_url VARCHAR(512),
  release_date DATE,
  category VARCHAR(50),
  introduction TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX idx_album_artist ON tb_album(artist_id);
```

2) 歌曲表 tb_song 增加 album_id
- 目的：让歌曲与专辑建立强关联，后续前端可可靠跳转。
```sql
ALTER TABLE tb_song ADD COLUMN album_id BIGINT NULL AFTER artist_id;
CREATE INDEX idx_song_album ON tb_song(album_id);
```

二、后端基础：实体/VO/Mapper/Service/Controller
1) 实体与 VO
- Album 实体（关键字段）
```java
@TableName("tb_album")
public class Album implements Serializable {
  @TableId(value = "id", type = IdType.AUTO)
  private Long albumId;
  @TableField("artist_id")
  private Long artistId;
  @TableField("title")
  private String title;
  @TableField("cover_url")
  private String coverUrl;
  @TableField("release_date")
  private LocalDate releaseDate;
  @TableField("category")
  private String category;
  @TableField("introduction")
  private String introduction;
  // create_time / update_time ...
}
```
- AlbumVO 用于返回给前端
```java
public class AlbumVO implements Serializable {
  private Long albumId;
  private Long artistId;
  private String title;
  private String coverUrl;
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate releaseDate;
  private String category;
  private String introduction;
  // 可扩展 artistName
}
```
- SongVO 增加关联字段（关键）
```java
public class SongVO implements Serializable {
  private Long songId;
  private String songName;
  private String artistName;
  private Long artistId;   // 新增
  private String album;
  private Long albumId;    // 新增
  private String duration;
  private String coverUrl;
  private String audioUrl;
  @JsonFormat(pattern = "yyyy-MM-dd")
  private LocalDate releaseTime;
}
```

2) AlbumMapper
- 直接继承 BaseMapper<Album> 即可。

3) Service 与实现
- IAlbumService：分页查询、详情、后台 CRUD、按歌手取专辑标题列表等
```java
Result<PageResult<AlbumVO>> getAlbumsByArtist(Long artistId, Integer pageNum, Integer pageSize);
Result<AlbumVO> getAlbumDetail(Long albumId);
Result<PageResult<AlbumVO>> getAllAlbums(AlbumDTO albumDTO);
Result addAlbum(AlbumAddDTO albumAddDTO);
Result updateAlbum(AlbumUpdateDTO albumUpdateDTO);
Result deleteAlbum(Long albumId);
Result deleteAlbums(List<Long> albumIds);
// 管理端表单用：按歌手取专辑标题列表
Result<List<String>> getAlbumTitlesByArtist(Long artistId);
```
- AlbumServiceImpl 关键点
  - 分页/详情：BeanUtils.copyProperties 到 AlbumVO
  - 新增/更新时将字符串 releaseDate 转为 LocalDate
  - 删除专辑时，置空该专辑下所有歌曲的 album_id 与 album 文本，避免前端残留旧专辑名
```java
UpdateWrapper<Song> uw = new UpdateWrapper<>();
uw.eq("album_id", albumId).set("album_id", null).set("album", "");
songMapper.update(null, uw);
albumMapper.deleteById(albumId);
```
  - 管理端按歌手取专辑名列表：直接查 tb_album 去重返回

4) Controller（AdminController）
- 管理端专辑 API
```java
@PostMapping("/getAllAlbums") public Result<PageResult<AlbumVO>> getAllAlbums(@RequestBody AlbumDTO albumDTO)
@PostMapping("/addAlbum") public Result addAlbum(@RequestBody @Valid AlbumAddDTO albumAddDTO, ...)
@PutMapping("/updateAlbum") public Result updateAlbum(@RequestBody @Valid AlbumUpdateDTO albumUpdateDTO, ...)
@PatchMapping("/updateAlbumCover/{id}") public Result updateAlbumCover(@PathVariable Long id, @RequestParam("cover") MultipartFile cover)
@DeleteMapping("/deleteAlbum/{id}") public Result deleteAlbum(@PathVariable Long albumId)
@DeleteMapping("/deleteAlbums") public Result deleteAlbums(@RequestBody List<Long> albumIds)
@GetMapping("/getAlbumsByArtist/{id}") public Result<List<String>> getAlbumsByArtist(@PathVariable Long artistId)
```
- 文件上传：转发到 Minio，拿到 URL 后更新专辑封面

三、专辑查询/分页/联动（SongMapper 补充返回字段）
- 为了前端能点击跳转，需要在所有歌曲列表查询里返回 artistId 与 albumId
```java
@Select("""
  SELECT s.id AS songId, s.name AS songName,
         s.artist_id AS artistId, s.album_id AS albumId,
         s.album, s.duration, s.cover_url AS coverUrl, s.audio_url AS audioUrl,
         s.release_time AS releaseTime, a.name AS artistName
  FROM tb_song s LEFT JOIN tb_artist a ON s.artist_id = a.id
  WHERE (#{songName} IS NULL OR s.name LIKE CONCAT('%', #{songName}, '%'))
    AND (#{artistName} IS NULL OR a.name LIKE CONCAT('%', #{artistName}, '%'))
    AND (#{album} IS NULL OR s.album LIKE CONCAT('%', #{album}, '%'))
""")
IPage<SongVO> getSongsWithArtist(...);

@Select("""
  SELECT s.id AS songId, s.name AS songName,
         s.artist_id AS artistId, s.album_id AS albumId,
         s.album, s.duration, s.cover_url AS coverUrl, s.audio_url AS audioUrl,
         s.release_time AS releaseTime, a.name AS artistName
  FROM tb_song s LEFT JOIN tb_artist a ON s.artist_id = a.id
  WHERE s.album_id = #{albumId}
  ORDER BY s.release_time DESC
""")
IPage<SongVO> getSongsByAlbumId(...);
```
- 这样前端 Table 就能拿到 `row.artistId/row.albumId`，点击跳转时不再丢失上下文。

四、歌曲与专辑的关联：自动维护 album_id
- 新增/更新歌曲时，按 “artistId + albumTitle” 解析专辑ID，如果不存在自动创建一条“最简专辑”，并把 song.album_id 写入。
```java
private Long ensureAlbumId(Long artistId, String albumTitle, LocalDate releaseDate) {
  if (artistId == null || StringUtils.isBlank(albumTitle)) return null;
  Album exist = albumMapper.selectOne(new QueryWrapper<Album>()
      .eq("artist_id", artistId).eq("title", albumTitle).last("LIMIT 1"));
  if (exist != null) return exist.getAlbumId();
  Album album = new Album();
  album.setArtistId(artistId);
  album.setTitle(albumTitle);
  if (releaseDate != null) album.setReleaseDate(releaseDate);
  albumMapper.insert(album);
  return album.getAlbumId();
}
```
- 在 `addSong/updateSong` 中调用：
```java
Long resolvedAlbumId = ensureAlbumId(songAddDTO.getArtistId(), songAddDTO.getAlbum(), songAddDTO.getReleaseTime());
song.setAlbumId(resolvedAlbumId);
songMapper.insert(song); // or updateById(song)
```
- 好处：用户在“歌曲管理”里只要填专辑名，就能自动建立专辑并且歌曲挂上专辑；前端专辑详情页即可通过 albumId 查询。

五、前端管理端：专辑管理页
- 路由：`/album/index`
- 列表列：专辑编号、封面、专辑名、歌手、发行时间、类型、操作（修改/删除/上传封面）
- 搜索栏：歌手名字 + 专辑名称（歌手名字用于前端过滤）
- “新增/修改”弹窗：
  - 表单字段：歌手编号（禁用）、专辑名、发行日期、类型、简介
  - 新增时要求先在左侧树选择歌手；修改不强制
- 上传封面：在操作列的“更多”下拉里“上传封面”，弹裁剪框，提交 FormData 到 `/admin/updateAlbumCover/{id}`

关键前端片段（album/index.vue）
```ts
const columns: TableColumns[] = [
  { type: "selection", width: 50, reserveSelection: true },
  { label: "专辑编号", prop: "albumId", width: 90 },
  { label: "封面", prop: "coverUrl", width: 100, slot: "cover", align: "center", headerAlign: "center" },
  { label: "专辑名", prop: "title", minWidth: 200 },
  { label: "歌手", prop: "artistName", width: 160 },
  { label: "发行时间", prop: "releaseDate", width: 120 },
  { label: "类型", prop: "category", width: 120 },
  { label: "操作", fixed: "right", width: 220, slot: "operation", align: "center", headerAlign: "center" }
];

const handleUpload = (row: any) => { /* 调用裁剪上传，表单名 cover */ }
```

六、前端客户端：专辑详情与曲库联动（概述）
- 专辑详情页 `/album/:id`
  - 通过 `getAlbumDetail(id)` 获取专辑元数据
  - `getSongsByAlbumId` 拉取歌曲
  - 顶部“简介”做折叠展示（最多4行），避免大面积空白
  - 为歌曲行提供 `artistId/albumId`，点击歌手/专辑高亮跳转
- 曲库页表格组件 Table.vue
  - 新增 props：`clickableArtist/clickableAlbum` 控制是否可跳转
  - 悬停高亮 + 点击跳转 `/artist/{artistId}` 或 `/album/{albumId}`；若 albumId 为空给出“目前没有该专辑信息”的提示

### 第二批：客户端页面对接、缓存与一致性、交互细节与问题修复记录。

七、客户端页面对接（用户端）
1) 专辑详情页 `/album/:id`
- 关键职责
  - 拉取专辑元信息与专辑内歌曲
  - 收藏/取消收藏专辑
  - 评论模块（可后续扩展）
  - 顶部简介摘要 + “专辑信息”页签详细展示
- 关键代码（精简）
```ts
// src/pages/album/[id].vue
const albumId = computed(() => Number(route.params.id));
const album = ref({ albumId: 0, artistId: 0, title: '', coverUrl: '', releaseDate: '', category: '', introduction: '' });
const songs = ref<Song[]>([]);
const activeTab = ref<'songs'|'info'|'comments'>('songs');

// 获取详情与歌曲
const fetchAlbum = async (): Promise<boolean> => {
  const res = await getAlbumDetail(albumId.value);
  if (res.code === 0 && res.data) { Object.assign(album.value, res.data); return true; }
  return false;
};
const fetchAlbumSongs = async () => {
  const res: any = await getSongsByAlbumId({ albumId: albumId.value, pageNum: 1, pageSize: 200 });
  songs.value = (res?.data?.items || []) as Song[];
};

// 路由监听（仅 album 路由生效；无数据时静默回到 library）
watch(() => route.params.id, async () => {
  if (!route.path.startsWith('/album/')) return;
  const idNum = Number(route.params.id);
  if (!Number.isFinite(idNum) || idNum <= 0) { router.replace('/library'); return; }
  const ok = await fetchAlbum(); if (!ok) { router.replace('/library'); return; }
  await fetchAlbumSongs();
}, { immediate: true });
```
- UI 细节
  - 顶部简介摘要：四行折叠 `line-clamp-4`
  - “专辑信息”页签：使用 `whitespace-pre-line` 展示换行文本
  - 歌曲表使用通用 `Table.vue`（见下一节）

2) 通用歌曲表组件 Table.vue（点击高亮与跳转）
- 目标：悬停高亮；可点击跳转；在特定页面禁用对应点击
- 关键 props 与逻辑
```ts
// props
clickableTitle: boolean = true
clickableArtist: boolean = true
clickableAlbum: boolean = true

// 悬停高亮（样式）
<span :class="props.clickableArtist ? 'hover:text-[hsl(var(--el-color-primary))] hover:underline cursor-pointer' : ''" @click.stop="goArtist(row, $event)">
  {{ row.artistName }}
</span>

// 路由跳转
const goArtist = (row: Song, e: Event) => {
  if (!props.clickableArtist) return; e.stopPropagation();
  const id = (row as any)?.artistId;
  if (id) router.push(`/artist/${id}`); else ElMessage.warning('未找到该歌手的详情信息');
};

const goAlbum = (row: Song, e: Event) => {
  if (!props.clickableAlbum) return; e.stopPropagation();
  const id = (row as any)?.albumId;
  if (id) router.push(`/album/${id}`); else ElMessage.warning('目前没有该专辑信息');
};
```
- 在歌手详情页使用时可传 `:clickableArtist="false"`；在专辑详情页使用时可传 `:clickableAlbum="false"` 避免自指跳转。

八、缓存与一致性
1) 专辑/歌曲缓存策略
- 专辑相关接口使用 `@CacheConfig(cacheNames = "albumCache")`
- 歌曲列表 `SongServiceImpl` 使用 `@CacheConfig(cacheNames = "songCache")`
- 当发生写操作时必须清除相关缓存
  - 新增/修改/删除专辑 → 清空 `albumCache` 并清 `songCache`（防止曲库仍展示旧专辑名）
  - 新增/修改歌曲 → 清空 `songCache` 与适当的 `albumCache`（我们采用 allEntries = true 简化）

示例：
```java
@CacheEvict(cacheNames = "albumCache", allEntries = true) // 新增/更新
public Result updateAlbum(AlbumUpdateDTO dto) {...}

@CacheEvict(cacheNames = {"albumCache", "songCache"}, allEntries = true)
public Result deleteAlbum(Long albumId) {
  // 置空歌曲 album_id/album 文本，防止前端残留
  UpdateWrapper<Song> uw = new UpdateWrapper<>();
  uw.eq("album_id", albumId).set("album_id", null).set("album", "");
  songMapper.update(null, uw);
  albumMapper.deleteById(albumId);
  return Result.success("删除成功");
}
```

2) 删除专辑后的“残留专辑名”问题
- 根因：很多历史数据仅有 `tb_song.album` 文本，`album_id` 为空
- 修复：删除专辑时批量 UPDATE 置空该专辑下所有歌曲的 `album_id` 与 `album` 文本，避免曲库继续展示旧值；同时清 `songCache` 立即可见。

九、问题修复记录（关键节点）
1) 曲库“歌手/专辑列为无”的问题
- 根因：查询 SELECT 未返回 `artist_id/album_id` 字段，前端无法跳转且有提示
- 修复：在 `SongMapper` 所有列表查询里统一返回：
```sql
s.artist_id AS artistId, s.album_id AS albumId, a.name AS artistName
```

2) 专辑详情页“无效ID弹窗”范围过大
- 修复：仅在 `/album/:id` 路由下做校验；并最终移除弹窗，采用静默回到 `/library`

3) 歌曲新增/修改后未写入 album_id
- 修复：在 `addSong/updateSong` 统一调用 `ensureAlbumId`，若专辑不存在则自动创建最简专辑再把 `song.album_id` 落库

十、管理端 UI 交互与表单
1) 专辑管理列表
- 列：专辑编号、封面、专辑名、歌手、发行时间、类型、操作（修改/删除/上传封面）
- 专辑名列右移对齐封面，操作列/封面列居中
- 删除操作使用 `el-popconfirm` 明确告知会清空其下歌曲专辑字段

2) 新增/修改弹窗
- 新增前需选择歌手；修改可不选
- 表单字段：专辑名/发行日期/类型/简介；支持上传封面（裁剪后提交 `FormData cover`）

3) 歌曲管理表单联动专辑
- 选择左侧歌手后，请求 `/admin/getAlbumsByArtist/{id}` 获取该歌手已建专辑名作为下拉；也支持 allow-create 手动输入
- 在后端自动关联 album_id，避免出现“专辑名相同但未关联ID”的不一致

十一、接口清单（摘要）
- 前台
  - GET `/album/getAlbumDetail?albumId=xxx`
  - GET `/album/getAlbumsByArtist?artistId=xxx&pageNum=1&pageSize=20`
  - GET `/song/getSongsByAlbumId?albumId=xxx&pageNum=1&pageSize=20`
  - 收藏/评论等接口略
- 后台（Admin）
  - POST `/admin/getAllAlbums`
  - POST `/admin/addAlbum`
  - PUT `/admin/updateAlbum`
  - PATCH `/admin/updateAlbumCover/{id}` (multipart/form-data, field=cover)
  - DELETE `/admin/deleteAlbum/{id}` / `/admin/deleteAlbums`
  - GET `/admin/getAlbumsByArtist/{id}`（返回 List<String> 专辑名，用于歌曲表单）

十二、可复用的最佳实践小结
- 统一在 VO 层补齐前端需要的跳转关键字段（artistId/albumId）
- 写操作后立刻 `@CacheEvict`，并注意跨实体的缓存（删专辑需要清 songCache）
- 数据一致性优先：删除专辑 → 置空歌曲 album_id/album 文本
- 组件化 Table：通过 props 控制“在哪些页面可点击高亮”与“跳转行为”，避免在详情页自指跳转
- 顶部“摘要+信息页签”：摘要折叠展示、信息页签保留换行（`whitespace-pre-line`）共同提升观感

如果你需要，我可以在第三批补充：
- 端到端调用链的时序图（新增歌曲 → 自动建专辑 → 前端联动）
- 重点 SQL 一页表与索引建议
- 管理端/客户端页面结构与样式要点的汇总代码片段（便于直接粘贴到你的笔记）

### 第三批我补充更“工程化”的总结：端到端调用链、重点 SQL 与索引、页面结构速览、常见坑位 Checklist

一、端到端调用链（新增歌曲 → 自动建专辑 → 前端联动）
1) 管理端在“歌曲管理”新增歌曲
- 前端：表单允许选择/输入“专辑名”，左侧选定歌手后会调用 `/admin/getAlbumsByArtist/{artistId}` 回显专辑下拉
- 后端：
  - Service `addSong` → `ensureAlbumId(artistId, albumTitle, releaseDate)`
  - 若 album 不存在：插入 “最简专辑” 到 `tb_album`（artist_id/title/release_date），返回新 id
  - 写入 `tb_song.album_id = albumId`，并保留 `tb_song.album = albumTitle` 文本（兼容显示）
- 影响：
  - 曲库页列表查询 SELECT 出 `artistId/albumId` → 前端 Table 有跳转能力
  - 专辑详情页 `/album/:id` 能按 albumId 拉取到歌曲

2) 删除专辑
- 后端 `AlbumServiceImpl.deleteAlbum`：
  - 先置空 `tb_song` 中属于该专辑的 `album_id` 与 `album` 文本
  - 删除 `tb_album` 记录
  - `@CacheEvict(cacheNames = {"albumCache","songCache"}, allEntries = true)`
- 前端：
  - 曲库页立即刷新后不再显示被删专辑名（避免残留）

3) 客户端跳转
- Table 点击歌手名 → `/artist/{artistId}`
- Table 点击专辑名 → `/album/{albumId}`，无 albumId 时提示“目前没有该专辑信息”

二、重点 SQL 与索引建议
1) 专辑表
```sql
CREATE TABLE tb_album (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  artist_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  cover_url VARCHAR(512),
  release_date DATE,
  category VARCHAR(50),
  introduction TEXT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_album_artist(artist_id),
  INDEX idx_album_title_artist(title, artist_id) -- 可选：确保 title+artist_id 查找高效
);
```
2) 歌曲表专辑字段
```sql
ALTER TABLE tb_song ADD COLUMN album_id BIGINT NULL AFTER artist_id;
CREATE INDEX idx_song_album ON tb_song(album_id);
```
3) 歌曲列表常用查询（务必 SELECT 出关键字段）
```sql
SELECT s.id AS songId, s.name AS songName,
       s.artist_id AS artistId, s.album_id AS albumId,
       s.album, s.duration, s.cover_url AS coverUrl, s.audio_url AS AudioUrl,
       s.release_time AS releaseTime, a.name AS artistName
FROM tb_song s LEFT JOIN tb_artist a ON s.artist_id = a.id
-- WHERE ... ORDER BY ...
```

三、页面结构速览（可复制到笔记）
1) 管理端 - 专辑管理
- 左：歌手树；右：列表与工具栏
- 列表列：专辑编号 | 封面 | 专辑名 | 歌手 | 发行时间 | 类型 | 操作
- 操作：修改 | 删除（确认） | 更多 → 上传封面（裁剪后 FormData: cover）
- 弹窗：新增/修改专辑表单（专辑名、发行日期、类型、简介）

2) 管理端 - 歌曲管理与专辑联动
- 选定歌手后，专辑下拉通过 `/admin/getAlbumsByArtist/{id}` 回显；允许 allow-create 手输
- 提交后 Service 端自动建专辑并写 song.album_id

3) 客户端 - 专辑详情页
- 左侧：封面；右侧：标题、发行时间、类型、（摘要简介 4 行折叠）
- Tabs：歌曲 | 专辑信息 | 评论
- 专辑信息：`whitespace-pre-line` 还原换行
- 歌曲列表：使用通用 Table，点击歌手/专辑名跳转

四、常见坑位 Checklist（带对策）
- [x] “曲库页歌手/专辑列为空/无法跳转”
  - 统一在 `SongMapper` 查询里 SELECT `artistId/albumId/artistName`
- [x] “删除专辑后曲库仍显示专辑名”
  - `deleteAlbum` 前置空 `tb_song.album_id/album`；并清 `songCache`
- [x] “新增/修改歌曲后专辑未关联”
  - `addSong/updateSong` 必须先 `ensureAlbumId` → 将 album_id 写入
- [x] “专辑详情路由误弹提示”
  - 仅在 `/album/:id` 下做校验；最终移除弹窗，改为静默跳回 `/library`
- [x] “信息页签换行不生效”
  - 详情说明使用 `whitespace-pre-line`

五、核心方法清单（摘录模板）
1) ensureAlbumId（放 SongServiceImpl）
```java
private Long ensureAlbumId(Long artistId, String albumTitle, LocalDate releaseDate) {
  if (artistId == null || StringUtils.isBlank(albumTitle)) return null;
  Album exist = albumMapper.selectOne(new QueryWrapper<Album>()
      .eq("artist_id", artistId).eq("title", albumTitle).last("LIMIT 1"));
  if (exist != null) return exist.getAlbumId();
  Album album = new Album();
  album.setArtistId(artistId);
  album.setTitle(albumTitle);
  if (releaseDate != null) album.setReleaseDate(releaseDate);
  albumMapper.insert(album);
  return album.getAlbumId();
}
```
2) deleteAlbum（放 AlbumServiceImpl）
```java
@CacheEvict(cacheNames = {"albumCache","songCache"}, allEntries = true)
public Result deleteAlbum(Long albumId) {
  UpdateWrapper<Song> uw = new UpdateWrapper<>();
  uw.eq("album_id", albumId).set("album_id", null).set("album", "");
  songMapper.update(null, uw);
  if (albumMapper.deleteById(albumId) == 0) return Result.error("删除失败");
  return Result.success("删除成功");
}
```
3) SongMapper 核心 SELECT（保留关键字段）
```java
SELECT s.id AS songId, s.name AS songName,
       s.artist_id AS artistId, s.album_id AS albumId,
       s.album, s.duration, s.cover_url AS coverUrl, s.audio_url AS audioUrl,
       s.release_time AS releaseTime, a.name AS artistName
FROM tb_song s LEFT JOIN tb_artist a ON s.artist_id = a.id
```

六、后续可优化点（Roadmap）
- 专辑唯一约束：同歌手下（artist_id + title）唯一，避免误建重名
- 后台列表分页/搜索下推：将“歌手名字过滤”改为后端 where 子句
- 专辑评论改造：表结构独立 `album_comment`（当前如复用 playlist 字段可逐步迁移）
- 搜索推荐：按用户喜欢的专辑风格生成“推荐专辑”

### 第四批：调试与自检清单、接口用例、关键页面速抄片段，帮助你快速复盘与二次扩展。

一、端到端调试与自检清单
- 数据准备
  - 新建 1 个歌手（artist），记下 id
  - 新建 1 张专辑（album），填发行日期/类型/简介
  - 新建 2 首歌曲，分别填写相同“专辑名”，确认后端自动把 album_id 写入
- 功能验证
  - 管理端“专辑管理”：分页、搜索、上传封面、修改、删除（确认歌曲 album_id/album 被置空）
  - 管理端“歌曲管理”：选择歌手后，专辑下拉正确回显；新增后数据库 `tb_song.album_id` 非空
  - 客户端“曲库”：歌手/专辑列展示正确，悬停高亮，点击能跳转；无 albumId 时提示“目前没有该专辑信息”
  - 客户端“专辑详情页”：顶部简介折叠，信息页签换行；歌曲列表能跳转至歌手详情
- 缓存与一致性
  - 新增/修改/删除专辑 → 刷新前台曲库后立即生效（验证 @CacheEvict）
  - 删除专辑后，曲库是否还残留旧专辑名？若有，检查置空与缓存清理

二、接口用例（可直接贴到 Postman/HTTPie）
1) 专辑管理分页（后台）
```http
POST /admin/getAllAlbums
Content-Type: application/json
Authorization: Bearer <token>

{ "pageNum": 1, "pageSize": 10, "artistId": 142, "title": "" }
```
2) 新增专辑
```http
POST /admin/addAlbum
Content-Type: application/json
Authorization: Bearer <token>

{ "artistId": 142, "title": "测试专辑", "releaseDate": "2025-09-09", "category": "流行", "introduction": "简介..." }
```
3) 更新专辑封面
```http
PATCH /admin/updateAlbumCover/5
Authorization: Bearer <token>
Content-Type: multipart/form-data

cover: <binary-image>
```
4) 删除专辑
```http
DELETE /admin/deleteAlbum/5
Authorization: Bearer <token>
```
5) 按歌手获取已建专辑名（供歌曲表单回显）
```http
GET /admin/getAlbumsByArtist/142
Authorization: Bearer <token>
```
6) 专辑详情（前台）
```http
GET /album/getAlbumDetail?albumId=5
```
7) 专辑内歌曲
```http
GET /song/getSongsByAlbumId?albumId=5&pageNum=1&pageSize=50
```

三、关键页面速抄片段
1) Table 点击跳转（已集成）
```vue
<span :class="clickableArtist ? 'hover:text-[hsl(var(--el-color-primary))] hover:underline cursor-pointer' : ''"
      @click.stop="goArtist(row, $event)">{{ row.artistName }}</span>
<span :class="clickableAlbum ? 'hover:text-[hsl(var(--el-color-primary))] hover:underline cursor-pointer' : ''"
      @click.stop="goAlbum(row, $event)">{{ row.album || '未命名专辑' }}</span>
```

2) 专辑详情页路由守卫（静默回退，无弹窗）
```ts
watch(() => route.params.id, async () => {
  if (!route.path.startsWith('/album/')) return;
  const idNum = Number(route.params.id);
  if (!Number.isFinite(idNum) || idNum <= 0) { router.replace('/library'); return; }
  const ok = await fetchAlbum(); if (!ok) { router.replace('/library'); return; }
  await fetchAlbumSongs();
}, { immediate: true });
```

3) 删除专辑统一置空歌曲关联
```java
UpdateWrapper<Song> uw = new UpdateWrapper<>();
uw.eq("album_id", albumId).set("album_id", null).set("album", "");
songMapper.update(null, uw);
albumMapper.deleteById(albumId);
```

四、常见变更点一览（升级或迁移时复查）
- VO/Mapper 返回字段是否包含 artistId/albumId/artistName
- Service 写操作是否清理 `albumCache` / `songCache`
- 前端是否在“详情页自身”禁用自指跳转（clickableArtist / clickableAlbum）
- 专辑删除是否影响“喜欢/收藏”计数（如后续扩展统计需要）

五、性能与稳定性建议
- 为 `tb_album (artist_id, title)` 建唯一索引，避免专辑重名（同一歌手）
- 常用查询增加覆盖索引（如 `idx_song_album`，`idx_album_title_artist`）
- 上传封面走对象存储（MinIO/S3），服务端返回直链（或签名 URL）
- 批量删除专辑时，使用一次 `UPDATE tb_song SET album_id=NULL, album='' WHERE album_id in (...)`（若更新量大可分批）

六、可选增强（下一步 Roadmap）
- 专辑收藏/喜欢统计与榜单
- 专辑评论表独立（album_comment），支持楼中楼与点赞
- 前台“专辑页”推荐与相似专辑（按歌手/风格/年份）
- 管理端“歌手-专辑”联动批量导入（CSV/Excel）

