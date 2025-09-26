package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.entity.UserFavorite;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.model.vo.ArtistVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
public interface IUserFavoriteService extends IService<UserFavorite> {

    // 获取用户收藏的歌曲列表
    Result<PageResult<SongVO>> getUserFavoriteSongs(SongDTO songDTO);

    // 收藏歌曲
    Result<String> collectSong(Long songId);

    // 取消收藏歌曲
    Result<String> cancelCollectSong(Long songId);

    // 获取用户收藏的歌单列表
    Result<PageResult<PlaylistVO>> getUserFavoritePlaylists(PlaylistDTO playlistDTO);

    // 收藏歌单
    Result<String> collectPlaylist(Long playlistId);

    // 取消收藏歌单
    Result<String> cancelCollectPlaylist(Long playlistId);

    // 获取我关注的歌手列表
    Result<PageResult<ArtistVO>> getFollowArtists(Integer pageNum, Integer pageSize, String artistName);

    // 关注歌手
    Result<String> followArtist(Long artistId);

    // 取消关注歌手
    Result<String> cancelFollowArtist(Long artistId);

    // 是否已关注某歌手
    Result<Boolean> isArtistFollowed(Long artistId);

    // 收藏专辑
    Result<String> collectAlbum(Long albumId);

    // 取消收藏专辑
    Result<String> cancelCollectAlbum(Long albumId);

    // 获取用户收藏的专辑列表
    Result<PageResult<AlbumVO>> getUserFavoriteAlbums(Integer pageNum, Integer pageSize, String albumTitle);

    // 判断专辑是否已收藏（幂等查询）
    Result<Boolean> isAlbumCollected(Long albumId);

    // 公开：按 userId 查询其收藏（用于个人主页展示他人资料）
    Result<PageResult<SongVO>> getFavoriteSongsByUserId(Long userId, cn.edu.seig.vibemusic.model.dto.SongDTO songDTO);
    Result<PageResult<PlaylistVO>> getFavoritePlaylistsByUserId(Long userId, cn.edu.seig.vibemusic.model.dto.PlaylistDTO playlistDTO);

    // 公开：按 userId 查询其收藏的专辑
    Result<PageResult<AlbumVO>> getFavoriteAlbumsByUserId(Long userId, Integer pageNum, Integer pageSize, String albumTitle);

}
