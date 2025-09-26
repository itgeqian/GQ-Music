package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.enumeration.LikeStatusEnum;
import cn.edu.seig.vibemusic.enumeration.RoleEnum;
import cn.edu.seig.vibemusic.mapper.*;
import cn.edu.seig.vibemusic.model.dto.ArtistAddDTO;
import cn.edu.seig.vibemusic.model.dto.ArtistDTO;
import cn.edu.seig.vibemusic.model.dto.ArtistUpdateDTO;
import cn.edu.seig.vibemusic.model.entity.Artist;
import cn.edu.seig.vibemusic.model.entity.UserFavorite;
import cn.edu.seig.vibemusic.model.vo.ArtistDetailVO;
import cn.edu.seig.vibemusic.model.vo.ArtistNameVO;
import cn.edu.seig.vibemusic.model.vo.ArtistVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IArtistService;
import cn.edu.seig.vibemusic.service.MinioService;
import cn.edu.seig.vibemusic.util.JwtUtil;
import cn.edu.seig.vibemusic.util.TypeConversionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author  geqian
 * @since 2025-01-09
 */
@Service
@CacheConfig(cacheNames = "artistCache")
public class ArtistServiceImpl extends ServiceImpl<ArtistMapper, Artist> implements IArtistService {

    @Autowired
    private ArtistMapper artistMapper;
    @Autowired
    private UserFavoriteMapper userFavoriteMapper;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private AlbumMapper albumMapper;
    @Autowired
    private GenreMapper genreMapper;
    @Autowired
    private PlaylistBindingMapper playlistBindingMapper;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private BannerMapper bannerMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private cn.edu.seig.vibemusic.util.CachePurger cachePurger;

    /**
     * 获取所有歌手列表
     *
     * @param artistDTO artistDTO
     * @return 歌手列表
     */
    @Override
    @Cacheable(key = "#artistDTO.pageNum + '-' + #artistDTO.pageSize + '-' + #artistDTO.artistName + '-' + #artistDTO.gender + '-' + #artistDTO.area")
    public Result<PageResult<ArtistVO>> getAllArtists(ArtistDTO artistDTO) {
        // 分页查询
        Page<Artist> page = new Page<>(artistDTO.getPageNum(), artistDTO.getPageSize());
        QueryWrapper<Artist> queryWrapper = new QueryWrapper<>();
        // 根据 artistDTO 的条件构建查询条件
        if (artistDTO.getArtistName() != null) {
            queryWrapper.like("name", artistDTO.getArtistName());
        }
        if (artistDTO.getGender() != null) {
            queryWrapper.eq("gender", artistDTO.getGender());
        }
        if (artistDTO.getArea() != null) {
            queryWrapper.like("area", artistDTO.getArea());
        }

        IPage<Artist> artistPage = artistMapper.selectPage(page, queryWrapper);
        if (artistPage.getRecords().size() == 0) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        // 转换成 ArtistVO
        List<ArtistVO> artistVOList = artistPage.getRecords().stream()
                .map(artist -> {
                    ArtistVO artistVO = new ArtistVO();
                    BeanUtils.copyProperties(artist, artistVO);
                    return artistVO;
                }).toList();

        return Result.success(new PageResult<>(artistPage.getTotal(), artistVOList));
    }

    /**
     * 获取所有歌手列表（含详情）
     *
     * @param artistDTO artistDTO
     * @return 歌手列表
     */
    @Override
    @Cacheable(key = "#artistDTO.pageNum + '-' + #artistDTO.pageSize + '-' + #artistDTO.artistName + '-' + #artistDTO.gender + '-' + #artistDTO.area + '-admin'")
    public Result<PageResult<Artist>> getAllArtistsAndDetail(ArtistDTO artistDTO) {
        // 分页查询
        Page<Artist> page = new Page<>(artistDTO.getPageNum(), artistDTO.getPageSize());
        QueryWrapper<Artist> queryWrapper = new QueryWrapper<>();
        // 根据 artistDTO 的条件构建查询条件
        if (artistDTO.getArtistName() != null) {
            queryWrapper.like("name", artistDTO.getArtistName());
        }
        if (artistDTO.getGender() != null) {
            queryWrapper.eq("gender", artistDTO.getGender());
        }
        if (artistDTO.getArea() != null) {
            queryWrapper.like("area", artistDTO.getArea());
        }

        // 倒序排序
        queryWrapper.orderByDesc("id");

        IPage<Artist> artistPage = artistMapper.selectPage(page, queryWrapper);
        if (artistPage.getRecords().size() == 0) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        return Result.success(new PageResult<>(artistPage.getTotal(), artistPage.getRecords()));
    }

    /**
     * 获取所有歌手id和歌手名称
     *
     * @return 歌手名称列表
     */
    @Override
    @Cacheable(key = "'allArtistNames'")
    public Result<List<ArtistNameVO>> getAllArtistNames() {
        List<Artist> artists = artistMapper.selectList(new QueryWrapper<Artist>().orderByDesc("id"));
        if (artists.isEmpty()) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, null);
        }

        List<ArtistNameVO> artistNameVOList = artists.stream()
                .map(artist -> {
                    ArtistNameVO artistNameVO = new ArtistNameVO();
                    artistNameVO.setArtistId(artist.getArtistId());
                    artistNameVO.setArtistName(artist.getArtistName());
                    return artistNameVO;
                }).toList();

        return Result.success(artistNameVOList);
    }

    /**
     * 获取随机歌手
     * 随机歌手的数量为 10
     *
     * @return 随机歌手列表
     */
    @Override
    public Result<List<ArtistVO>> getRandomArtists() {
        QueryWrapper<Artist> queryWrapper = new QueryWrapper<>();
        queryWrapper.last("ORDER BY RAND() LIMIT 10");

        List<Artist> artists = artistMapper.selectList(queryWrapper);
        if (artists.isEmpty()) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, null);
        }

        List<ArtistVO> artistVOList = artists.stream()
                .map(artist -> {
                    ArtistVO artistVO = new ArtistVO();
                    BeanUtils.copyProperties(artist, artistVO);
                    return artistVO;
                }).toList();

        return Result.success(artistVOList);
    }

    /**
     * 获取歌手详情
     *
     * @param artistId 歌手id
     * @param request  HttpServletRequest，用于获取请求头中的 token
     * @return 歌手详情
     */
    @Override
    @Cacheable(key = "#artistId")
    public Result<ArtistDetailVO> getArtistDetail(Long artistId, HttpServletRequest request) {
        ArtistDetailVO artistDetailVO = artistMapper.getArtistDetailById(artistId);

        // 设置默认状态
        List<SongVO> songVOList = artistDetailVO.getSongs();
        songVOList.forEach(songVO -> songVO.setLikeStatus(LikeStatusEnum.DEFAULT.getId()));

        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 去掉 "Bearer " 前缀
        }

        Map<String, Object> map = null;
        if (token != null && !token.isEmpty()) {
            map = JwtUtil.parseToken(token);
        }

        // 如果 token 解析成功且用户为登录状态，进一步操作
        if (map != null) {
            String role = (String) map.get(JwtClaimsConstant.ROLE);
            if (role.equals(RoleEnum.USER.getRole())) {
                Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
                Long userId = TypeConversionUtil.toLong(userIdObj);

                // 获取用户收藏的歌曲
                List<UserFavorite> favoriteSongs = userFavoriteMapper.selectList(new QueryWrapper<UserFavorite>()
                        .eq("user_id", userId)
                        .eq("type", 0));

                // 获取用户收藏的歌曲 id
                Set<Long> favoriteSongIds = favoriteSongs.stream()
                        .map(UserFavorite::getSongId)
                        .collect(Collectors.toSet());

                // 检查并更新状态
                for (SongVO songVO : songVOList) {
                    if (favoriteSongIds.contains(songVO.getSongId())) {
                        songVO.setLikeStatus(LikeStatusEnum.LIKE.getId());
                    }
                }
            }
        }

        // 设置歌曲列表
        artistDetailVO.setSongs(songVOList);

        return Result.success(artistDetailVO);
    }

    /**
     * 获取所有歌手数量
     *
     * @param gender 性别
     * @param area   地区
     * @return 歌手数量
     */
    @Override
    public Result<Long> getAllArtistsCount(Integer gender, String area) {
        QueryWrapper<Artist> queryWrapper = new QueryWrapper<>();
        if (gender != null) {
            queryWrapper.eq("gender", gender);
        }
        if (area != null) {
            queryWrapper.eq("area", area);
        }

        return Result.success(artistMapper.selectCount(queryWrapper));
    }

    /**
     * 添加歌手
     *
     * @param artistAddDTO 歌手添加DTO
     * @return 添加结果
     */
    @Override
    @CacheEvict(cacheNames = "artistCache", allEntries = true)
    public Result addArtist(ArtistAddDTO artistAddDTO) {
        QueryWrapper<Artist> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", artistAddDTO.getArtistName());
        if (artistMapper.selectCount(queryWrapper) > 0) {
            return Result.error(MessageConstant.ARTIST + MessageConstant.ALREADY_EXISTS);
        }

        Artist artist = new Artist();
        BeanUtils.copyProperties(artistAddDTO, artist);
        artistMapper.insert(artist);

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌手
     *
     * @param artistUpdateDTO 歌手更新DTO
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = "artistCache", allEntries = true)
    public Result updateArtist(ArtistUpdateDTO artistUpdateDTO) {
        Long artistId = artistUpdateDTO.getArtistId();

        Artist artistByArtistName = artistMapper.selectOne(new QueryWrapper<Artist>().eq("name", artistUpdateDTO.getArtistName()));
        if (artistByArtistName != null && !artistByArtistName.getArtistId().equals(artistId)) {
            return Result.error(MessageConstant.ARTIST + MessageConstant.ALREADY_EXISTS);
        }

        Artist artist = new Artist();
        BeanUtils.copyProperties(artistUpdateDTO, artist);
        if (artistMapper.updateById(artist) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌手头像
     *
     * @param artistId 歌手id
     * @param avatar   头像
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = "artistCache", allEntries = true)
    public Result updateArtistAvatar(Long artistId, String avatar) {
        Artist artist = artistMapper.selectById(artistId);
        String avatarUrl = artist.getAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            minioService.deleteFile(avatarUrl);
        }

        artist.setAvatar(avatar);
        if (artistMapper.updateById(artist) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 删除歌手
     *
     * @param artistId 歌手id
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = "artistCache", allEntries = true)
    public Result deleteArtist(Long artistId) {
        cascadeDeleteArtist(artistId);
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /**
     * 批量删除歌手
     *
     * @param artistIds 歌手id列表
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = {"artistCache", "songCache"}, allEntries = true)
    public Result deleteArtists(List<Long> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
        for (Long artistId : artistIds) {
            cascadeDeleteArtist(artistId);
        }
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /** 级联删除歌手相关的数据与 MinIO 文件 */
    private void cascadeDeleteArtist(Long artistId) {
        if (artistId == null) return;
        Artist artist = artistMapper.selectById(artistId);
        if (artist == null) return;

        // 1) 该歌手的歌曲，清理 MinIO 文件并删库
        List<cn.edu.seig.vibemusic.model.entity.Song> songs = songMapper.selectList(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Song>().eq("artist_id", artistId));
        List<Long> songIds = songs.stream().map(cn.edu.seig.vibemusic.model.entity.Song::getSongId).toList();
        for (cn.edu.seig.vibemusic.model.entity.Song s : songs) {
            if (s.getCoverUrl() != null && !s.getCoverUrl().isEmpty()) minioService.deleteFile(s.getCoverUrl());
            if (s.getAudioUrl() != null && !s.getAudioUrl().isEmpty()) minioService.deleteFile(s.getAudioUrl());
            if (s.getLyricUrl() != null && !s.getLyricUrl().isEmpty()) minioService.deleteFile(s.getLyricUrl());
        }
        if (!songIds.isEmpty()) {
            // 关联删除：tb_genre、歌单绑定、评论(歌曲)、用户收藏(歌曲)
            genreMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Genre>().in("song_id", songIds));
            playlistBindingMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.PlaylistBinding>().in("song_id", songIds));
            commentMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>().in("song_id", songIds).eq("type", 0));
            userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 0).in("song_id", songIds));
            songMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Song>().in("id", songIds));
        }

        // 2) 该歌手的专辑，清理封面、专辑评论、收藏、轮播引用
        List<cn.edu.seig.vibemusic.model.entity.Album> albums = albumMapper.selectList(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Album>().eq("artist_id", artistId));
        List<Long> albumIds = albums.stream().map(cn.edu.seig.vibemusic.model.entity.Album::getAlbumId).toList();
        for (cn.edu.seig.vibemusic.model.entity.Album a : albums) {
            if (a.getCoverUrl() != null && !a.getCoverUrl().isEmpty()) minioService.deleteFile(a.getCoverUrl());
        }
        if (!albumIds.isEmpty()) {
            // 专辑评论（当前实现 type=2 存在 playlist_id 字段）
            commentMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>().in("playlist_id", albumIds).eq("type", 2));
            // 用户收藏专辑
            userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 3).in("album_id", albumIds));
            // 轮播引用到专辑的图片也删除
            List<cn.edu.seig.vibemusic.model.entity.Banner> banners = bannerMapper.selectList(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Banner>().in("album_id", albumIds));
            for (cn.edu.seig.vibemusic.model.entity.Banner b : banners) {
                if (b.getBannerUrl() != null && !b.getBannerUrl().isEmpty()) minioService.deleteFile(b.getBannerUrl());
            }
            if (!banners.isEmpty()) {
                bannerMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Banner>().in("album_id", albumIds));
            }
            albumMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Album>().in("id", albumIds));
        }

        // 3) 取消用户关注该歌手
        userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 2).eq("artist_id", artistId));

        // 4) 删除歌手头像与歌手记录
        if (artist.getAvatar() != null && !artist.getAvatar().isEmpty()) minioService.deleteFile(artist.getAvatar());
        artistMapper.deleteById(artistId);

        // 5) 兜底：全局清理可能遗留的脏数据（外键缺失的收藏、绑定、流派映射、评论）
        cleanupOrphans();
        // 6) 额外：清理自定义 Redis Key（如推荐列表）
        try { cachePurger.purgeForArtist(artistId); } catch (Exception ignored) {}
    }

    /** 全表兜底清理：移除已不存在实体的引用行，避免脏数据 */
    private void cleanupOrphans() {
        // 歌单绑定里引用了不存在的歌曲
        playlistBindingMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.PlaylistBinding>()
                .apply("song_id is not null and song_id not in (select id from tb_song)"));

        // 收藏-歌曲/专辑/歌手 的孤儿记录
        userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>()
                .eq("type", 0).apply("song_id is not null and song_id not in (select id from tb_song)"));
        userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>()
                .eq("type", 3).apply("album_id is not null and album_id not in (select id from tb_album)"));
        userFavoriteMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>()
                .eq("type", 2).apply("artist_id is not null and artist_id not in (select id from tb_artist)"));

        // 流派映射里引用了不存在的歌曲
        genreMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Genre>()
                .apply("song_id is not null and song_id not in (select id from tb_song)"));

        // 评论-歌曲
        commentMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>()
                .eq("type", 0).apply("song_id is not null and song_id not in (select id from tb_song)"));
        // 评论-专辑（存放在 playlist_id 列）
        commentMapper.delete(new QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>()
                .eq("type", 2).apply("album_id is not null and album_id not in (select id from tb_album)"));
    }

    /**
     * 定时脏数据巡检与清理：默认每晚 02:30 运行一次。
     * 可通过 spring 定时任务配置调整频率。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void scheduledCleanupOrphans() {
        try { cleanupOrphans(); } catch (Exception ignored) {}
    }

}
