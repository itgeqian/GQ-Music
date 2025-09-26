package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.enumeration.LikeStatusEnum;
import cn.edu.seig.vibemusic.enumeration.RoleEnum;
import cn.edu.seig.vibemusic.mapper.GenreMapper;
import cn.edu.seig.vibemusic.mapper.PlaylistBindingMapper;
import cn.edu.seig.vibemusic.mapper.CommentMapper;
import cn.edu.seig.vibemusic.mapper.AlbumMapper;
import cn.edu.seig.vibemusic.mapper.SongMapper;
import cn.edu.seig.vibemusic.mapper.StyleMapper;
import cn.edu.seig.vibemusic.mapper.UserFavoriteMapper;
import cn.edu.seig.vibemusic.model.dto.SongAddDTO;
import cn.edu.seig.vibemusic.model.dto.SongAndArtistDTO;
import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.dto.SongUpdateDTO;
import cn.edu.seig.vibemusic.model.entity.Genre;
import cn.edu.seig.vibemusic.model.entity.Album;
import cn.edu.seig.vibemusic.model.entity.Song;
import cn.edu.seig.vibemusic.model.entity.Style;
import cn.edu.seig.vibemusic.model.entity.UserFavorite;
import cn.edu.seig.vibemusic.model.vo.SongAdminVO;
import cn.edu.seig.vibemusic.model.vo.SongBatchImportResultVO;
import cn.edu.seig.vibemusic.model.vo.SongDetailVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.model.vo.LyricLine;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.ISongService;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Service
@CacheConfig(cacheNames = "songCache")
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements ISongService {

    @Autowired
    private SongMapper songMapper;
    @Autowired
    private UserFavoriteMapper userFavoriteMapper;
    @Autowired
    private StyleMapper styleMapper;
    @Autowired
    private GenreMapper genreMapper;
    @Autowired
    private AlbumMapper albumMapper;
    @Autowired
    private PlaylistBindingMapper playlistBindingMapper;
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private RedisTemplate<String, SongVO> redisTemplate;
    @Autowired
    private cn.edu.seig.vibemusic.util.CachePurger cachePurger;
    @Autowired
    private cn.edu.seig.vibemusic.service.HotSearchService hotSearchService;
    @Autowired
    private cn.edu.seig.vibemusic.mapper.ArtistMapper artistMapper;

    @Autowired
    private cn.edu.seig.vibemusic.service.ICommentService commentService;

    /**
     * 获取所有歌曲
     *
     * @param songDTO songDTO
     * @return 歌曲列表
     */
    @Override
    @Cacheable(key = "#songDTO.pageNum + '-' + #songDTO.pageSize + '-' + #songDTO.keyword + '-' + #songDTO.songName + '-' + #songDTO.artistName + '-' + #songDTO.album")
    public Result<PageResult<SongVO>> getAllSongs(SongDTO songDTO, HttpServletRequest request) {
        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 去掉 "Bearer " 前缀
        }

        Map<String, Object> map = null;
        if (token != null && !token.isEmpty()) {
            map = JwtUtil.parseToken(token);
        }

        // 查询歌曲列表：若传 keyword 则按统一关键字（歌名/歌手/专辑）搜索，否则走原有精确字段
        Page<SongVO> page = new Page<>(songDTO.getPageNum(), songDTO.getPageSize());
        IPage<SongVO> songPage;
        if (songDTO.getKeyword() != null && !songDTO.getKeyword().isEmpty()) {
            // 热搜计数 +1（关键词统一用去首尾空格的小写计数，避免重复）
            try { hotSearchService.increaseKeyword(songDTO.getKeyword().trim().toLowerCase()); } catch (Exception ignored) {}
            songPage = songMapper.getSongsByKeyword(page, songDTO.getKeyword());
        } else {
            songPage = songMapper.getSongsWithArtist(page, songDTO.getSongName(), songDTO.getArtistName(), songDTO.getAlbum());
        }
        if (songPage.getRecords().isEmpty()) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        // 设置默认状态
        List<SongVO> songVOList = songPage.getRecords().stream()
                .peek(songVO -> songVO.setLikeStatus(LikeStatusEnum.DEFAULT.getId()))
                .toList();

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

        return Result.success(new PageResult<>(songPage.getTotal(), songVOList));
    }

    /**
     * 获取歌手的所有歌曲
     *
     * @param songDTO songAndArtistDTO
     * @return 歌曲列表
     */
    @Override
    //@Cacheable(key = "#songDTO.pageNum + '-' + #songDTO.pageSize + '-' + #songDTO.songName + '-' + #songDTO.album + '-' + #songDTO.artistId")
    public Result<PageResult<SongAdminVO>> getAllSongsByArtist(SongAndArtistDTO songDTO) {
        // 分页查询
        Page<SongAdminVO> page = new Page<>(songDTO.getPageNum(), songDTO.getPageSize());
        IPage<SongAdminVO> songPage = songMapper.getSongsWithArtistName(page, songDTO.getArtistId(), songDTO.getSongName(), songDTO.getAlbum());

        if (songPage.getRecords().isEmpty()) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        return Result.success(new PageResult<>(songPage.getTotal(), songPage.getRecords()));
    }

    /**
     * 获取推荐歌曲
     * 推荐歌曲的数量为 20
     *
     * @param request HttpServletRequest，用于获取请求头中的 token
     * @return 推荐歌曲列表
     */
    @Override
    public Result<List<SongVO>> getRecommendedSongs(HttpServletRequest request) {
        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);  // 去掉 "Bearer " 前缀
        }

        Map<String, Object> map = null;
        if (token != null && !token.isEmpty()) {
            map = JwtUtil.parseToken(token);
        }

        // 用户未登录，返回随机歌曲列表
        if (map == null) {
            return Result.success(songMapper.getRandomSongsWithArtist());
        }

        // 获取用户 ID
        Long userId = TypeConversionUtil.toLong(map.get(JwtClaimsConstant.USER_ID));

        // 查询用户收藏的歌曲 ID
        List<Long> favoriteSongIds = userFavoriteMapper.getFavoriteSongIdsByUserId(userId);
        if (favoriteSongIds.isEmpty()) {
            return Result.success(songMapper.getRandomSongsWithArtist());
        }

        // 查询用户收藏的歌曲风格并统计频率
        List<Long> favoriteStyleIds = songMapper.getFavoriteSongStyles(favoriteSongIds);
        Map<Long, Long> styleFrequency = favoriteStyleIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // 按风格出现次数降序排序
        List<Long> sortedStyleIds = styleFrequency.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 从 Redis 获取缓存的推荐列表
        String redisKey = "recommended_songs:" + userId;
        List<SongVO> cachedSongs = redisTemplate.opsForList().range(redisKey, 0, -1);

        // 如果 Redis 没有缓存，则查询数据库并缓存
        if (cachedSongs == null || cachedSongs.isEmpty()) {
            // 根据排序后的风格推荐歌曲（排除已收藏歌曲）
            cachedSongs = songMapper.getRecommendedSongsByStyles(sortedStyleIds, favoriteSongIds, 80);
            redisTemplate.opsForList().rightPushAll(redisKey, cachedSongs);
            redisTemplate.expire(redisKey, 30, TimeUnit.MINUTES); // 设置过期时间 30 分钟
        }

        // 随机选取 20 首
        Collections.shuffle(cachedSongs);
        List<SongVO> recommendedSongs = cachedSongs.subList(0, Math.min(20, cachedSongs.size()));

        // 如果推荐的歌曲不足 20 首，则用随机歌曲填充
        if (recommendedSongs.size() < 20) {
            List<SongVO> randomSongs = songMapper.getRandomSongsWithArtist();
            Set<Long> addedSongIds = recommendedSongs.stream().map(SongVO::getSongId).collect(Collectors.toSet());
            for (SongVO song : randomSongs) {
                if (recommendedSongs.size() >= 20) break;
                if (!addedSongIds.contains(song.getSongId())) {
                    recommendedSongs.add(song);
                }
            }
        }

        return Result.success(recommendedSongs);
    }

    /**
     * 获取歌曲详情
     *
     * @param songId  歌曲id
     * @param request HttpServletRequest，用于获取请求头中的 token
     * @return 歌曲详情
     */
    @Override
    @Cacheable(key = "#songId")
    public Result<SongDetailVO> getSongDetail(Long songId, HttpServletRequest request) {
        SongDetailVO songDetailVO = songMapper.getSongDetailById(songId);
        // 补充：加载歌曲评论（带children）
        try {
            var commentService = this.commentService;
            if (commentService != null) {
                var comments = commentService.getSongComments(songId);
                if (comments != null && comments.getCode() == 0) {
                    songDetailVO.setComments(comments.getData());
                }
            }
        } catch (Exception ignored) {}

        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);  // 去掉 "Bearer " 前缀
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
                UserFavorite favoriteSong = userFavoriteMapper.selectOne(new QueryWrapper<UserFavorite>()
                        .eq("user_id", userId)
                        .eq("type", 0)
                        .eq("song_id", songId));
                if (favoriteSong != null) {
                    songDetailVO.setLikeStatus(LikeStatusEnum.LIKE.getId());
                }
            }
        }

        return Result.success(songDetailVO);
    }

    /** 获取某歌手已有专辑列表（去重） */
    @Override
    public Result<List<String>> getAlbumsByArtist(Long artistId) {
        List<Song> list = songMapper.selectList(new QueryWrapper<Song>()
                .select("album")
                .eq("artist_id", artistId)
                .isNotNull("album")
                .ne("album", ""));
        List<String> albums = list.stream()
                .map(Song::getAlbum)
                .filter(Objects::nonNull)
                .filter(a -> !a.isEmpty())
                .distinct()
                .toList();
        return Result.success(albums);
    }

    /** 按专辑ID获取歌曲列表（不缓存，避免数据变更后短期内拿到旧缓存） */
    @Override
    public Result<PageResult<SongVO>> getSongsByAlbumId(Long albumId, Integer pageNum, Integer pageSize) {
        Page<SongVO> page = new Page<>(pageNum, pageSize);
        IPage<SongVO> songPage = songMapper.getSongsByAlbumId(page, albumId);
        List<SongVO> list = songPage.getRecords().stream()
                .peek(s -> s.setLikeStatus(LikeStatusEnum.DEFAULT.getId()))
                .toList();
        return Result.success(new PageResult<>(songPage.getTotal(), list));
    }

    /**
     * 获取歌词（优先 lyric_url -> 否则 lyric 字段），解析 LRC 并缓存
     */
    @Override
    @Cacheable(cacheNames = "lyricCache", key = "'lyric-' + #songId")
    public Result<List<LyricLine>> getLyric(Long songId) {
        Song song = songMapper.selectById(songId);
        if (song == null) return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);

        String raw = null;
        if (song.getLyricUrl() != null && !song.getLyricUrl().isEmpty()) {
            raw = minioService.readText(song.getLyricUrl());
        }
        if ((raw == null || raw.isEmpty()) && song.getLyric() != null) {
            raw = song.getLyric();
        }
        List<LyricLine> lines = parseLrc(raw);
        return Result.success(lines);
    }

    private List<LyricLine> parseLrc(String lrc) {
        List<LyricLine> list = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return list;

        long globalOffset = 0;
        Matcher off = Pattern.compile("(?m)^\\[offset:(-?\\d+)]\\s*$").matcher(lrc);
        if (off.find()) globalOffset = Long.parseLong(off.group(1));

        // 允许毫秒为 1-3 位：如 [01:23.4] / [01:23.45] / [01:23.456]
        Pattern p = Pattern.compile("(?m)^((?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?])+)(.*)$");
        Matcher m = p.matcher(lrc);
        while (m.find()) {
            String tsGroup = m.group(1);
            String text = m.group(2).trim();
            Matcher tm = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]").matcher(tsGroup);
            while (tm.find()) {
                int min = Integer.parseInt(tm.group(1));
                int sec = Integer.parseInt(tm.group(2));
                String frac = tm.group(3);
                int fracLen = frac == null ? 0 : frac.length();
                int fracVal = frac == null ? 0 : Integer.parseInt(frac);
                long extraMs = 0;
                if (fracLen == 3) extraMs = fracVal;           // 毫秒
                else if (fracLen == 2) extraMs = fracVal * 10L; // 厘秒 -> 毫秒
                else if (fracLen == 1) extraMs = fracVal * 100L; // 十分之一秒 -> 毫秒
                long ms = min * 60_000L + sec * 1_000L + extraMs;
                list.add(new LyricLine(ms + globalOffset, text));
            }
        }
        list.sort(Comparator.comparingLong(LyricLine::getTimeMs));
        return list;
    }

    /**
     * 获取所有歌曲的数量
     *
     * @param style 歌曲风格
     * @return 歌曲数量
     */
    @Override
    public Result<Long> getAllSongsCount(String style) {
        QueryWrapper<Song> queryWrapper = new QueryWrapper<>();
        if (style != null) {
            queryWrapper.like("style", style);
        }

        return Result.success(songMapper.selectCount(queryWrapper));
    }

    /**
     * 添加歌曲信息
     *
     * @param songAddDTO 歌曲信息
     * @return 结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache", "albumCache"}, allEntries = true)
    public Result<String> addSong(SongAddDTO songAddDTO) {
        // 先解析/创建专辑，拿到 albumId
        Long resolvedAlbumId = ensureAlbumId(songAddDTO.getArtistId(), songAddDTO.getAlbum(), songAddDTO.getReleaseTime());

        Song song = new Song();
        BeanUtils.copyProperties(songAddDTO, song);
        song.setAlbumId(resolvedAlbumId);

        // 规则：专辑与歌曲的发行时间一致
        try {
            if (resolvedAlbumId != null) {
                Album album = albumMapper.selectById(resolvedAlbumId);
                // 若歌曲未填时间而专辑已填，则回显专辑时间到歌曲
                if (song.getReleaseTime() == null && album != null && album.getReleaseDate() != null) {
                    song.setReleaseTime(album.getReleaseDate());
                }
                // 若歌曲已填而专辑未填，则反向补齐专辑时间
                if (song.getReleaseTime() != null && album != null && album.getReleaseDate() == null) {
                    album.setReleaseDate(song.getReleaseTime());
                    try { albumMapper.updateById(album); } catch (Exception ignored) {}
                }
                // 若歌曲未上传封面而专辑有封面，则沿用专辑封面
                if ((song.getCoverUrl() == null || song.getCoverUrl().isEmpty())
                        && album != null && album.getCoverUrl() != null && !album.getCoverUrl().isEmpty()) {
                    song.setCoverUrl(album.getCoverUrl());
                }
            }
        } catch (Exception ignored) {}

        // 插入歌曲记录
        if (songMapper.insert(song) == 0) {
            return Result.error(MessageConstant.ADD + MessageConstant.FAILED);
        }

        // 获取刚插入的歌曲记录
        Song songInDB = songMapper.selectOne(new QueryWrapper<Song>()
                .eq("artist_id", songAddDTO.getArtistId())
                .eq("name", songAddDTO.getSongName())
                .eq("album", songAddDTO.getAlbum())
                .orderByDesc("id")
                .last("LIMIT 1"));

        if (songInDB == null) {
            return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);
        }

        Long songId = songInDB.getSongId();

        // 计算该歌曲对应的专辑id与专辑类型(style) id
        Long albumStyleId = null;
        if (resolvedAlbumId != null) {
            Album _album = albumMapper.selectById(resolvedAlbumId);
            if (_album != null && _album.getCategory() != null && !_album.getCategory().isBlank()) {
                Style albumStyle = styleMapper.selectOne(new QueryWrapper<Style>().eq("name", _album.getCategory()));
                if (albumStyle != null) albumStyleId = albumStyle.getStyleId();
            }
        }

        // 解析风格字段（多个风格以逗号分隔）
        String styleStr = songAddDTO.getStyle();
        if (styleStr != null && !styleStr.isEmpty()) {
            List<String> styles = Arrays.asList(styleStr.split(","));

            // 查询风格 ID
            List<Style> styleList = styleMapper.selectList(new QueryWrapper<Style>().in("name", styles));

            // 插入到 tb_genre
            for (Style style : styleList) {
                Genre genre = new Genre();
                genre.setSongId(songId);
                genre.setStyleId(style.getStyleId());
                genre.setAlbumId(resolvedAlbumId);
                genre.setAlbumStyleId(albumStyleId);
                genreMapper.insert(genre);
            }
        }

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌曲信息
     *
     * @param songUpdateDTO 歌曲信息
     * @return 结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache", "albumCache"}, allEntries = true)
    public Result<String> updateSong(SongUpdateDTO songUpdateDTO) {
        // 查询数据库中是否存在该歌曲
        Song songInDB = songMapper.selectById(songUpdateDTO.getSongId());
        if (songInDB == null) {
            return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);
        }

        // 先解析/创建专辑，拿到 albumId（若未填写专辑名则置空）
        Long resolvedAlbumId = ensureAlbumId(songUpdateDTO.getArtistId(), songUpdateDTO.getAlbum(), songUpdateDTO.getReleaseTime());

        // 更新歌曲基本信息
        Song song = new Song();
        BeanUtils.copyProperties(songUpdateDTO, song);
        song.setAlbumId(resolvedAlbumId);

        // 规则：同步专辑与歌曲发行时间
        try {
            if (resolvedAlbumId != null) {
                Album album = albumMapper.selectById(resolvedAlbumId);
                // 若本次更新歌曲未带时间且专辑有时间，保持歌曲与专辑一致
                if (song.getReleaseTime() == null && album != null && album.getReleaseDate() != null) {
                    song.setReleaseTime(album.getReleaseDate());
                }
                // 若本次更新歌曲带时间且专辑为空，则补齐专辑
                if (song.getReleaseTime() != null && album != null && album.getReleaseDate() == null) {
                    album.setReleaseDate(song.getReleaseTime());
                    try { albumMapper.updateById(album); } catch (Exception ignored) {}
                }
                // 若歌曲封面未设置而专辑有封面，则沿用专辑封面
                if ((song.getCoverUrl() == null || song.getCoverUrl().isEmpty())
                        && album != null && album.getCoverUrl() != null && !album.getCoverUrl().isEmpty()) {
                    song.setCoverUrl(album.getCoverUrl());
                }
            }
        } catch (Exception ignored) {}
        if (songMapper.updateById(song) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        Long songId = songUpdateDTO.getSongId();

        // 删除 tb_genre 中该歌曲的原有风格映射
        genreMapper.delete(new QueryWrapper<Genre>().eq("song_id", songId));

        // 解析新的风格字段（多个风格以逗号分隔）
        String styleStr = songUpdateDTO.getStyle();
        if (styleStr != null && !styleStr.isEmpty()) {
            List<String> styles = Arrays.asList(styleStr.split(","));

            // 查询风格 ID
            List<Style> styleList = styleMapper.selectList(new QueryWrapper<Style>().in("name", styles));

            // 计算该歌曲对应的专辑类型(style) id
            Long albumStyleId = null;
            if (resolvedAlbumId != null) {
                Album _album = albumMapper.selectById(resolvedAlbumId);
                if (_album != null && _album.getCategory() != null && !_album.getCategory().isBlank()) {
                    Style albumStyle = styleMapper.selectOne(new QueryWrapper<Style>().eq("name", _album.getCategory()));
                    if (albumStyle == null) {
                        albumStyle = new Style();
                        albumStyle.setName(_album.getCategory());
                        styleMapper.insert(albumStyle);
                    }
                    if (albumStyle != null) albumStyleId = albumStyle.getStyleId();
                }
            }
            if (resolvedAlbumId != null) {
                Album _album = albumMapper.selectById(resolvedAlbumId);
                if (_album != null && _album.getCategory() != null && !_album.getCategory().isBlank()) {
                    Style albumStyle = styleMapper.selectOne(new QueryWrapper<Style>().eq("name", _album.getCategory()));
                    if (albumStyle == null) {
                        albumStyle = new Style();
                        albumStyle.setName(_album.getCategory());
                        styleMapper.insert(albumStyle);
                    }
                    if (albumStyle != null) albumStyleId = albumStyle.getStyleId();
                }
            }

            // 插入新的风格映射到 tb_genre
            for (Style style : styleList) {
                Genre genre = new Genre();
                genre.setSongId(songId);
                genre.setStyleId(style.getStyleId());
                genre.setAlbumId(resolvedAlbumId);
                genre.setAlbumStyleId(albumStyleId);
                genreMapper.insert(genre);
            }
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 如果该歌手名下不存在同名专辑，则创建一条最简专辑记录
     */
    private Long ensureAlbumId(Long artistId, String albumTitle, java.time.LocalDate releaseDate) {
        if (artistId == null || albumTitle == null || albumTitle.isEmpty()) {
            return null;
        }
        Album exist = albumMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Album>()
                .eq("artist_id", artistId)
                .eq("title", albumTitle)
                .last("LIMIT 1"));
        if (exist != null) return exist.getAlbumId();

        Album album = new Album();
        album.setArtistId(artistId);
        album.setTitle(albumTitle);
        if (releaseDate != null) album.setReleaseDate(releaseDate);
        albumMapper.insert(album);
        return album.getAlbumId();
    }

    /**
     * 更新歌曲封面
     *
     * @param songId   歌曲id
     * @param coverUrl 封面url
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
    public Result<String> updateSongCover(Long songId, String coverUrl) {
        Song song = songMapper.selectById(songId);
        String cover = song.getCoverUrl();
        if (cover != null && !cover.isEmpty()) {
            minioService.deleteFile(cover);
        }

        song.setCoverUrl(coverUrl);
        if (songMapper.updateById(song) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        // 同步：若歌曲所属专辑存在且专辑未设置封面，则回填专辑封面
        try {
            if (song.getAlbumId() != null) {
                Album album = albumMapper.selectById(song.getAlbumId());
                if (album != null && (album.getCoverUrl() == null || album.getCoverUrl().isEmpty())) {
                    album.setCoverUrl(coverUrl);
                    albumMapper.updateById(album);
                }
            }
        } catch (Exception ignored) {}

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌曲音频
     *
     * @param songId   歌曲id
     * @param audioUrl 音频url
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
    public Result<String> updateSongAudio(Long songId, String audioUrl, org.springframework.web.multipart.MultipartFile audioFile) {
        Song song = songMapper.selectById(songId);
        String audio = song.getAudioUrl();
        if (audio != null && !audio.isEmpty()) {
            minioService.deleteFile(audio);
        }

        song.setAudioUrl(audioUrl);
        // 提取音频时长（秒）并写入 duration（支持 mp3、flac 等）
        try {
            String seconds = cn.edu.seig.vibemusic.util.AudioDurationUtil.extractDurationSeconds(audioFile);
            if (seconds != null && !seconds.isEmpty()) {
                long s;
                try { s = Long.parseLong(seconds); } catch (NumberFormatException e) { s = -1L; }
                if (s < 0) {
                    s = Math.abs(s);
                }
                // 合理上限（例如 4 小时），防止异常大值污染
                long max = 4L * 60L * 60L;
                if (s > max) {
                    s = max;
                }
                song.setDuration(String.valueOf(s));
            }
        } catch (Exception ignored) {}
        if (songMapper.updateById(song) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌曲歌词URL，并清理歌词缓存
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "lyricCache"}, key = "'lyric-' + #songId", allEntries = false)
    public Result<String> updateSongLyric(Long songId, String lyricUrl) {
        Song song = songMapper.selectById(songId);
        if (song == null) {
            return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);
        }
        song.setLyricUrl(lyricUrl);
        // 可选：清空旧内嵌歌词字段
        // song.setLyric(null);
        if (songMapper.updateById(song) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }
        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 删除歌曲
     *
     * @param songId 歌曲id
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
    public Result<String> deleteSong(Long songId) {
        Song song = songMapper.selectById(songId);
        if (song == null) {
            return Result.error(MessageConstant.SONG + MessageConstant.NOT_FOUND);
        }
        String cover = song.getCoverUrl();
        String audio = song.getAudioUrl();
        String lyric = song.getLyricUrl();

        if (cover != null && !cover.isEmpty()) {
            minioService.deleteFile(cover);
        }
        if (audio != null && !audio.isEmpty()) {
            minioService.deleteFile(audio);
        }
        if (lyric != null && !lyric.isEmpty()) {
            minioService.deleteFile(lyric);
        }

        // 关联清理：tb_genre、歌单绑定、评论(歌曲)、用户收藏(歌曲)
        genreMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Genre>().eq("song_id", songId));
        playlistBindingMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.PlaylistBinding>().eq("song_id", songId));
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>().eq("song_id", songId).eq("type", 0));
        userFavoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 0).eq("song_id", songId));

        if (songMapper.deleteById(songId) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }

        try { cachePurger.purgeForSong(songId); } catch (Exception ignored) {}
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /**
     * 批量删除歌曲
     *
     * @param songIds 歌曲id列表
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = {"songCache", "artistCache"}, allEntries = true)
    public Result<String> deleteSongs(List<Long> songIds) {
        // 1. 查询歌曲信息，获取歌曲相关 URL 列表
        List<Song> songs = songMapper.selectByIds(songIds);
        List<String> coverUrlList = songs.stream()
                .map(Song::getCoverUrl)
                .filter(coverUrl -> coverUrl != null && !coverUrl.isEmpty())
                .toList();
        List<String> audioUrlList = songs.stream()
                .map(Song::getAudioUrl)
                .filter(audioUrl -> audioUrl != null && !audioUrl.isEmpty())
                .toList();
        List<String> lyricUrlList = songs.stream()
                .map(Song::getLyricUrl)
                .filter(u -> u != null && !u.isEmpty())
                .toList();

        // 2. 先删除 MinIO 里的歌曲封面、音频、歌词文件
        for (String coverUrl : coverUrlList) {
            minioService.deleteFile(coverUrl);
        }
        for (String audioUrl : audioUrlList) {
            minioService.deleteFile(audioUrl);
        }
        for (String u : lyricUrlList) {
            minioService.deleteFile(u);
        }

        // 2.5 关联清理：tb_genre、歌单绑定、评论(歌曲)、用户收藏(歌曲)
        genreMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Genre>().in("song_id", songIds));
        playlistBindingMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.PlaylistBinding>().in("song_id", songIds));
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Comment>().in("song_id", songIds).eq("type", 0));
        userFavoriteMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.UserFavorite>().eq("type", 0).in("song_id", songIds));

        // 3. 删除数据库中的歌曲信息
        if (songMapper.deleteByIds(songIds) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }

        try { cachePurger.purgeForSong(null); } catch (Exception ignored) {}
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }


    /**
     * 批量导入歌曲（支持文件上传）
     *
     * @param artistId 歌手ID
     * @param albumName 专辑名称
     * @param songNames 歌曲名称列表（逗号分隔）
     * @param songStyles 歌曲风格列表（逗号分隔）
     * @param audioFiles 音频文件列表
     * @param lyricFiles 歌词文件列表
     * @return 导入结果
     */
    @Override
    @CacheEvict(allEntries = true)
    public Result<SongBatchImportResultVO> batchImportSongsWithFiles(Long artistId, String albumName, String songNames, String songStyles,
                                                                    List<org.springframework.web.multipart.MultipartFile> audioFiles,
                                                                    List<org.springframework.web.multipart.MultipartFile> lyricFiles) {
        
        // 验证歌手是否存在
        cn.edu.seig.vibemusic.model.entity.Artist artist = artistMapper.selectById(artistId);
        if (artist == null) {
            return Result.error("歌手不存在");
        }

        // 解析歌曲名称
        String[] songNameArray = songNames.split(",");
        List<String> songNameList = new ArrayList<>();
        for (String name : songNameArray) {
            String trimmedName = name.trim();
            if (!trimmedName.isEmpty()) {
                songNameList.add(trimmedName);
            }
        }

        // 解析歌曲风格
        String[] songStyleArray = songStyles.split(",");
        List<String> songStyleList = new ArrayList<>();
        for (String style : songStyleArray) {
            String trimmedStyle = style.trim();
            if (!trimmedStyle.isEmpty()) {
                songStyleList.add(trimmedStyle);
            }
        }

        if (songNameList.isEmpty()) {
            return Result.error("歌曲名称不能为空");
        }

        if (songStyleList.isEmpty()) {
            return Result.error("歌曲风格不能为空");
        }

        if (songNameList.size() != songStyleList.size()) {
            return Result.error("歌曲名称数量与歌曲风格数量不匹配");
        }

        if (audioFiles == null || audioFiles.size() != songNameList.size()) {
            return Result.error("音频文件数量与歌曲名称数量不匹配");
        }

        List<SongBatchImportResultVO.ImportFailureItem> failures = new ArrayList<>();
        int successCount = 0;
        int totalCount = songNameList.size();

        // 获取专辑发行日期（从现有歌曲中获取，如果没有则使用当前日期）
        java.time.LocalDate albumReleaseDate = getAlbumReleaseDate(artistId, albumName);
        
        // 获取或创建专辑ID
        Long albumId = ensureAlbumId(artistId, albumName.trim(), albumReleaseDate);

        for (int i = 0; i < songNameList.size(); i++) {
            String songName = songNameList.get(i);
            String songStyle = songStyleList.get(i);
            org.springframework.web.multipart.MultipartFile audioFile = audioFiles.get(i);
            org.springframework.web.multipart.MultipartFile lyricFile = (lyricFiles != null && i < lyricFiles.size()) ? lyricFiles.get(i) : null;

            try {
                // 验证必填字段
                if (songName == null || songName.trim().isEmpty()) {
                    failures.add(createFailureItem(i + 1, songName, "歌名不能为空"));
                    continue;
                }

                // 检查歌曲是否已存在
                QueryWrapper<Song> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("artist_id", artistId)
                           .eq("name", songName.trim());
                Song existingSong = songMapper.selectOne(queryWrapper);
                if (existingSong != null) {
                    failures.add(createFailureItem(i + 1, songName, "歌曲已存在"));
                    continue;
                }

                // 验证音频文件
                if (audioFile == null || audioFile.isEmpty()) {
                    failures.add(createFailureItem(i + 1, songName, "音频文件不能为空"));
                    continue;
                }

                String audioFileName = audioFile.getOriginalFilename();
                if (audioFileName == null || (!audioFileName.toLowerCase().endsWith(".mp3") && !audioFileName.toLowerCase().endsWith(".flac"))) {
                    failures.add(createFailureItem(i + 1, songName, "音频文件格式不正确，仅支持MP3和FLAC格式"));
                    continue;
                }

                // 上传音频文件
                String audioUrl = minioService.uploadFile(audioFile, "songs");
                
                // 上传歌词文件（如果存在）
                String lyricUrl = "";
                if (lyricFile != null && !lyricFile.isEmpty()) {
                    String lyricFileName = lyricFile.getOriginalFilename();
                    if (lyricFileName != null && lyricFileName.toLowerCase().endsWith(".lrc")) {
                        lyricUrl = minioService.uploadFile(lyricFile, "songLyrics");
                    }
                }

                // 获取音频时长
                String duration = "";
                try {
                    duration = getAudioDuration(audioFile);
                } catch (Exception e) {
                    // 如果无法获取时长，使用默认值
                    duration = "00:00";
                }

                // 获取专辑封面URL
                String albumCoverUrl = getAlbumCoverUrl(artistId, albumName.trim());
                
                // 创建歌曲实体
                Song song = new Song();
                song.setArtistId(artistId);
                song.setSongName(songName.trim());
                song.setAlbum(albumName.trim());
                song.setAlbumId(albumId); // 设置专辑ID
                song.setStyle(songStyle.trim());
                song.setReleaseTime(albumReleaseDate);
                song.setDuration(duration);
                song.setCoverUrl(albumCoverUrl); // 使用专辑封面
                song.setAudioUrl(audioUrl);
                song.setLyricUrl(lyricUrl);

                // 保存歌曲
                if (songMapper.insert(song) > 0) {
                    successCount++;
                } else {
                    failures.add(createFailureItem(i + 1, songName, "保存失败"));
                }

            } catch (Exception e) {
                failures.add(createFailureItem(i + 1, songName, "导入异常：" + e.getMessage()));
            }
        }

        // 构建返回结果
        SongBatchImportResultVO result = new SongBatchImportResultVO();
        result.setTotalCount(totalCount);
        result.setSuccessCount(successCount);
        result.setFailureCount(failures.size());
        result.setFailures(failures);

        // 清理缓存
        try { cachePurger.purgeForSong(null); } catch (Exception ignored) {}

        return Result.success("批量导入完成", result);
    }

    /**
     * 获取专辑发行日期
     */
    private java.time.LocalDate getAlbumReleaseDate(Long artistId, String albumName) {
        try {
            // 从现有歌曲中获取专辑的发行日期
            QueryWrapper<Song> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("artist_id", artistId)
                       .eq("album", albumName)
                       .isNotNull("release_time")
                       .orderByAsc("release_time")
                       .last("limit 1");
            Song song = songMapper.selectOne(queryWrapper);
            if (song != null && song.getReleaseTime() != null) {
                return song.getReleaseTime();
            }
        } catch (Exception e) {
            // 忽略异常，使用默认日期
        }
        // 如果没有找到，使用当前日期
        return java.time.LocalDate.now();
    }

    /**
     * 获取专辑封面URL
     */
    private String getAlbumCoverUrl(Long artistId, String albumName) {
        try {
            // 首先从专辑表中查找封面
            QueryWrapper<Album> albumQuery = new QueryWrapper<>();
            albumQuery.eq("artist_id", artistId)
                     .eq("title", albumName)
                     .isNotNull("cover_url")
                     .ne("cover_url", "")
                     .last("limit 1");
            Album album = albumMapper.selectOne(albumQuery);
            if (album != null && album.getCoverUrl() != null && !album.getCoverUrl().isEmpty()) {
                return album.getCoverUrl();
            }
            
            // 如果专辑表中没有，从现有歌曲中查找
            QueryWrapper<Song> songQuery = new QueryWrapper<>();
            songQuery.eq("artist_id", artistId)
                    .eq("album", albumName)
                    .isNotNull("cover_url")
                    .ne("cover_url", "")
                    .orderByDesc("id")
                    .last("limit 1");
            Song song = songMapper.selectOne(songQuery);
            if (song != null && song.getCoverUrl() != null && !song.getCoverUrl().isEmpty()) {
                return song.getCoverUrl();
            }
        } catch (Exception e) {
            // 忽略异常，返回空字符串
        }
        // 如果没有找到，返回空字符串
        return "";
    }

    /**
     * 获取音频文件时长
     */
    private String getAudioDuration(org.springframework.web.multipart.MultipartFile audioFile) {
        try {
            // 使用音频时长工具类获取真实的音频时长（秒）
            String seconds = cn.edu.seig.vibemusic.util.AudioDurationUtil.extractDurationSeconds(audioFile);
            if (seconds != null && !seconds.isEmpty()) {
                long s;
                try { 
                    s = Long.parseLong(seconds); 
                } catch (NumberFormatException e) { 
                    s = 0L; 
                }
                if (s < 0) {
                    s = Math.abs(s);
                }
                // 合理上限（例如 4 小时），防止异常大值污染
                long max = 4L * 60L * 60L;
                if (s > max) {
                    s = max;
                }
                return String.valueOf(s);
            }
        } catch (Exception e) {
            // 如果解析失败，返回默认值
        }
        return "0"; // 默认0秒
    }

    /**
     * 创建失败项
     */
    private SongBatchImportResultVO.ImportFailureItem createFailureItem(int rowNumber, String songName, String reason) {
        SongBatchImportResultVO.ImportFailureItem failure = new SongBatchImportResultVO.ImportFailureItem();
        failure.setRowNumber(rowNumber);
        failure.setSongName(songName != null ? songName : "");
        failure.setReason(reason);
        return failure;
    }

}
