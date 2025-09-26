package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.entity.Album;
import cn.edu.seig.vibemusic.model.vo.AlbumVO;
import cn.edu.seig.vibemusic.model.dto.AlbumDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumAddDTO;
import cn.edu.seig.vibemusic.model.dto.AlbumUpdateDTO;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.result.Result;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IAlbumService extends IService<Album> {

    Result<PageResult<AlbumVO>> getAlbumsByArtist(Long artistId, Integer pageNum, Integer pageSize);

    Result<AlbumVO> getAlbumDetail(Long albumId);

    // 管理端表单：按歌手获取专辑标题列表
    Result<java.util.List<String>> getAlbumTitlesByArtist(Long artistId);

    // 统计专辑下歌曲数量
    Result<Long> getAlbumSongCount(Long albumId);

    // 统计所有专辑数量
    Result<Long> getAllAlbumsCount();

    // 管理端：分页获取专辑列表
    Result<cn.edu.seig.vibemusic.result.PageResult<AlbumVO>> getAllAlbums(AlbumDTO albumDTO);

    // 管理端：新增专辑
    Result addAlbum(AlbumAddDTO albumAddDTO);

    // 管理端：更新专辑
    Result updateAlbum(AlbumUpdateDTO albumUpdateDTO);

    // 管理端：删除专辑
    Result deleteAlbum(Long albumId);

    // 管理端：批量删除专辑
    Result deleteAlbums(java.util.List<Long> albumIds);
}


