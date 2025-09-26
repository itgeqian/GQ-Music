## todo22删除歌手的级联清理与脏数据防护（开发说明）

本说明记录删除歌手时的“全链路清理”设计与关键实现，确保不会残留脏数据与无用对象存储文件。



一、目标与范围
- 删除一个歌手后，必须同时删除：
  - MinIO：歌手头像、所有专辑封面、所有歌曲音频/封面、歌词
  - DB 记录：
    - 歌手、其所有专辑、其所有歌曲
    - 歌曲评论(type=0)、专辑评论(type=2，复用 `playlist_id` 存放 albumId)
    - 用户收藏（歌曲 type=0、歌手 type=2、专辑 type=3）
    - 歌单绑定 `tb_playlist_binding` 中这些歌曲的绑定
    - 风格映射 `tb_genre` 中这些歌曲的行
    - 轮播图 `tb_banner` 中与这些专辑相关的图片与记录
- 删除完成后执行一次“脏数据兜底清理”，移除因历史/并发导致的孤儿引用。

——

二、关键代码与位置
1) 级联删除入口
文件：`src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java`
方法：`deleteArtist(Long artistId)`、`deleteArtists(List<Long> artistIds)`
实现：调用 `cascadeDeleteArtist(artistId)`

```351:356:src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java
@Override
@CacheEvict(cacheNames = "artistCache", allEntries = true)
public Result deleteArtist(Long artistId) {
    cascadeDeleteArtist(artistId);
    return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
}
```

2) 级联删除核心
文件：`ArtistServiceImpl`
方法：`private void cascadeDeleteArtist(Long artistId)`

职责：
- 删歌曲及其 MinIO 文件、关联映射与收藏/评论/歌单绑定
- 删专辑及其封面 MinIO、专辑评论/收藏、轮播及其图片
- 删关注歌手收藏
- 删歌手头像与记录
- 最后调用 `cleanupOrphans()` 兜底

```374:428:src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java
private void cascadeDeleteArtist(Long artistId) {
    if (artistId == null) return;
    Artist artist = artistMapper.selectById(artistId);
    if (artist == null) return;

    // 1) 歌曲 -> MinIO(cover/audio/lyric) + tb_song
    List<Song> songs = songMapper.selectList(new QueryWrapper<Song>().eq("artist_id", artistId));
    List<Long> songIds = songs.stream().map(Song::getSongId).toList();
    for (Song s : songs) {
        if (s.getCoverUrl() != null && !s.getCoverUrl().isEmpty()) minioService.deleteFile(s.getCoverUrl());
        if (s.getAudioUrl() != null && !s.getAudioUrl().isEmpty()) minioService.deleteFile(s.getAudioUrl());
        if (s.getLyricUrl() != null && !s.getLyricUrl().isEmpty()) minioService.deleteFile(s.getLyricUrl());
    }
    if (!songIds.isEmpty()) {
        genreMapper.delete(new QueryWrapper<Genre>().in("song_id", songIds));
        playlistBindingMapper.delete(new QueryWrapper<PlaylistBinding>().in("song_id", songIds));
        commentMapper.delete(new QueryWrapper<Comment>().in("song_id", songIds).eq("type", 0));
        userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 0).in("song_id", songIds));
        songMapper.delete(new QueryWrapper<Song>().in("id", songIds));
    }

    // 2) 专辑 -> MinIO(cover) + tb_album；清 banner/comment/favorite
    List<Album> albums = albumMapper.selectList(new QueryWrapper<Album>().eq("artist_id", artistId));
    List<Long> albumIds = albums.stream().map(Album::getAlbumId).toList();
    for (Album a : albums) {
        if (a.getCoverUrl() != null && !a.getCoverUrl().isEmpty()) minioService.deleteFile(a.getCoverUrl());
    }
    if (!albumIds.isEmpty()) {
        commentMapper.delete(new QueryWrapper<Comment>().in("playlist_id", albumIds).eq("type", 2)); // 专辑评论
        userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 3).in("album_id", albumIds));
        List<Banner> banners = bannerMapper.selectList(new QueryWrapper<Banner>().in("album_id", albumIds));
        for (Banner b : banners) {
            if (b.getBannerUrl() != null && !b.getBannerUrl().isEmpty()) minioService.deleteFile(b.getBannerUrl());
        }
        if (!banners.isEmpty()) {
            bannerMapper.delete(new QueryWrapper<Banner>().in("album_id", albumIds));
        }
        albumMapper.delete(new QueryWrapper<Album>().in("id", albumIds));
    }

    // 3) 关注歌手
    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 2).eq("artist_id", artistId));

    // 4) 歌手头像与记录
    if (artist.getAvatar() != null && !artist.getAvatar().isEmpty()) minioService.deleteFile(artist.getAvatar());
    artistMapper.deleteById(artistId);

    // 5) 兜底清理
    cleanupOrphans();
}
```

3) 脏数据兜底清理
文件：`ArtistServiceImpl`
方法：`private void cleanupOrphans()`

职责：按子查询判断孤儿行并删除
- `tb_playlist_binding`：引用不存在歌曲的绑定
- `tb_user_favorite`：指向不存在的歌曲(type=0)/专辑(type=3)/歌手(type=2)
- `tb_genre`：引用不存在歌曲
- `tb_comment`：歌曲评论(type=0)引用不存在歌曲、专辑评论(type=2)引用不存在专辑

```430:454:src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java
private void cleanupOrphans() {
    playlistBindingMapper.delete(new QueryWrapper<PlaylistBinding>()
        .apply("song_id is not null and song_id not in (select id from tb_song)"));

    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>()
        .eq("type", 0).apply("song_id is not null and song_id not in (select id from tb_song)"));
    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>()
        .eq("type", 3).apply("album_id is not null and album_id not in (select id from tb_album)"));
    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>()
        .eq("type", 2).apply("artist_id is not null and artist_id not in (select id from tb_artist)"));

    genreMapper.delete(new QueryWrapper<Genre>()
        .apply("song_id is not null and song_id not in (select id from tb_song)"));

    commentMapper.delete(new QueryWrapper<Comment>()
        .eq("type", 0).apply("song_id is not null and song_id not in (select id from tb_song)"));
    commentMapper.delete(new QueryWrapper<Comment>()
        .eq("type", 2).apply("playlist_id is not null and playlist_id not in (select id from tb_album)"));
}
```

4) 定时巡检清理
- 应用已启用 `@EnableScheduling`（`VibeMusicServerApplication`）
- 每天 02:30 自动执行清理，避免长时间积累脏数据

```456:463:src/main/java/cn/edu/seig/vibemusic/service/impl/ArtistServiceImpl.java
@Scheduled(cron = "0 30 2 * * ?")
public void scheduledCleanupOrphans() {
    try { cleanupOrphans(); } catch (Exception ignored) {}
}
```

——

三、设计要点与注意
- 清理顺序：先文件后表；歌曲优先于专辑；再清收藏/评论/绑定映射，最后清歌手。
- 专辑评论字段复用：现存实现用 `type=2 + playlist_id=albumId` 表示专辑评论。若未来增加独立 `album_id` 列，只需在上述两处查询条件同步调整。
- 幂等：重复执行无副作用，子查询能保证仅删除“引用已不存在实体”的行。
- 性能：大表删除采用单 SQL 条件批量删除，避免逐行循环；如数据量巨大，可改为分批 LIMIT 方式。
- 手动触发：如需增加管理端手动触发接口，可添加 `POST /admin/maintenance/cleanup` 直接调用 `cleanupOrphans()`。

——

四、快速验证清单
- 构造：歌手A 有专辑/歌曲；用户关注/收藏；歌单绑定歌曲；有歌曲/专辑评论；有轮播图关联专辑
- 删除歌手A 后核对：
  - MinIO：头像、专辑封面、歌曲音频/封面/歌词全部删除
  - DB：`tb_song`、`tb_album` 清空该歌手数据
  - `tb_comment` type=0/2 无该数据
  - `tb_user_favorite` 中 type=0/2/3 无孤儿行
  - `tb_playlist_binding` 无这些歌曲的绑定
  - `tb_genre` 无这些歌曲的映射
  - `tb_banner` 删除关联记录与图片

