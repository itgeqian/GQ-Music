package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.PlaylistAddDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistUpdateDTO;
import cn.edu.seig.vibemusic.model.entity.Playlist;
import cn.edu.seig.vibemusic.model.vo.PlaylistDetailVO;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
public interface IPlaylistService extends IService<Playlist> {

    // 获取所有歌单
    Result<PageResult<PlaylistVO>> getAllPlaylists(PlaylistDTO playlistDTO);

    // 获取所有歌单
    Result<PageResult<Playlist>> getAllPlaylistsInfo(PlaylistDTO playlistDTO);

    // 获取推荐歌单
    Result<List<PlaylistVO>> getRecommendedPlaylists(HttpServletRequest request);

    // 根据id获取歌单详情
    Result<PlaylistDetailVO> getPlaylistDetail(Long playlistId, HttpServletRequest request);

    // 获取所有歌单数量
    Result<Long> getAllPlaylistsCount(String style);

    // 添加歌单
    Result addPlaylist(PlaylistAddDTO playlistAddDTO);

    // 更新歌单
    Result updatePlaylist(PlaylistUpdateDTO playlistUpdateDTO);

    // 更新歌单封面
    Result updatePlaylistCover(Long playlistId, String coverUrl);

    // 删除歌单
    Result deletePlaylist(Long playlistId);

    // 批量删除歌单
    Result deletePlaylists(List<Long> playlistIds);

    // =================== 用户侧：我的歌单 ===================
    // 新增我的歌单（归属当前登录用户）
    Result<String> addUserPlaylist(PlaylistAddDTO playlistAddDTO);

    // 更新我的歌单（仅限本人）
    Result<String> updateUserPlaylist(PlaylistUpdateDTO playlistUpdateDTO);

    // 删除我的歌单（仅限本人）
    Result<String> deleteUserPlaylist(Long playlistId);

    // 我的歌单分页查询
    Result<PageResult<PlaylistVO>> getMyPlaylists(PlaylistDTO playlistDTO);

    // 向我的歌单添加歌曲（仅限本人）
    Result<String> addSongToMyPlaylist(Long playlistId, Long songId);

    // 从我的歌单移除歌曲（仅限本人）
    Result<String> removeSongFromMyPlaylist(Long playlistId, Long songId);

    // ================ 管理端：用户歌单只读 + 推荐开关 =================
    Result<PageResult<cn.edu.seig.vibemusic.model.vo.PlaylistVO>> getUserPlaylistsOnly(PlaylistDTO playlistDTO);
    Result<String> recommendPlaylist(Long playlistId, Integer weight);
    Result<String> cancelRecommendPlaylist(Long playlistId);

    // 固定推荐列表（管理端用于展示推荐状态）
    Result<java.util.List<PlaylistVO>> getPinnedRecommendedPlaylists(Integer limit);

}
