package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.model.vo.ArtistVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IUserFavoriteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@RestController
@RequestMapping("/favorite")
public class UserFavoriteController {

    @Autowired
    private IUserFavoriteService userFavoriteService;

    /**
     * 获取用户收藏的歌曲列表
     *
     * @return 用户收藏的歌曲列表
     */
    @PostMapping("/getFavoriteSongs")
    public Result<PageResult<SongVO>> getUserFavoriteSongs(@RequestBody @Valid SongDTO songDTO) {
        return userFavoriteService.getUserFavoriteSongs(songDTO);
    }

    /**
     * 收藏歌曲
     *
     * @param songId 歌曲id
     * @return 收藏结果
     */
    @PostMapping("/collectSong")
    public Result<String> collectSong(@RequestParam Long songId) {
        return userFavoriteService.collectSong(songId);
    }

    /**
     * 取消收藏歌曲
     *
     * @param songId 歌曲id
     * @return 取消收藏结果
     */
    @DeleteMapping("/cancelCollectSong")
    public Result<String> cancelCollectSong(@RequestParam Long songId) {
        return userFavoriteService.cancelCollectSong(songId);
    }

    /**
     * 获取用户收藏的歌单列表
     *
     * @return 用户收藏的歌单列表
     */
    @PostMapping("/getFavoritePlaylists")
    public Result<PageResult<PlaylistVO>> getFavoritePlaylists(@RequestBody @Valid PlaylistDTO playlistDTO) {
        return userFavoriteService.getUserFavoritePlaylists(playlistDTO);
    }

    /**
     * 收藏歌单
     *
     * @param playlistId 歌单id
     * @return 收藏结果
     */
    @PostMapping("/collectPlaylist")
    public Result<String> collectPlaylist(@RequestParam Long playlistId) {
        return userFavoriteService.collectPlaylist(playlistId);
    }

    /**
     * 取消收藏歌单
     *
     * @param playlistId 歌单id
     * @return 取消收藏结果
     */
    @DeleteMapping("/cancelCollectPlaylist")
    public Result<String> cancelCollectPlaylist(@RequestParam Long playlistId) {
        return userFavoriteService.cancelCollectPlaylist(playlistId);
    }

    /**
     * 获取我关注的歌手
     */
    @GetMapping("/getFollowArtists")
    public Result<PageResult<ArtistVO>> getFollowArtists(@RequestParam Integer pageNum,
                                                         @RequestParam Integer pageSize,
                                                         @RequestParam(required = false) String artistName) {
        return userFavoriteService.getFollowArtists(pageNum, pageSize, artistName);
    }

    /** 关注歌手 */
    @PostMapping("/followArtist")
    public Result<String> followArtist(@RequestParam Long artistId) {
        return userFavoriteService.followArtist(artistId);
    }

    /** 取消关注歌手 */
    @DeleteMapping("/cancelFollowArtist")
    public Result<String> cancelFollowArtist(@RequestParam Long artistId) {
        return userFavoriteService.cancelFollowArtist(artistId);
    }

    /** 是否已关注歌手 */
    @GetMapping("/isArtistFollowed")
    public Result<Boolean> isArtistFollowed(@RequestParam Long artistId) {
        return userFavoriteService.isArtistFollowed(artistId);
    }

    /** 收藏专辑 */
    @PostMapping("/collectAlbum")
    public Result<String> collectAlbum(@RequestParam Long albumId) {
        return userFavoriteService.collectAlbum(albumId);
    }

    /** 取消收藏专辑 */
    @DeleteMapping("/cancelCollectAlbum")
    public Result<String> cancelCollectAlbum(@RequestParam Long albumId) {
        return userFavoriteService.cancelCollectAlbum(albumId);
    }

    /** 获取用户收藏的专辑列表 */
    @GetMapping("/getFavoriteAlbums")
    public Result<PageResult<AlbumVO>> getFavoriteAlbums(@RequestParam Integer pageNum,
                                                         @RequestParam Integer pageSize,
                                                         @RequestParam(required = false) String albumTitle) {
        return userFavoriteService.getUserFavoriteAlbums(pageNum, pageSize, albumTitle);
    }

    /** 判断专辑是否已收藏 */
    @GetMapping("/isAlbumCollected")
    public Result<Boolean> isAlbumCollected(@RequestParam Long albumId) {
        return userFavoriteService.isAlbumCollected(albumId);
    }

    /** 公开：按 userId 查询其收藏的专辑列表 */
    @GetMapping("/getFavoriteAlbumsByUser")
    public Result<PageResult<AlbumVO>> getFavoriteAlbumsByUser(@RequestParam Long userId,
                                                               @RequestParam Integer pageNum,
                                                               @RequestParam Integer pageSize,
                                                               @RequestParam(required = false) String albumTitle) {
        return userFavoriteService.getFavoriteAlbumsByUserId(userId, pageNum, pageSize, albumTitle);
    }

    /** 公开：按 userId 查询其收藏的歌曲（用于他人主页） */
    @PostMapping("/getFavoriteSongsByUser")
    public Result<PageResult<SongVO>> getFavoriteSongsByUser(@RequestParam Long userId, @RequestBody @Valid SongDTO songDTO) {
        return userFavoriteService.getFavoriteSongsByUserId(userId, songDTO);
    }

    /** 公开：按 userId 查询其收藏的歌单（用于他人主页） */
    @PostMapping("/getFavoritePlaylistsByUser")
    public Result<PageResult<PlaylistVO>> getFavoritePlaylistsByUser(@RequestParam Long userId, @RequestBody @Valid PlaylistDTO playlistDTO) {
        return userFavoriteService.getFavoritePlaylistsByUserId(userId, playlistDTO);
    }
}
