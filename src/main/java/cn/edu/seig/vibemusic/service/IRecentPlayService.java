package cn.edu.seig.vibemusic.service;

import cn.edu.seig.vibemusic.result.PageResult;

public interface IRecentPlayService {
    void reportRecent(Long userId, Long songId);
    PageResult<?> page(Long userId, Integer pageNum, Integer pageSize);
    void removeOne(Long userId, Long songId);
    void clearAll(Long userId);
}


