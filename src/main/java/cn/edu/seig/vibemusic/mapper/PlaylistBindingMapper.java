package cn.edu.seig.vibemusic.mapper;

import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import cn.edu.seig.vibemusic.model.vo.PlaylistSongVO;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Mapper
public interface PlaylistBindingMapper extends BaseMapper<PlaylistBinding> {
    int insertBatchIgnore(@Param("list") java.util.List<PlaylistBinding> list);

    java.util.List<PlaylistSongVO> selectSongsOfPlaylist(@Param("playlistId") Long playlistId,
                                                        @Param("keyword") String keyword);

    // 排序已移除
}
