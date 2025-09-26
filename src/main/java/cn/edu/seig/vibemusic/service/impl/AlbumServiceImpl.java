package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.mapper.AlbumMapper;
import cn.edu.seig.vibemusic.mapper.GenreMapper;
import cn.edu.seig.vibemusic.mapper.SongMapper;
import cn.edu.seig.vibemusic.mapper.StyleMapper;
import cn.edu.seig.vibemusic.mapper.BannerMapper;
import cn.edu.seig.vibemusic.mapper.UserFavoriteMapper;
import cn.edu.seig.vibemusic.mapper.CommentMapper;
import cn.edu.seig.vibemusic.model.entity.Album;
import cn.edu.seig.vibemusic.model.entity.Genre;
import cn.edu.seig.vibemusic.model.entity.Song;
import cn.edu.seig.vibemusic.model.entity.Banner;
import cn.edu.seig.vibemusic.model.entity.Style;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IAlbumService;
import cn.edu.seig.vibemusic.service.MinioService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import cn.edu.seig.vibemusic.model.dto.AlbumDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumAddDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumUpdateDTO;
import cn.edu.seig.vibemusic.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

@Service
@CacheConfig(cacheNames = "albumCache")
public class AlbumServiceImpl extends ServiceImpl<AlbumMapper, Album> implements IAlbumService {

    @Autowired
    private AlbumMapper albumMapper;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private StyleMapper styleMapper;
    @Autowired
    private GenreMapper genreMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private UserFavoriteMapper userFavoriteMapper;
    @Autowired
    private BannerMapper bannerMapper;
    @Autowired
    private cn.edu.seig.vibemusic.util.CachePurger cachePurger;

    @Override
    @Cacheable(key = "'albumsByArtist-' + #artistId + '-' + #pageNum + '-' + #pageSize")
    public Result<PageResult<AlbumVO>> getAlbumsByArtist(Long artistId, Integer pageNum, Integer pageSize) {
        Page<Album> page = new Page<>(pageNum, pageSize);
        IPage<Album> p = albumMapper.selectPage(page, new QueryWrapper<Album>().eq("artist_id", artistId).orderByDesc("release_date"));
        List<AlbumVO> list = p.getRecords().stream().map(a -> {
            AlbumVO vo = new AlbumVO();
            BeanUtils.copyProperties(a, vo);
            vo.setAlbumId(a.getAlbumId());
            // 取歌手名
            var artist = albumMapper.selectById(a.getArtistId());
            // albumMapper 无法查 artist，保持为空或另行查询；前端不依赖此处
            return vo;
        }).toList();
        return Result.success(new PageResult<>(p.getTotal(), list));
    }

    @Override
    @Cacheable(key = "'albumDetail-' + #albumId")
    public Result<AlbumVO> getAlbumDetail(Long albumId) {
        Album a = albumMapper.selectById(albumId);
        if (a == null) return Result.success("数据不存在", null);
        AlbumVO vo = new AlbumVO();
        BeanUtils.copyProperties(a, vo);
        vo.setAlbumId(a.getAlbumId());
        return Result.success(vo);
    }

    @Override
    public Result<java.util.List<String>> getAlbumTitlesByArtist(Long artistId) {
        List<Album> list = this.lambdaQuery().eq(Album::getArtistId, artistId).select(Album::getTitle).list();
        java.util.List<String> titles = list.stream().map(Album::getTitle).filter(java.util.Objects::nonNull).distinct().toList();
        return Result.success(titles);
    }

    @Override
    public Result<Long> getAlbumSongCount(Long albumId) {
        Long count = songMapper.selectCount(new QueryWrapper<Song>().eq("album_id", albumId));
        return Result.success(count == null ? 0L : count);
    }

    @Override
    public Result<Long> getAllAlbumsCount() {
        Long count = this.lambdaQuery().count();
        return Result.success(count == null ? 0L : count);
    }

    // 管理端：分页获取专辑列表
    @Override
    @Cacheable(key = "'adminAlbums-' + #albumDTO.pageNum + '-' + #albumDTO.pageSize + '-' + #albumDTO.artistId + '-' + #albumDTO.title")
    public Result<PageResult<AlbumVO>> getAllAlbums(AlbumDTO albumDTO) {
        Page<Album> page = new Page<>(albumDTO.getPageNum(), albumDTO.getPageSize());
        QueryWrapper<Album> qw = new QueryWrapper<>();
        if (albumDTO.getArtistId() != null) qw.eq("artist_id", albumDTO.getArtistId());
        if (albumDTO.getTitle() != null && !albumDTO.getTitle().isBlank()) qw.like("title", albumDTO.getTitle());
        IPage<Album> p = albumMapper.selectPage(page, qw.orderByDesc("release_date"));
        java.util.List<AlbumVO> list = p.getRecords().stream().map(a -> {
            AlbumVO vo = new AlbumVO();
            BeanUtils.copyProperties(a, vo);
            vo.setAlbumId(a.getAlbumId());
            // 这里留空 artistName，由前端通过左侧树补充展示
            return vo;
        }).toList();
        return Result.success(new PageResult<>(p.getTotal(), list));
    }

    // 管理端：新增专辑
    @Override
    @CacheEvict(cacheNames = {"albumCache", "songCache"}, allEntries = true)
    public Result addAlbum(AlbumAddDTO albumAddDTO) {
        Album a = new Album();
        BeanUtils.copyProperties(albumAddDTO, a);
        // releaseDate 传入的是 yyyy-MM-dd 字符串，需转为 LocalDate
        try {
            if (albumAddDTO.getReleaseDate() != null && !albumAddDTO.getReleaseDate().isBlank()) {
                a.setReleaseDate(java.time.LocalDate.parse(albumAddDTO.getReleaseDate()));
            }
        } catch (Exception ignored) {}
        if (albumMapper.insert(a) == 0) return Result.error("新增失败");

        // 规则：若新增专辑时间为空，但该专辑名下已有歌曲具有时间，则回显为专辑时间（取最早一首）
        try {
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
        } catch (Exception ignored) {}
        return Result.success("新增成功");
    }

    // 管理端：更新专辑
    @Override
    @CacheEvict(cacheNames = {"albumCache"}, allEntries = true)
    public Result updateAlbum(AlbumUpdateDTO albumUpdateDTO) {
        Album a = new Album();
        BeanUtils.copyProperties(albumUpdateDTO, a);
        try {
            if (albumUpdateDTO.getReleaseDate() != null && !albumUpdateDTO.getReleaseDate().isBlank()) {
                a.setReleaseDate(java.time.LocalDate.parse(albumUpdateDTO.getReleaseDate()));
            }
        } catch (Exception ignored) {}
        if (albumMapper.updateById(a) == 0) return Result.error("更新失败");

        // 若本次更新仅设置了专辑封面，且该专辑下歌曲没有封面，则回填到歌曲
        try {
            if (a.getCoverUrl() != null && !a.getCoverUrl().isEmpty()) {
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Song> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                uw.eq("album_id", a.getAlbumId())
                  .and(w -> w.isNull("cover_url").or().eq("cover_url", ""))
                  .set("cover_url", a.getCoverUrl());
                songMapper.update(null, uw);
            }
        } catch (Exception ignored) {}

        // 规则：若本次更新未带专辑时间，则尝试从该专辑下已有歌曲回显（最早一首）
        try {
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
        } catch (Exception ignored) {}

        // 专辑更新后，同步回填 tb_genre 的 album_id 与 album_style_id
        try {
            Long albumId = albumUpdateDTO.getAlbumId();
            // 计算专辑类型 styleId（按专辑 category 文本匹配 tb_style.name）
            Long albumStyleId = null;
            if (a.getCategory() != null && !a.getCategory().isBlank()) {
                Style st = styleMapper.selectOne(new QueryWrapper<Style>().eq("name", a.getCategory()));
                if (st == null) {
                    // 若不存在该类型，自动创建一条 style 记录
                    st = new Style();
                    st.setName(a.getCategory());
                    styleMapper.insert(st);
                }
                if (st != null) albumStyleId = st.getStyleId();
            }

            // 找到该专辑下的歌曲（优先按 album_id，其次兼容老数据按标题+artist_id）
            java.util.List<Song> songs = songMapper.selectList(new QueryWrapper<Song>().eq("album_id", albumId));
            if (songs == null || songs.isEmpty()) {
                songs = songMapper.selectList(new QueryWrapper<Song>().eq("album", a.getTitle()).eq("artist_id", a.getArtistId()));
            }
            for (Song s : songs) {
                // 更新已有的 genre 行
                com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Genre> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
                uw.eq("song_id", s.getSongId()).set("album_id", albumId).set("album_style_id", albumStyleId);
                int affected = genreMapper.update(null, uw);
                // 若不存在，则补一行仅含专辑信息（style_id 允许为空）
                if (affected == 0) {
                    Genre g = new Genre();
                    g.setSongId(s.getSongId());
                    g.setAlbumId(albumId);
                    g.setAlbumStyleId(albumStyleId);
                    genreMapper.insert(g);
                }
            }
        } catch (Exception ignored) {}

        return Result.success("更新成功");
    }

    // 管理端：删除专辑
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

        // 2) 清理关联：专辑评论(type=2，存 playlist_id)、专辑收藏(type=3)、轮播及其图片
        try {
            commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>().eq("type", 2).eq("album_id", albumId));
            userFavoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 3).eq("album_id", albumId));
            java.util.List<cn.edu.seig.vibemusic.model.entity.Banner> banners = bannerMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Banner>().eq("album_id", albumId));
            for (cn.edu.seig.vibemusic.model.entity.Banner b : banners) {
                String url = b.getBannerUrl();
                if (url != null && !url.isEmpty()) minioService.deleteFile(url);
            }
            if (!banners.isEmpty()) {
                bannerMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Banner>().eq("album_id", albumId));
            }
        } catch (Exception ignored) {}

        // 3) 置空该专辑下歌曲的 album_id 与 album 文本
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Song> uw = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("album_id", albumId).set("album_id", null).set("album", "");
        songMapper.update(null, uw);

        // 3.1) 同步清理 tb_genre 中该专辑的专辑关联信息（不删歌曲风格，仅清专辑维度）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Genre> ug = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        ug.eq("album_id", albumId).set("album_id", null).set("album_style_id", null);
        genreMapper.update(null, ug);

        // 4) 删除专辑
        if (albumMapper.deleteById(albumId) == 0) return Result.error("删除失败");

        // 5) 兜底清理
        try {
            // 复用 artist 的清理逻辑位置不便直接引用，这里适度清理关键表
            commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>()
                    .eq("type", 2).apply("album_id not in (select id from tb_album)"));
            userFavoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>()
                    .eq("type", 3).apply("album_id not in (select id from tb_album)"));
        } catch (Exception ignored) {}

        // 6) 自定义 Redis Key 清理
        try { cachePurger.purgeForAlbum(albumId); } catch (Exception ignored) {}

        return Result.success("删除成功");
    }

    // 管理端：批量删除专辑
    @Override
    @CacheEvict(cacheNames = {"albumCache", "songCache"}, allEntries = true)
    public Result deleteAlbums(java.util.List<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) return Result.success("删除成功");
        int affected = albumMapper.deleteBatchIds(albumIds);
        return affected > 0 ? Result.success("删除成功") : Result.error("删除失败");
    }
}


