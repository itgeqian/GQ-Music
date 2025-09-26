### 顶栏统一关键字搜索优化开发文档

本次目标
- 顶栏搜索支持“歌手/歌名/专辑”任一匹配，而不是只能按歌名匹配。

后端改造
- 新增统一关键字 `keyword`，当传入 `keyword` 时，以 OR 组合对 歌名/歌手/专辑 进行模糊匹配；若未传 `keyword`，仍兼容旧的按字段组合查询。

关键代码

```java
// 1) DTO：新增统一关键字
// src/main/java/cn/edu/seig/vibemusic/model/dto/SongDTO.java
private String keyword; // 统一关键字（歌名/歌手/专辑）
```

```java
// 2) Mapper：新增按 keyword 搜索（歌名/歌手/专辑 任一匹配）
// src/main/java/cn/edu/seig/vibemusic/mapper/SongMapper.java
@Select("""
        SELECT 
            s.id AS songId, s.name AS songName, s.album, s.duration,
            s.cover_url AS coverUrl, s.audio_url AS audioUrl, 
            s.release_time AS releaseTime, a.name AS artistName
        FROM tb_song s
        LEFT JOIN tb_artist a ON s.artist_id = a.id
        WHERE 
            (#{keyword} IS NULL OR #{keyword} = '' 
             OR s.name  LIKE CONCAT('%', #{keyword}, '%')
             OR a.name  LIKE CONCAT('%', #{keyword}, '%')
             OR s.album LIKE CONCAT('%', #{keyword}, '%'))
        """)
IPage<SongVO> getSongsByKeyword(Page<SongVO> page, @Param("keyword") String keyword);
```

```java
// 3) Service：优先使用 keyword，否则回退到原有 songName/artistName/album
// src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java（片段）
Page<SongVO> page = new Page<>(songDTO.getPageNum(), songDTO.getPageSize());
IPage<SongVO> songPage;
if (songDTO.getKeyword() != null && !songDTO.getKeyword().isEmpty()) {
    songPage = songMapper.getSongsByKeyword(page, songDTO.getKeyword());
} else {
    songPage = songMapper.getSongsWithArtist(page, songDTO.getSongName(), songDTO.getArtistName(), songDTO.getAlbum());
}
```

前端改造
- 顶栏回车后仍跳转 `library?query=xxx`；曲库页改为把 `route.query.query` 映射到后端的 `keyword` 参数提交，从而实现统一关键字搜索。

关键代码

```vue
<!-- src/pages/library/index.vue（片段） -->
getAllSongs({
  pageNum: currentPage.value,
  pageSize: pageSize.value,
  keyword: (route.query.query as string) || '', // 映射到后端 keyword
  songName: '',
  artistName: '',
  album: '',
}).then(...)
```

接口行为说明
- 若 `keyword` 非空：按 OR 匹配 歌名(s.name)/歌手(a.name)/专辑(s.album)。
- 若 `keyword` 为空：沿用旧的 `songName/artistName/album` 组合匹配。

测试要点
- 顶栏输入歌手名（例：林俊杰）→ 曲库页可返回其歌曲。
- 顶栏输入歌名或专辑名同样可返回相关歌曲。
- 空关键字或清空搜索时，可返回默认列表或无数据提示（依现有逻辑）。

注意
- 已兼容旧参数调用方式，其他页面无需修改。
- 缓存 Key 已包含 `keyword`，避免不同关键字缓存互相污染。

至此，“顶栏统一关键字搜索（歌手/歌名/专辑）”优化完成并验证通过。