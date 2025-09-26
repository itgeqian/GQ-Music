## todo23删除专辑与删除歌曲的级联实现说明。

一、目标与边界
- 删除专辑 deleteAlbum：不删除歌曲，只解除专辑与歌曲的关联，同时清理与专辑相关的评论/收藏/轮播和专辑封面，并更新风格映射表中的专辑维度字段。
- 删除歌曲 deleteSong / deleteSongs：删除歌曲本身，以及与歌曲相关的 MinIO 文件和所有关联数据（评论、收藏、歌单绑定、风格映射）。

二、数据对象与关联
- 专辑：`tb_album(id, artist_id, title, cover_url, release_date, category, …)`
- 歌曲：`tb_song(id, artist_id, album_id, album, cover_url, audio_url, lyric_url, …)`
- 风格映射：`tb_genre(song_id, album_id, style_id, album_style_id)` 歌曲风格与专辑风格分维度存储
- 评论：`tb_comment(type, song_id, playlist_id, album_id, …)` 专辑评论使用 `type=2 + album_id`
- 收藏：`tb_user_favorite(type, song_id, album_id, artist_id, …)` 歌曲(type=0)、歌手(type=2)、专辑(type=3)
- 歌单-歌曲绑定：`tb_playlist_binding(playlist_id, song_id)`
- 轮播：`tb_banner(id, banner_url, album_id)`
- 对象存储：MinIO（歌曲封面、音频、歌词；专辑封面；轮播图片）

三、删除专辑链路（不删歌曲）
入口
- `AlbumServiceImpl.deleteAlbum(Long albumId)`

处理步骤
1) 删除 MinIO 专辑封面
2) 清理关联
   - 专辑评论：`DELETE FROM tb_comment WHERE type=2 AND album_id = :albumId`
   - 专辑收藏：`DELETE FROM tb_user_favorite WHERE type=3 AND album_id = :albumId`
   - 轮播：删除 `tb_banner` 记录，并删除其 `banner_url` 图片
3) 解除歌曲与专辑关联
   - `UPDATE tb_song SET album_id = NULL, album = '' WHERE album_id = :albumId`
4) 清理风格映射中的专辑维度字段
   - `UPDATE tb_genre SET album_id = NULL, album_style_id = NULL WHERE album_id = :albumId`
5) 删除专辑记录
6) 兜底清理（防并发/历史）
   - 删除 `tb_comment`、`tb_user_favorite` 中 album_id 指向不存在专辑的孤儿行
7) 缓存清理
   - `@CacheEvict(cacheNames={"albumCache","songCache"}, allEntries=true)` 清 SpringCache
   - `CachePurger.purgeForAlbum` 清自定义 Redis Key（例如推荐列表）

关键代码
```198:251:src/main/java/cn/edu/seig/vibemusic/service/impl/AlbumServiceImpl.java
@Override
@CacheEvict(cacheNames = {"albumCache", "songCache"}, allEntries = true)
public Result deleteAlbum(Long albumId) {
    Album album = albumMapper.selectById(albumId);
    if (album == null) return Result.success("删除成功");

    // 1) 删除 MinIO 专辑封面
    try {
        String cover = album.getCoverUrl();
        if (cover != null && !cover.isEmpty()) minioService.deleteFile(cover);
    } catch (Exception ignored) {}

    // 2) 清理关联：专辑评论/收藏/轮播及图片
    try {
        commentMapper.delete(new QueryWrapper<Comment>().eq("type", 2).eq("album_id", albumId));
        userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 3).eq("album_id", albumId));
        List<Banner> banners = bannerMapper.selectList(new QueryWrapper<Banner>().eq("album_id", albumId));
        for (Banner b : banners) {
            String url = b.getBannerUrl();
            if (url != null && !url.isEmpty()) minioService.deleteFile(url);
        }
        if (!banners.isEmpty()) {
            bannerMapper.delete(new QueryWrapper<Banner>().eq("album_id", albumId));
        }
    } catch (Exception ignored) {}

    // 3) 置空该专辑下歌曲的 album_id 与 album 文本
    UpdateWrapper<Song> uw = new UpdateWrapper<>();
    uw.eq("album_id", albumId).set("album_id", null).set("album", "");
    songMapper.update(null, uw);

    // 3.1) 清 tb_genre 中专辑维度（不影响歌曲风格）
    UpdateWrapper<Genre> ug = new UpdateWrapper<>();
    ug.eq("album_id", albumId).set("album_id", null).set("album_style_id", null);
    genreMapper.update(null, ug);

    // 4) 删除专辑
    if (albumMapper.deleteById(albumId) == 0) return Result.error("删除失败");

    // 5) 兜底清理
    try {
        commentMapper.delete(new QueryWrapper<Comment>().eq("type", 2)
                .apply("album_id not in (select id from tb_album)"));
        userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 3)
                .apply("album_id not in (select id from tb_album)"));
    } catch (Exception ignored) {}

    // 6) 自定义 Redis Key 清理
    try { cachePurger.purgeForAlbum(albumId); } catch (Exception ignored) {}

    return Result.success("删除成功");
}
```

四、删除歌曲链路
入口
- `SongServiceImpl.deleteSong(Long songId)`
- `SongServiceImpl.deleteSongs(List<Long> songIds)`

处理步骤
1) 删除 MinIO 文件
   - 封面 `cover_url`、音频 `audio_url`、歌词 `lyric_url`
2) 删除关联
   - `tb_genre` 风格映射
   - `tb_playlist_binding` 歌单绑定
   - `tb_comment` 歌曲评论（type=0）
   - `tb_user_favorite` 歌曲收藏（type=0）
3) 删除歌曲记录
4) 缓存清理
   - `@CacheEvict(cacheNames={"songCache","artistCache"}, allEntries=true)` 清 SpringCache
   - `CachePurger.purgeForSong` 清自定义 Redis Key

关键代码（单条）
```651:684:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
public Result<String> deleteSong(Long songId) {
    Song song = songMapper.selectById(songId);
    if (song == null) return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);
    String cover = song.getCoverUrl(), audio = song.getAudioUrl(), lyric = song.getLyricUrl();

    if (cover != null && !cover.isEmpty()) minioService.deleteFile(cover);
    if (audio != null && !audio.isEmpty()) minioService.deleteFile(audio);
    if (lyric != null && !lyric.isEmpty()) minioService.deleteFile(lyric);

    // 关联清理
    genreMapper.delete(new QueryWrapper<Genre>().eq("song_id", songId));
    playlistBindingMapper.delete(new QueryWrapper<PlaylistBinding>().eq("song_id", songId));
    commentMapper.delete(new QueryWrapper<Comment>().eq("song_id", songId).eq("type", 0));
    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 0).eq("song_id", songId));

    if (songMapper.deleteById(songId) == 0) return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);

    try { cachePurger.purgeForSong(songId); } catch (Exception ignored) {}
    return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
}
```

关键代码（批量）
```692:734:src/main/java/cn/edu/seig/vibemusic/service/impl/SongServiceImpl.java
@Override
@CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
public Result<String> deleteSongs(List<Long> songIds) {
    List<Song> songs = songMapper.selectByIds(songIds);
    List<String> coverUrlList = ...
    List<String> audioUrlList = ...
    List<String> lyricUrlList = ...
    // MinIO 批量删除（遍历调用）
    ...
    // 关联清理（in）
    genreMapper.delete(new QueryWrapper<Genre>().in("song_id", songIds));
    playlistBindingMapper.delete(new QueryWrapper<PlaylistBinding>().in("song_id", songIds));
    commentMapper.delete(new QueryWrapper<Comment>().in("song_id", songIds).eq("type", 0));
    userFavoriteMapper.delete(new QueryWrapper<UserFavorite>().eq("type", 0).in("song_id", songIds));

    if (songMapper.deleteByIds(songIds) == 0) return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);

    try { cachePurger.purgeForSong(null); } catch (Exception ignored) {}
    return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
}
```

五、缓存清理策略
- 使用 `@CacheEvict(allEntries=true)` 清除 Spring Cache 命名空间：`albumCache / songCache / artistCache` 等
- 使用应用级清理器 `CachePurger` 清除非 Spring Cache 的自定义 Redis Key（当前包含推荐列表 `recommended_songs:*`），防止页面取到过期列表

六、注意事项与约束
- 专辑删除仅解除关联，不会删除歌曲。若要强制删除专辑下歌曲，可改造为先取 `album_id` 的歌曲列表，再复用删除歌曲链路。
- 专辑评论已改用 `album_id`，并在保存时强制 `playlist_id = null`，避免外键冲突。
- 删除链路均添加兜底 SQL，处理并发/历史留下的孤儿行，保证最终一致性。

