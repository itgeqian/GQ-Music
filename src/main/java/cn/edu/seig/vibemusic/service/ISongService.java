package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.dto.SongAddDTO;
import cn.edu.seig.vibemusic.model.dto.SongAndArtistDTO;
import cn.edu.seig.vibemusic.model.dto.SongDTO;
import cn.edu.seig.vibemusic.model.dto.SongUpdateDTO;
import cn.edu.seig.vibemusic.model.entity.Song;
import cn.edu.seig.vibemusic.model.vo.SongAdminVO;
import cn.edu.seig.vibemusic.model.vo.SongBatchImportResultVO;
import cn.edu.seig.vibemusic.model.vo.SongDetailVO;
import cn.edu.seig.vibemusic.model.vo.SongVO;
import cn.edu.seig.vibemusic.model.vo.LyricLine;
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
public interface ISongService extends IService<Song> {

    // 获取所有歌曲
    Result<PageResult<SongVO>> getAllSongs(SongDTO songDTO, HttpServletRequest request);

    // 获取所有歌曲
    Result<PageResult<SongAdminVO>> getAllSongsByArtist(SongAndArtistDTO songDTO);

    // 获取推荐歌曲
    Result<List<SongVO>> getRecommendedSongs(HttpServletRequest request);

    // 根据id获取歌曲详情
    Result<SongDetailVO> getSongDetail(Long songId, HttpServletRequest request);

    // 获取所有歌曲数量
    Result<Long> getAllSongsCount(String style);

    // 添加歌曲信息
    Result<String> addSong(SongAddDTO songAddDTO);

    // 更新歌曲信息
    Result<String> updateSong(SongUpdateDTO songUpdateDTO);

    // 更新歌曲封面
    Result<String> updateSongCover(Long songId, String coverUrl);

    // 更新歌曲音频（并可更新时长）
    Result<String> updateSongAudio(Long songId, String audioUrl, org.springframework.web.multipart.MultipartFile audioFile);

    // 删除歌曲
    Result<String> deleteSong(Long songId);

    // 批量删除歌曲
    Result<String> deleteSongs(List<Long> songIds);

    // 更新歌曲歌词URL（并清理歌词缓存）
    Result<String> updateSongLyric(Long songId, String lyricUrl);

    // 获取某歌手已有专辑列表（去重）
    Result<List<String>> getAlbumsByArtist(Long artistId);

    // 按专辑ID获取歌曲列表
    Result<PageResult<SongVO>> getSongsByAlbumId(Long albumId, Integer pageNum, Integer pageSize);

    // 获取歌词（结构化）
    Result<List<LyricLine>> getLyric(Long songId);

    // 批量导入歌曲（支持文件上传）
    Result<SongBatchImportResultVO> batchImportSongsWithFiles(Long artistId, String albumName, String songNames, String songStyles,
                                                             java.util.List<org.springframework.web.multipart.MultipartFile> audioFiles,
                                                             java.util.List<org.springframework.web.multipart.MultipartFile> lyricFiles);

}
