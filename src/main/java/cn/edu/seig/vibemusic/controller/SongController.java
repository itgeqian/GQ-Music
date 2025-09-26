package cn.edu.seig.vibemusic.controller;


import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.vo.SongDetailVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.model.vo.LyricLine;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.ISongService;
import cn.edu.seig.vibemusic.service.HotSearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@RestController
@RequestMapping("/song")
public class SongController {

    @Autowired
    private ISongService songService;
    @Autowired
    private HotSearchService hotSearchService;

    /**
     * 获取所有歌曲
     *
     * @param songDTO songDTO
     * @return 歌曲列表
     */
    @PostMapping("/getAllSongs")
    public Result<PageResult<SongVO>> getAllSongs(@RequestBody @Valid SongDTO songDTO, HttpServletRequest request) {
        // 记录热搜计数（即使命中缓存也要累加）
        if (songDTO.getKeyword() != null && !songDTO.getKeyword().isEmpty()) {
            try { hotSearchService.increaseKeyword(songDTO.getKeyword().trim().toLowerCase()); } catch (Exception ignored) {}
        }
        return songService.getAllSongs(songDTO, request);
    }

    /**
     * 获取推荐歌曲
     * 推荐歌曲的数量为 20
     *
     * @param request 请求
     * @return 推荐歌曲列表
     */
    @GetMapping("/getRecommendedSongs")
    public Result<List<SongVO>> getRecommendedSongs(HttpServletRequest request) {
        return songService.getRecommendedSongs(request);
    }

    /**
     * 获取歌曲详情
     *
     * @param songId 歌曲id
     * @return 歌曲详情
     */
    @GetMapping("/getSongDetail/{id}")
    public Result<SongDetailVO> getSongDetail(@PathVariable("id") Long songId, HttpServletRequest request) {
        return songService.getSongDetail(songId, request);
    }

    /**
     * 按专辑ID获取歌曲列表
     */
    @GetMapping("/getSongsByAlbumId")
    public Result<PageResult<SongVO>> getSongsByAlbumId(@RequestParam Long albumId,
                                                        @RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "50") Integer pageSize) {
        return songService.getSongsByAlbumId(albumId, pageNum, pageSize);
    }

    /**
     * 获取歌词（公开）
     */
    @GetMapping("/getLyric/{songId}")
    public Result<List<LyricLine>> getLyric(@PathVariable Long songId) {
        return songService.getLyric(songId);
    }


}
