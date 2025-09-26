package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.model.entity.PlaylistBinding;
import cn.edu.seig.vibemusic.mapper.PlaylistBindingMapper;
import cn.edu.seig.vibemusic.service.IPlaylistBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;

import cn.edu.seig.vibemusic.result.Result;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.model.vo.SongAdminVO;
import cn.edu.seig.vibemusic.model.vo.PlaylistSongVO;
import cn.edu.seig.vibemusic.model.dto.PlaylistSongQueryDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author geqian
 * @since 2025-01-09
 */
@Service
public class PlaylistBindingServiceImpl extends ServiceImpl<PlaylistBindingMapper, PlaylistBinding> implements IPlaylistBindingService {
    @Autowired
    private PlaylistBindingMapper bindingMapper;

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> addSongs(Long playlistId, java.util.List<Long> songIds) {
        if (playlistId == null || songIds == null || songIds.isEmpty()) return Result.success("OK");
        java.util.List<PlaylistBinding> rows = songIds.stream().distinct().map(id -> {
            PlaylistBinding b = new PlaylistBinding();
            b.setPlaylistId(playlistId);
            b.setSongId(id);
            return b;
        }).toList();
        // 使用自定义 SQL：INSERT IGNORE，拿到受影响行数
        int affected = 0;
        try {
            affected = bindingMapper.insertBatchIgnore(rows);
        } catch (Exception e) {
            // 回退：逐条保存（已存在会失败，忽略），统计成功数量
            for (PlaylistBinding r : rows) {
                try {
                    boolean ok = this.save(r);
                    if (ok) affected++;
                } catch (Exception ignored) {}
            }
        }
        int requested = rows.size();
        int ignored = Math.max(0, requested - affected);
        String msg = ignored == 0 ? ("添加成功，新增 " + affected + " 首") : ("已添加 " + affected + " 首，" + ignored + " 首已在歌单中");
        return Result.success(msg);
    }

    @Override
    @CacheEvict(cacheNames = "playlistCache", allEntries = true)
    public Result<String> removeSongs(Long playlistId, java.util.List<Long> songIds) {
        if (playlistId == null || songIds == null || songIds.isEmpty()) return Result.success("OK");
        remove(new LambdaQueryWrapper<PlaylistBinding>()
                .eq(PlaylistBinding::getPlaylistId, playlistId)
                .in(PlaylistBinding::getSongId, songIds));
        return Result.success("删除成功");
    }

    @Override
    public Result<PageResult<SongAdminVO>> getSongsOfPlaylist(PlaylistSongQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 10 : dto.getPageSize();
        // 使用自定义 SQL 查询所有结果，再在内存分页（简化实现）
        java.util.List<PlaylistSongVO> all = bindingMapper.selectSongsOfPlaylist(dto.getPlaylistId(), dto.getKeyword());
        java.util.List<SongAdminVO> mapped = all.stream().map(p -> {
            SongAdminVO v = new SongAdminVO();
            v.setSongId(p.getSongId());
            v.setSongName(p.getSongName());
            v.setArtistName(p.getArtistName());
            v.setAlbum(p.getAlbum());
            v.setCoverUrl(p.getCoverUrl());
            v.setAudioUrl(p.getAudioUrl());
            v.setReleaseTime(p.getReleaseTime());
            return v;
        }).toList();

        long total = mapped.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(mapped.size(), from + pageSize);
        java.util.List<SongAdminVO> pageItems = from >= to ? java.util.List.of() : mapped.subList(from, to);
        return Result.success(new PageResult<>(total, pageItems));
    }

    // 排序能力已移除
}
