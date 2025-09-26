package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.model.dto.PlaylistAddDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistDTO;
import cn.edu.seig.vibemusic.model.dto.PlaylistUpdateDTO;
import cn.edu.seig.vibemusic.model.vo.PlaylistVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IPlaylistService;
import cn.edu.seig.vibemusic.service.MinioService;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户侧：我的歌单控制器
 */
@RestController
@RequestMapping("/user/playlist")
public class UserPlaylistController {

    @Autowired
    private IPlaylistService playlistService;
    @Autowired
    private MinioService minioService;
    @Autowired
    private cn.edu.seig.vibemusic.mapper.PlaylistMapper playlistMapper;

    /** 新建我的歌单 */
    @PostMapping("/create")
    public Result<String> create(@RequestBody @Valid PlaylistAddDTO dto) {
        return playlistService.addUserPlaylist(dto);
    }

    /** 更新我的歌单 */
    @PutMapping("/update")
    public Result<String> update(@RequestBody @Valid PlaylistUpdateDTO dto) {
        return playlistService.updateUserPlaylist(dto);
    }

    /** 删除我的歌单 */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable("id") Long playlistId) {
        return playlistService.deleteUserPlaylist(playlistId);
    }

    /** 我的歌单分页 */
    @PostMapping("/my")
    public Result<PageResult<PlaylistVO>> my(@RequestBody @Valid PlaylistDTO dto) {
        return playlistService.getMyPlaylists(dto);
    }

    /** 添加歌曲到我的歌单 */
    @PostMapping("/addSong")
    public Result<String> addSong(@RequestParam Long playlistId, @RequestParam Long songId) {
        return playlistService.addSongToMyPlaylist(playlistId, songId);
    }

    /** 从我的歌单移除歌曲 */
    @DeleteMapping("/removeSong")
    public Result<String> removeSong(@RequestParam Long playlistId, @RequestParam Long songId) {
        return playlistService.removeSongFromMyPlaylist(playlistId, songId);
    }

    /** 更新我的歌单封面（上传到 MinIO 后回写） */
    @PatchMapping("/updateCover/{id}")
    public Result<String> updateCover(@PathVariable("id") Long playlistId, @RequestParam("cover") MultipartFile cover) {
        String coverUrl = minioService.uploadFile(cover, "playlists");
        return playlistService.updatePlaylistCover(playlistId, coverUrl);
    }

    /** 公开：按 userId 查询他创建的歌单（用于个人主页） */
    @PostMapping("/byUser")
    public Result<PageResult<PlaylistVO>> byUser(@RequestParam Long userId, @RequestBody @Valid PlaylistDTO dto) {
        // 直接通过 mapper 分页查询指定用户创建的歌单
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<cn.edu.seig.vibemusic.model.entity.Playlist> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(dto.getPageNum(), dto.getPageSize());
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<cn.edu.seig.vibemusic.model.entity.Playlist> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.eq("user_id", userId);
        if (dto.getTitle() != null && !dto.getTitle().isEmpty()) qw.like("title", dto.getTitle());
        if (dto.getStyle() != null && !dto.getStyle().isEmpty()) qw.eq("style", dto.getStyle());
        com.baomidou.mybatisplus.core.metadata.IPage<cn.edu.seig.vibemusic.model.entity.Playlist> p = playlistMapper.selectPage(page, qw);
        java.util.List<PlaylistVO> items = p.getRecords() == null ? java.util.List.of() : p.getRecords().stream().map(pl -> {
            PlaylistVO vo = new PlaylistVO();
            org.springframework.beans.BeanUtils.copyProperties(pl, vo);
            return vo;
        }).toList();
        return Result.success(new PageResult<>(p.getTotal(), items));
    }
}


