package cn.edu.seig.vibemusic.controller;

import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.service.IAlbumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/album")
public class AlbumController {

    @Autowired
    private IAlbumService albumService;

    /** 获取某歌手的专辑分页列表 */
    @GetMapping("/getAlbumsByArtist")
    public Result<PageResult<AlbumVO>> getAlbumsByArtist(@RequestParam Long artistId,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "20") Integer pageSize) {
        return albumService.getAlbumsByArtist(artistId, pageNum, pageSize);
    }

    /** 获取专辑详情 */
    @GetMapping("/getAlbumDetail/{id}")
    public Result<AlbumVO> getAlbumDetail(@PathVariable("id") Long albumId) {
        return albumService.getAlbumDetail(albumId);
    }
}


