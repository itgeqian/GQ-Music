package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.enumeration.LikeStatusEnum;
import cn.edu.seig.vibemusic.mapper.PlaylistMapper;
import cn.edu.seig.vibemusic.mapper.ArtistMapper;
import cn.edu.seig.vibemusic.mapper.SongMapper;
import cn.edu.seig.vibemusic.mapper.UserFavoriteMapper;
import cn.edu.seig.vibemusic.mapper.AlbumMapper;
import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.entity.UserFavorite;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.model.vo.ArtistVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IUserFavoriteService;
import cn.edu.seig.vibemusic.util.ThreadLocalUtil;
import cn.edu.seig.vibemusic.util.TypeConversionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Service
@CacheConfig(cacheNames = "userFavoriteCache")
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite> implements IUserFavoriteService {

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;
    @Autowired
    private SongMapper songMapper;
    @Autowired
    private PlaylistMapper playlistMapper;
    @Autowired
    private ArtistMapper artistMapper;
    @Autowired
    private AlbumMapper albumMapper;

    /** 提供给 SpEL 的当前用户ID（用于缓存隔离） */
    public Long currentUserId() {
        try {
            Map<String, Object> map = ThreadLocalUtil.get();
            return TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 获取用户收藏的歌曲列表
     *
     * @param songDTO 歌曲查询条件
     * @return 用户收藏的歌曲列表
     */
    @Override
    @Cacheable(key = "'favSongs-v3-' + #root.target.currentUserId() + '-' + #songDTO.pageNum + '-' + #songDTO.pageSize + '-' + #songDTO.songName + '-' + #songDTO.artistName + '-' + #songDTO.album")
    public Result<PageResult<SongVO>> getUserFavoriteSongs(SongDTO songDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        // 获取用户收藏的歌曲 ID 列表
        List<Long> favoriteSongIds = userFavoriteMapper.getUserFavoriteSongIds(userId);
        if (favoriteSongIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, Collections.emptyList()));
        }

        // 分页查询收藏的歌曲，支持模糊查询
        Page<SongVO> page = new Page<>(songDTO.getPageNum(), songDTO.getPageSize());
        IPage<SongVO> songPage = songMapper.getSongsByIds(
                page,
                userId,
                favoriteSongIds,
                songDTO.getSongName(),
                songDTO.getArtistName(),
                songDTO.getAlbum()
        );

        // 遍历结果，设置 likeStatus
        List<SongVO> songVOList = songPage.getRecords().stream()
                .peek(songVO -> songVO.setLikeStatus(LikeStatusEnum.LIKE.getId())) // 设置为已收藏
                .toList();

        return Result.success(new PageResult<>(songPage.getTotal(), songVOList));
    }

    /** 判断是否已关注某歌手 */
    @Override
    @Cacheable(key = "'isArtistFollowed-v2-' + #root.target.currentUserId() + '-' + #artistId")
    public Result<Boolean> isArtistFollowed(Long artistId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 2).eq("artist_id", artistId);
        boolean exists = userFavoriteMapper.selectCount(qw) > 0;
        return Result.success(exists);
    }

    /** 获取用户收藏的专辑列表 */
    @Override
    @org.springframework.cache.annotation.Cacheable(key = "'favAlbums-v2-' + #root.target.currentUserId() + '-' + #pageNum + '-' + #pageSize + '-' + #albumTitle")
    public Result<PageResult<AlbumVO>> getUserFavoriteAlbums(Integer pageNum, Integer pageSize, String albumTitle) {
        java.util.Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(cn.edu.seig.vibemusic.constant.JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);

        java.util.List<Long> albumIds = userFavoriteMapper.getUserFavoriteAlbumIds(userId);
        if (albumIds == null || albumIds.isEmpty()) {
            return cn.edu.seig.vibemusic.result.Result.success(new cn.edu.seig.vibemusic.result.PageResult<>(0L, java.util.Collections.emptyList()));
        }

        // 批量查询并在内存中过滤、分页
        java.util.List<cn.edu.seig.vibemusic.model.entity.Album> albums = albumMapper.selectBatchIds(albumIds);
        java.util.List<AlbumVO> list = albums.stream()
                .filter(a -> albumTitle == null || albumTitle.isBlank() || (a.getTitle() != null && a.getTitle().contains(albumTitle)))
                .map(a -> {
                    AlbumVO vo = new AlbumVO();
                    org.springframework.beans.BeanUtils.copyProperties(a, vo);
                    vo.setAlbumId(a.getAlbumId());
                    return vo;
                })
                .toList();

        long total = list.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(list.size(), from + pageSize);
        java.util.List<AlbumVO> pageList = from >= to ? java.util.Collections.emptyList() : list.subList(from, to);
        return cn.edu.seig.vibemusic.result.Result.success(new cn.edu.seig.vibemusic.result.PageResult<>(total, pageList));
    }

    /**
     * 收藏歌曲
     *
     * @param songId 歌曲 ID
     * @return 成功或失败
     */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "songCache", "artistCache", "playlistCache"}, allEntries = true)
    public Result<String> collectSong(Long songId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        QueryWrapper<UserFavorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", 0).eq("song_id", songId);
        if (userFavoriteMapper.selectCount(queryWrapper) > 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        UserFavorite userFavorite = new UserFavorite();
        userFavorite.setUserId(userId).setType(0).setSongId(songId).setCreateTime(LocalDateTime.now());
        userFavoriteMapper.insert(userFavorite);

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 取消收藏歌曲
     *
     * @param songId 歌曲 ID
     * @return 成功或失败
     */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "songCache", "artistCache", "playlistCache"}, allEntries = true)
    public Result<String> cancelCollectSong(Long songId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        QueryWrapper<UserFavorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", 0).eq("song_id", songId);
        if (userFavoriteMapper.delete(queryWrapper) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /**
     * 获取用户收藏的歌单列表
     *
     * @param playlistDTO 歌单查询条件
     * @return 用户收藏的歌单列表
     */
    @Override
    @Cacheable(key = "'favPlaylists-v2-' + #root.target.currentUserId() + '-' + #playlistDTO.pageNum + '-' + #playlistDTO.pageSize + '-' + #playlistDTO.title + '-' + #playlistDTO.style")
    public Result<PageResult<PlaylistVO>> getUserFavoritePlaylists(PlaylistDTO playlistDTO) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        // 获取用户收藏的歌单 ID 列表
        List<Long> favoritePlaylistIds = userFavoriteMapper.getUserFavoritePlaylistIds(userId);
        if (favoritePlaylistIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, Collections.emptyList()));
        }

        // 分页查询收藏的歌单，支持模糊查询
        Page<PlaylistVO> page = new Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        IPage<PlaylistVO> playlistPage = playlistMapper.getPlaylistsByIds(
                userId,
                page,
                favoritePlaylistIds,
                playlistDTO.getTitle(),
                playlistDTO.getStyle()
        );

        return Result.success(new PageResult<>(playlistPage.getTotal(), playlistPage.getRecords()));
    }

    /**
     * 收藏歌单
     *
     * @param playlistId 歌单 ID
     * @return 成功或失败
     */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "songCache", "artistCache", "playlistCache"}, allEntries = true)
    public Result<String> collectPlaylist(Long playlistId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        QueryWrapper<UserFavorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", 1).eq("playlist_id", playlistId);
        if (userFavoriteMapper.selectCount(queryWrapper) > 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        UserFavorite userFavorite = new UserFavorite();
        userFavorite.setUserId(userId).setType(1).setPlaylistId(playlistId).setCreateTime(LocalDateTime.now());
        userFavoriteMapper.insert(userFavorite);

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 取消收藏歌单
     *
     * @param playlistId 歌单 ID
     * @return 成功或失败
     */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "songCache", "artistCache", "playlistCache"}, allEntries = true)
    public Result<String> cancelCollectPlaylist(Long playlistId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = TypeConversionUtil.toLong(userIdObj);

        QueryWrapper<UserFavorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", 1).eq("playlist_id", playlistId);
        if (userFavoriteMapper.delete(queryWrapper) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /**
     * 获取我关注的歌手
     */
    @Override
    @Cacheable(key = "'followArtists-v2-' + #root.target.currentUserId() + '-' + #pageNum + '-' + #pageSize + '-' + #artistName")
    public Result<PageResult<ArtistVO>> getFollowArtists(Integer pageNum, Integer pageSize, String artistName) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        List<Long> artistIds = userFavoriteMapper.getUserFollowArtistIds(userId);
        if (artistIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, Collections.emptyList()));
        }

        // 这里简单分页（因为没有现成的 mapper 方法按 ID 列表分页筛选名称），先查出全部再内存分页
        List<ArtistVO> all = artistIds.stream().map(id -> {
            var artist = artistMapper.selectById(id);
            if (artist == null) return null;
            ArtistVO vo = new ArtistVO();
            vo.setArtistId(artist.getArtistId());
            vo.setArtistName(artist.getArtistName());
            vo.setAvatar(artist.getAvatar());
            return vo;
        }).filter(java.util.Objects::nonNull)
          .filter(vo -> artistName == null || artistName.isBlank() || vo.getArtistName().contains(artistName))
          .toList();

        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(all.size(), from + pageSize);
        List<ArtistVO> pageList = from >= all.size() ? Collections.emptyList() : all.subList(from, to);
        return Result.success(new PageResult<>((long) all.size(), pageList));
    }

    /** 关注歌手 */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "artistCache"}, allEntries = true)
    public Result<String> followArtist(Long artistId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 2).eq("artist_id", artistId);
        if (userFavoriteMapper.selectCount(qw) > 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        UserFavorite uf = new UserFavorite();
        uf.setUserId(userId).setType(2).setArtistId(artistId).setCreateTime(LocalDateTime.now());
        userFavoriteMapper.insert(uf);
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /** 取消关注歌手 */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "artistCache"}, allEntries = true)
    public Result<String> cancelFollowArtist(Long artistId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 2).eq("artist_id", artistId);
        if (userFavoriteMapper.delete(qw) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /** 收藏专辑 */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "albumCache"}, allEntries = true)
    public Result<String> collectAlbum(Long albumId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 3).eq("album_id", albumId);
        if (userFavoriteMapper.selectCount(qw) > 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        UserFavorite uf = new UserFavorite();
        uf.setUserId(userId).setType(3).setAlbumId(albumId).setCreateTime(LocalDateTime.now());
        userFavoriteMapper.insert(uf);
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /** 取消收藏专辑 */
    @Override
    @CacheEvict(cacheNames = {"userFavoriteCache", "albumCache"}, allEntries = true)
    public Result<String> cancelCollectAlbum(Long albumId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 3).eq("album_id", albumId);
        if (userFavoriteMapper.delete(qw) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /** 判断专辑是否已收藏 */
    @Override
    @Cacheable(key = "'isAlbumCollected-v2-' + #root.target.currentUserId() + '-' + #albumId")
    public Result<Boolean> isAlbumCollected(Long albumId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));
        QueryWrapper<UserFavorite> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("type", 3).eq("album_id", albumId);
        boolean exists = userFavoriteMapper.selectCount(qw) > 0;
        return Result.success(exists);
    }

    /** 公开：按 userId 查询其收藏的专辑 */
    @Override
    public Result<PageResult<AlbumVO>> getFavoriteAlbumsByUserId(Long userId, Integer pageNum, Integer pageSize, String albumTitle) {
        java.util.List<Long> albumIds = userFavoriteMapper.getUserFavoriteAlbumIds(userId);
        if (albumIds == null || albumIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, java.util.Collections.emptyList()));
        }
        java.util.List<cn.edu.seig.vibemusic.model.entity.Album> albums = albumMapper.selectBatchIds(albumIds);
        java.util.List<AlbumVO> list = albums.stream()
                .filter(a -> albumTitle == null || albumTitle.isBlank() || (a.getTitle() != null && a.getTitle().contains(albumTitle)))
                .map(a -> {
                    AlbumVO vo = new AlbumVO();
                    org.springframework.beans.BeanUtils.copyProperties(a, vo);
                    vo.setAlbumId(a.getAlbumId());
                    return vo;
                })
                .toList();
        long total = list.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(list.size(), from + pageSize);
        java.util.List<AlbumVO> pageList = from >= to ? java.util.Collections.emptyList() : list.subList(from, to);
        return Result.success(new PageResult<>(total, pageList));
    }

    /** 公开：按 userId 查询其收藏的歌曲 */
    @Override
    public Result<PageResult<SongVO>> getFavoriteSongsByUserId(Long userId, SongDTO songDTO) {
        // 获取用户收藏的歌曲 ID 列表
        java.util.List<Long> favoriteSongIds = userFavoriteMapper.getUserFavoriteSongIds(userId);
        if (favoriteSongIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, java.util.Collections.emptyList()));
        }
        // 分页查询收藏的歌曲
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SongVO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(songDTO.getPageNum(), songDTO.getPageSize());
        com.baomidou.mybatisplus.core.metadata.IPage<SongVO> songPage = songMapper.getSongsByIds(
                page,
                userId,
                favoriteSongIds,
                songDTO.getSongName(),
                songDTO.getArtistName(),
                songDTO.getAlbum()
        );
        java.util.List<SongVO> songVOList = songPage.getRecords();
        return Result.success(new PageResult<>(songPage.getTotal(), songVOList));
    }

    /** 公开：按 userId 查询其收藏的歌单 */
    @Override
    public Result<PageResult<PlaylistVO>> getFavoritePlaylistsByUserId(Long userId, PlaylistDTO playlistDTO) {
        java.util.List<Long> favoritePlaylistIds = userFavoriteMapper.getUserFavoritePlaylistIds(userId);
        if (favoritePlaylistIds.isEmpty()) {
            return Result.success(new PageResult<>(0L, java.util.Collections.emptyList()));
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PlaylistVO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        com.baomidou.mybatisplus.core.metadata.IPage<PlaylistVO> playlistPage = playlistMapper.getPlaylistsByIds(
                userId,
                page,
                favoritePlaylistIds,
                playlistDTO.getTitle(),
                playlistDTO.getStyle()
        );
        return Result.success(new PageResult<>(playlistPage.getTotal(), playlistPage.getRecords()));
    }

}
