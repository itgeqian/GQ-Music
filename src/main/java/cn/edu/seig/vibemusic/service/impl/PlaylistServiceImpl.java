package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.JwtClaimsConstant;
import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.enumeration.LikeStatusEnum;
import cn.edu.seig.vibemusic.enumeration.RoleEnum;
import cn.edu.seig.vibemusic.mapper.PlaylistMapper;
import cn.edu.seig.vibemusic.mapper.UserFavoriteMapper;
import cn.edu.seig.vibemusic.model.dto.PlaylistAddDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistUpdateDTO;
import cn.edu.seig.vibemusic.model.entity.Playlist;
import cn.edu.seig.vibemusic.model.entity.UserFavorite;
import cn.edu.seig.vibemusic.model.vo.PlaylistDetailVO;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IPlaylistService;
import cn.edu.seig.vibemusic.service.ICommentService;
import cn.edu.seig.vibemusic.service.MinioService;
import cn.edu.seig.vibemusic.service.IPlaylistBindingService;
import cn.edu.seig.vibemusic.mapper.PlaylistRecommendationMapper;
import cn.edu.seig.vibemusic.model.entity.PlaylistRecommendation;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Service
@CacheConfig(cacheNames = "playlistCache")
public class PlaylistServiceImpl extends ServiceImpl<PlaylistMapper, Playlist> implements IPlaylistService {

    @Autowired
    private PlaylistMapper playlistMapper;
    @Autowired
    private UserFavoriteMapper userFavoriteMapper;
    @Autowired
    private MinioService minioService;
    @Autowired
    private IPlaylistBindingService playlistBindingService;
    @Autowired
    private PlaylistRecommendationMapper playlistRecommendationMapper;
    @Autowired
    private cn.edu.seig.vibemusic.util.CachePurger cachePurger;
    @Autowired
    private ICommentService commentService;

    /**
     * 获取所有歌单
     *
     * @param playlistDTO playlistDTO
     * @return 歌单列表
     */
    @Override
    @Cacheable(key = "#playlistDTO.pageNum + '-' + #playlistDTO.pageSize + '-' + #playlistDTO.title + '-' + #playlistDTO.style")
    public Result<PageResult<PlaylistVO>> getAllPlaylists(PlaylistDTO playlistDTO) {
        // 分页查询
        Page<Playlist> page = new Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        QueryWrapper<Playlist> queryWrapper = new QueryWrapper<>();
        // 仅展示官方歌单（管理员创建）：用户歌单不应出现在“歌单管理”
        queryWrapper.isNull("user_id");
        // 根据 playlistDTO 的条件构建查询条件
        if (playlistDTO.getTitle() != null) {
            queryWrapper.like("title", playlistDTO.getTitle());
        }
        if (playlistDTO.getStyle() != null) {
            queryWrapper.eq("style", playlistDTO.getStyle());
        }

        IPage<Playlist> playlistPage = playlistMapper.selectPage(page, queryWrapper);
        if (playlistPage.getRecords().size() == 0) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        // 转换为 PlaylistVO
        List<PlaylistVO> playlistVOList = playlistPage.getRecords().stream()
                .map(playlist -> {
                    PlaylistVO playlistVO = new PlaylistVO();
                    BeanUtils.copyProperties(playlist, playlistVO);
                    return playlistVO;
                }).toList();

        return Result.success(new PageResult<>(playlistPage.getTotal(), playlistVOList));
    }

    /**
     * 获取所有歌单信息
     *
     * @param playlistDTO playlistDTO
     * @return 歌单列表
     */
    @Override
    @Cacheable(key = "#playlistDTO.pageNum + '-' + #playlistDTO.pageSize + '-' + #playlistDTO.title + '-' + #playlistDTO.style + '-admin'")
    public Result<PageResult<Playlist>> getAllPlaylistsInfo(PlaylistDTO playlistDTO) {
        // 分页查询
        Page<Playlist> page = new Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        QueryWrapper<Playlist> queryWrapper = new QueryWrapper<>();
        // 仅展示官方歌单（管理员创建），排除用户歌单
        queryWrapper.isNull("user_id");
        // 根据 playlistDTO 的条件构建查询条件
        if (playlistDTO.getTitle() != null) {
            queryWrapper.like("title", playlistDTO.getTitle());
        }
        if (playlistDTO.getStyle() != null) {
            queryWrapper.eq("style", playlistDTO.getStyle());
        }
        // 倒序排序
        queryWrapper.orderByDesc("id");

        IPage<Playlist> playlistPage = playlistMapper.selectPage(page, queryWrapper);
        if (playlistPage.getRecords().size() == 0) {
            return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
        }

        return Result.success(new PageResult<>(playlistPage.getTotal(), playlistPage.getRecords()));
    }

    /**
     * 获取推荐歌单
     * 推荐歌单的数量为 10
     *
     * @param request HttpServletRequest，用于获取请求头中的 token
     * @return 随机歌单列表
     */
    @Override
    public Result<List<PlaylistVO>> getRecommendedPlaylists(HttpServletRequest request) {
        // 获取请求头中的 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);  // 去掉 "Bearer " 前缀
        }

        Map<String, Object> map = null;
        if (token != null && !token.isEmpty()) {
            map = JwtUtil.parseToken(token);
        }

        Long userId = null;
        if (map != null) {
            String role = (String) map.get(JwtClaimsConstant.ROLE);
            if (role.equals(RoleEnum.USER.getRole())) {
                Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
                userId = TypeConversionUtil.toLong(userIdObj);
            }
        }

        // 固定推荐优先（常驻）
        List<PlaylistVO> pinned = playlistMapper.getPinnedRecommendedPlaylists(10);
        int remain = Math.max(0, 10 - pinned.size());

        if (userId == null) {
            // 未登录：固定推荐 + 随机补足
            if (remain == 0) return Result.success(pinned);
            List<PlaylistVO> random = playlistMapper.getRandomPlaylists(remain);
            java.util.LinkedHashMap<Long, PlaylistVO> mapVo = new java.util.LinkedHashMap<>();
            for (PlaylistVO vo : pinned) mapVo.put(vo.getPlaylistId(), vo);
            for (PlaylistVO vo : random) mapVo.putIfAbsent(vo.getPlaylistId(), vo);
            return Result.success(new java.util.ArrayList<>(mapVo.values()));
        }

        // 获取用户收藏的歌单 ID
        List<Long> favoritePlaylistIds = userFavoriteMapper.getFavoritePlaylistIdsByUserId(userId);
        if (favoritePlaylistIds.isEmpty()) {
            // 固定推荐 + 随机补足
            if (remain == 0) return Result.success(pinned);
            List<PlaylistVO> random = playlistMapper.getRandomPlaylists(remain);
            java.util.LinkedHashMap<Long, PlaylistVO> mapVo = new java.util.LinkedHashMap<>();
            for (PlaylistVO vo : pinned) mapVo.put(vo.getPlaylistId(), vo);
            for (PlaylistVO vo : random) mapVo.putIfAbsent(vo.getPlaylistId(), vo);
            return Result.success(new java.util.ArrayList<>(mapVo.values()));
        }

        // 查询用户收藏的歌单风格并统计频率
        List<String> favoriteStyles = playlistMapper.getFavoritePlaylistStyles(favoritePlaylistIds);
        List<Long> favoriteStyleIds = userFavoriteMapper.getFavoriteIdsByStyle(favoriteStyles);
        Map<Long, Long> styleFrequency = favoriteStyleIds.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // 按风格出现次数降序排序
        List<Long> sortedStyleIds = styleFrequency.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 根据排序后的风格推荐歌单（排除已收藏歌单）
        List<PlaylistVO> personalized = playlistMapper.getRecommendedPlaylistsByStyles(sortedStyleIds, favoritePlaylistIds, Math.max(0, 10 - pinned.size()));

        // 组装：固定推荐常驻 + 个性化补足（去重）
        java.util.LinkedHashMap<Long, PlaylistVO> mapVo = new java.util.LinkedHashMap<>();
        for (PlaylistVO vo : pinned) mapVo.put(vo.getPlaylistId(), vo);
        for (PlaylistVO vo : personalized) {
            if (mapVo.size() >= 10) break;
            mapVo.putIfAbsent(vo.getPlaylistId(), vo);
        }
        // 若仍不足再随机填充
        while (mapVo.size() < 10) {
            List<PlaylistVO> random = playlistMapper.getRandomPlaylists(10);
            for (PlaylistVO vo : random) {
                if (mapVo.size() >= 10) break;
                mapVo.putIfAbsent(vo.getPlaylistId(), vo);
            }
            break;
        }
        return Result.success(new java.util.ArrayList<>(mapVo.values()));
    }

    /**
     * 获取歌单详情
     *
     * @param playlistId 歌单id
     * @param request    HttpServletRequest，用于获取请求头中的 token
     * @return 歌单详情
     */
    @Override
    @Cacheable(key = "#playlistId")
    public Result<PlaylistDetailVO> getPlaylistDetail(Long playlistId, HttpServletRequest request) {
        PlaylistDetailVO playlistDetailVO = playlistMapper.getPlaylistDetailById(playlistId);
        if (playlistDetailVO == null) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        }

        // 设置默认状态，兜底空集合
        List<SongVO> songVOList = playlistDetailVO.getSongs();
        if (songVOList == null) {
            songVOList = new ArrayList<>();
            playlistDetailVO.setSongs(songVOList);
        }
        songVOList.forEach(songVO -> songVO.setLikeStatus(LikeStatusEnum.DEFAULT.getId()));
        playlistDetailVO.setLikeStatus(LikeStatusEnum.DEFAULT.getId());

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
            if (RoleEnum.USER.getRole().equals(role)) {
                Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
                Long userId = TypeConversionUtil.toLong(userIdObj);

                // 获取用户收藏的歌单
                UserFavorite favoritePlaylist = userFavoriteMapper.selectOne(new QueryWrapper<UserFavorite>()
                        .eq("user_id", userId)
                        .eq("type", 1)
                        .eq("playlist_id", playlistId));
                if (favoritePlaylist != null) {
                    playlistDetailVO.setLikeStatus(LikeStatusEnum.LIKE.getId());
                }

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

        // 追加：加载歌单评论（树形）
        try {
            var comments = commentService.getPlaylistComments(playlistId);
            if (comments != null && comments.getCode() == 0) {
                playlistDetailVO.setComments(comments.getData());
            }
        } catch (Exception ignored) {}

        return Result.success(playlistDetailVO);
    }

    /**
     * 获取所有歌单数量
     *
     * @param style 歌单风格
     * @return 歌单数量
     */
    @Override
    public Result<Long> getAllPlaylistsCount(String style) {
        QueryWrapper<Playlist> queryWrapper = new QueryWrapper<>();
        if (style != null) {
            queryWrapper.eq("style", style);
        }

        return Result.success(playlistMapper.selectCount(queryWrapper));
    }

    /**
     * 添加歌单
     *
     * @param playlistAddDTOO 歌单DTO
     * @return 添加结果
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result addPlaylist(PlaylistAddDTO playlistAddDTOO) {
        QueryWrapper<Playlist> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("title", playlistAddDTOO.getTitle());
        if (playlistMapper.selectCount(queryWrapper) > 0) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.ALREADY_EXISTS);
        }

        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistAddDTOO, playlist);
        playlistMapper.insert(playlist);

        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌单
     *
     * @param playlistUpdateDTO 歌单更新DTO
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result updatePlaylist(PlaylistUpdateDTO playlistUpdateDTO) {
        Long playlistId = playlistUpdateDTO.getPlaylistId();

        Playlist playlistByTitle = playlistMapper.selectOne(new QueryWrapper<Playlist>().eq("title", playlistUpdateDTO.getTitle()));
        if (playlistByTitle != null && !playlistByTitle.getPlaylistId().equals(playlistId)) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.ALREADY_EXISTS);
        }

        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistUpdateDTO, playlist);
        if (playlistMapper.updateById(playlist) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 更新歌单封面
     *
     * @param playlistId 歌单id
     * @param coverUrl   歌单封面url
     * @return 更新结果
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result updatePlaylistCover(Long playlistId, String coverUrl) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        String cover = playlist.getCoverUrl();
        if (cover != null && !cover.isEmpty()) {
            minioService.deleteFile(cover);
        }

        playlist.setCoverUrl(coverUrl);
        if (playlistMapper.updateById(playlist) == 0) {
            return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        }

        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    /**
     * 删除歌单
     *
     * @param playlistId 歌单id
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result deletePlaylist(Long playlistId) {
        // 1. 查询歌单信息，获取封面 URL
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        }
        String coverUrl = playlist.getCoverUrl();

        // 2. 先删除 MinIO 里的封面文件
        if (coverUrl != null && !coverUrl.isEmpty()) {
            minioService.deleteFile(coverUrl);
        }

        // 3. 删除数据库中的歌单信息
        if (playlistMapper.deleteById(playlistId) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }

        try { cachePurger.purgeForPlaylist(playlistId); } catch (Exception ignored) {}
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    /**
     * 批量删除歌单
     *
     * @param playlistIds 歌单id列表
     * @return 删除结果
     */
    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result deletePlaylists(List<Long> playlistIds) {
        List<Playlist> playlists = playlistMapper.selectBatchIds(playlistIds);
        List<String> coverUrlList = playlists.stream()
                .map(Playlist::getCoverUrl)
                .filter(coverUrl -> coverUrl != null && !coverUrl.isEmpty())
                .toList();

        // 2. 先删除 MinIO 里的封面文件
        for (String coverUrl : coverUrlList) {
            minioService.deleteFile(coverUrl);
        }

        // 3. 删除数据库中的歌单信息
        if (playlistMapper.deleteBatchIds(playlistIds) == 0) {
            return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        }
        try { cachePurger.purgeForPlaylist(null); } catch (Exception ignored) {}
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    // =================== 用户侧：我的歌单 ===================

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> addUserPlaylist(PlaylistAddDTO playlistAddDTO) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }

        // 用户内重名校验
        QueryWrapper<Playlist> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("title", playlistAddDTO.getTitle());
        if (playlistMapper.selectCount(queryWrapper) > 0) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.ALREADY_EXISTS);
        }

        Playlist playlist = new Playlist();
        BeanUtils.copyProperties(playlistAddDTO, playlist);
        playlist.setUserId(userId);
        playlistMapper.insert(playlist);
        return Result.success(MessageConstant.ADD + MessageConstant.SUCCESS);
    }

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> updateUserPlaylist(PlaylistUpdateDTO playlistUpdateDTO) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }

        Long playlistId = playlistUpdateDTO.getPlaylistId();
        Playlist exist = playlistMapper.selectById(playlistId);
        if (exist == null) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        }
        if (exist.getUserId() == null || !exist.getUserId().equals(userId)) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }

        // 用户内重名校验（排除自身）
        Playlist sameTitle = playlistMapper.selectOne(new QueryWrapper<Playlist>()
                .eq("user_id", userId)
                .eq("title", playlistUpdateDTO.getTitle()));
        if (sameTitle != null && !sameTitle.getPlaylistId().equals(playlistId)) {
            return Result.error(MessageConstant.PLAYLIST + MessageConstant.ALREADY_EXISTS);
        }

        Playlist toUpdate = new Playlist();
        BeanUtils.copyProperties(playlistUpdateDTO, toUpdate);
        int n = playlistMapper.updateById(toUpdate);
        if (n == 0) return Result.error(MessageConstant.UPDATE + MessageConstant.FAILED);
        return Result.success(MessageConstant.UPDATE + MessageConstant.SUCCESS);
    }

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> deleteUserPlaylist(Long playlistId) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }

        Playlist exist = playlistMapper.selectById(playlistId);
        if (exist == null) return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        if (exist.getUserId() == null || !exist.getUserId().equals(userId)) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }

        // 删除封面文件
        String coverUrl = exist.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            minioService.deleteFile(coverUrl);
        }

        // 删除歌单本身
        int n = playlistMapper.deleteById(playlistId);
        if (n == 0) return Result.error(MessageConstant.DELETE + MessageConstant.FAILED);
        return Result.success(MessageConstant.DELETE + MessageConstant.SUCCESS);
    }

    @Override
    public Result<PageResult<PlaylistVO>> getMyPlaylists(PlaylistDTO playlistDTO) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }

        Page<Playlist> page = new Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        QueryWrapper<Playlist> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        if (playlistDTO.getTitle() != null && !playlistDTO.getTitle().isEmpty()) {
            qw.like("title", playlistDTO.getTitle());
        }
        if (playlistDTO.getStyle() != null && !playlistDTO.getStyle().isEmpty()) {
            qw.eq("style", playlistDTO.getStyle());
        }
        qw.orderByDesc("id");

        IPage<Playlist> p = playlistMapper.selectPage(page, qw);
        List<PlaylistVO> items = (p.getRecords() == null ? java.util.List.<Playlist>of() : p.getRecords()).stream().map(pl -> {
            PlaylistVO vo = new PlaylistVO();
            BeanUtils.copyProperties(pl, vo);
            return vo;
        }).toList();
        return Result.success(new PageResult<>(p.getTotal(), items));
    }

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> addSongToMyPlaylist(Long playlistId, Long songId) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }
        Playlist exist = playlistMapper.selectById(playlistId);
        if (exist == null) return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        if (exist.getUserId() == null || !exist.getUserId().equals(userId)) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }
        return playlistBindingService.addSongs(playlistId, java.util.List.of(songId));
    }

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> removeSongFromMyPlaylist(Long playlistId, Long songId) {
        Map<String, Object> map = cn.edu.seig.vibemusic.util.ThreadLocalUtil.get();
        Object userIdObj = map.get(JwtClaimsConstant.USER_ID);
        Long userId = cn.edu.seig.vibemusic.util.TypeConversionUtil.toLong(userIdObj);
        if (userId == null) {
            return Result.error(MessageConstant.NOT_LOGIN);
        }
        Playlist exist = playlistMapper.selectById(playlistId);
        if (exist == null) return Result.error(MessageConstant.PLAYLIST + MessageConstant.NOT_FOUND);
        if (exist.getUserId() == null || !exist.getUserId().equals(userId)) {
            return Result.error(MessageConstant.NO_PERMISSION);
        }
        return playlistBindingService.removeSongs(playlistId, java.util.List.of(songId));
    }

    // ================= 管理端：用户歌单只读分页 =================
    @Override
    public Result<PageResult<PlaylistVO>> getUserPlaylistsOnly(PlaylistDTO playlistDTO) {
        Page<Playlist> page = new Page<>(playlistDTO.getPageNum(), playlistDTO.getPageSize());
        QueryWrapper<Playlist> qw = new QueryWrapper<>();
        // user_id 不为空表示由用户创建
        qw.isNotNull("user_id");
        if (playlistDTO.getTitle() != null && !playlistDTO.getTitle().isEmpty()) {
            qw.like("title", playlistDTO.getTitle());
        }
        if (playlistDTO.getStyle() != null && !playlistDTO.getStyle().isEmpty()) {
            qw.eq("style", playlistDTO.getStyle());
        }
        qw.orderByDesc("id");
        IPage<Playlist> p = playlistMapper.selectPage(page, qw);
        java.util.List<PlaylistVO> items = (p.getRecords() == null ? java.util.List.<Playlist>of() : p.getRecords()).stream().map(pl -> {
            PlaylistVO vo = new PlaylistVO();
            BeanUtils.copyProperties(pl, vo);
            return vo;
        }).toList();
        return Result.success(new PageResult<>(p.getTotal(), items));
    }

    // ================= 管理端：推荐开关 =================
    @Override
    @CacheEvict(cacheNames = {"playlistCache"}, allEntries = true)
    public Result<String> recommendPlaylist(Long playlistId, Integer weight) {
        if (weight == null) weight = 100;
        PlaylistRecommendation rec = new PlaylistRecommendation();
        rec.setPlaylistId(playlistId);
        rec.setWeight(weight);
        rec.setCreateTime(java.time.LocalDateTime.now());
        // created_by 可从 ThreadLocal 取 adminId（此处省略）
        // 唯一约束交给数据库，失败则更新
        try {
            playlistRecommendationMapper.insert(rec);
        } catch (Exception e) {
            // 存在则更新权重
            playlistRecommendationMapper.update(rec,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PlaylistRecommendation>()
                            .eq("playlist_id", playlistId)
                            .set("weight", weight));
        }
        return Result.success("设置为推荐成功");
    }

    @Override
    @CacheEvict(cacheNames = {"playlistCache"}, allEntries = true)
    public Result<String> cancelRecommendPlaylist(Long playlistId) {
        playlistRecommendationMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PlaylistRecommendation>()
                .eq("playlist_id", playlistId));
        return Result.success("取消推荐成功");
    }

    @Override
    public Result<List<PlaylistVO>> getPinnedRecommendedPlaylists(Integer limit) {
        return Result.success(playlistMapper.getPinnedRecommendedPlaylists(limit == null ? 100 : limit));
    }
}
