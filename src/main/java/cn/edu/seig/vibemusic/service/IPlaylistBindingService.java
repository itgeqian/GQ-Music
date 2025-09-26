package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
public interface IPlaylistBindingService extends IService<PlaylistBinding> {
    cn.edu.seig.vibemusic.result.Result<String> addSongs(Long playlistId, java.util.List<Long> songIds);

    cn.edu.seig.vibemusic.result.Result<String> removeSongs(Long playlistId, java.util.List<Long> songIds);

    cn.edu.seig.vibemusic.result.Result<cn.edu.seig.vibemusic.result.PageResult<cn.edu.seig.vibemusic.model.vo.SongAdminVO>> getSongsOfPlaylist(
            cn.edu.seig.vibemusic.model.dto.PlaylistSongQueryDTO dto
    );

    // 排序能力已移除
}
