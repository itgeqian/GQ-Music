package cn.edu.seig.vibemusic.controller;


import cn.edu.seig.vibemusic.model.dto.*;
import cn.edu.seig.vibemusic.model.entity.Artist;
import cn.edu.seig.vibemusic.model.entity.Playlist;
import cn.edu.seig.vibemusic.model.vo.ArtistNameVO;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.model.vo.SongBatchImportResultVO;
import cn.edu.seig.vibemusic.model.dto.AlbumDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumAddDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumUpdateDTO;
import cn.edu.seig.vibemusic.model.vo.SongAdminVO;
import cn.edu.seig.vibemusic.model.vo.UserManagementVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.*;
import cn.edu.seig.vibemusic.util.BindingResultUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author   geqian
 * @since 2025-01-09
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private IAdminService adminService;
    @Autowired
    private IUserService userService;
    @Autowired
    private IArtistService artistService;
    @Autowired
    private ISongService songService;
    @Autowired
    private IPlaylistService playlistService;
    @Autowired
    private MinioService minioService;
    @Autowired
    private IAlbumService albumService;
    @Autowired
    private cn.edu.seig.vibemusic.mapper.SongMapper songMapper;
    @Autowired
    private ICommentService commentService;
    @Autowired
    private IPlaylistBindingService playlistBindingService;

    /**
     * 注册管理员
     *
     * @param adminDTO      管理员信息
     * @param bindingResult 绑定结果
     * @return 结果
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Valid AdminDTO adminDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return adminService.register(adminDTO);
    }

    /**
     * 登录管理员
     *
     * @param adminDTO      管理员信息
     * @param bindingResult 绑定结果
     * @return 结果
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid AdminDTO adminDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return adminService.login(adminDTO);
    }

    /**
     * 登出
     *
     * @param token 认证token
     * @return 结果
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        return adminService.logout(token);
    }

    /**********************************************************************************************/

    /**
     * 获取所有用户数量
     *
     * @return 用户数量
     */
    @GetMapping("/getAllUsersCount")
    public Result<Long> getAllUsersCount() {
        return userService.getAllUsersCount();
    }

    /**
     * 获取所有用户信息
     *
     * @param userSearchDTO 用户搜索条件
     * @return 结果
     */
    @PostMapping("/getAllUsers")
    public Result<PageResult<UserManagementVO>> getAllUsers(@RequestBody UserSearchDTO userSearchDTO) {
        return userService.getAllUsers(userSearchDTO);
    }

    /**
     * 新增用户
     *
     * @param userAddDTO 用户注册信息
     * @return 结果
     */
    @PostMapping("/addUser")
    public Result<String> addUser(@RequestBody @Valid UserAddDTO userAddDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return userService.addUser(userAddDTO);
    }

    /**
     * 更新用户信息
     *
     * @param userDTO 用户信息
     * @return 结果
     */
    @PutMapping("/updateUser")
    public Result<String> updateUser(@RequestBody @Valid UserDTO userDTO, BindingResult bindingResult) {
        // 校验失败时，返回错误信息
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) {
            return Result.error(errorMessage);
        }

        return userService.updateUser(userDTO);
    }

    /**
     * 更新用户状态
     *
     * @param userId     用户id
     * @param userStatus 用户状态
     * @return 结果
     */
    @PatchMapping("/updateUserStatus/{id}/{status}")
    public Result<String> updateUserStatus(@PathVariable("id") Long userId, @PathVariable("status") Integer userStatus) {
        return userService.updateUserStatus(userId, userStatus);
    }

    /**
     * 删除用户
     *
     * @param userId 用户id
     * @return 结果
     */
    @DeleteMapping("/deleteUser/{id}")
    public Result<String> deleteUser(@PathVariable("id") Long userId) {
        return userService.deleteUser(userId);
    }

    /**
     * 批量删除用户
     *
     * @param userIds 用户id列表
     * @return 结果
     */
    @DeleteMapping("/deleteUsers")
    public Result<String> deleteUsers(@RequestBody List<Long> userIds) {
        return userService.deleteUsers(userIds);
    }

    /**********************************************************************************************/

    /**
     * 管理端：分页获取专辑列表
     */
    @PostMapping("/getAllAlbums")
    public Result<PageResult<AlbumVO>> getAllAlbums(@RequestBody AlbumDTO albumDTO) {
        return albumService.getAllAlbums(albumDTO);
    }

    /** 管理端：新增专辑 */
    @PostMapping("/addAlbum")
    public Result<String> addAlbum(@RequestBody @Valid AlbumAddDTO albumAddDTO, BindingResult bindingResult) {
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) return Result.error(errorMessage);
        return albumService.addAlbum(albumAddDTO);
    }

    /** 管理端：更新专辑 */
    @PutMapping("/updateAlbum")
    public Result<String> updateAlbum(@RequestBody @Valid AlbumUpdateDTO albumUpdateDTO, BindingResult bindingResult) {
        String errorMessage = BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) return Result.error(errorMessage);
        return albumService.updateAlbum(albumUpdateDTO);
    }

    /** 管理端：更新专辑封面 */
    @PatchMapping("/updateAlbumCover/{id}")
    public Result<String> updateAlbumCover(@PathVariable("id") Long albumId, @RequestParam("cover") MultipartFile cover) {
        String coverUrl = minioService.uploadFile(cover, "albums");
        AlbumUpdateDTO dto = new AlbumUpdateDTO();
        dto.setAlbumId(albumId);
        dto.setCoverUrl(coverUrl);
        return albumService.updateAlbum(dto);
    }

    /** 管理端：删除专辑 */
    @DeleteMapping("/deleteAlbum/{id}")
    public Result<String> deleteAlbum(@PathVariable("id") Long albumId) {
        return albumService.deleteAlbum(albumId);
    }

    /** 管理端：批量删除专辑 */
    @DeleteMapping("/deleteAlbums")
    public Result<String> deleteAlbums(@RequestBody java.util.List<Long> albumIds) {
        return albumService.deleteAlbums(albumIds);
    }

    /** 管理端：删除任意评论（专辑/歌曲/歌单通用） */
    @DeleteMapping("/deleteComment/{id}")
    public Result<String> deleteAnyComment(@PathVariable("id") Long commentId) {
        return commentService.deleteComment(commentId);
    }

    /** 管理端：获取专辑评论列表（避免/comment/权限问题） */
    @GetMapping("/getAlbumComments")
    public Result<java.util.List<cn.edu.seig.vibemusic.model.vo.CommentVO>> getAlbumCommentsAdmin(@RequestParam Long albumId) {
        return commentService.getAlbumComments(albumId);
    }

    /**
     * 获取所有专辑数量
     *
     * @return 专辑数量
     */
    @GetMapping("/getAllAlbumsCount")
    public Result<Long> getAllAlbumsCount() {
        return albumService.getAllAlbumsCount();
    }

    /** 管理端：获取歌曲评论列表 */
    @GetMapping("/getSongComments")
    public Result<java.util.List<cn.edu.seig.vibemusic.model.vo.CommentVO>> getSongCommentsAdmin(@RequestParam Long songId) {
        return commentService.getSongComments(songId);
    }

    /** 管理端：获取歌单评论列表 */
    @GetMapping("/getPlaylistComments")
    public Result<java.util.List<cn.edu.seig.vibemusic.model.vo.CommentVO>> getPlaylistCommentsAdmin(@RequestParam Long playlistId) {
        return commentService.getPlaylistComments(playlistId);
    }

    /**********************************************************************************************/
    /**
     * 歌单-歌曲绑定：批量添加歌曲到歌单
     */
    @PostMapping("/playlist/addSongs")
    public Result<String> addSongsToPlaylist(@RequestBody @Valid cn.edu.seig.vibemusic.model.dto.PlaylistSongBatchDTO dto,
                                             BindingResult bindingResult) {
        String errorMessage = cn.edu.seig.vibemusic.util.BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) return Result.error(errorMessage);
        return playlistBindingService.addSongs(dto.getPlaylistId(), dto.getSongIds());
    }

    /** 歌单-歌曲绑定：批量从歌单移除歌曲 */
    @DeleteMapping("/playlist/removeSongs")
    public Result<String> removeSongsFromPlaylist(@RequestBody @Valid cn.edu.seig.vibemusic.model.dto.PlaylistSongBatchDTO dto,
                                                  BindingResult bindingResult) {
        String errorMessage = cn.edu.seig.vibemusic.util.BindingResultUtil.handleBindingResultErrors(bindingResult);
        if (errorMessage != null) return Result.error(errorMessage);
        return playlistBindingService.removeSongs(dto.getPlaylistId(), dto.getSongIds());
    }

    /** 歌单-歌曲绑定：分页查询歌单内的歌曲 */
    @PostMapping("/playlist/songs")
    public Result<PageResult<cn.edu.seig.vibemusic.model.vo.SongAdminVO>> getSongsOfPlaylist(@RequestBody cn.edu.seig.vibemusic.model.dto.PlaylistSongQueryDTO dto) {
        return playlistBindingService.getSongsOfPlaylist(dto);
    }

    // 排序能力已移除

    /**
     * 获取所有歌手数量
     *
     * @param gender 性别
     * @param area   地区
     * @return 歌手数量
     */
    @GetMapping("/getAllArtistsCount")
    public Result<Long> getAllArtistsCount(@RequestParam(required = false) Integer gender, @RequestParam(required = false) String area) {
        return artistService.getAllArtistsCount(gender, area);
    }

    /**
     * 获取所有歌手信息
     *
     * @param artistDTO 歌手搜索条件
     * @return 结果
     */
    @PostMapping("/getAllArtists")
    public Result<PageResult<Artist>> getAllArtists(@RequestBody ArtistDTO artistDTO) {
        return artistService.getAllArtistsAndDetail(artistDTO);
    }

    /**
     * 新增歌手
     *
     * @param artistAddDTO 歌手信息
     * @return 结果
     */
    @PostMapping("/addArtist")
    public Result<String> addArtist(@RequestBody ArtistAddDTO artistAddDTO) {
        return artistService.addArtist(artistAddDTO);
    }

    /**
     * 更新歌手信息
     *
     * @param artistUpdateDTO 歌手信息
     * @return 结果
     */
    @PutMapping("/updateArtist")
    public Result<String> updateArtist(@RequestBody ArtistUpdateDTO artistUpdateDTO) {
        return artistService.updateArtist(artistUpdateDTO);
    }

    /**
     * 更新歌手头像
     *
     * @param artistId 歌手id
     * @param avatar   头像
     * @return 结果
     */
    @PatchMapping("/updateArtistAvatar/{id}")
    public Result<String> updateArtistAvatar(@PathVariable("id") Long artistId, @RequestParam("avatar") MultipartFile avatar) {
        String avatarUrl = minioService.uploadFile(avatar, "artists");  // 上传到 artists 目录
        return artistService.updateArtistAvatar(artistId, avatarUrl);
    }

    /**
     * 删除歌手
     *
     * @param artistId 歌手id
     * @return 结果
     */
    @DeleteMapping("/deleteArtist/{id}")
    public Result<String> deleteArtist(@PathVariable("id") Long artistId) {
        return artistService.deleteArtist(artistId);
    }

    /**
     * 批量删除歌手
     *
     * @param artistIds 歌手id列表
     * @return 结果
     */
    @DeleteMapping("/deleteArtists")
    public Result<String> deleteArtists(@RequestBody List<Long> artistIds) {
        return artistService.deleteArtists(artistIds);
    }

    /**********************************************************************************************/

    /**
     * 获取所有歌曲的数量
     *
     * @param style 歌曲风格
     * @return 歌曲数量
     */
    @GetMapping("/getAllSongsCount")
    public Result<Long> getAllSongsCount(@RequestParam(required = false) String style) {
        return songService.getAllSongsCount(style);
    }

    /**
     * 获取所有歌手id和名称
     *
     * @return 结果
     */
    @GetMapping("/getAllArtistNames")
    public Result<List<ArtistNameVO>> getAllArtistNames() {
        return artistService.getAllArtistNames();
    }

    /**
     * 根据歌手id获取其歌曲信息
     *
     * @param songDTO 歌曲搜索条件
     * @return 结果
     */
    @PostMapping("/getAllSongsByArtist")
    public Result<PageResult<SongAdminVO>> getAllSongsByArtist(@RequestBody SongAndArtistDTO songDTO) {
        return songService.getAllSongsByArtist(songDTO);
    }

    /**
     * 添加歌曲信息
     *
     * @param songAddDTO 歌曲信息
     * @return 结果
     */
    @PostMapping("/addSong")
    public Result<String> addSong(@RequestBody SongAddDTO songAddDTO) {
        return songService.addSong(songAddDTO);
    }

    /**
     * 修改歌曲信息
     *
     * @param songUpdateDTO 歌曲信息
     * @return 结果
     */
    @PutMapping("/updateSong")
    public Result<String> UpdateSong(@RequestBody SongUpdateDTO songUpdateDTO) {
        return songService.updateSong(songUpdateDTO);
    }

    /**
     * 更新歌曲封面
     *
     * @param songId 歌曲id
     * @param cover  封面
     * @return 结果
     */
    @PatchMapping("/updateSongCover/{id}")
    public Result<String> updateSongCover(@PathVariable("id") Long songId, @RequestParam("cover") MultipartFile cover) {
        String coverUrl = minioService.uploadFile(cover, "songCovers");  // 上传到 songCovers 目录
        return songService.updateSongCover(songId, coverUrl);
    }

    /**
     * 更新歌曲音频
     *
     * @param songId 歌曲id
     * @param audio  音频
     * @return 结果
     */
    @PatchMapping("/updateSongAudio/{id}")
    public Result<String> updateSongAudio(@PathVariable("id") Long songId, @RequestParam("audio") MultipartFile audio) {
        // 简单的服务端白名单校验：仅允许 mp3 / flac
        String name = audio.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (!(lower.endsWith(".mp3") || lower.endsWith(".flac"))) {
                return Result.error("仅支持 mp3 或 flac 音频");
            }
        }
        String audioUrl = minioService.uploadFile(audio, "songs");  // 上传到 songs 目录
        return songService.updateSongAudio(songId, audioUrl, audio);
    }

    /** 管理端：更新歌曲歌词（上传 .lrc 到 MinIO 并写入 lyric_url） */
    @PatchMapping("/updateSongLyric/{id}")
    public Result<String> updateSongLyric(@PathVariable("id") Long songId, @RequestParam("lyric") MultipartFile lyric) {
        String lyricUrl = minioService.uploadFile(lyric, "songLyrics");  // 上传到 songLyrics 目录
        return songService.updateSongLyric(songId, lyricUrl);
    }

    /**
     * 删除歌曲
     *
     * @param songId 歌曲id
     * @return 结果
     */
    @DeleteMapping("/deleteSong/{id}")
    public Result<String> deleteSong(@PathVariable("id") Long songId) {
        return songService.deleteSong(songId);
    }

    /**
     * 批量删除歌曲
     *
     * @param songIds 歌曲id列表
     * @return 结果
     */
    @DeleteMapping("/deleteSongs")
    public Result<String> deleteSongs(@RequestBody List<Long> songIds) {
        return songService.deleteSongs(songIds);
    }

    /**
     * 获取某歌手已有专辑列表（去重）
     */
    @GetMapping("/getAlbumsByArtist/{id}")
    public Result<List<String>> getAlbumsByArtist(@PathVariable("id") Long artistId) {
        return albumService.getAlbumTitlesByArtist(artistId);
    }

    /** 管理端：根据歌手与专辑标题获取专辑发行日期（用于前端回显） */
    @GetMapping("/getAlbumReleaseDate")
    public Result<String> getAlbumReleaseDate(@RequestParam("artistId") Long artistId,
                                              @RequestParam("title") String title) {
        var album = albumService.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Album>()
                .eq("artist_id", artistId)
                .eq("title", title)
                .last("limit 1"));
        if (album == null || album.getReleaseDate() == null) return Result.success("OK", null);
        return Result.success("OK", album.getReleaseDate().toString());
    }

    /** 管理端：根据歌手与专辑标题猜测发行日期（从已存在歌曲中取最早一首的日期，用于新增专辑表单回显） */
    @GetMapping("/guessAlbumReleaseDate")
    public Result<String> guessAlbumReleaseDate(@RequestParam("artistId") Long artistId,
                                                @RequestParam("title") String title) {
        var one = songMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Song>()
                .eq("artist_id", artistId)
                .eq("album", title)
                .isNotNull("release_time")
                .orderByAsc("release_time")
                .last("limit 1"));
        if (one == null || one.getReleaseTime() == null) return Result.success("OK", null);
        return Result.success("OK", one.getReleaseTime().toString());
    }


    /**
     * 批量导入歌曲
     *
     * @param artistId 歌手ID
     * @param albumName 专辑名称
     * @param songNames 歌曲名称列表（逗号分隔）
     * @param songStyles 歌曲风格列表（逗号分隔）
     * @param audioFiles 音频文件列表
     * @param lyricFiles 歌词文件列表（可选）
     * @return 导入结果
     */
    @PostMapping("/batchImportSongs")
    public Result<SongBatchImportResultVO> batchImportSongs(
            @RequestParam("artistId") Long artistId,
            @RequestParam("albumName") String albumName,
            @RequestParam("songNames") String songNames,
            @RequestParam("songStyles") String songStyles,
            @RequestParam(value = "audioFiles", required = false) List<MultipartFile> audioFiles,
            @RequestParam(value = "lyricFiles", required = false) List<MultipartFile> lyricFiles) {
        
        if (artistId == null) {
            return Result.error("歌手ID不能为空");
        }
        
        if (albumName == null || albumName.trim().isEmpty()) {
            return Result.error("专辑名称不能为空");
        }
        
        if (songNames == null || songNames.trim().isEmpty()) {
            return Result.error("歌曲名称不能为空");
        }
        
        if (songStyles == null || songStyles.trim().isEmpty()) {
            return Result.error("歌曲风格不能为空");
        }
        
        return songService.batchImportSongsWithFiles(artistId, albumName, songNames, songStyles, audioFiles, lyricFiles);
    }

    /**********************************************************************************************/

    /**
     * 获取所有歌单数量
     *
     * @param style 歌单风格
     * @return 歌单数量
     */
    @GetMapping("/getAllPlaylistsCount")
    public Result<Long> getAllPlaylistsCount(@RequestParam(required = false) String style) {
        return playlistService.getAllPlaylistsCount(style);
    }

    /**
     * 获取所有歌单信息
     *
     * @param playlistDTO 歌单搜索条件
     * @return 结果
     */
    @PostMapping("/getAllPlaylists")
    public Result<PageResult<Playlist>> getAllPlaylists(@RequestBody PlaylistDTO playlistDTO) {
        return playlistService.getAllPlaylistsInfo(playlistDTO);
    }

    /** 管理端：用户歌单只读列表 */
    @PostMapping("/getUserPlaylists")
    public Result<PageResult<cn.edu.seig.vibemusic.model.vo.PlaylistVO>> getUserPlaylists(@RequestBody PlaylistDTO playlistDTO) {
        return playlistService.getUserPlaylistsOnly(playlistDTO);
    }

    /** 管理端：设为推荐 */
    @PostMapping("/recommendPlaylist/{id}")
    public Result<String> recommendPlaylist(@PathVariable("id") Long playlistId, @RequestParam(required = false) Integer weight) {
        return playlistService.recommendPlaylist(playlistId, weight);
    }

    /** 管理端：取消推荐 */
    @DeleteMapping("/cancelRecommendPlaylist/{id}")
    public Result<String> cancelRecommendPlaylist(@PathVariable("id") Long playlistId) {
        return playlistService.cancelRecommendPlaylist(playlistId);
    }

    /** 管理端：获取固定推荐列表（用于展示推荐状态） */
    @GetMapping("/pinnedPlaylists")
    public Result<java.util.List<cn.edu.seig.vibemusic.model.vo.PlaylistVO>> pinnedPlaylists(@RequestParam(required = false) Integer limit) {
        return playlistService.getPinnedRecommendedPlaylists(limit);
    }

    /**
     * 新增歌单
     *
     * @param playlistAddDTO 歌单信息
     * @return 结果
     */
    @PostMapping("/addPlaylist")
    public Result<String> addPlaylist(@RequestBody PlaylistAddDTO playlistAddDTO) {
        return playlistService.addPlaylist(playlistAddDTO);
    }

    /**
     * 更新歌单信息
     *
     * @param playlistUpdateDTO 歌单信息
     * @return 结果
     */
    @PutMapping("/updatePlaylist")
    public Result<String> updatePlaylist(@RequestBody PlaylistUpdateDTO playlistUpdateDTO) {
        return playlistService.updatePlaylist(playlistUpdateDTO);
    }

    /**
     * 更新歌单封面
     *
     * @param playlistId 歌单id
     * @param cover      封面
     * @return 结果
     */
    @PatchMapping("/updatePlaylistCover/{id}")
    public Result<String> updatePlaylistCover(@PathVariable("id") Long playlistId, @RequestParam("cover") MultipartFile cover) {
        String coverUrl = minioService.uploadFile(cover, "playlists");  // 上传到 playlists 目录
        return playlistService.updatePlaylistCover(playlistId, coverUrl);
    }

    /**
     * 删除歌单
     *
     * @param playlistId 歌单id
     * @return 结果
     */
    @DeleteMapping("/deletePlaylist/{id}")
    public Result<String> deletePlaylist(@PathVariable("id") Long playlistId) {
        return playlistService.deletePlaylist(playlistId);
    }

    /**
     * 批量删除歌单
     *
     * @param playlistIds 歌单id列表
     * @return 结果
     */
    @DeleteMapping("/deletePlaylists")
    public Result<String> deletePlaylists(@RequestBody List<Long> playlistIds) {
        return playlistService.deletePlaylists(playlistIds);
    }

}
