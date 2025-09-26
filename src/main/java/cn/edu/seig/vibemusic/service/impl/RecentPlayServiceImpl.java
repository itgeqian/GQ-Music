package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.mapper.SongMapper;
import cn.edu.seig.vibemusic.mapper.UserRecentPlayMapper;
import cn.edu.seig.vibemusic.model.entity.Song;
import cn.edu.seig.vibemusic.model.entity.UserRecentPlay;
import cn.edu.seig.vibemusic.model.entity.Artist;
import cn.edu.seig.vibemusic.mapper.ArtistMapper;
import cn.edu.seig.vibemusic.result.PageResult;
import cn.edu.seig.vibemusic.service.IRecentPlayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecentPlayServiceImpl implements IRecentPlayService {

    private final UserRecentPlayMapper recentMapper;
    private final SongMapper songMapper;
    private final ArtistMapper artistMapper;

    private static final int MAX_RECENT = 200;

    @Override
    public void reportRecent(Long userId, Long songId) {
        if (userId == null || songId == null) return;
        // 去重：删除已有记录
        recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>()
                .eq(UserRecentPlay::getUserId, userId)
                .eq(UserRecentPlay::getSongId, songId));
        // 插入最新
        UserRecentPlay rec = new UserRecentPlay();
        rec.setUserId(userId);
        rec.setSongId(songId);
        rec.setCreateTime(LocalDateTime.now());
        recentMapper.insert(rec);
        // 超限截断（按最早的删除）
        int count = recentMapper.countByUser(userId);
        if (count > MAX_RECENT) {
            List<Long> ids = recentMapper.findOldestIds(userId, count - MAX_RECENT);
            if (ids != null && !ids.isEmpty()) {
                recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>().in(UserRecentPlay::getId, ids));
            }
        }
    }

    @Override
    public PageResult<?> page(Long userId, Integer pageNum, Integer pageSize) {
        Page<UserRecentPlay> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        Page<UserRecentPlay> res = recentMapper.selectPage(page, new LambdaQueryWrapper<UserRecentPlay>()
                .eq(UserRecentPlay::getUserId, userId)
                .orderByDesc(UserRecentPlay::getCreateTime));

        List<Long> songIds = res.getRecords().stream().map(UserRecentPlay::getSongId).collect(Collectors.toList());
        List<Song> songs = songIds.isEmpty() ? java.util.Collections.emptyList() : songMapper.selectBatchIds(songIds);
        Map<Long, Song> songMap = songs.stream().collect(Collectors.toMap(Song::getSongId, s -> s));
        // 批量查询歌手名称
        java.util.Set<Long> artistIds = songs.stream()
                .map(Song::getArtistId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Artist> artists = artistIds.isEmpty() ? java.util.Collections.emptyList() : artistMapper.selectBatchIds(artistIds);
        Map<Long, String> artistNameMap = artists.stream().collect(Collectors.toMap(Artist::getArtistId, Artist::getArtistName));

        List<Map<String, Object>> items = res.getRecords().stream().map(r -> {
            Song s = songMap.getOrDefault(r.getSongId(), null);
            java.util.HashMap<String, Object> m = new java.util.HashMap<>();
            m.put("songId", r.getSongId());
            m.put("songName", s != null ? s.getSongName() : "");
            Long aid = s != null ? s.getArtistId() : null;
            m.put("artistId", aid);
            m.put("artistName", s != null ? artistNameMap.getOrDefault(aid, "") : "");
            m.put("album", s != null ? s.getAlbum() : "");
            String durStr = s != null ? s.getDuration() : null;
            long seconds = 0;
            try {
                if (durStr != null && !durStr.isBlank()) {
                    seconds = Math.round(Double.parseDouble(durStr));
                }
            } catch (Exception ignore) {}
            m.put("duration", String.valueOf(seconds));
            m.put("coverUrl", s != null ? s.getCoverUrl() : null);
            m.put("audioUrl", s != null ? s.getAudioUrl() : null);
            m.put("style", s != null ? s.getStyle() : null);
            m.put("createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : null);
            // 附带歌手头像
            String artistAvatar = null;
            if (aid != null) {
                for (Artist a : artists) {
                    if (aid.equals(a.getArtistId())) { artistAvatar = a.getAvatar(); break; }
                }
            }
            m.put("artistAvatar", artistAvatar);
            m.put("likeStatus", 0);
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(res.getTotal(), items);
    }

    @Override
    public void removeOne(Long userId, Long songId) {
        if (userId == null || songId == null) return;
        recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>()
                .eq(UserRecentPlay::getUserId, userId)
                .eq(UserRecentPlay::getSongId, songId));
    }

    @Override
    public void clearAll(Long userId) {
        if (userId == null) return;
        recentMapper.delete(new LambdaQueryWrapper<UserRecentPlay>()
                .eq(UserRecentPlay::getUserId, userId));
    }
}


