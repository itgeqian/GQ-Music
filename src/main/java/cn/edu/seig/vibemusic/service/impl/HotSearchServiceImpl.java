package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.RedisKeyConstant;
import cn.edu.seig.vibemusic.service.HotSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class HotSearchServiceImpl implements HotSearchService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void increaseKeyword(String keyword) {
        if (keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) return;
        stringRedisTemplate.opsForZSet().incrementScore(RedisKeyConstant.HOT_SEARCH_ZSET, trimmed, 1D);
    }

    @Override
    public List<String> getTopKeywords(int topN) {
        if (topN <= 0) return List.of();
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRange(RedisKeyConstant.HOT_SEARCH_ZSET, 0, topN - 1);
        if (set == null || set.isEmpty()) return List.of();
        return new ArrayList<>(set);
    }

    @Override
    public void clearAll() {
        stringRedisTemplate.delete(RedisKeyConstant.HOT_SEARCH_ZSET);
    }
}


