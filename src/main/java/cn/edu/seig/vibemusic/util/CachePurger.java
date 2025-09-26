package cn.edu.seig.vibemusic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CachePurger {

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CachePurger(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 通用：按前缀批量删除
    private void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // 删除与用户推荐/榜单等有关的列表缓存
    private void purgeRecommendationLists() {
        deleteByPrefix("recommended_songs:");
    }

    public void purgeForArtist(Long artistId) {
        purgeRecommendationLists();
    }

    public void purgeForAlbum(Long albumId) {
        purgeRecommendationLists();
    }

    public void purgeForSong(Long songId) {
        purgeRecommendationLists();
    }

    public void purgeForPlaylist(Long playlistId) {
        purgeRecommendationLists();
    }
}


